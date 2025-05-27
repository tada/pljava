/*
 * Copyright (c) 2018-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

/**
 * A pair of Vertex instances for the same payload, for use when two directions
 * of topological ordering must be computed.
 */
class VertexPair<P>
{
	Vertex<P> fwd;
	Vertex<P> rev;

	VertexPair( P payload)
	{
		fwd = new Vertex<>( payload);
		rev = new Vertex<>( payload);
	}

	P payload()
	{
		return rev.payload;
	}
}
