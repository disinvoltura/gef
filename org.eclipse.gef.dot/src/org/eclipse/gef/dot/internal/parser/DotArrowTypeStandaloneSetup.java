/*******************************************************************************
 * Copyright (c) 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *******************************************************************************/
package org.eclipse.gef.dot.internal.parser;

import org.eclipse.gef.dot.internal.parser.DotArrowTypeStandaloneSetupGenerated;

/**
 * Initialization support for running Xtext languages 
 * without equinox extension registry
 */
public class DotArrowTypeStandaloneSetup extends DotArrowTypeStandaloneSetupGenerated{

	public static void doSetup() {
		new DotArrowTypeStandaloneSetup().createInjectorAndDoEMFRegistration();
	}
}

