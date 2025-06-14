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

import java.util.Comparator;
import static java.util.Comparator.comparing;
import java.util.Map;

import javax.lang.model.type.TypeMirror;

/**
 * Resolve ties in type-mapping resolution in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class TypeTiebreaker
implements Comparator<Vertex<Map.Entry<TypeMirror, DBType>>>
{
	private static final Comparator<Vertex<Map.Entry<TypeMirror, DBType>>> VCMP;

	static
	{
		Comparator<Map.Entry<TypeMirror, DBType>> ecmp =
			comparing(
				(Map.Entry<TypeMirror, DBType> e) -> e.getValue().toString())
			.thenComparing(e -> e.getKey().toString());

		VCMP = comparing(v -> v.payload, ecmp);
	}

	@Override
	public int compare(
		Vertex<Map.Entry<TypeMirror, DBType>> o1,
		Vertex<Map.Entry<TypeMirror, DBType>> o2)
	{
		return VCMP.compare(o1, o2);
	}
}
