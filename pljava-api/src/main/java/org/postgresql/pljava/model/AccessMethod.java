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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;
import org.postgresql.pljava.model.RegProcedure.Memo.Why;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a relation access method.
 */
public interface AccessMethod
extends
	Addressed<AccessMethod>, Named<Simple>
{
	RegClass.Known<AccessMethod> CLASSID =
		formClassId(AccessMethodRelationId, AccessMethod.class);

	enum Type { TABLE, INDEX }

	interface AMHandler extends Why<AMHandler> { }

	RegProcedure<AMHandler> handler();

	Type type();
}
