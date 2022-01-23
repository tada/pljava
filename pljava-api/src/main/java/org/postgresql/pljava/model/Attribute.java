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
 * An attribute (column), either of a known relation, or of a transient record
 * type.
 */
public interface Attribute
extends
	Addressed<RegClass>, Component, Named<Simple>,
	AccessControlled<Grant.OnAttribute>
{
	/**
	 * CLASS rather than CLASSID because Attribute isn't an object class
	 * in its own right.
	 *<p>
	 * This simply identifies the table in the catalog that holds attribute
	 * definitions. An Attribute is not regarded as an object of that 'class';
	 * it is a subId of whatever other RegClass object it defines an attribute
	 * of.
	 */
	RegClass CLASS = formObjectId(RegClass.CLASSID, AttributeRelationId);

	RegType type();
	short length();
}
