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

import java.util.List;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of PostgreSQL relations/"classes"/tables.
 *<p>
 * Instances of {@code RegClass} also serve as the "class ID" values for
 * objects within the catalog (including for {@code RegClass} objects, which
 * are no different from others in being defined by rows that appear in a
 * catalog table; there is a row in {@code pg_class} for {@code pg_class}).
 */
public interface RegClass
extends
	Addressed<RegClass>, Namespaced<Simple>, Owned,
	AccessControlled<Grant.OnClass>
{
	Known<RegClass> CLASSID = formClassId(RelationRelationId, RegClass.class);

	/**
	 * A more-specifically-typed subinterface of {@code RegClass}, used in the
	 * {@code CLASSID} static fields of interfaces in this package.
	 * @param <T> identifies the specific CatalogObject.Addressed subinterface
	 * to result when this is applied as the {@code classId} to a bare
	 * {@code CatalogObject}.
	 */
	interface Known<T extends Addressed<T>> extends RegClass
	{
	}

	/**
	 * The PostgreSQL type that is associated with this relation as its
	 * "row type".
	 *<p>
	 * This is the type that will be found in a
	 * {@link TupleDescriptor TupleDescriptor} for this relation.
	 */
	RegType type();

	/**
	 * Only for a relation that was created with {@code CREATE TABLE ... OF}
	 * <var>type</var>, this will be that type; the invalid {@code RegType}
	 * otherwise.
	 *<p>
	 * Even though the tuple structure will match, this is not the same type
	 * returned by {@link #type() type()}; that will still be a type distinctly
	 * associated with this relation.
	 */
	RegType ofType();
	// am
	// filenode
	// tablespace

	/* Of limited interest ... estimates used by planner
	 *
	int pages();
	float tuples();
	int allVisible();
	 */

	RegClass toastRelation();
	boolean hasIndex();

	/**
	 * Whether this relation is shared across all databases in the cluster.
	 *<p>
	 * Contrast {@link shared()}, which indicates, for any catalog object,
	 * whether that object is shared across the cluster. For any
	 * {@code RegClass} instance, {@code shared()} will be false (the
	 * {@code pg_class} catalog is not shared), but if the instance represents
	 * a shared class, {@code isShared()} will be true (and {@code shared()}
	 * will be true for any catalog object formed with that instance as its
	 * {@code classId}).
	 * @return whether the relation represented by this RegClass instance is
	 * shared across all databases in the cluster.
	 */
	boolean isShared();
	// persistence
	// kind
	short nAttributes();
	short checks();
	boolean hasRules();
	boolean hasTriggers();
	boolean hasSubclass();
	boolean rowSecurity();
	boolean forceRowSecurity();
	boolean isPopulated();
	// replident
	boolean isPartition();
	// rewrite
	// frozenxid
	// minmxid
	/**
	 * This is a list of {@code keyword=value} pairs and ought to have
	 * a more specific return type.
	 *<p>
	 * XXX
	 */
	List<String> options();
	// partbound

	TupleDescriptor.Interned tupleDescriptor();
}
