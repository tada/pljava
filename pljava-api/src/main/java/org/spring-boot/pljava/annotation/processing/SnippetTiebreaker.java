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

import java.util.Arrays;
import java.util.Comparator;
import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;
import static java.util.Comparator.nullsFirst;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Resolve ties in {@code Snippet} ordering in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class SnippetTiebreaker implements Comparator<Vertex<Snippet>>
{
	private static final Comparator<Vertex<Snippet>> VCMP;

	static
	{
		Comparator<Snippet> scmp =
			comparing(Snippet::implementorName,
				nullsFirst(comparing(Simple::pgFolded, naturalOrder()))
			)
			.thenComparing(Snippet::deployStrings,   Arrays::compare)
			.thenComparing(Snippet::undeployStrings, Arrays::compare);

		VCMP = comparing(v -> v.payload, scmp);
	}

	@Override
	public int compare(Vertex<Snippet> o1, Vertex<Snippet> o2)
	{
		return VCMP.compare(o1, o2);
	}
}
