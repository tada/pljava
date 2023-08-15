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
package org.postgresql.pljava.pg;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.SwitchPoint;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLType;
import java.sql.SQLException;
import java.sql.SQLXML;

import java.util.Iterator;
import java.util.List;

import java.util.function.UnaryOperator;

import org.postgresql.pljava.TargetList.Projection;

import static org.postgresql.pljava.internal.SwitchPointCache.doNotCache;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.TYPEOID; // syscache
import static org.postgresql.pljava.pg.ModelConstants.alignmentFromCatalog;
import static org.postgresql.pljava.pg.ModelConstants.storageFromCatalog;

import org.postgresql.pljava.pg.adt.GrantAdapter;
import org.postgresql.pljava.pg.adt.NameAdapter;
import org.postgresql.pljava.pg.adt.OidAdapter;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCLASS_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCOLLATION_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGNAMESPACE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGPROCEDURE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGROLE_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;
import static org.postgresql.pljava.pg.adt.XMLAdapter.SYNTHETIC_INSTANCE;
import static org.postgresql.pljava.pg.adt.Primitives.*;

import org.postgresql.pljava.annotation.BaseUDT.Alignment;
import org.postgresql.pljava.annotation.BaseUDT.Storage;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Qualified;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Unqualified;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

/*
 * Can get lots of information, including TupleDesc, domain constraints, etc.,
 * from the typcache. A typcache entry is immortal but bits of it can change.
 * So it may be safe to keep a reference to the entry forever, but detect when
 * bits have changed. See in particular tupDesc_identifier.
 *
 * Many of the attributes of pg_type are available in the typcache. But
 * lookup_type_cache() does not have a _noerror version. If there is any doubt
 * about the existence of a type to be looked up, one must either do a syscache
 * lookup first anyway, or have a plan to catch an undefined_object error.
 * Same if you happen to look up a type still in the "only a shell" stage.
 * At that rate, may as well rely on the syscache for all the pg_type info.
 */

