/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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

import java.util.Map;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a foreign server, with which foreign tables can be declared.
 */
public interface ForeignServer
extends
	Addressed<ForeignServer>, Named<Simple>,
	Owned, AccessControlled<USAGE>
{
	RegClass.Known<ForeignServer> CLASSID =
		formClassId(ForeignServerRelationId, ForeignServer.class);

	ForeignDataWrapper fdw();

	String type();

	String version();

	Map<Simple,String> options();
}
