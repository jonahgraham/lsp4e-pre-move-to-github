package org.eclipse.lsp4e.debug.sourcelookup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupParticipant;
import org.eclipse.lsp4e.debug.debugmodel.DSPStackFrame;

public class DSPSourceLookupParticipant extends AbstractSourceLookupParticipant {

	@Override
	public String getSourceName(Object object) throws CoreException {
		if (object instanceof DSPStackFrame) {
			return ((DSPStackFrame) object).getSourceName();
		}
		return null;
	}

}
