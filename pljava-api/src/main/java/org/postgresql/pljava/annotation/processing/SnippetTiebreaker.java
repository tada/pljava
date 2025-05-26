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

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * Resolve ties in {@code Snippet} ordering in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class SnippetTiebreaker implements Comparator<Vertex<Snippet>>
{
	@Override
	public int compare( Vertex<Snippet> o1, Vertex<Snippet> o2)
	{
		Snippet s1 = o1.payload;
		Snippet s2 = o2.payload;
		int diff;
		Identifier.Simple s1imp = s1.implementorName();
		Identifier.Simple s2imp = s2.implementorName();
		if ( null != s1imp  &&  null != s2imp )
		{
			diff = s1imp.pgFolded().compareTo( s2imp.pgFolded());
			if ( 0 != diff )
				return diff;
		}
		else
			return null == s1imp ? -1 : 1;
		String[] ds1 = s1.deployStrings();
		String[] ds2 = s2.deployStrings();
		diff = ds1.length - ds2.length;
		if ( 0 != diff )
			return diff;
		for ( int i = 0 ; i < ds1.length ; ++ i )
		{
			diff = ds1[i].compareTo( ds2[i]);
			if ( 0 != diff )
				return diff;
		}
		assert s1 == s2 : "Two distinct Snippets compare equal by tiebreaker";
		return 0;
	}
}
