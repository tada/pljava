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

import java.sql.SQLException;

import static java.util.Objects.requireNonNull;

import org.postgresql.pljava.model.*;

import static org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.*;
import static org.postgresql.pljava.pg.TupleDescImpl.Ephemeral;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

abstract class AttributeImpl extends Addressed<RegClass>
implements
	Nonshared<RegClass>, Named<Simple>,
	AccessControlled<CatalogObject.Grant.OnAttribute>, Attribute
{
	@Override
	public RegType type()
	{
		throw notyet();
	}

	boolean foundIn(TupleDescriptor td)
	{
		return this == td.attributes().get(subId() - 1);
	}

	/**
	 * An attribute that belongs to a full-fledged cataloged composite type.
	 *<p>
	 * It holds a reference to the relation that defines the composite type
	 * layout. While that can always be found from the class and object IDs
	 * of the object address, that is too much fuss for as often as
	 * {@code relation()} is called.
	 */
	static class Cataloged extends AttributeImpl
	{
	}

	/**
	 * An attribute that belongs to a transient {@code TupleDescriptor}, not
	 * to any relation in the catalog (and therefore isn't really
	 * a {@code CatalogObject}, though it still pretends to be one).
	 *<p>
	 * For now, this is simply a subclass of {@code AttributeImpl} to inherit
	 * most of the same machinery, and simply overrides and disables the methods
	 * of a real {@code CatalogObject}. In an alternative, it could be an
	 * independent implementation of the {@code Attribute} interface, but that
	 * could require more duplication of implementation. A cost of this
	 * implementation is that every instance will carry around one unused
	 * {@code CatalogObjectImpl.m_objectAddress} field.
	 */
	static class Transient extends AttributeImpl
	{
		private final TupleDescriptor m_containingTupleDescriptor;
		private final int m_attnum;

		Transient(TupleDescriptor td, int attnum)
		{
			m_containingTupleDescriptor = requireNonNull(td);
			assert 0 < attnum : "nonpositive attnum in transient attribute";
			m_attnum = attnum;
		}

		@Override
		public int oid()
		{
			return InvalidOid;
		}

		@Override
		public int classOid()
		{
			return RegClass.CLASSID.oid();
		}

		@Override
		public int subId()
		{
			return m_attnum;
		}

		/**
		 * Returns true for an attribute of a transient {@code TupleDescriptor},
		 * even though {@code oid()} will return {@code InvalidOid}.
		 *<p>
		 * It's not clear any other convention would be less weird.
		 */
		@Override
		public boolean isValid()
		{
			return true;
		}

		@Override
		public boolean equals(Object other)
		{
			if ( this == other )
				return true;
			if ( ! super.equals(other) )
				return false;
			return ! ( m_containingTupleDescriptor instanceof Ephemeral );
		}

		@Override
		public int hashCode()
		{
			if ( m_containingTupleDescriptor instanceof Ephemeral )
				return System.identityHashCode(this);
			return super.hashCode();
		}

		@Override
		boolean foundIn(TupleDescriptor td)
		{
			return m_containingTupleDescriptor == td;
		}
	}

	/**
	 * A transient attribute belonging to a synthetic tuple descriptor with
	 * one element of a specified {@code RegType}.
	 *<p>
	 * Such a singleton tuple descriptor allows the {@code TupleTableSlot} API
	 * to be used as-is for related applications like array element access.
	 *<p>
	 * Most methods simply delegate to the associated RegType.
	 */
	static class OfType extends Transient
	{
		private static final Simple s_anonymous = Simple.fromJava("?column?");

		private final RegType m_type;

		OfType(TupleDescriptor td, RegType type)
		{
			super(td, 1);
			m_type = requireNonNull(type);
		}

		@Override
		public Simple name()
		{
			return s_anonymous;
		}

		@Override
		public RegType type()
		{
			return m_type;
		}
	}
}
