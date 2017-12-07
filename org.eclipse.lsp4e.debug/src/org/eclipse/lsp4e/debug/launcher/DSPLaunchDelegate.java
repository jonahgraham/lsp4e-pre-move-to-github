package org.eclipse.lsp4e.debug.launcher;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.ILaunchConfigurationDelegate;
import org.eclipse.lsp4e.debug.DSPPlugin;
import org.eclipse.lsp4e.debug.debugmodel.DSPDebugTarget;
import org.eclipse.osgi.util.NLS;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class DSPLaunchDelegate implements ILaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		boolean launchNotConnect = DSPPlugin.DSP_MODE_LAUNCH
				.equals(configuration.getAttribute(DSPPlugin.ATTR_DSP_MODE, DSPPlugin.DSP_MODE_LAUNCH));

		String launchArgumentJson = configuration.getAttribute(DSPPlugin.ATTR_DSP_PARAM, (String) null);
		Gson gson = new Gson();
		Type type = new TypeToken<Map<String, Object>>() {
		}.getType();
		Map<String, Object> launchArguments = gson.fromJson(launchArgumentJson, type);

		// DSP supports run/debug as a simple flag to the debug server.
		// See LaunchRequestArguments.noDebug
		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			launchArguments.put("noDebug", false);
		} else if (ILaunchManager.RUN_MODE.equals(mode)) {
			launchArguments.put("noDebug", true);
		} else {
			abort(NLS.bind("Unsupported launch mode '{0}'.", mode), null);
		}

		// TODO close socket
		Socket socket = null;
		// TODO kill process
		Process process = null;
		InputStream inputStream;
		OutputStream outputStream;
		try {

			if (launchNotConnect) {
				List<String> command = new ArrayList<>();
				String debugCmd = configuration.getAttribute(DSPPlugin.ATTR_DSP_CMD, (String) null);

				if (debugCmd == null) {
					abort("Debug command unspecified.", null); //$NON-NLS-1$
				}
				command.add(debugCmd);
				List<String> debugArgs = configuration.getAttribute(DSPPlugin.ATTR_DSP_ARGS, (List<String>) null);
				if (debugArgs != null && !debugArgs.isEmpty()) {
					command.addAll(debugArgs);
				}

				ProcessBuilder processBuilder = new ProcessBuilder(command);
				process = processBuilder.start();
				inputStream = process.getInputStream();
				outputStream = process.getOutputStream();
			} else {
				String server = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_HOST, (String) null);

				if (server == null) {
					abort("Debug server host unspecified.", null); //$NON-NLS-1$
				}
				int port = configuration.getAttribute(DSPPlugin.ATTR_DSP_SERVER_PORT, 0);

				if (port < 1 || port > 65535) {
					abort("Debug server port unspecified or out of range 1-65535.", null); //$NON-NLS-1$
				}
				socket = new Socket("127.0.0.1", 4711);
				inputStream = socket.getInputStream();
				outputStream = socket.getOutputStream();
			}

			IDebugTarget target = new DSPDebugTarget(launch, process, inputStream, outputStream, launchArguments);
			launch.addDebugTarget(target);
		} catch (IOException e1) {
			abort("Failed to launch debug process", e1);
		}
	}

	/**
	 * Throws an exception with a new status containing the given message and
	 * optional exception.
	 *
	 * @param message
	 *            error message
	 * @param e
	 *            underlying exception
	 * @throws CoreException
	 */
	private void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, DSPPlugin.PLUGIN_ID, 0, message, e));
	}

}
