/*******************************************************************************
 * Copyright (c) 2014 itemis AG and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Matthias Wienand (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.fx.nodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.property.ReadOnlyMapProperty;
import javafx.beans.property.ReadOnlyMapWrapper;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.transform.Rotate;

import org.eclipse.gef4.common.adapt.AdapterKey;
import org.eclipse.gef4.common.adapt.AdapterStore;
import org.eclipse.gef4.fx.anchors.AnchorKey;
import org.eclipse.gef4.fx.anchors.FXStaticAnchor;
import org.eclipse.gef4.fx.anchors.IFXAnchor;
import org.eclipse.gef4.geometry.convert.fx.JavaFX2Geometry;
import org.eclipse.gef4.geometry.euclidean.Angle;
import org.eclipse.gef4.geometry.euclidean.Vector;
import org.eclipse.gef4.geometry.planar.BezierCurve;
import org.eclipse.gef4.geometry.planar.ICurve;
import org.eclipse.gef4.geometry.planar.Point;

public class FXConnection extends Group {

	/**
	 * CSS class assigned to decoration visuals.
	 */
	public static final String CSS_CLASS_DECORATION = "decoration";

	/**
	 * The <i>id</i> used to identify the start point of this connection at the
	 * start anchor.
	 */
	private static final String START_ROLE = "start";

	/**
	 * The <i>id</i> used to identify the end point of this connection at the
	 * end anchor.
	 */
	private static final String END_ROLE = "end";

	/**
	 * Prefix for the default <i>ids</i> used by this connection to identify
	 * specific way points at way point anchors.
	 */
	private static final String WAY_POINT_ROLE_PREFIX = "waypoint-";

	// visuals
	private FXGeometryNode<ICurve> curveNode = new FXGeometryNode<ICurve>();
	private IFXConnectionRouter router = new FXPolylineConnectionRouter();

	// used to pass as argument to IFXAnchor#attach() and #detach()
	private AdapterStore as = new AdapterStore();

	// TODO: use ReadOnlyObjectWrapper (JavaFX Property) for decorations
	private IFXDecoration startDecoration = null;
	private IFXDecoration endDecoration = null;
	private ReadOnlyMapWrapper<AnchorKey, IFXAnchor> anchorsProperty = new ReadOnlyMapWrapper<AnchorKey, IFXAnchor>(
			FXCollections.<AnchorKey, IFXAnchor> observableHashMap());

	private List<AnchorKey> wayAnchorKeys = new ArrayList<AnchorKey>();
	private int nextWayAnchorId = 0;

	// refresh geometry on position changes
	private boolean inRefresh = false;
	private Map<AnchorKey, MapChangeListener<? super AnchorKey, ? super Point>> anchorKeyPCL = new HashMap<AnchorKey, MapChangeListener<? super AnchorKey, ? super Point>>();

	public FXConnection() {
		// disable resizing children which would change their layout positions
		// in some cases
		setAutoSizeChildren(false);

		// register an FXChopBoxHelper, which is passed to the attached anchors.
		as.setAdapter(AdapterKey.get(FXChopBoxHelper.class),
				new FXChopBoxHelper(this));
	}

	public void addWayAnchor(int index, IFXAnchor anchor) {
		if (anchor == null) {
			throw new IllegalArgumentException("anchor may not be null.");
		}

		AnchorKey anchorKey = generateWayAnchorKey();
		// Important: attach() before putting into the anchors-map, so that
		// listeners on the anchors-map can retrieve the anchor position.
		if (anchorKeyPCL.containsKey(anchorKey)) {
			anchor.positionProperty().removeListener(
					anchorKeyPCL.remove(anchorKey));
		}
		wayAnchorKeys.add(anchorKey);
		anchor.attach(anchorKey, as);
		// TODO: listen on map property to add position change listener
		if (!anchorKeyPCL.containsKey(anchorKey)) {
			MapChangeListener<? super AnchorKey, ? super Point> pcl = createPCL(anchorKey);
			anchorKeyPCL.put(anchorKey, pcl);
			anchor.positionProperty().addListener(pcl);
		}
		anchorsProperty.put(anchorKey, anchor);

		refreshGeometry(); // TODO: possibly unnecessary
	}

	public void addWayPoint(int index, Point wayPointInLocal) {
		FXStaticAnchor anchor = new FXStaticAnchor(
				JavaFX2Geometry.toPoint(localToScene(wayPointInLocal.x,
						wayPointInLocal.y)));
		addWayAnchor(index, anchor);
	}

	public ReadOnlyMapProperty<AnchorKey, IFXAnchor> anchorsProperty() {
		return anchorsProperty.getReadOnlyProperty();
	}

	/**
	 * Arranges the given decoration according to the passed-in values. Returns
	 * the transformed end point of the arranged decoration.
	 *
	 * @param deco
	 * @param start
	 * @param direction
	 * @param decoStart
	 * @param decoDirection
	 * @return the transformed end point of the arranged decoration
	 */
	protected Point arrangeDecoration(IFXDecoration deco, Point start,
			Vector direction, Point decoStart, Vector decoDirection) {
		Node visual = deco.getVisual();

		// position
		visual.setLayoutX(start.x);
		visual.setLayoutY(start.y);

		// rotation
		Angle angleCW = null;
		if (!direction.isNull() && !decoDirection.isNull()) {
			angleCW = decoDirection.getAngleCW(direction);
			visual.getTransforms().clear();
			visual.getTransforms().add(new Rotate(angleCW.deg(), 0, 0));
		}

		// return corresponding curve point
		return angleCW == null ? start : start.getTranslated(decoDirection
				.getRotatedCW(angleCW).toPoint());
	}

	protected void arrangeEndDecoration() {
		if (endDecoration == null) {
			return;
		}

		// determine curve end point and curve end direction
		Point endPoint = getEndPoint();
		ICurve curve = getCurveNode().getGeometry();
		if (curve == null) {
			return;
		}

		BezierCurve[] beziers = curve.toBezier();
		if (beziers.length == 0) {
			return;
		}

		BezierCurve endDerivative = beziers[beziers.length - 1].getDerivative();
		Point slope = endDerivative.get(1);
		if (slope.equals(0, 0)) {
			/*
			 * This is the case when beziers[-1] is a degenerated curve where
			 * the last control point equals the end point. As a work around, we
			 * evaluate the derivative at t = 0.99.
			 */
			slope = endDerivative.get(0.99);
		}
		Vector endDirection = new Vector(slope.getNegated());

		// determine decoration start point and decoration direction
		Point decoStartPoint = endDecoration.getLocalStartPoint();
		Point decoEndPoint = endDecoration.getLocalEndPoint();
		Vector decoDirection = new Vector(decoStartPoint, decoEndPoint);

		arrangeDecoration(endDecoration, endPoint, endDirection,
				decoStartPoint, decoDirection);
	}

	protected void arrangeStartDecoration() {
		if (startDecoration == null) {
			return;
		}

		// determine curve start point and curve start direction
		Point startPoint = getStartPoint();
		ICurve curve = getCurveNode().getGeometry();
		if (curve == null) {
			return;
		}

		BezierCurve[] beziers = curve.toBezier();
		if (beziers.length == 0) {
			return;
		}

		BezierCurve startDerivative = beziers[0].getDerivative();
		Point slope = startDerivative.get(0);
		if (slope.equals(0, 0)) {
			/*
			 * This is the case when beziers[0] is a degenerated curve where the
			 * start point equals the first control point. As a work around, we
			 * evaluate the derivative at t = 0.01.
			 */
			slope = startDerivative.get(0.01);
		}
		Vector curveStartDirection = new Vector(slope);

		// determine decoration start point and decoration start direction
		Point decoStartPoint = startDecoration.getLocalStartPoint();
		Point decoEndPoint = startDecoration.getLocalEndPoint();
		Vector decoDirection = new Vector(decoStartPoint, decoEndPoint);

		arrangeDecoration(startDecoration, startPoint, curveStartDirection,
				decoStartPoint, decoDirection);
	}

	protected MapChangeListener<? super AnchorKey, ? super Point> createPCL(
			final AnchorKey anchorKey) {
		return new MapChangeListener<AnchorKey, Point>() {
			@Override
			public void onChanged(
					javafx.collections.MapChangeListener.Change<? extends AnchorKey, ? extends Point> change) {
				if (change.getKey().equals(anchorKey)) {
					refreshGeometry();
				}
			}
		};
	}

	private AnchorKey generateWayAnchorKey() {
		if (nextWayAnchorId == Integer.MAX_VALUE) {
			List<IFXAnchor> wayAnchors = getWayAnchors();
			removeAllWayPoints();
			nextWayAnchorId = 0;
			setWayAnchors(wayAnchors);
		}
		return new AnchorKey(getCurveNode(), WAY_POINT_ROLE_PREFIX
				+ nextWayAnchorId++);
	}

	public List<IFXAnchor> getAnchors() {
		int wayPointCount = getWayAnchorsSize();
		List<IFXAnchor> anchors = new ArrayList<>(wayPointCount + 2);

		IFXAnchor startAnchor = getStartAnchor();
		if (startAnchor == null) {
			return Collections.emptyList();
		}
		anchors.add(startAnchor);

		anchors.addAll(getWayAnchors());

		IFXAnchor endAnchor = getEndAnchor();
		if (endAnchor == null) {
			return Collections.emptyList();
		}
		anchors.add(endAnchor);

		return anchors;
	}

	public FXGeometryNode<ICurve> getCurveNode() {
		return curveNode;
	}

	public IFXAnchor getEndAnchor() {
		IFXAnchor endAnchor = anchorsProperty.get(getEndAnchorKey());
		if (endAnchor == null) {
			// in order not to return null as start/end anchor, we set it to
			// static (0, 0) here
			setEndPoint(new Point());
		}
		endAnchor = anchorsProperty.get(getEndAnchorKey());
		return endAnchor;
	}

	public AnchorKey getEndAnchorKey() {
		return new AnchorKey(getCurveNode(), END_ROLE);
	}

	public IFXDecoration getEndDecoration() {
		return endDecoration;
	}

	public Point getEndPoint() {
		IFXAnchor anchor = getEndAnchor();
		if (anchor == null) {
			return null;
		}
		if (!anchor.isAttached(getEndAnchorKey())) {
			return null;
		}
		return anchor.getPosition(getEndAnchorKey());
	}

	public Point[] getPoints() {
		int wayPointCount = getWayAnchorsSize();
		Point[] points = new Point[wayPointCount + 2];

		points[0] = getStartPoint();
		if (points[0] == null) {
			return new Point[] {};
		}

		for (int i = 0; i < wayPointCount; i++) {
			points[i + 1] = getWayPoint(i);
			if (points[i + 1] == null) {
				return new Point[] {};
			}
		}

		points[points.length - 1] = getEndPoint();
		if (points[points.length - 1] == null) {
			return new Point[] {};
		}

		return points;
	}

	public IFXConnectionRouter getRouter() {
		return router;
	}

	public IFXAnchor getStartAnchor() {
		IFXAnchor startAnchor = anchorsProperty.get(getStartAnchorKey());
		if (startAnchor == null) {
			// in order not to return null as start/end anchor, we set it to
			// static (0, 0) here
			setStartPoint(new Point());
		}
		startAnchor = anchorsProperty.get(getStartAnchorKey());
		return startAnchor;
	}

	public AnchorKey getStartAnchorKey() {
		return new AnchorKey(getCurveNode(), START_ROLE);
	}

	public IFXDecoration getStartDecoration() {
		return startDecoration;
	}

	public Point getStartPoint() {
		IFXAnchor anchor = getStartAnchor();
		if (anchor == null) {
			return null;
		}
		if (!anchor.isAttached(getStartAnchorKey())) {
			return null;
		}
		return anchor.getPosition(getStartAnchorKey());
	}

	public Node getVisual() {
		return this;
	}

	public IFXAnchor getWayAnchor(int index) {
		return anchorsProperty.get(getWayAnchorKey(index));
	}

	public AnchorKey getWayAnchorKey(int index) {
		if (0 <= index && index < wayAnchorKeys.size()) {
			return wayAnchorKeys.get(index);
		}
		return null;
	}

	public List<IFXAnchor> getWayAnchors() {
		int wayPointsCount = getWayAnchorsSize();
		List<IFXAnchor> wayPointAnchors = new ArrayList<IFXAnchor>(
				wayPointsCount);
		for (int i = 0; i < wayPointsCount; i++) {
			wayPointAnchors.add(getWayAnchor(i));
		}
		return wayPointAnchors;
	}

	public int getWayAnchorsSize() {
		return wayAnchorKeys.size();
	}

	public Point getWayPoint(int index) {
		IFXAnchor anchor = getWayAnchor(index);
		if (anchor == null) {
			throw new IllegalArgumentException("No waypoint at index " + index);
		}
		if (!anchor.isAttached(getWayAnchorKey(index))) {
			return null;
		}
		return anchor.getPosition(getWayAnchorKey(index));
	}

	public List<Point> getWayPoints() {
		List<IFXAnchor> wayPointAnchors = getWayAnchors();
		List<Point> wayPoints = new ArrayList<Point>(wayPointAnchors.size());
		for (int i = 0; i < wayPointAnchors.size(); i++) {
			wayPoints.add(wayPointAnchors.get(i)
					.getPosition(getWayAnchorKey(i)));
		}
		return wayPoints;
	}

	public boolean isEndConnected() {
		IFXAnchor anchor = getEndAnchor();
		return anchor != null && anchor.getAnchorage() != null
				&& anchor.getAnchorage() != this;
	}

	public boolean isStartConnected() {
		IFXAnchor anchor = getStartAnchor();
		return anchor != null && anchor.getAnchorage() != null
				&& anchor.getAnchorage() != this;
	}

	public boolean isWayConnected(int index) {
		IFXAnchor anchor = getWayAnchor(index);
		return anchor.getAnchorage() != null && anchor.getAnchorage() != this;
	}

	protected void refreshGeometry() {
		// TODO: this should not be here
		// guard against recomputing the curve while recomputing the curve
		if (inRefresh) {
			return;
		}

		ICurve newGeometry = router.routeConnection(getPoints());
		if (curveNode != null && curveNode.getGeometry() != null
				&& curveNode.getGeometry().equals(newGeometry)) {
			return;
		}
		inRefresh = true;

		// clear current visuals
		getChildren().clear();

		// compute new curve (this can lead to another refreshGeometry() call
		// which is not executed)
		curveNode.setGeometry(newGeometry);

		// z-order decorations above curve
		getChildren().add(curveNode);
		if (startDecoration != null) {
			getChildren().add(startDecoration.getVisual());
			arrangeStartDecoration();
		}
		if (endDecoration != null) {
			getChildren().add(endDecoration.getVisual());
			arrangeEndDecoration();
		}

		inRefresh = false;
	}

	public void removeAllWayPoints() {
		for (int i = getWayAnchorsSize() - 1; i >= 0; i--) {
			removeWayPoint(i);
		}
	}

	public void removeWayPoint(int index) {
		// check index out of range
		if (index < 0 || index >= getWayAnchorsSize()) {
			throw new IllegalArgumentException("Index out of range (index: "
					+ index + ", size: " + getWayAnchorsSize() + ").");
		}

		AnchorKey anchorKey = getWayAnchorKey(index);
		if (!anchorsProperty.containsKey(anchorKey)) {
			throw new IllegalStateException(
					"Inconsistent state: way anchor not in map!");
		}

		wayAnchorKeys.remove(index);
		IFXAnchor oldAnchor = anchorsProperty.remove(anchorKey);
		if (oldAnchor == null) {
			throw new IllegalStateException("old anchor is null!");
		}

		oldAnchor.detach(anchorKey, as);
		if (anchorKeyPCL.containsKey(anchorKey)) {
			oldAnchor.positionProperty().removeListener(
					anchorKeyPCL.remove(anchorKey));
		}

		refreshGeometry();
	}

	public void setAnchors(java.util.List<IFXAnchor> anchors) {
		if (anchors.size() < 2) {
			throw new IllegalArgumentException(
					"start end end anchors have to be provided.");
		}
		setStartAnchor(anchors.get(0));
		if (anchors.size() > 2) {
			setWayAnchors(anchors.subList(1, anchors.size() - 1));
		} else {
			removeAllWayPoints();
		}
		setEndAnchor(anchors.get(anchors.size() - 1));
	}

	public void setEndAnchor(IFXAnchor anchor) {
		if (anchor == null) {
			throw new IllegalArgumentException("anchor may not be null.");
		}

		AnchorKey anchorKey = getEndAnchorKey();
		IFXAnchor oldAnchor = anchorsProperty.get(anchorKey);
		if (oldAnchor != anchor) {
			if (oldAnchor != null) {
				// Important: detach() after removing from the anchors-map, so
				// that listeners on the anchors-map can retrieve the anchor
				// position.
				anchorsProperty.remove(anchorKey);
				oldAnchor.detach(anchorKey, as);
				if (anchorKeyPCL.containsKey(anchorKey)) {
					oldAnchor.positionProperty().removeListener(
							anchorKeyPCL.remove(anchorKey));
				}
			}
			// Important: attach() before putting into anchors-map, so that
			// listeners on the anchors-map can retrieve the anchor position.
			if (anchorKeyPCL.containsKey(anchorKey)) {
				anchor.positionProperty().removeListener(
						anchorKeyPCL.remove(anchorKey));
			}
			anchor.attach(anchorKey, as);
			// TODO: listen on anchors map to add the PCL
			if (!anchorKeyPCL.containsKey(anchorKey)) {
				MapChangeListener<? super AnchorKey, ? super Point> pcl = createPCL(anchorKey);
				anchorKeyPCL.put(anchorKey, pcl);
				anchor.positionProperty().addListener(pcl);
			}
			anchorsProperty.put(anchorKey, anchor);
			refreshGeometry();
		}
	}

	public void setEndDecoration(IFXDecoration endDeco) {
		endDecoration = endDeco;
		if (endDecoration != null) {
			ObservableList<String> styleClasses = endDecoration.getVisual()
					.getStyleClass();
			if (!styleClasses.contains(CSS_CLASS_DECORATION)) {
				styleClasses.add(CSS_CLASS_DECORATION);
			}
		}
		refreshGeometry();
	}

	public void setEndPoint(Point endPointInLocal) {
		FXStaticAnchor anchor = new FXStaticAnchor(
				JavaFX2Geometry.toPoint(localToScene(endPointInLocal.x,
						endPointInLocal.y)));
		setEndAnchor(anchor);
	}

	public void setRouter(IFXConnectionRouter router) {
		this.router = router;
		refreshGeometry();
	}

	public void setStartAnchor(IFXAnchor anchor) {
		if (anchor == null) {
			throw new IllegalArgumentException("anchor may not be null.");
		}

		AnchorKey anchorKey = getStartAnchorKey();
		IFXAnchor oldAnchor = anchorsProperty.get(anchorKey);
		if (oldAnchor != anchor) {
			if (oldAnchor != null) {
				// Important: detach() after removing from the anchors-map, so
				// that listeners on the anchors-map can retrieve the anchor
				// position.
				anchorsProperty.remove(anchorKey);
				oldAnchor.detach(anchorKey, as);
				if (anchorKeyPCL.containsKey(anchorKey)) {
					oldAnchor.positionProperty().removeListener(
							anchorKeyPCL.remove(anchorKey));
				}
			}
			// Important: attach() before putting into the anchors-map, so that
			// listeners on the anchors-map can retrieve the anchor position.
			if (anchorKeyPCL.containsKey(anchorKey)) {
				anchor.positionProperty().removeListener(
						anchorKeyPCL.remove(anchorKey));
			}
			anchor.attach(anchorKey, as);
			// TODO: listen on anchors map to add the PCL
			if (!anchorKeyPCL.containsKey(anchorKey)) {
				MapChangeListener<? super AnchorKey, ? super Point> pcl = createPCL(anchorKey);
				anchorKeyPCL.put(anchorKey, pcl);
				anchor.positionProperty().addListener(pcl);
			}
			anchorsProperty.put(anchorKey, anchor);
			refreshGeometry();
		}
	}

	public void setStartDecoration(IFXDecoration startDeco) {
		startDecoration = startDeco;
		if (startDecoration != null) {
			ObservableList<String> styleClasses = startDecoration.getVisual()
					.getStyleClass();
			if (!styleClasses.contains(CSS_CLASS_DECORATION)) {
				styleClasses.add(CSS_CLASS_DECORATION);
			}
		}
		refreshGeometry();
	}

	public void setStartPoint(Point startPointInLocal) {
		FXStaticAnchor anchor = new FXStaticAnchor(
				JavaFX2Geometry.toPoint(localToScene(startPointInLocal.x,
						startPointInLocal.y)));
		setStartAnchor(anchor);
	}

	public void setWayAnchor(int index, IFXAnchor anchor) {
		if (index < 0 || index >= wayAnchorKeys.size()) {
			throw new IllegalArgumentException("index out of range.");
		}
		if (anchor == null) {
			throw new IllegalArgumentException("anchor may not be null.");
		}

		AnchorKey anchorKey = getWayAnchorKey(index);
		IFXAnchor oldAnchor = anchorsProperty.get(anchorKey);
		if (oldAnchor != anchor) {
			// Important: detach() after removing from the anchors-map, so
			// that listeners on the anchors-map can retrieve the anchor
			// position.
			// TODO: extract to method
			anchorsProperty.remove(anchorKey);
			oldAnchor.detach(anchorKey, as);
			if (anchorKeyPCL.containsKey(anchorKey)) {
				oldAnchor.positionProperty().removeListener(
						anchorKeyPCL.remove(anchorKey));
			}
			// Important: attach() before putting into the anchors-map, so that
			// listeners on the anchors-map can retrieve the anchor position.
			// TODO: extract to method
			if (anchorKeyPCL.containsKey(anchorKey)) {
				anchor.positionProperty().removeListener(
						anchorKeyPCL.remove(anchorKey));
			}
			anchor.attach(anchorKey, as);
			// TODO: listen on anchors map to add the PCL
			if (!anchorKeyPCL.containsKey(anchorKey)) {
				MapChangeListener<? super AnchorKey, ? super Point> pcl = createPCL(anchorKey);
				anchorKeyPCL.put(anchorKey, pcl);
				anchor.positionProperty().addListener(pcl);
			}
			anchorsProperty.put(anchorKey, anchor);
			refreshGeometry();
		}
	}

	public void setWayAnchors(List<IFXAnchor> anchors) {
		int wayPointsSize = getWayAnchorsSize();
		// Important: We have to do the removal of way anchors before
		// changing/adding anchors.
		for (int i = wayPointsSize - 1; i >= anchors.size(); i--) {
			removeWayPoint(i);
		}
		for (int i = 0; i < wayPointsSize && i < anchors.size(); i++) {
			setWayAnchor(i, anchors.get(i));
		}
		for (int i = wayPointsSize; i < anchors.size(); i++) {
			addWayAnchor(i, anchors.get(i));
		}
	}

	public void setWayPoint(int index, Point wayPointInLocal) {
		FXStaticAnchor anchor = new FXStaticAnchor(
				JavaFX2Geometry.toPoint(localToScene(wayPointInLocal.x,
						wayPointInLocal.y)));
		setWayAnchor(index, anchor);
	}

	public void setWayPoints(List<Point> wayPoints) {
		int waySize = wayAnchorKeys.size();
		int i = 0;
		for (; i < waySize && i < wayPoints.size(); i++) {
			setWayPoint(i, wayPoints.get(i));
		}
		for (; i < wayPoints.size(); i++) {
			addWayPoint(i, wayPoints.get(i));
		}
		for (; i < waySize; i++) {
			removeWayPoint(i);
		}
	}
}
