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
import java.util.Map;

import javax.lang.model.type.TypeMirror;

/**
 * Resolve ties in type-mapping resolution in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class TypeTiebreaker
implements Comparator<Vertex<Map.Entry<TypeMirror, DBType>>>
{
	@Override
	public int compare(
		Vertex<Map.Entry<TypeMirror, DBType>> o1,
		Vertex<Map.Entry<TypeMirror, DBType>> o2)
	{
		Map.Entry<TypeMirror, DBType> m1 = o1.payload;
		Map.Entry<TypeMirror, DBType> m2 = o2.payload;
		int diff =
			m1.getValue().toString().compareTo( m2.getValue().toString());
		if ( 0 != diff )
			return diff;
		diff = m1.getKey().toString().compareTo( m2.getKey().toString());
		if ( 0 != diff )
			return diff;
		assert
			m1 == m2 : "Two distinct type mappings compare equal by tiebreaker";
		return 0;
	}
}
