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

import java.sql.SQLException;
import java.sql.SQLSyntaxErrorException; // javadoc

import java.util.List;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Conceptually, a {@code TupleDescriptor} is a list of {@code Attribute}, with
 * a {@code RegType} that identifies its corresponding row type.
 *<p>
 * The row type might be just {@code RECORD}, though, representing a
 * transient, unregistered type.
 *<p>
 * The {@code Attribute} instances may then correspond to nothing that exists in
 * {@code pg_attribute}, in which case they will be 'virtual' instances whose
 * {@code CatalogObject.Addressed} methods don't work, but which simply hold a
 * reference to the {@code TupleDescriptor} they came from instead.
 *<p>
 * A {@code TupleDescriptor} may also contain attribute defaults and/or
 * constraints. These would be less often of interest in Java; if there is
 * a need to make them available, rather than complicating
 * {@code TupleDescriptor}, it will probably be more natural to make them
 * available by methods on {@code Attribute}.
 */
public interface TupleDescriptor
{
	List<Attribute> attributes();

	RegType rowType();

	/**
	 * Gets an attribute by name.
	 *<p>
	 * This API should be considered scaffolding or preliminary, until an API
	 * can be designed that might offer a convenient usage idiom without
	 * presupposing something like a name-to-attribute map in every decriptor.
	 * @throws SQLSyntaxErrorException 42703 if no attribute name matches
	 */
	Attribute get(Simple name) throws SQLException;

	/**
	 * Equivalent to {@code get(Simple.fromJava(name))}.
	 *<p>
	 * This API should be considered scaffolding or preliminary, until an API
	 * can be designed that might offer a convenient usage idiom without
	 * presupposing something like a name-to-attribute map in every descriptor.
	 * @throws SQLSyntaxErrorException 42703 if no attribute name matches
	 */
	default Attribute get(String name) throws SQLException
	{
		return get(Simple.fromJava(name));
	}

	/**
	 * (Convenience method) Retrieve an attribute by its familiar (1-based)
	 * SQL attribute number.
	 *<p>
	 * The Java {@link List#get List.get} API uses zero-based numbering, so this
	 * convenience method is equivalent to {@code attributes().get(attrNum-1)}.
	 */
	Attribute sqlGet(int attrNum);

	/**
	 * Return this descriptor unchanged if it is already interned in
	 * PostgreSQL's type cache, otherwise an equivalent new descriptor with
	 * a different {@link #rowType rowType} uniquely assigned to identify it.
	 *<p>
	 * PostgreSQL calls this operation "BlessTupleDesc", which updates the
	 * descriptor in place; in PL/Java code, the descriptor returned by this
	 * method should be used in place of the original.
	 */
	Interned intern();

	/**
	 * A descriptor that either describes a known composite type in the catalogs
	 * or has been interned in PostgreSQL's type cache, and has a distinct
	 * {@link #rowType rowType} that can be used to identify it.
	 *<p>
	 * Some operations, such as constructing a composite value for a function
	 * to return, require this.
	 */
	interface Interned extends TupleDescriptor
	{
		@Override
		default Interned intern()
		{
			return this;
		}
	}

	/**
	 * A descriptor that has been constructed on the fly and has not been
	 * interned.
	 *<p>
	 * For all such descriptors, {@link #rowType rowType} returns
	 * {@link RegType#RECORD RECORD}, which is of no use for identification.
	 * For some purposes (such as constructing a composite value for a function
	 * to return), an ephemeral descriptor must be interned before it can
	 * be used.
	 */
	interface Ephemeral extends TupleDescriptor
	{
	}
}
