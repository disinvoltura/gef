/*******************************************************************************
 * Copyright (c) 2014, 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef.mvc.fx.policies;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.gef.fx.nodes.Connection;
import org.eclipse.gef.fx.nodes.OrthogonalRouter;
import org.eclipse.gef.fx.utils.NodeUtils;
import org.eclipse.gef.geometry.planar.Dimension;
import org.eclipse.gef.geometry.planar.Point;
import org.eclipse.gef.geometry.planar.Rectangle;
import org.eclipse.gef.mvc.models.GridModel;
import org.eclipse.gef.mvc.models.SelectionModel;
import org.eclipse.gef.mvc.parts.IContentPart;
import org.eclipse.gef.mvc.viewer.IViewer;

import com.google.common.reflect.TypeToken;

import javafx.scene.Node;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * The {@link FXTranslateSelectedOnDragPolicy} is an {@link IFXOnDragPolicy}
 * that relocates its {@link #getHost() host} when it is dragged with the mouse.
 *
 * @author anyssen
 *
 */
public class FXTranslateSelectedOnDragPolicy extends AbstractFXInteractionPolicy
		implements IFXOnDragPolicy {

	private CursorSupport cursorSupport = new CursorSupport(this);
	private SnapSupport snapSupport = new SnapSupport(this);
	private Point initialMouseLocationInScene = null;
	private Map<IContentPart<Node, ? extends Node>, Integer> translationIndices = new HashMap<>();
	private List<IContentPart<Node, ? extends Node>> targetParts;

	// gesture validity
	private boolean invalidGesture = false;

	private Map<IContentPart<Node, ? extends Node>, Rectangle> boundsInScene = new IdentityHashMap<>();

	@Override
	public void abortDrag() {
		if (invalidGesture) {
			return;
		}
		// roll back changes for all target parts
		for (IContentPart<Node, ? extends Node> part : targetParts) {
			FXTransformPolicy policy = getTransformPolicy(part);
			if (policy != null) {
				rollback(policy);
				restoreRefreshVisuals(part);
			}
		}
		// reset target parts
		targetParts = null;
		// reset initial pointer location
		setInitialMouseLocationInScene(null);
		// reset translation indices
		translationIndices.clear();
	}

	@Override
	public void drag(MouseEvent e, Dimension delta) {
		// abort this policy if no target parts could be found
		if (invalidGesture) {
			return;
		}
		// determine viewer
		IViewer<Node> viewer = getHost().getRoot().getViewer();
		// prepare data for snap-to-grid
		Node gridLocalVisual = null;
		GridModel gridModel = null;
		double granularityX = 0d;
		double granularityY = 0d;
		if (!isPrecise(e)) {
			granularityX = snapSupport.getSnapToGridGranularityX();
			granularityY = snapSupport.getSnapToGridGranularityY();
			gridModel = viewer.getAdapter(GridModel.class);
			gridLocalVisual = snapSupport.getGridLocalVisual(viewer);
		}
		// apply changes to the target parts
		for (IContentPart<Node, ? extends Node> part : targetParts) {
			FXTransformPolicy policy = getTransformPolicy(part);
			if (policy != null) {
				// determine start and end position in scene coordinates
				Point startInScene = boundsInScene.get(part).getTopLeft();
				Point endInScene = startInScene.getTranslated(delta);
				// snap to grid
				Point newEndInScene = endInScene.getCopy();
				if (gridLocalVisual != null) {
					newEndInScene = snapSupport.snapToGrid(endInScene.x,
							endInScene.y, gridModel, granularityX, granularityY,
							gridLocalVisual);
				}
				// compute delta in parent coordinates
				Point newEndInParent = NodeUtils.sceneToLocal(
						part.getVisual().getParent(), newEndInScene);
				Point startInParent = NodeUtils.sceneToLocal(
						part.getVisual().getParent(), startInScene);
				Point deltaInParent = newEndInParent
						.getTranslated(startInParent.getNegated());
				// update transformation
				policy.setPostTranslate(translationIndices.get(part),
						deltaInParent.x, deltaInParent.y);
			}
		}
	}

	@Override
	public void endDrag(MouseEvent e, Dimension delta) {
		// abort this policy if no target parts could be found
		if (invalidGesture) {
			return;
		}

		// commit changes for all target parts
		for (IContentPart<Node, ? extends Node> part : targetParts) {
			FXTransformPolicy policy = getTransformPolicy(part);
			if (policy != null) {
				commit(policy);
				restoreRefreshVisuals(part);
			}
		}

		// reset target parts
		targetParts = null;
		// reset initial pointer location
		setInitialMouseLocationInScene(null);
		// reset translation indices
		translationIndices.clear();
	}

	/**
	 * Returns the {@link CursorSupport} of this policy.
	 *
	 * @return The {@link CursorSupport} of this policy.
	 */
	protected CursorSupport getCursorSupport() {
		return cursorSupport;
	}

	/**
	 * Returns the initial mouse location in scene coordinates.
	 *
	 * @return The initial mouse location in scene coordinates.
	 */
	protected Point getInitialMouseLocationInScene() {
		return initialMouseLocationInScene;
	}

	/**
	 * Returns a {@link List} containing all {@link IContentPart}s that should
	 * be relocated by this policy.
	 *
	 * @return A {@link List} containing all {@link IContentPart}s that should
	 *         be relocated by this policy.
	 */
	@SuppressWarnings("serial")
	protected List<IContentPart<Node, ? extends Node>> getTargetParts() {
		return getHost().getRoot().getViewer()
				.getAdapter(new TypeToken<SelectionModel<Node>>() {
				}).getSelectionUnmodifiable();
	}

	/**
	 * Returns the {@link FXTransformPolicy} that is installed on the given
	 * {@link IContentPart}.
	 *
	 * @param part
	 *            The {@link IContentPart} for which to return the installed
	 *            {@link FXTransformPolicy}.
	 * @return The {@link FXTransformPolicy} that is installed on the given
	 *         {@link IContentPart}.
	 */
	protected FXTransformPolicy getTransformPolicy(
			IContentPart<Node, ? extends Node> part) {
		return part.getAdapter(FXTransformPolicy.class);
	}

	@Override
	public void hideIndicationCursor() {
		getCursorSupport().restoreCursor();
	}

	/**
	 * Returns <code>true</code> if precise manipulations should be performed
	 * for the given {@link MouseEvent}. Otherwise returns <code>false</code>.
	 *
	 * @param e
	 *            The {@link MouseEvent} that is used to determine if precise
	 *            manipulations should be performed (i.e. if the corresponding
	 *            modifier key is pressed).
	 * @return <code>true</code> if precise manipulations should be performed,
	 *         <code>false</code> otherwise.
	 */
	protected boolean isPrecise(MouseEvent e) {
		return e.isShortcutDown();
	}

	/**
	 * Returns whether the given {@link MouseEvent} should trigger translation.
	 * Per default, will return <code>true</code> if we have more than one
	 * target part or the single target part does not have a connection with an
	 * orthogonal router.
	 *
	 * @param event
	 *            The {@link MouseEvent} in question.
	 * @return <code>true</code> if the given {@link MouseEvent} should trigger
	 *         translation, otherwise <code>false</code>.
	 */
	// TODO (bug #493418): This condition needs improvement
	protected boolean isTranslate(MouseEvent event) {
		// do not translate the only selected part if an
		// FXBendOnSegmentDragPolicy is registered for that part and the part is
		// an orthogonal connection that is connected at source and/or target
		if (targetParts.size() == 1 && targetParts.get(0)
				.getAdapter(FXBendOnSegmentDragPolicy.class) != null) {
			IContentPart<Node, ? extends Node> part = targetParts.get(0);
			Node visual = part.getVisual();
			if (visual instanceof Connection
					&& ((Connection) visual)
							.getRouter() instanceof OrthogonalRouter
					&& (((Connection) visual).isStartConnected()
							|| ((Connection) visual).isEndConnected())) {
				targetParts = null;
			}
		}
		if (targetParts == null || targetParts.isEmpty()) {
			// abort this policy if no target parts could be found
			return false;
		}
		return true;
	}

	/**
	 * Sets the initial mouse location to the given value.
	 *
	 * @param point
	 *            The initial mouse location.
	 */
	protected void setInitialMouseLocationInScene(Point point) {
		initialMouseLocationInScene = point;
	}

	@Override
	public boolean showIndicationCursor(KeyEvent event) {
		return false;
	}

	@Override
	public boolean showIndicationCursor(MouseEvent event) {
		return false;
	}

	@Override
	public void startDrag(MouseEvent e) {
		// determine target parts
		targetParts = getTargetParts();

		// decide whether to perform translation
		invalidGesture = !isTranslate(e);
		if (invalidGesture) {
			return;
		}

		// save initial pointer location
		setInitialMouseLocationInScene(new Point(e.getSceneX(), e.getSceneY()));

		// initialize this policy for all determined target parts
		for (IContentPart<Node, ? extends Node> part : targetParts) {
			// init transaction policy
			FXTransformPolicy policy = getTransformPolicy(part);
			if (policy != null) {
				storeAndDisableRefreshVisuals(part);
				init(policy);
				translationIndices.put(part, policy.createPostTransform());
				// determine shape bounds
				Rectangle shapeBounds = NodeUtils
						.getShapeBounds(getHost().getVisual());
				Rectangle shapeBoundsInScene = NodeUtils
						.localToScene(getHost().getVisual(), shapeBounds)
						.getBounds();
				boundsInScene.put(part, shapeBoundsInScene);
			}
		}
	}

}
