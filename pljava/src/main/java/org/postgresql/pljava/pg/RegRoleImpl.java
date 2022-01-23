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

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.SwitchPoint;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

import java.sql.SQLException;

import java.util.List;

import org.postgresql.pljava.RolePrincipal;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.AUTHOID; // syscache
import static org.postgresql.pljava.pg.ModelConstants.AUTHMEMMEMROLE;
import static org.postgresql.pljava.pg.ModelConstants.AUTHMEMROLEMEM;

import static org.postgresql.pljava.pg.adt.NameAdapter.SIMPLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT4_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

/**
 * Implementation of the {@link RegRole RegRole} interface.
 *<p>
 * That this class can in fact be cast to {@link RegRole.Grantee Grantee} is an
 * unadvertised implementation detail.
 */
class RegRoleImpl extends Addressed<RegRole>
implements
	Shared<RegRole>, Named<Simple>,
	AccessControlled<CatalogObject.Grant.OnRole>, RegRole.Grantee
{
	private static UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegRole> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return AUTHOID;
	}

	/* Implementation of Named, AccessControlled */

	private static Simple name(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(t.descriptor().get("rolname"), SIMPLE_INSTANCE);
	}

	private static List<CatalogObject.Grant.OnRole> grants(RegRoleImpl o)
	{
		throw notyet("CatCList support needed");
	}

	/* Implementation of RegRole */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegRoleImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_MEMBEROF;
	static final int SLOT_SUPERUSER;
	static final int SLOT_INHERIT;
	static final int SLOT_CREATEROLE;
	static final int SLOT_CREATEDB;
	static final int SLOT_CANLOGIN;
	static final int SLOT_REPLICATION;
	static final int SLOT_BYPASSRLS;
	static final int SLOT_CONNECTIONLIMIT;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegRoleImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(RegRoleImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent(    "grants", SLOT_ACL)

			.withReceiverType(null)
			.withDependent(       "memberOf", SLOT_MEMBEROF        = i++)
			.withDependent(      "superuser", SLOT_SUPERUSER       = i++)
			.withDependent(        "inherit", SLOT_INHERIT         = i++)
			.withDependent(     "createRole", SLOT_CREATEROLE      = i++)
			.withDependent(       "createDB", SLOT_CREATEDB        = i++)
			.withDependent(       "canLogIn", SLOT_CANLOGIN        = i++)
			.withDependent(    "replication", SLOT_REPLICATION     = i++)
			.withDependent(      "bypassRLS", SLOT_BYPASSRLS       = i++)
			.withDependent("connectionLimit", SLOT_CONNECTIONLIMIT = i++)

			.build()
			/*
			 * Add these slot initializers after what Addressed does.
			 */
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	/* computation methods */

	private static List<RegRole> memberOf(RegRoleImpl o)
	{
		throw notyet("CatCList support needed");
	}

	private static boolean superuser(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolsuper"), BOOLEAN_INSTANCE);
	}

	private static boolean inherit(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolinherit"), BOOLEAN_INSTANCE);
	}

	private static boolean createRole(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolcreaterole"), BOOLEAN_INSTANCE);
	}

	private static boolean createDB(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolcreatedb"), BOOLEAN_INSTANCE);
	}

	private static boolean canLogIn(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolcanlogin"), BOOLEAN_INSTANCE);
	}

	private static boolean replication(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolreplication"), BOOLEAN_INSTANCE);
	}

	private static boolean bypassRLS(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolbypassrls"), BOOLEAN_INSTANCE);
	}

	private static int connectionLimit(RegRoleImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(s.descriptor().get("rolconnlimit"), INT4_INSTANCE);
	}

	/* API methods */

	@Override
	public List<RegRole> memberOf()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_MEMBEROF];
			return (List<RegRole>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean superuser()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SUPERUSER];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean inherit()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INHERIT];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean createRole()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CREATEROLE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean createDB()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CREATEDB];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean canLogIn()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CANLOGIN];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean replication()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_REPLICATION];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean bypassRLS()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_BYPASSRLS];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int connectionLimit()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CONNECTIONLIMIT];
			return (int)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	/* Implementation of RegRole.Grantee */

	/*
	 * As it turns out, PostgreSQL doesn't use a notion like Identifier.Pseudo
	 * for the name of the public grantee. It uses the ordinary, folding name
	 * "public" and reserves it, forbidding that any actual role have any name
	 * that matches it according to the usual folding rules. So, construct that
	 * name here.
	 */
	private static final Simple s_public_name = Simple.fromCatalog("public");

	@Override
	public Simple nameAsGrantee()
	{
		return isPublic() ? s_public_name : name();
	}
}