abstract class RegTypeImpl extends Addressed<RegType>
implements
	Nonshared<RegType>,	Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.USAGE>, RegType
{
	/**
	 * Per-instance switch point, to be invalidated selectively
	 * by a syscache callback.
	 *<p>
	 * Only {@link NoModifier NoModifier} carries one; derived instances of
	 * {@link Modified Modified} or {@link Blessed Blessed} return that one.
	 */
	abstract SwitchPoint cacheSwitchPoint();

	/* Implementation of Addressed */

	@Override
	public RegClass.Known<RegType> classId()
	{
		return CLASSID;
	}

	@Override
	int cacheId()
	{
		return TYPEOID;
	}

	/* Implementation of Named, Namespaced, Owned, AccessControlled */

	private static Simple name(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return
			t.get(Att.TYPNAME, NameAdapter.SIMPLE_INSTANCE);
	}

	private static RegNamespace namespace(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPNAMESPACE, REGNAMESPACE_INSTANCE);
	}

	private static RegRole owner(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPOWNER, REGROLE_INSTANCE);
	}

	private static List<CatalogObject.Grant> grants(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPACL, GrantAdapter.LIST_INSTANCE);
	}

	/* Implementation of RegType */

	/**
	 * Merely passes the supplied slots array to the superclass constructor; all
	 * initialization of the slots will be the responsibility of the subclass.
	 */
	RegTypeImpl(MethodHandle[] slots)
	{
		super(slots);
	}

	/**
	 * Called from {@code Factory}'s {@code invalidateType} to set up
	 * the invalidation of this type's metadata.
	 *<p>
	 * Adds this type's {@code SwitchPoint} to the caller's list so that,
	 * if more than one is to be invalidated, that can be done in bulk. Adds to
	 * <var>postOps</var> any operations the caller should conclude with
	 * after invalidating the {@code SwitchPoint}.
	 */
	void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
	{
		/*
		 * We don't expect invalidations for any flavor except NoModifier, so
		 * this no-op version will be overridden there only.
		 */
	}

	/**
	 * Holder for the {@code RegClass} corresponding to {@code relation()},
	 * only non-null during a call of {@code dualHandshake}.
	 */
	private RegClass m_dual = null;

	/**
	 * A lazily-populated synthetic tuple descriptor with a single element
	 * of this type.
	 */
	private TupleDescriptor m_singleton;

	/**
	 * Called by the corresponding {@code RegClass} instance if it has just
	 * looked us up.
	 *<p>
	 * Because the {@code SwitchPointCache} recomputation methods always execute
	 * on the PG thread, plain access to an instance field does the trick here.
	 */
	void dualHandshake(RegClass dual)
	{
		try
		{
			m_dual = dual;
			dual = relation();
			assert dual == m_dual : "RegClass/RegType handshake outcome";
		}
		finally
		{
			m_dual = null;
		}
	}

	static final UnaryOperator<MethodHandle[]> s_initializer;

	static final int SLOT_TUPLEDESCRIPTOR;
	static final int SLOT_LENGTH;
	static final int SLOT_BYVALUE;
	static final int SLOT_TYPE;
	static final int SLOT_CATEGORY;
	static final int SLOT_PREFERRED;
	static final int SLOT_DEFINED;
	static final int SLOT_DELIMITER;
	static final int SLOT_RELATION;
	static final int SLOT_ELEMENT;
	static final int SLOT_ARRAY;
	static final int SLOT_INPUT;
	static final int SLOT_OUTPUT;
	static final int SLOT_RECEIVE;
	static final int SLOT_SEND;
	static final int SLOT_MODIFIERINPUT;
	static final int SLOT_MODIFIEROUTPUT;
	static final int SLOT_ANALYZE;
	static final int SLOT_SUBSCRIPT;
	static final int SLOT_ALIGNMENT;
	static final int SLOT_STORAGE;
	static final int SLOT_NOTNULL;
	static final int SLOT_BASETYPE;
	static final int SLOT_DIMENSIONS;
	static final int SLOT_COLLATION;
	static final int SLOT_DEFAULTTEXT;
	static final int NSLOTS;

	static
	{
		int i = CatalogObjectImpl.Addressed.NSLOTS;
		s_initializer =
			new Builder<>(RegTypeImpl.class)
			.withLookup(lookup().in(RegTypeImpl.class))
			.withSwitchPoint(RegTypeImpl::cacheSwitchPoint)
			.withSlots(o -> o.m_slots)

			.withCandidates(
				CatalogObjectImpl.Addressed.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Addressed.class)
			.withDependent("cacheTuple", SLOT_TUPLE)

			.withCandidates(RegTypeImpl.class.getDeclaredMethods())
			.withReceiverType(CatalogObjectImpl.Named.class)
			.withReturnType(Unqualified.class)
			.withDependent("name", SLOT_NAME)
			.withReceiverType(CatalogObjectImpl.Namespaced.class)
			.withReturnType(null)
			.withDependent("namespace", SLOT_NAMESPACE)
			.withReceiverType(CatalogObjectImpl.Owned.class)
			.withDependent("owner", SLOT_OWNER)
			.withReceiverType(CatalogObjectImpl.AccessControlled.class)
			.withDependent("grants", SLOT_ACL)

			.withReceiverType(null)
			.withSwitchPoint(o ->
			{
				RegClassImpl c = (RegClassImpl)o.relation();
				if ( c.isValid() )
					return c.m_cacheSwitchPoint;
				return o.cacheSwitchPoint();
			})
			.withDependent(
				  "tupleDescriptorCataloged", SLOT_TUPLEDESCRIPTOR = i++)

			.withSwitchPoint(RegTypeImpl::cacheSwitchPoint)
			.withDependent(         "length", SLOT_LENGTH          = i++)
			.withDependent(        "byValue", SLOT_BYVALUE         = i++)
			.withDependent(           "type", SLOT_TYPE            = i++)
			.withDependent(       "category", SLOT_CATEGORY        = i++)
			.withDependent(      "preferred", SLOT_PREFERRED       = i++)
			.withDependent(        "defined", SLOT_DEFINED         = i++)
			.withDependent(      "delimiter", SLOT_DELIMITER       = i++)
			.withDependent(       "relation", SLOT_RELATION        = i++)
			.withDependent(        "element", SLOT_ELEMENT         = i++)
			.withDependent(          "array", SLOT_ARRAY           = i++)
			.withDependent(          "input", SLOT_INPUT           = i++)
			.withDependent(         "output", SLOT_OUTPUT          = i++)
			.withDependent(        "receive", SLOT_RECEIVE         = i++)
			.withDependent(           "send", SLOT_SEND            = i++)
			.withDependent(  "modifierInput", SLOT_MODIFIERINPUT   = i++)
			.withDependent( "modifierOutput", SLOT_MODIFIEROUTPUT  = i++)
			.withDependent(        "analyze", SLOT_ANALYZE         = i++)
			.withDependent(      "subscript", SLOT_SUBSCRIPT       = i++)
			.withDependent(      "alignment", SLOT_ALIGNMENT       = i++)
			.withDependent(        "storage", SLOT_STORAGE         = i++)
			.withDependent(        "notNull", SLOT_NOTNULL         = i++)
			.withDependent(       "baseType", SLOT_BASETYPE        = i++)
			.withDependent(     "dimensions", SLOT_DIMENSIONS      = i++)
			.withDependent(      "collation", SLOT_COLLATION       = i++)
			.withDependent(    "defaultText", SLOT_DEFAULTTEXT     = i++)

			.build();
		NSLOTS = i;
	}

	static class Att
	{
		static final Projection TYPBASETYPE_TYPTYPMOD;

		static final Attribute TYPNAME;
		static final Attribute TYPNAMESPACE;
		static final Attribute TYPOWNER;
		static final Attribute TYPACL;
		static final Attribute TYPLEN;
		static final Attribute TYPBYVAL;
		static final Attribute TYPTYPE;
		static final Attribute TYPCATEGORY;
		static final Attribute TYPISPREFERRED;
		static final Attribute TYPISDEFINED;
		static final Attribute TYPDELIM;
		static final Attribute TYPRELID;
		static final Attribute TYPELEM;
		static final Attribute TYPARRAY;
		static final Attribute TYPINPUT;
		static final Attribute TYPOUTPUT;
		static final Attribute TYPRECEIVE;
		static final Attribute TYPSEND;
		static final Attribute TYPMODIN;
		static final Attribute TYPMODOUT;
		static final Attribute TYPANALYZE;
		static final Attribute TYPSUBSCRIPT;
		static final Attribute TYPALIGN;
		static final Attribute TYPSTORAGE;
		static final Attribute TYPNOTNULL;
		static final Attribute TYPNDIMS;
		static final Attribute TYPCOLLATION;
		static final Attribute TYPDEFAULT;
		static final Attribute TYPDEFAULTBIN;

		static
		{
			Projection p = CLASSID.tupleDescriptor().project(
				"typbasetype",  // these two are wanted
				"typtypmod",    // together, first, below
				"typname",
				"typnamespace",
				"typowner",
				"typacl",
				"typlen",
				"typbyval",
				"typtype",
				"typcategory",
				"typispreferred",
				"typisdefined",
				"typdelim",
				"typrelid",
				"typelem",
				"typarray",
				"typinput",
				"typoutput",
				"typreceive",
				"typsend",
				"typmodin",
				"typmodout",
				"typanalyze",
				"typsubscript",
				"typalign",
				"typstorage",
				"typnotnull",
				"typndims",
				"typcollation",
				"typdefault",
				"typdefaultbin"
			);

			Iterator<Attribute> itr = p.iterator();

			TYPBASETYPE_TYPTYPMOD = p.project(itr.next(), itr.next());

			TYPNAME        = itr.next();
			TYPNAMESPACE   = itr.next();
			TYPOWNER       = itr.next();
			TYPACL         = itr.next();
			TYPLEN         = itr.next();
			TYPBYVAL       = itr.next();
			TYPTYPE        = itr.next();
			TYPCATEGORY    = itr.next();
			TYPISPREFERRED = itr.next();
			TYPISDEFINED   = itr.next();
			TYPDELIM       = itr.next();
			TYPRELID       = itr.next();
			TYPELEM        = itr.next();
			TYPARRAY       = itr.next();
			TYPINPUT       = itr.next();
			TYPOUTPUT      = itr.next();
			TYPRECEIVE     = itr.next();
			TYPSEND        = itr.next();
			TYPMODIN       = itr.next();
			TYPMODOUT      = itr.next();
			TYPANALYZE     = itr.next();
			TYPSUBSCRIPT   = itr.next();
			TYPALIGN       = itr.next();
			TYPSTORAGE     = itr.next();
			TYPNOTNULL     = itr.next();
			TYPNDIMS       = itr.next();
			TYPCOLLATION   = itr.next();
			TYPDEFAULT     = itr.next();
			TYPDEFAULTBIN  = itr.next();

			assert ! itr.hasNext() : "attribute initialization miscount";
		}
	}

	/* computation methods */

	/**
	 * Obtain the tuple descriptor for an ordinary cataloged composite type.
	 *<p>
	 * Every such type has a corresponding {@link RegClass RegClass}, which has
	 * the {@code SwitchPoint} that will govern the descriptor's invalidation,
	 * and a one-element array in which the descriptor should be stored. This
	 * method returns the array.
	 */
	private static TupleDescriptor.Interned[]
		tupleDescriptorCataloged(RegTypeImpl o)
	{
		RegClassImpl c = (RegClassImpl)o.relation();

		/*
		 * If this is not a composite type, c won't be valid, and our API
		 * contract is to return null (which means, here, return {null}).
		 */
		if ( ! c.isValid() )
			return new TupleDescriptor.Interned[] { null };

		TupleDescriptor.Interned[] r = c.m_tupDescHolder;

		/*
		 * If c is RegClass.CLASSID itself, it has the descriptor by now
		 * (bootstrapped at the latest during the above relation() call,
		 * if it wasn't there already).
		 */
		if ( RegClass.CLASSID == c )
		{
			assert null != r && null != r[0] :
				"RegClass TupleDescriptor bootstrap outcome";
			return r;
		}

		assert null == r : "RegClass has tuple descriptor when RegType doesn't";

		/*
		 * Otherwise, do the work here, and store the descriptor in r.
		 * Can pass -1 for the modifier; Blessed types do not use this method.
		 */

		ByteBuffer b = _lookupRowtypeTupdesc(o.oid(), -1);
		assert null != b : "cataloged composite type tupdesc lookup";
		b.order(nativeOrder());
		r = new TupleDescriptor.Interned[]{ new TupleDescImpl.Cataloged(b, c) };
		return c.m_tupDescHolder = r;
	}

	private static TupleDescriptor.Interned[] tupleDescriptorBlessed(Blessed o)
	{
		TupleDescriptor.Interned[] r = new TupleDescriptor.Interned[1];
		ByteBuffer b = _lookupRowtypeTupdesc(o.oid(), o.modifier());

		/*
		 * If there is no registered tuple descriptor for this typmod, return an
		 * empty value to the current caller, but do not cache it; a later call
		 * could find one has been registered.
		 */
		if ( null == b )
		{
			doNotCache();
			return r;
		}

		b.order(nativeOrder());
		r[0] = new TupleDescImpl.Blessed(b, o);
		return o.m_tupDescHolder = r;
	}

	private static short length(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPLEN, INT2_INSTANCE);
	}

	private static boolean byValue(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPBYVAL, BOOLEAN_INSTANCE);
	}

	private static Type type(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return typeFromCatalog(
			t.get(Att.TYPTYPE, INT1_INSTANCE));
	}

	private static char category(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return (char)
			(0xff & t.get(Att.TYPCATEGORY, INT1_INSTANCE));
	}

	private static boolean preferred(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPISPREFERRED, BOOLEAN_INSTANCE);
	}

	private static boolean defined(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPISDEFINED, BOOLEAN_INSTANCE);
	}

	private static byte delimiter(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPDELIM, INT1_INSTANCE);
	}

	private static RegClass relation(RegTypeImpl o) throws SQLException
	{
		/*
		 * If this is a handshake occurring when the corresponding RegClass
		 * has just looked *us* up, we are done.
		 */
		if ( null != o.m_dual )
			return o.m_dual;

		/*
		 * Otherwise, look up the corresponding RegClass, and do the same
		 * handshake in reverse. Either way, the connection is set up
		 * bidirectionally with one cache lookup starting from either. That
		 * can avoid extra work in operations (like TupleDescriptor caching)
		 * that may touch both objects, without complicating their code.
		 */
		TupleTableSlot t = o.cacheTuple();
		RegClass c = t.get(Att.TYPRELID, REGCLASS_INSTANCE);

		((RegClassImpl)c).dualHandshake(o);
		return c;
	}

	private static RegType element(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPELEM, REGTYPE_INSTANCE);
	}

	private static RegType array(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPARRAY, REGTYPE_INSTANCE);
	}

	private static RegProcedure<TypeInput> input(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeInput> p = (RegProcedure<TypeInput>)
			t.get(Att.TYPINPUT, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeOutput> output(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeOutput> p = (RegProcedure<TypeOutput>)
			t.get(Att.TYPOUTPUT, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeReceive> receive(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeReceive> p = (RegProcedure<TypeReceive>)
			t.get(Att.TYPRECEIVE, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeSend> send(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeSend> p = (RegProcedure<TypeSend>)
			t.get(Att.TYPSEND, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeModifierInput> modifierInput(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeModifierInput> p = (RegProcedure<TypeModifierInput>)
			t.get(Att.TYPMODIN, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeModifierOutput> modifierOutput(
		RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeModifierOutput> p = (RegProcedure<TypeModifierOutput>)
			t.get(Att.TYPMODOUT, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeAnalyze> analyze(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeAnalyze> p = (RegProcedure<TypeAnalyze>)
			t.get(Att.TYPANALYZE, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static RegProcedure<TypeSubscript> subscript(RegTypeImpl o)
	throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		@SuppressWarnings("unchecked") // XXX add memo magic here
		RegProcedure<TypeSubscript> p = (RegProcedure<TypeSubscript>)
			t.get(Att.TYPSUBSCRIPT, REGPROCEDURE_INSTANCE);
		return p;
	}

	private static Alignment alignment(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return alignmentFromCatalog(
			t.get(Att.TYPALIGN, INT1_INSTANCE));
	}

	private static Storage storage(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return storageFromCatalog(
			t.get(Att.TYPSTORAGE, INT1_INSTANCE));
	}

	private static boolean notNull(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPNOTNULL, BOOLEAN_INSTANCE);
	}

	private static RegType baseType(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return Att.TYPBASETYPE_TYPTYPMOD
			.applyOver(t, c ->
				c.apply(OidAdapter.INT4_INSTANCE, INT4_INSTANCE,
					(              oid,           mod           ) ->
					CatalogObjectImpl.Factory.formMaybeModifiedType(oid, mod)));
	}

	private static int dimensions(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPNDIMS, INT4_INSTANCE);
	}

	private static RegCollation collation(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPCOLLATION, REGCOLLATION_INSTANCE);
	}

	private static String defaultText(RegTypeImpl o) throws SQLException
	{
		TupleTableSlot t = o.cacheTuple();
		return t.get(Att.TYPDEFAULT, TextAdapter.INSTANCE);
	}

	/* API methods */

	@Override
	public TupleDescriptor.Interned tupleDescriptor()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TUPLEDESCRIPTOR];
			return ((TupleDescriptor.Interned[])h.invokeExact(this, h))[0];
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public short length()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_LENGTH];
			return (short)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public boolean byValue()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_BYVALUE];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public Type type()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_TYPE];
			return (Type)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public char category()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_CATEGORY];
			return (char)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean preferred()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_PREFERRED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public boolean defined()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DEFINED];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public byte delimiter()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DELIMITER];
			return (byte)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegClass relation()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RELATION];
			return (RegClass)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public RegType element()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ELEMENT];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public RegType array()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ARRAY];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeInput> input()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_INPUT];
			return (RegProcedure<TypeInput>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeOutput> output()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_OUTPUT];
			return (RegProcedure<TypeOutput>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeReceive> receive()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_RECEIVE];
			return (RegProcedure<TypeReceive>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeSend> send()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SEND];
			return (RegProcedure<TypeSend>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeModifierInput> modifierInput()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_MODIFIERINPUT];
			return (RegProcedure<TypeModifierInput>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeModifierOutput> modifierOutput()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_MODIFIEROUTPUT];
			return (RegProcedure<TypeModifierOutput>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeAnalyze> analyze()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ANALYZE];
			return (RegProcedure<TypeAnalyze>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegProcedure<TypeSubscript> subscript()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_SUBSCRIPT];
			return (RegProcedure<TypeSubscript>)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public Alignment alignment()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_ALIGNMENT];
			return (Alignment)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public Storage storage()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_STORAGE];
			return (Storage)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public boolean notNull()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_NOTNULL];
			return (boolean)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegType baseType()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_BASETYPE];
			return (RegType)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public int dimensions()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DIMENSIONS];
			return (int)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	@Override
	public RegCollation collation()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_COLLATION];
			return (RegCollation)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
		// also available in the typcache, FWIW
	}

	@Override
	public SQLXML defaultBin()
	{
		/*
		 * Because of the JDBC rules that an SQLXML instance lasts no longer
		 * than one transaction and can only be read once, it is not a good
		 * candidate for caching. We will just fetch a new one from the cached
		 * tuple as needed.
		 */
		TupleTableSlot s = cacheTuple();
		return s.get(Att.TYPDEFAULTBIN, SYNTHETIC_INSTANCE);
	}

	@Override
	public String defaultText()
	{
		try
		{
			MethodHandle h = m_slots[SLOT_DEFAULTTEXT];
			return (String)h.invokeExact(this, h);
		}
		catch ( Throwable t )
		{
			throw unchecked(t);
		}
	}

	/**
	 * Return the expected zero value for {@code subId}.
	 *<p>
	 * For keying the {@code CacheMap}, we sneak type modifiers in there
	 * (PG types do not otherwise use {@code subId}), but that's an
	 * implementation detail that could be done a different way if upstream
	 * ever decided to have subIds for types, and having it show in the address
	 * triple of a modified type could be surprising to an old PostgreSQL hand.
	 */
	@Override
	public int subId()
	{
		return 0;
	}

	/**
	 * Return the type modifier.
	 *<p>
	 * In this implementation, where we snuck it in as the third component
	 * of the cache key, sneak it back out.
	 */
	@Override
	public int modifier()
	{
		int m = super.subId();
		if ( -1 == m )
			return 0;
		return m;
	}

	/**
	 * Return a synthetic tuple descriptor with a single element of this type.
	 */
	public TupleDescriptor singletonTupleDescriptor()
	{
		TupleDescriptor td = m_singleton;
		if ( null != td )
			return td;
		/*
		 * In case of a race, the synthetic tuple descriptors will be
		 * equivalent anyway.
		 */
		return m_singleton = new TupleDescImpl.OfType(this);
	}

	/**
	 * Represents a type that has been mentioned without an accompanying type
	 * modifier (or with the 'unspecified' value -1 for its type modifier).
	 */
	static class NoModifier extends RegTypeImpl
	{
		private SwitchPoint m_sp;

		@Override
		SwitchPoint cacheSwitchPoint()
		{
			return m_sp;
		}

		NoModifier()
		{
			super(s_initializer.apply(new MethodHandle[NSLOTS]));
			m_sp = new SwitchPoint();
		}

		@Override
		void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
		{
			sps.add(m_sp);
			m_sp = new SwitchPoint();
		}

		@Override
		public int modifier()
		{
			return -1;
		}

		@Override
		public RegType modifier(int typmod)
		{
			if ( -1 == typmod )
				return this;
			return
				CatalogObjectImpl.Factory.formMaybeModifiedType(oid(), typmod);
		}

		@Override
		public RegType withoutModifier()
		{
			return this;
		}
	}

	/**
	 * Represents a type that is not {@code RECORD} and has a type modifier that
	 * is not the unspecified value.
	 *<p>
	 * When the {@code RECORD} type appears in PostgreSQL with a type modifier,
	 * that is a special case; see {@link Blessed Blessed}.
	 */
	static class Modified extends RegTypeImpl
	{
		private final NoModifier m_base;

		@Override
		SwitchPoint cacheSwitchPoint()
		{
			return m_base.m_sp;
		}

		Modified(NoModifier base)
		{
			super(base.m_slots);
			m_base = base; // must keep it live, not only share its slots
		}

		@Override
		public RegType modifier(int typmod)
		{
			if ( modifier() == typmod )
				return this;
			return m_base.modifier(typmod);
		}

		@Override
		public RegType withoutModifier()
		{
			return m_base;
		}

		/**
		 * Whether a just-mentioned modified type "exists" depends on whether
		 * its unmodified type exists and has a modifier input function.
		 *<p>
		 * No attempt is made here to verify that the modifier value is one that
		 * the modifier input/output functions would produce or accept.
		 */
		@Override
		public boolean exists()
		{
			return m_base.exists()  &&  modifierInput().isValid();
		}

		@Override
		public String toString()
		{
			String prefix = super.toString();
			return prefix + "(" + modifier() + ")";
		}
	}

	/**
	 * Represents the "row type" of a {@link TupleDescriptor TupleDescriptor}
	 * that has been programmatically constructed and interned ("blessed").
	 *<p>
	 * Such a type is represented in PostgreSQL as the type {@code RECORD}
	 * with a type modifier assigned uniquely for the life of the backend.
	 */
	static class Blessed extends RegTypeImpl
	{
		/**
		 * Associated tuple descriptor, redundantly kept accessible here as well
		 * as opaquely bound into a {@code SwitchPointCache} method handle.
		 *<p>
		 * A {@code Blessed} descriptor has no associated {@code RegClass}, so
		 * a slot for the descriptor is provided here. No invalidation events
		 * are expected for a blessed type, but the one-element array form here
		 * matches that used in {@code RegClass} for cataloged descriptors, to
		 * avoid multiple cases in the code. Only accessed from
		 * {@code SwitchPointCache} computation methods and
		 * {@code TupleDescImpl} factory methods, all of which execute on the PG
		 * thread; no synchronization fuss needed.
		 *<p>
		 * When null, no computation method has run, and the state is not known.
		 * Otherwise, the single element is the result to be returned by
		 * the {@code tupleDescriptor()} API method.
		 */
		TupleDescriptor.Interned[] m_tupDescHolder;
		private final MethodHandle[] m_moreSlots;
		private static final UnaryOperator<MethodHandle[]> s_initializer;
		private static final int SLOT_TDBLESSED;
		private static final int NSLOTS;

		static
		{
			int i = 0;
			s_initializer =
				new Builder<>(Blessed.class)
				.withLookup(lookup().in(RegTypeImpl.class))
				.withSwitchPoint(Blessed::cacheSwitchPoint)
				.withSlots(o -> o.m_moreSlots)
				.withCandidates(RegTypeImpl.class.getDeclaredMethods())
				.withDependent("tupleDescriptorBlessed", SLOT_TDBLESSED = i++)
				.build();
			NSLOTS = i;
		}

		@Override
		SwitchPoint cacheSwitchPoint()
		{
			return ((NoModifier)RECORD).m_sp;
		}

		Blessed()
		{
			super(((RegTypeImpl)RECORD).m_slots);
			// RECORD is static final, no other effort needed to keep it live
			m_moreSlots = s_initializer.apply(new MethodHandle[NSLOTS]);
		}

		/**
		 * The tuple descriptor registered in the type cache for this 'blessed'
		 * type, or null if none.
		 *<p>
		 * A null value is not sticky; it would be possible to 'mention' a
		 * blessed type with a not-yet-used typmod, which could then later exist
		 * after a tuple descriptor has been interned. (Such usage would be odd,
		 * though; typically one will obtain a blessed instance from an existing
		 * tuple descriptor.)
		 */
		@Override
		public TupleDescriptor.Interned tupleDescriptor()
		{
			try
			{
				MethodHandle h = m_moreSlots[SLOT_TDBLESSED];
				return ((TupleDescriptor.Interned[])h.invokeExact(this, h))[0];
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}

		@Override
		public RegType modifier(int typmod)
		{
			throw new UnsupportedOperationException(
				"may not alter the type modifier of an interned row type");
		}

		@Override
		public RegType withoutModifier()
		{
			return RECORD;
		}

		/**
		 * Whether a just-mentioned blessed type "exists" depends on whether
		 * there is a tuple descriptor registered for it in the type cache.
		 *<p>
		 * A false value is not sticky; it would be possible to 'mention' a
		 * blessed type with a not-yet-used typmod, which could then later exist
		 * after a tuple descriptor has been interned. (Such usage would be odd,
		 * though; typically one will obtain a blessed instance from an existing
		 * tuple descriptor.)
		 */
		@Override
		public boolean exists()
		{
			return null != tupleDescriptor();
		}

		@Override
		public String toString()
		{
			String prefix = super.toString();
			return prefix + "[" + modifier() + "]";
		}
	}

	private static Type typeFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'b': return Type.BASE;
		case (byte)'c': return Type.COMPOSITE;
		case (byte)'d': return Type.DOMAIN;
		case (byte)'e': return Type.ENUM;
		case (byte)'m': return Type.MULTIRANGE;
		case (byte)'p': return Type.PSEUDO;
		case (byte)'r': return Type.RANGE;
		}
		throw unchecked(new SQLException(
			"unrecognized Type type '" + (char)b + "' in catalog", "XX000"));
	}
}
