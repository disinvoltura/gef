/*******************************************************************************
	 * Copyright (c) 2016 itemis AG and others.
	 * All rights reserved. This program and the accompanying materials
	 * are made available under the terms of the Eclipse Public License v1.0
	 * which accompanies this distribution, and is available at
	 * http://www.eclipse.org/legal/epl-v10.html
	 *
	 * Contributors:
	 *     Matthias Wienand (itemis AG) - initial API and implementation
	 *
	 *******************************************************************************/
package org.eclipse.gef.mvc.fx.policies;

import org.eclipse.gef.mvc.policies.IPolicy;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;

/**
 * The {@link IFXOnStrokePolicy} can be used to process key presses and
 * releases. The policy starts processing when the first key is pressed, and
 * stops processing when the last key is released. The initial key press and
 * final key release are handled separately to the presses and releases
 * in-between.
 * <p>
 * If you are interested in typed characters or in a single combination of
 * pressed keys, you can use {@link IFXOnTypePolicy} instead.
 *
 * @author mwienand
 *
 */
public interface IFXOnStrokePolicy extends IPolicy<Node> {

	/**
	 * This callback method is invoked when the viewer loses its focus while a
	 * key press/release gesture is running.
	 */
	void abortPress();

	/**
	 * This callback method is invoked when the user releases a key while the
	 * host has keyboard focus.
	 *
	 * @param event
	 *            The original {@link KeyEvent}.
	 */
	void finalRelease(KeyEvent event);

	/**
	 * This callback method is invoked when the user presses a key while the
	 * host has keyboard focus.
	 *
	 * @param event
	 *            The original {@link KeyEvent}.
	 */
	void initialPress(KeyEvent event);

	/**
	 * This callback method is invoked when the user presses a key while a
	 * keyboard gesture is active, i.e. after the initial press (
	 * {@link #initialPress(KeyEvent)}) and before the final release
	 * ({@link #finalRelease(KeyEvent)}).
	 *
	 * @param event
	 *            The original {@link KeyEvent}.
	 */
	void press(KeyEvent event);

	/**
	 * This callback method is invoked when the user releases a key while a
	 * keyboard gesture is active, i.e. after the initial press (
	 * {@link #initialPress(KeyEvent)}) and before the final release
	 * ({@link #finalRelease(KeyEvent)}).
	 *
	 * @param event
	 *            The original {@link KeyEvent}.
	 */
	void release(KeyEvent event);

}
