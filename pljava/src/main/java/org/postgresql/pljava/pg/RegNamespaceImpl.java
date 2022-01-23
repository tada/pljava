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
package org.postgresql.pljava.pg;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

class RegNamespaceImpl extends Addressed<RegNamespace>
implements
	Nonshared<RegNamespace>, Named<Simple>, Owned,
	AccessControlled<CatalogObject.Grant.OnNamespace>, RegNamespace
{
}
