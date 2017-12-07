package org.eclipse.lsp4e.debug.debugmodel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.eclipse.core.runtime.Assert;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IStackFrame;
import org.eclipse.debug.core.model.IThread;
import org.eclipse.lsp4j.debug.ContinueArguments;
import org.eclipse.lsp4j.debug.NextArguments;
import org.eclipse.lsp4j.debug.StackFrame;
import org.eclipse.lsp4j.debug.StackTraceArguments;
import org.eclipse.lsp4j.debug.StackTraceResponse;
import org.eclipse.lsp4j.debug.Thread;

public class DSPThread extends DSPDebugElement implements IThread {
	private final Integer id;
	private String name;
	private List<DSPStackFrame> frames;

	public DSPThread(DSPDebugTarget debugTarget, Thread thread) {
		super(debugTarget);
		this.id = thread.getId();
		this.name = thread.getName();
	}

	/**
	 * Update properties of the thread. The ID can't be changed, so the passed in
	 * thread should match.
	 * 
	 * @param thread
	 * @throws DebugException
	 */
	public void update(Thread thread) throws DebugException {
		Assert.isTrue(Objects.equals(this.id, thread.getId()));
		this.name = thread.getName();
		calculateFrames();
	}

	private void calculateFrames() throws DebugException {
		if (frames == null) {
			frames = new ArrayList<DSPStackFrame>();
		}
		StackTraceArguments arguments = new StackTraceArguments();
		arguments.setThreadId(id);
		arguments.setStartFrame(0);
		arguments.setLevels(20);
		CompletableFuture<StackTraceResponse> future = getDebugTarget().debugProtocolServer.stackTrace(arguments);
		StackTraceResponse stackTraceResposeBody = complete(future);

		StackFrame[] backendFrames = stackTraceResposeBody.getStackFrames();
		for (int i = 0; i < backendFrames.length; i++) {
			if (i < frames.size()) {
				frames.set(i, frames.get(i).replace(backendFrames[i], i));
			} else {
				frames.add(new DSPStackFrame(this, backendFrames[i], i));
			}
		}
		frames.subList(backendFrames.length, frames.size()).clear();
	}

	@Override
	public void terminate() throws DebugException {
		getDebugTarget().terminate();
	}

	@Override
	public boolean isTerminated() {
		return getDebugTarget().isTerminated();
	}

	@Override
	public boolean canTerminate() {
		return getDebugTarget().canTerminate();
	}

	@Override
	public void stepReturn() throws DebugException {
	}

	@Override
	public void stepOver() throws DebugException {
		getDebugTarget().fireResumeEvent(DebugEvent.STEP_OVER);
		NextArguments arguments = new NextArguments();
		arguments.setThreadId(id);
		complete(getDebugTarget().debugProtocolServer.next(arguments));
		// TODO: move this to after getting response...
		// getDebugTarget().fireSuspendEvent(DebugEvent.STEP_OVER);
	}

	@Override
	public void stepInto() throws DebugException {
	}

	@Override
	public boolean isStepping() {
		return false;
	}

	@Override
	public boolean canStepReturn() {
		return false;
	}

	@Override
	public boolean canStepOver() {
		return true;
	}

	@Override
	public boolean canStepInto() {
		return false;
	}

	@Override
	public void suspend() throws DebugException {
	}

	@Override
	public void resume() throws DebugException {
		ContinueArguments arguments = new ContinueArguments();
		arguments.setThreadId(id);
		complete(getDebugTarget().debugProtocolServer.continue_(arguments));
		getDebugTarget().fireResumeEvent(0);
	}

	@Override
	public boolean isSuspended() {
		return getDebugTarget().isSuspended();
	}

	@Override
	public boolean canSuspend() {
		return false;
	}

	@Override
	public boolean canResume() {
		return true;
	}

	@Override
	public String getModelIdentifier() {
		return getDebugTarget().getModelIdentifier();
	}

	@Override
	public ILaunch getLaunch() {
		return getDebugTarget().getLaunch();
	}

	@Override
	public boolean hasStackFrames() throws DebugException {
		return true;
	}

	@Override
	public IStackFrame getTopStackFrame() throws DebugException {
		if (frames == null) {
			calculateFrames();
		}
		return frames.get(0);
	}

	@Override
	public IStackFrame[] getStackFrames() throws DebugException {
		if (frames == null) {
			calculateFrames();
		}
		return frames.toArray(new IStackFrame[frames.size()]);
	}

	@Override
	public int getPriority() throws DebugException {
		return 0;
	}

	@Override
	public String getName() throws DebugException {
		return name;
	}

	@Override
	public IBreakpoint[] getBreakpoints() {
		return new IBreakpoint[0];
	}
}