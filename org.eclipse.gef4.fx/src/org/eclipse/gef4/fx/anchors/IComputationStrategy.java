/*******************************************************************************
 * Copyright (c) 2016 itemis AG and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Alexander Nyßen (itemis AG) - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.gef4.fx.anchors;

import java.util.Set;

import org.eclipse.gef4.geometry.planar.Point;

import javafx.beans.binding.Binding;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/**
 * The {@link IComputationStrategy} is responsible for computing anchor
 * positions based on the anchorage {@link Node}, the anchored {@link Node}, and
 * respective (strategy-specific) {@link Parameter parameters}. ).
 */
public interface IComputationStrategy {

	/**
	 * Base class for all computation parameters that can be passed to an
	 * {@link IComputationStrategy}.
	 *
	 * @param <T>
	 *            The parameter value type.
	 */
	public abstract class Parameter<T> extends ObjectPropertyBase<T> {

		/**
		 * Indicates whether the parameter value can be shared to compute
		 * positions of all attached anchors or not.
		 */
		public enum Kind {
			/**
			 * Indicates that the parameter value may be shared to compute the
			 * position for all attached {@link AnchorKey}.
			 */
			STATIC,
			/**
			 * Indicates that the parameter value may not be shared, i.e. an
			 * individual value is required to compute the position for each
			 * attached {@link AnchorKey}, e.g. because the value depends on the
			 * anchored node.
			 */
			DYNAMIC
		};

		private Kind kind;
		private boolean optional;
		private ObservableValue<? extends T> bindingTarget;

		/**
		 * Creates a new mandatory {@link Parameter} of the given kind.
		 *
		 * @param kind
		 *            The parameter kind.
		 */
		public Parameter(Kind kind) {
			this(kind, false);
		}

		/**
		 * Creates a new optional parameter of the given kind.
		 *
		 * @param kind
		 *            The parameter kin.
		 *
		 * @param optional
		 *            Whether this parameter is optional or not.
		 */
		public Parameter(Kind kind, boolean optional) {
			this.kind = kind;
			this.optional = optional;
		}

		@Override
		public void bind(ObservableValue<? extends T> newObservable) {
			super.bind(newObservable);
			this.bindingTarget = newObservable;
		}

		@Override
		public Object getBean() {
			// no bean by default
			return null;
		}

		/**
		 * Retrieves the {@link Kind} of this parameter, which indicates whether
		 * a single value may be shared to compute the positions of all attached
		 * {@link AnchorKey}s or not.
		 *
		 * @return The parameter {@link Kind}.
		 */
		public Kind getKind() {
			return kind;
		}

		@Override
		public String getName() {
			// use type name as property name
			return getClass().getSimpleName();
		}

		/**
		 * If this parameter is bound, can be used to invalidate the underlying
		 * binding, so that the value is re-computed.
		 */
		public void invalidateBinding() {
			if (isBound() && bindingTarget instanceof Binding) {
				((Binding<? extends T>) bindingTarget).invalidate();
			}
		}

		/**
		 * Indicates whether this parameter is optional
		 *
		 * @return <code>true</code> if the parameter is optional,
		 *         <code>false</code> otherwise.
		 */
		public boolean isOptional() {
			return optional;
		}

		@Override
		public void unbind() {
			this.bindingTarget = null;
			super.unbind();
		}
	}

	/**
	 * Computes an anchor position based on the given anchorage visual, anchored
	 * visual, and anchored reference point.
	 *
	 * @param anchorage
	 *            The anchorage visual.
	 * @param anchored
	 *            The anchored visual.
	 * @param parameters
	 *            The available computation parameters. strategy.
	 * @return The anchor position.
	 */
	public Point computePositionInScene(Node anchorage, Node anchored,
			Set<Parameter<?>> parameters);

	/**
	 * Returns the types of parameters required by this strategy.
	 *
	 * @return The parameters required by this strategy.
	 */
	public Set<Class<? extends Parameter<?>>> getRequiredParameters();

}