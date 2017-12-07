package org.eclipse.lsp4e.debug.breakpoints;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.LineBreakpoint;
import org.eclipse.lsp4e.debug.DSPPlugin;

public class DSPLineBreakpoint extends LineBreakpoint {

	public static final String ID = "org.eclipse.lsp4e.debug.breakpoints.markerType.lineBreakpoint";

	public DSPLineBreakpoint() {
	}

	public DSPLineBreakpoint(final IResource resource, final int lineNumber) throws CoreException {
		run(getMarkerRule(resource), monitor -> {
			IMarker marker = resource.createMarker(ID);
			setMarker(marker);
			marker.setAttribute(IBreakpoint.ENABLED, Boolean.TRUE);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
			marker.setAttribute(IBreakpoint.ID, getModelIdentifier());
			marker.setAttribute(IMarker.MESSAGE, resource.getName() + " [line: " + lineNumber + "]");
		});
	}

	@Override
	public String getModelIdentifier() {
		return DSPPlugin.ID_DSP_DEBUG_MODEL;
	}
}
