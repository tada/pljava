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

import java.util.Set;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * Proxy a snippet that 'provides' an implementor tag and has no
 * undeployStrings, returning its deployStrings in their place.
 */
class ImpProvider implements Snippet
{
	Snippet s;

	ImpProvider( Snippet s) { this.s = s; }

	@Override public Identifier.Simple implementorName()
	{
		return s.implementorName();
	}
	@Override public String[]   deployStrings() { return s.deployStrings(); }
	@Override public String[] undeployStrings() { return s.deployStrings(); }
	@Override public Set<DependTag> provideTags() { return s.provideTags(); }
	@Override public Set<DependTag> requireTags() { return s.requireTags(); }
	@Override public Set<Snippet> characterize() { return s.characterize(); }
}
