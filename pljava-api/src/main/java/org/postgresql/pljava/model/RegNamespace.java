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
package org.postgresql.pljava.model;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a namespace (named schema) entry in the PostgreSQL catalogs.
 */
public interface RegNamespace
extends
	Addressed<RegNamespace>, Named<Simple>, Owned,
	AccessControlled<Grant.OnNamespace>
{
	RegClass.Known<RegNamespace> CLASSID =
		formClassId(NamespaceRelationId, RegNamespace.class);

	RegNamespace PG_CATALOG = formObjectId(CLASSID, PG_CATALOG_NAMESPACE);
	RegNamespace   PG_TOAST = formObjectId(CLASSID, PG_TOAST_NAMESPACE);
}
