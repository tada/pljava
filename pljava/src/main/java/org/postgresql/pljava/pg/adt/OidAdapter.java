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
package org.postgresql.pljava.pg.adt;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.model.Attribute;

import org.postgresql.pljava.model.*;

import static org.postgresql.pljava.pg.CatalogObjectImpl.of;

/**
 * PostgreSQL {@code oid} type represented as
 * {@code CatalogObject} or one of its {@code Addressed} subtypes.
 */
public class OidAdapter<T extends CatalogObject>
extends Adapter.As<T,Void>
{
	public static final OidAdapter<CatalogObject>     INSTANCE;
	public static final Int4                          INT4_INSTANCE;
	public static final Addressed<RegClass>           REGCLASS_INSTANCE;
	public static final Addressed<RegNamespace>       REGNAMESPACE_INSTANCE;
	public static final Addressed<RegRole>            REGROLE_INSTANCE;
	public static final Addressed<RegType>            REGTYPE_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure(OidAdapter.class, Via.INT32ZX),
				configure(      Int4.class, Via.INT32ZX),
				configure( Addressed.class, Via.INT32ZX)
			});

		INSTANCE               = new OidAdapter<>(configs[0], null);

		INT4_INSTANCE          = new         Int4(configs[1]);

		REGCLASS_INSTANCE      = new  Addressed<>(configs[2],
			RegClass.CLASSID, RegClass.class, RegType.REGCLASS);

		REGNAMESPACE_INSTANCE  = new  Addressed<>(configs[2],
			RegNamespace.CLASSID, RegNamespace.class, RegType.REGNAMESPACE);

		REGROLE_INSTANCE       = new  Addressed<>(configs[2],
			RegRole.CLASSID, RegRole.class, RegType.REGROLE);

		REGTYPE_INSTANCE       = new  Addressed<>(configs[2],
			RegType.CLASSID, RegType.class, RegType.REGTYPE);
	}

	/**
	 * Types for which the non-specific {@code OidAdapter} or {@code Int4} will
	 * allow itself to be applied.
	 *<p>
	 * Some halfhearted effort is put into ordering this with less commonly
	 * sought entries later.
	 */
	private static final RegType[] s_oidTypes =
	{
		RegType.OID, RegType.REGPROC, RegType.REGPROCEDURE, RegType.REGTYPE,
		RegType.REGNAMESPACE, RegType.REGOPER, RegType.REGOPERATOR,
		RegType.REGROLE, RegType.REGCLASS, RegType.REGCOLLATION,
		RegType.REGCONFIG, RegType.REGDICTIONARY
	};

	private OidAdapter(Configuration c, Class<T> witness)
	{
		super(c, null, witness);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		for ( RegType t : s_oidTypes )
			if ( t == pgType )
				return true;
		return false;
	}

	public CatalogObject fetch(Attribute a, int in)
	{
		return of(in);
	}

	/**
	 * Adapter for the {@code oid} type, returned as a primitive {@code int}.
	 */
	public static class Int4 extends Adapter.AsInt.Unsigned<Void>
	{
		private Int4(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			for ( RegType t : s_oidTypes )
				if ( t == pgType )
					return true;
			return false;
		}

		public int fetch(Attribute a, int in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code oid} type, able to return most of the
	 * {@link CatalogObject.Addressed CatalogObject.Addressed} subinterfaces.
	 */
	public static class Addressed<T extends CatalogObject.Addressed<T>>
	extends OidAdapter<T>
	{
		private final RegClass.Known<T> m_classId;
		private final RegType[] m_specificTypes;

		private Addressed(
			Configuration c, RegClass.Known<T> classId, Class<T> witness,
			RegType... specificTypes)
		{
			super(c, witness);
			m_classId = classId;
			m_specificTypes = specificTypes;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			for ( RegType t : m_specificTypes )
				if ( t == pgType )
					return true;
			return RegType.OID == pgType;
		}

		public T fetch(Attribute a, int in)
		{
			return of(m_classId, in);
		}
	}
}
