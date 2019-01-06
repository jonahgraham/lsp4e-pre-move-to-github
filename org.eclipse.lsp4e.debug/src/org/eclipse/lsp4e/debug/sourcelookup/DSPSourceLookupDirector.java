/*******************************************************************************
 * Copyright (c) 2017 Kichwa Coders Ltd. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.lsp4e.debug.sourcelookup;

import org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector;
import org.eclipse.debug.core.sourcelookup.ISourceLookupParticipant;

public class DSPSourceLookupDirector extends AbstractSourceLookupDirector {

	public static final String ID = "org.eclipse.lsp4e.debug.sourceLocator";

	@Override
	public void initializeParticipants() {
		addParticipants(new ISourceLookupParticipant[] { new DSPSourceLookupParticipant() });

	}

}
