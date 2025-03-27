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

import org.postgresql.pljava.annotation.BaseUDT.Alignment;
import org.postgresql.pljava.annotation.BaseUDT.Storage;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * An attribute (column), either of a known relation, or of a transient record
 * type.
 *<p>
 * Instances of the transient kind may be retrieved from a
 * {@link TupleDescriptor TupleDescriptor} and will compare unequal to other
 * {@code Attribute} instances even with the same {@code classId},
 * {@code subId}, and {@code oid} (which will be {@code InvalidOid}); for such
 * instances, {@link #containingTupleDescriptor() containingTupleDescriptor}
 * will return the specific transient {@code TupleDescriptor} to which
 * the attribute belongs. Such 'virtual' instances will appear to have
 * the invalid {@code RegClass} as {@code relation()}, and all access granted
 * to {@code public}.
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

	enum Identity { INAPPLICABLE, GENERATED_ALWAYS, GENERATED_BY_DEFAULT }

	enum Generated { INAPPLICABLE, STORED }

	RegClass relation();
	RegType type();
	short length();
	int dimensions();
	int cachedOffset();
	boolean byValue();
	Alignment alignment();
	Storage storage();
	boolean notNull();
	boolean hasDefault();
	boolean hasMissing();
	Identity identity();
	Generated generated();
	boolean dropped();
	boolean local();
	int inheritanceCount();
	RegCollation collation();
	// options
	// fdwoptions
	// missingValue

	/**
	 * Returns the tuple descriptor to which this attribute belongs.
	 *<p>
	 * For a 'cataloged' attribute corresponding to a known relation
	 * or row type, returns a {@code TupleDescriptor} for that. For a 'virtual'
	 * attribute obtained from some non-cataloged tuple descriptor, returns
	 * whatever {@code TupleDescriptor} it came from.
	 */
	TupleDescriptor containingTupleDescriptor();
}
