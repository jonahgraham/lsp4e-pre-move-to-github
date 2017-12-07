package org.eclipse.lsp4e.debug.debugmodel;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IBreakpointManagerListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IMemoryBlock;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.lsp4j.debug.BreakpointEventArguments;
import org.eclipse.lsp4j.debug.ConfigurationDoneArguments;
import org.eclipse.lsp4j.debug.ContinuedEventArguments;
import org.eclipse.lsp4j.debug.DisconnectArguments;
import org.eclipse.lsp4j.debug.ExitedEventArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.LoadedSourceEventArguments;
import org.eclipse.lsp4j.debug.ModuleEventArguments;
import org.eclipse.lsp4j.debug.OutputEventArguments;
import org.eclipse.lsp4j.debug.ProcessEventArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsArguments;
import org.eclipse.lsp4j.debug.SetBreakpointsResponse;
import org.eclipse.lsp4j.debug.Source;
import org.eclipse.lsp4j.debug.SourceBreakpoint;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.eclipse.lsp4j.debug.TerminatedEventArguments;
import org.eclipse.lsp4j.debug.Thread;
import org.eclipse.lsp4j.debug.ThreadEventArguments;
import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer;
import org.eclipse.lsp4j.jsonrpc.Launcher;

public class DSPDebugTarget extends DSPDebugElement
		implements IBreakpointManagerListener, IDebugTarget, IDebugProtocolClient {

	private ILaunch launch;
	private Process process;

	private boolean fTerminated = false;
	private boolean fSuspended = false;
	private String targetName = null;

	private Map<Integer, DSPThread> threads = new HashMap<>();

	private Future<?> debugProtocolFuture;
	IDebugProtocolServer debugProtocolServer;
	private Map<Source, List<SourceBreakpoint>> targetBreakpoints = new HashMap<>();

	public DSPDebugTarget(ILaunch launch, Process process, InputStream in, OutputStream out,
			Map<String, Object> launchArguments) throws CoreException {
		super(null);
		this.launch = launch;
		this.process = process;

		Launcher<IDebugProtocolServer> debugProtocolLauncher = DSPLauncher.createClientLauncher(this, in, out, true,
				new PrintWriter(System.out));

		debugProtocolFuture = debugProtocolLauncher.startListening();
		debugProtocolServer = debugProtocolLauncher.getRemoteProxy();

		InitializeRequestArguments arguments = new InitializeRequestArguments();
		arguments.setClientID("lsp4e.debug");
		arguments.setAdapterID((String) launchArguments.get("type"));
		arguments.setPathFormat("path");
		complete(debugProtocolServer.initialize(arguments));

		Object object = launchArguments.get("program");
		targetName = Objects.toString(object, "Debug Adapter Target");

		complete(debugProtocolServer.launch(launchArguments));
		complete(debugProtocolServer.configurationDone(new ConfigurationDoneArguments()));
		IBreakpointManager breakpointManager = getBreakpointManager();
		breakpointManager.addBreakpointListener(this);
		breakpointManager.addBreakpointManagerListener(this);
		breakpointManagerEnablementChanged(breakpointManager.isEnabled());
	}

	/**
	 * Throws a debug exception with a status code of
	 * <code>TARGET_REQUEST_FAILED</code>.
	 *
	 * @param message
	 *            exception message
	 * @param e
	 *            underlying exception or <code>null</code>
	 * @throws DebugException
	 *             if a problem is encountered
	 */
	@Override
	protected void requestFailed(String message, Throwable e) throws DebugException {
		throw newTargetRequestFailedException(message, e);
	}

	@Override
	public DSPDebugTarget getDebugTarget() {
		return this;
	}

	@Override
	public ILaunch getLaunch() {
		return launch;
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		return null;
	}

	@Override
	public boolean canTerminate() {
		return true;
	}

	@Override
	public boolean isTerminated() {
		return fTerminated || (process != null && !process.isAlive());
	}

	@Override
	public void terminated(TerminatedEventArguments body) {
		terminated();
	}

	@Override
	public void terminate() throws DebugException {
		DisconnectArguments arguments = new DisconnectArguments();
		arguments.setTerminateDebuggee(true);
		complete(debugProtocolServer.disconnect(arguments));
		terminated();
	}

	private void terminated() {
		fTerminated = true;
		IBreakpointManager breakpointManager = getBreakpointManager();
		breakpointManager.removeBreakpointListener(this);
		breakpointManager.removeBreakpointManagerListener(this);
		fireTerminateEvent();
	}

	@Override
	public void continued(ContinuedEventArguments body) {
		fSuspended = false;
		fireResumeEvent(DebugEvent.UNSPECIFIED);
	}

	@Override
	public void stopped(StoppedEventArguments body) {
		fSuspended = true;
		DSPDebugElement source = null;
		if (body.getThreadId() != null) {
			source = getThread(body.getThreadId());
		}
		if (source == null) {
			source = this;
		}
		fireEvent(new DebugEvent(source, DebugEvent.SUSPEND, calcDetail(body.getReason())));
	}

	private int calcDetail(String reason) {
		if (reason.equals("breakpoint")) { //$NON-NLS-1$
			return DebugEvent.BREAKPOINT;
		} else if (reason.equals("step")) { //$NON-NLS-1$
			return DebugEvent.STEP_OVER;
			// } else if (reason.equals("exception")) { //$NON-NLS-1$
			// return DebugEvent.STEP_RETURN;
		} else if (reason.equals("pause")) { //$NON-NLS-1$
			return DebugEvent.CLIENT_REQUEST;
			// } else if (reason.equals("event")) { //$NON-NLS-1$
			// return DebugEvent.BREAKPOINT;
		} else {
			return DebugEvent.UNSPECIFIED;
		}
	}

	@Override
	public boolean canResume() {
		return !isTerminated() && isSuspended();
	}

	@Override
	public boolean canSuspend() {
		return !isTerminated() && !isSuspended();
	}

	@Override
	public boolean isSuspended() {
		return fSuspended;
	}

	@Override
	public void resume() throws DebugException {
	}

	@Override
	public void suspend() throws DebugException {
	}

	@Override
	public boolean canDisconnect() {
		return false;
	}

	@Override
	public void disconnect() throws DebugException {
	}

	@Override
	public boolean isDisconnected() {
		return false;
	}

	@Override
	public boolean supportsStorageRetrieval() {
		return false;
	}

	@Override
	public IMemoryBlock getMemoryBlock(long startAddress, long length) throws DebugException {
		return null;
	}

	@Override
	public IProcess getProcess() {
		return null;
	}

	@Override
	public synchronized DSPThread[] getThreads() throws DebugException {
		// TODO use thread event in combination to keep track
		Thread[] body = complete(debugProtocolServer.threads()).getThreads();
		for (Thread thread : body) {
			DSPThread dspThread = threads.computeIfAbsent(thread.getId(), id -> new DSPThread(this, thread));
			dspThread.update(thread);
		}
		Collection<DSPThread> values = threads.values();
		return values.toArray(new DSPThread[values.size()]);
	}

	public DSPThread getThread(Integer threadId) {
		return threads.get(threadId);
	}

	@Override
	public boolean hasThreads() throws DebugException {
		return true;
	}

	@Override
	public String getName() {
		return targetName;
	}

	@Override
	public boolean supportsBreakpoint(IBreakpoint breakpoint) {
		if (!isTerminated() && breakpoint instanceof ILineBreakpoint) {
			return true;
		}
		return false;
	}

	/**
	 * When the breakpoint manager disables, remove all registered breakpoints
	 * requests from the VM. When it enables, reinstall them.
	 */
	@Override
	public void breakpointManagerEnablementChanged(boolean enabled) {
		try {
			IBreakpoint[] breakpoints = getBreakpointManager().getBreakpoints(getModelIdentifier());
			for (IBreakpoint breakpoint : breakpoints) {
				if (supportsBreakpoint(breakpoint)) {
					if (enabled) {
						addBreakpointToMap(breakpoint);
					} else {
						deleteBreakpointFromMap(breakpoint);
					}
				}
			}
			sendBreakpoints();
		} catch (CoreException e) {
			// TODO
			e.printStackTrace();
		}
	}

	@Override
	public void breakpointAdded(IBreakpoint breakpoint) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if ((breakpoint.isEnabled() && getBreakpointManager().isEnabled()) || !breakpoint.isRegistered()) {
					addBreakpointToMap(breakpoint);
					sendBreakpoints();
				}
			} catch (CoreException e) {
				// TODO
				e.printStackTrace();
			}
		}
	}

	@Override
	public void breakpointRemoved(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				deleteBreakpointFromMap(breakpoint);
				sendBreakpoints();
			} catch (CoreException e) {
				// TODO
				e.printStackTrace();
			}
		}
	}

	@Override
	public void breakpointChanged(IBreakpoint breakpoint, IMarkerDelta delta) {
		if (supportsBreakpoint(breakpoint)) {
			try {
				if (breakpoint.isEnabled() && getBreakpointManager().isEnabled()) {
					breakpointAdded(breakpoint);
				} else {
					breakpointRemoved(breakpoint, null);
				}
			} catch (CoreException e) {
			}
		}
	}

	private void addBreakpointToMap(IBreakpoint breakpoint) throws CoreException {
		Assert.isTrue(supportsBreakpoint(breakpoint) && breakpoint instanceof ILineBreakpoint);
		if (breakpoint instanceof ILineBreakpoint) {
			ILineBreakpoint lineBreakpoint = (ILineBreakpoint) breakpoint;
			IResource resource = lineBreakpoint.getMarker().getResource();
			IPath location = resource.getLocation();
			String path = location.toOSString();
			String name = location.lastSegment();
			int lineNumber = lineBreakpoint.getLineNumber();

			Source source = new Source();
			source.setName(name);
			source.setPath(path);

			List<SourceBreakpoint> sourceBreakpoints = targetBreakpoints.computeIfAbsent(source,
					s -> new ArrayList<>());
			SourceBreakpoint sourceBreakpoint = new SourceBreakpoint();
			sourceBreakpoint.setLine(lineNumber);
			sourceBreakpoints.add(sourceBreakpoint);
		}
	}

	private void deleteBreakpointFromMap(IBreakpoint breakpoint) throws CoreException {
		Assert.isTrue(supportsBreakpoint(breakpoint) && breakpoint instanceof ILineBreakpoint);
		if (breakpoint instanceof ILineBreakpoint) {
			ILineBreakpoint lineBreakpoint = (ILineBreakpoint) breakpoint;
			IResource resource = lineBreakpoint.getMarker().getResource();
			IPath location = resource.getLocation();
			String path = location.toOSString();
			String name = location.lastSegment();
			int lineNumber = lineBreakpoint.getLineNumber();
			for (Entry<Source, List<SourceBreakpoint>> entry : targetBreakpoints.entrySet()) {
				Source source = entry.getKey();
				if (Objects.equals(name, source.getName()) && Objects.equals(path, source.getPath())) {
					List<SourceBreakpoint> bps = entry.getValue();
					for (Iterator<SourceBreakpoint> iterator = bps.iterator(); iterator.hasNext();) {
						SourceBreakpoint sourceBreakpoint = (SourceBreakpoint) iterator.next();
						if (Objects.equals(lineNumber, sourceBreakpoint.getLine())) {
							iterator.remove();
						}
					}
				}
			}
		}
	}

	private void deleteAllBreakpointsFromMap() {
		for (Entry<Source, List<SourceBreakpoint>> entry : targetBreakpoints.entrySet()) {
			entry.getValue().clear();
		}
	}

	private void sendBreakpoints() throws DebugException {
		for (Iterator<Entry<Source, List<SourceBreakpoint>>> iterator = targetBreakpoints.entrySet()
				.iterator(); iterator.hasNext();) {
			Entry<Source, List<SourceBreakpoint>> entry = iterator.next();

			Source source = entry.getKey();
			List<SourceBreakpoint> bps = entry.getValue();
			Integer[] lines = bps.stream().map(sb -> sb.getLine()).toArray(Integer[]::new);
			SourceBreakpoint[] sourceBps = bps.toArray(new SourceBreakpoint[bps.size()]);

			SetBreakpointsArguments arguments = new SetBreakpointsArguments();
			arguments.setSource(source);
			arguments.setLines(lines);
			arguments.setBreakpoints(sourceBps);
			arguments.setSourceModified(false);
			CompletableFuture<SetBreakpointsResponse> future = debugProtocolServer.setBreakpoints(arguments);
			// TODO handle install info about breakpoint
			complete(future);

			// Once we told adapter there are no breakpoints for a source file, we can stop
			// tracking that file
			if (bps.isEmpty()) {
				iterator.remove();
			}
		}
	}

	@Override
	public void initialized() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void exited(ExitedEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void thread(ThreadEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void output(OutputEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void breakpoint(BreakpointEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void module(ModuleEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void loadedSource(LoadedSourceEventArguments args) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void process(ProcessEventArguments args) {
		// TODO Auto-generated method stub
		
	}
}
