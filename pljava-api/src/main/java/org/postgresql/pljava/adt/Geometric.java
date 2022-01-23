/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.adt;

import org.postgresql.pljava.Adapter.Contract;
import org.postgresql.pljava.Adapter.Dispenser;
import org.postgresql.pljava.Adapter.PullDispenser;

/**
 * Container for abstract-type functional interfaces in PostgreSQL's
 * {@code GEOMETRIC} type category.
 */
public interface Geometric
{
	/**
	 * The {@code POINT} type's PostgreSQL semantics: a pair of {@code float8}
	 * coordinates.
	 */
	@FunctionalInterface
	public interface Point<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 */
		T construct(double x, double y);
	}

	/**
	 * The {@code LSEG} type's PostgreSQL semantics: two endpoints.
	 * @param <T> the type returned by the constructor
	 * @param <I> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the {@code Dispenser} uses within the implementing body.
	 */
	@FunctionalInterface
	public interface LSeg<T,I> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param endpoints a dispenser that will dispense a {@code Point} for
		 * index 0 and index 1.
		 */
		T construct(PullDispenser<I,Point<I>> endpoints);
	}

	/**
	 * The {@code PATH} type's PostgreSQL semantics: vertex points and whether
	 * closed.
	 * @param <T> the type returned by the constructor
	 * @param <I> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the {@code Dispenser} uses within the implementing body.
	 */
	@FunctionalInterface
	public interface Path<T,I> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param nPoints the number of points on the path
		 * @param closed whether the path should be understood to include
		 * a segment joining the last point to the first one.
		 * @param points a dispenser that will dispense a {@code Point} for
		 * each index 0 through <var>nPoint</var> - 1.
		 */
		T construct(
			int nPoints, boolean closed, PullDispenser<I,Point<I>> points);
	}

	/**
	 * The {@code LINE} type's PostgreSQL semantics: coefficients of its
	 * general equation Ax+By+C=0.
	 */
	@FunctionalInterface
	public interface Line<T> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 */
		T construct(double A, double B, double C);
	}

	/**
	 * The {@code BOX} type's PostgreSQL semantics: two corner points.
	 * @param <T> the type returned by the constructor
	 * @param <I> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the {@code Dispenser} uses within the implementing body.
	 */
	@FunctionalInterface
	public interface Box<T,I> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 *<p>
		 * As stored, the corner point at index 0 is never below or to the left
		 * of that at index 1. This may be achieved by permuting the points
		 * or their coordinates obtained as input, in any way that preserves
		 * the box.
		 * @param corners a dispenser that will dispense a {@code Point} for
		 * index 0 and at index 1.
		 */
		T construct(PullDispenser<I,Point<I>> corners);
	}

	/**
	 * The {@code POLYGON} type's PostgreSQL semantics: vertex points and
	 * a bounding box.
	 * @param <T> the type returned by the constructor
	 * @param <I1> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the <var>boundingBox</var> dispenser used within
	 * the implementing body.
	 * @param <I2> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the <var>vertices</var> dispenser used within
	 * the implementing body.
	 */
	@FunctionalInterface
	public interface Polygon<T,I1,I2> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 * @param nVertices the number of vertices in the polygon
		 * @param boundingBox a dispenser from which the bounding box may be
		 * obtained.
		 * @param vertices a dispenser from which a vertex {@code Point} may be
		 * obtained for each index 0 through <var>nVertices</var> - 1.
		 */
		T construct(
			int nVertices, Dispenser<I1,Box<I1,?>> boundingBox,
			PullDispenser<I2,Point<I2>> vertices);
	}

	/**
	 * The {@code CIRCLE} type's PostgreSQL semantics: center point and radius.
	 * @param <T> the type returned by the constructor
	 * @param <I> internal parameter that consumers of this interface should
	 * wildcard; an implementor may bound this parameter to get stricter type
	 * checking of the {@code Dispenser} uses within the implementing body.
	 */
	@FunctionalInterface
	public interface Circle<T,I> extends Contract.Scalar<T>
	{
		/**
		 * Constructs a representation <var>T</var> from the components
		 * of the PostgreSQL data type.
		 */
		T construct(Dispenser<I,Point<I>> center, double radius);
	}
}
