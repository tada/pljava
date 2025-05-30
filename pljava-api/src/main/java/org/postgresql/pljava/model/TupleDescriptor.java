/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.TargetList.Projection;
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
public interface TupleDescriptor extends Projection
{
	/**
	 * @deprecated As a subinterface of {@link Projection Projection},
	 * a {@code TupleDescriptor} already is a {@code List<Attribute>}, and there
	 * is no need for this method to simply return its own receiver.
	 */
	@Deprecated(forRemoval=true)
	default List<Attribute> attributes()
	{
		return this;
	}

	/**
	 * If this tuple descriptor is not ephemeral, returns the PostgreSQL type
	 * that identifies it.
	 *<p>
	 * If the descriptor is for a known composite type in the PostgreSQL
	 * catalog, this method returns that type.
	 *<p>
	 * If the descriptor has been created programmatically and interned, this
	 * method returns the type
	 * {@link RegType#RECORD RECORD}.{@link RegType#modifier(int) modifier(n)}
	 * where <var>n</var> was uniquely assigned by PostgreSQL when the
	 * descriptor was interned, and will reliably refer to this tuple descriptor
	 * for the duration of the session.
	 *<p>
	 * For any ephemeral descriptor passed around in code without being
	 * interned, this method returns plain {@link RegType#RECORD RECORD}, which
	 * is useless for identifying the tuple structure.
	 */
	RegType rowType();

	/**
	 * Gets an attribute by name.
	 *<p>
	 * This API should be considered scaffolding or preliminary, until an API
	 * can be designed that might offer a convenient usage idiom without
	 * presupposing something like a name-to-attribute map in every decriptor.
	 *<p>
	 * This default implementation simply does {@code project(name).get(0)}.
	 * Code that will do so repeatedly might be improved by doing so once and
	 * retaining the result.
	 * @throws SQLSyntaxErrorException 42703 if no attribute name matches
	 * @deprecated A one-by-one lookup-by-name API forces the implementation to
	 * cater to an inefficient usage pattern, when callers will often have a
	 * number of named attributes to look up, which can be done more efficiently
	 * in one go; see the methods of {@link Projection Projection}.
	 */
	@Deprecated(forRemoval=true)
	default Attribute get(Simple name) throws SQLException
	{
		return project(name).get(0);
	}

	/**
	 * Equivalent to {@code get(Simple.fromJava(name))}.
	 *<p>
	 * This API should be considered scaffolding or preliminary, until an API
	 * can be designed that might offer a convenient usage idiom without
	 * presupposing something like a name-to-attribute map in every descriptor.
	 * @throws SQLSyntaxErrorException 42703 if no attribute name matches
	 * @deprecated A one-by-one lookup-by-name API forces the implementation to
	 * cater to an inefficient usage pattern, when callers will often have a
	 * number of named attributes to look up, which can be done more efficiently
	 * in one go; see the methods of {@link Projection Projection}.
	 */
	@Deprecated(forRemoval=true)
	default Attribute get(String name) throws SQLException
	{
		return get(Simple.fromJava(name));
	}

	/**
	 * Return this descriptor unchanged if it is already interned in
	 * PostgreSQL's type cache, otherwise an equivalent new descriptor with
	 * a different {@link #rowType rowType} uniquely assigned to identify it
	 * for the duration of the session.
	 *<p>
	 * PostgreSQL calls this operation "BlessTupleDesc", which updates the
	 * descriptor in place; in PL/Java code, the descriptor returned by this
	 * method should be used in place of the original.
	 */
	Interned intern();

	/**
	 * A descriptor that either describes a known composite type in the
	 * catalogs, or has been interned in PostgreSQL's type cache, and has
	 * a distinct {@link #rowType rowType} that can be used to identify it
	 * for the duration of the session.
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
