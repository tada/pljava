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
package org.postgresql.pljava;

import org.postgresql.pljava.model.RegNamespace;

public class CatalogTest
{
	public boolean whatbits(RegNamespace n)
	{
		return n.grants().stream().anyMatch(
			g -> g.usageGranted() && g.createGranted() );
	}
}
