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
package org.postgresql.pljava.pg;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;

import java.sql.SQLException;

import java.util.Iterator;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.OPEROID; // syscache

import static org.postgresql.pljava.pg.adt.NameAdapter.OPERATOR_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGOPERATOR_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.INT1_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.BOOLEAN_INSTANCE;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Operator;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

class RegOperatorImpl extends Addressed<RegOperator>
implements Nonshared<RegOperator>, Namespaced<Operator>, Owned, RegOperator
{
	private static final UnaryOperator<MethodHandle[]> s_initializer;

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegOperator> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return OPEROID;
	}

	/* Implementation of Named, Namespaced, Owned */

	private static Operator name(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.OPRNAME, OPERATOR_INSTANCE);
	}

	private static RegNamespace namespace(RegOperatorImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.OPRNAMESPACE, REGNAMESPACE_INSTANCE);
	}

	private static RegRole owner(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.OPROWNER, REGROLE_INSTANCE);
	}

	/* Implementation of RegOperator */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegOperatorImpl()
	{
		super(s_initializer.apply(new MethodHandle[NSLOTS]));
	}

	static final int SLOT_KIND;
	static final int SLOT_CANMERGE;
	static final int SLOT_CANHASH;
	static final int SLOT_LEFTOPERAND;
	static final int SLOT_RIGHTOPERAND;
	static final int SLOT_RESULT;
	static final int SLOT_COMMUTATOR;
	static final int SLOT_NEGATOR;
	static final int SLOT_EVALUATOR;
	static final int SLOT_RESTRICTIONESTIMATOR;
	static final int SLOT_JOINESTIMATOR;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegOperatorImpl.class)
			.withLookup(lookup())
			.withSwitchPoint(o -> s_globalPoint[0])
			.withSlots(o -> o.m_slots)
			.withCandidates(RegOperatorImpl.class.getDeclaredMethods())

			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent(      "name", SLOT_NAME)
			.withReturnType(null)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withDependent( "namespace", SLOT_NAMESPACE)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent(     "owner", SLOT_OWNER)

			.withReceiverType(null)
			.withDependent(         "kind", SLOT_KIND                 = i++)
			.withDependent(     "canMerge", SLOT_CANMERGE             = i++)
			.withDependent(      "canHash", SLOT_CANHASH              = i++)
			.withDependent(  "leftOperand", SLOT_LEFTOPERAND          = i++)
			.withDependent( "rightOperand", SLOT_RIGHTOPERAND         = i++)
			.withDependent(       "result", SLOT_RESULT               = i++)
			.withDependent(   "commutator", SLOT_COMMUTATOR           = i++)
			.withDependent(      "negator", SLOT_NEGATOR              = i++)
			.withDependent(    "evaluator", SLOT_EVALUATOR            = i++)
			.withDependent(
				    "restrictionEstimator", SLOT_RESTRICTIONESTIMATOR = i++)
			.withDependent("joinEstimator", SLOT_JOINESTIMATOR        = i++)

			.build()
			.compose(CatalogObjectImpl.Addressed.s_initializer)::apply;
		NSLOTS = i;
	}

	static class Att
	{
		static final Attribute OPRNAME;
		static final Attribute OPRNAMESPACE;
		static final Attribute OPROWNER;
		static final Attribute OPRKIND;
		static final Attribute OPRCANMERGE;
		static final Attribute OPRCANHASH;
		static final Attribute OPRLEFT;
		static final Attribute OPRRIGHT;
		static final Attribute OPRRESULT;
		static final Attribute OPRCOM;
		static final Attribute OPRNEGATE;
		static final Attribute OPRCODE;
		static final Attribute OPRREST;
		static final Attribute OPRJOIN;

		static
		{
			Iterator<Attribute> itr = CLASSID.tupleDescriptor().project(
				"oprname",
				"oprnamespace",
				"oprowner",
				"oprkind",
				"oprcanmerge",
				"oprcanhash",
				"oprleft",
				"oprright",
				"oprresult",
				"oprcom",
				"oprnegate",
				"oprcode",
				"oprrest",
				"oprjoin"
			).iterator();

			OPRNAME      = itr.next();
			OPRNAMESPACE = itr.next();
			OPROWNER     = itr.next();
			OPRKIND      = itr.next();
			OPRCANMERGE  = itr.next();
			OPRCANHASH   = itr.next();
			OPRLEFT      = itr.next();
			OPRRIGHT     = itr.next();
			OPRRESULT    = itr.next();
			OPRCOM       = itr.next();
			OPRNEGATE    = itr.next();
			OPRCODE      = itr.next();
			OPRREST      = itr.next();
			OPRJOIN      = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	private static Kind kind(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		byte b = s.get(Att.OPRKIND, INT1_INSTANCE);
		switch ( b )
		{
		case (byte)'b':
			return Kind.INFIX;
		case (byte)'l':
			return Kind.PREFIX;
		case (byte)'r':
			@SuppressWarnings("deprecation")
			Kind k = Kind.POSTFIX;
			return k;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized operator kind value %#x", b));
		}
	}

	private static boolean canMerge(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRCANMERGE, BOOLEAN_INSTANCE);
	}

	private static boolean canHash(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRCANHASH, BOOLEAN_INSTANCE);
	}

	private static RegType leftOperand(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRLEFT, REGTYPE_INSTANCE);
	}

	private static RegType rightOperand(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRRIGHT, REGTYPE_INSTANCE);
	}

	private static RegType result(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRRESULT, REGTYPE_INSTANCE);
	}

	private static RegOperator commutator(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRCOM, REGOPERATOR_INSTANCE);
	}

	private static RegOperator negator(RegOperatorImpl o) throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		return s.get(Att.OPRNEGATE, REGOPERATOR_INSTANCE);
	}

	private static RegProcedure<Evaluator> evaluator(RegOperatorImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<Evaluator> p = (RegProcedure<Evaluator>)
			s.get(Att.OPRCODE, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<RestrictionSelectivity>
		restrictionEstimator(RegOperatorImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<RestrictionSelectivity> p =
			(RegProcedure<RestrictionSelectivity>)
			s.get(Att.OPRREST, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<JoinSelectivity>
		joinEstimator(RegOperatorImpl o)
	throws SQLException
	{
		TupleTableSlot s = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<JoinSelectivity> p = (RegProcedure<JoinSelectivity>)
			s.get(Att.OPRJOIN, REGPROCEDURE_INSTANCE);
		return p;
	}

	/* API methods */

	@Override
	public Kind kind()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_KIND];
			return (Kind)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean canMerge()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CANMERGE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean canHash()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CANHASH];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType leftOperand()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LEFTOPERAND];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType rightOperand()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RIGHTOPERAND];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType result()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RESULT];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegOperator commutator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COMMUTATOR];
			return (RegOperator)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegOperator negator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_NEGATOR];
			return (RegOperator)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<Evaluator> evaluator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_EVALUATOR];
			return (RegProcedure<Evaluator>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<RestrictionSelectivity> restrictionEstimator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RESTRICTIONESTIMATOR];
			return (RegProcedure<RestrictionSelectivity>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<JoinSelectivity> joinEstimator()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_JOINESTIMATOR];
			return (RegProcedure<JoinSelectivity>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}
}
