/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
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

import java.lang.annotation.Native;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import java.nio.charset.CharacterCodingException;

import java.sql.SQLException;
import java.sql.SQLXML;

import static java.util.Collections.unmodifiableSet;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.annotation.Trigger.Called;
import org.postgresql.pljava.annotation.Trigger.Event;
import org.postgresql.pljava.annotation.Trigger.Scope;

import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.Checked;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.jdbc.SQLXMLImpl;

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.CharsetEncoding.SERVER_ENCODING;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;

import static
	org.postgresql.pljava.pg.CatalogObjectImpl.Addressed._sysTableGetByOid;

import static org.postgresql.pljava.pg.DatumUtils.asAlwaysCopiedDatum;
import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;
import static org.postgresql.pljava.pg.DatumUtils.mapCString;
import static org.postgresql.pljava.pg.DatumUtils.mapFixedLength;

import org.postgresql.pljava.pg.LookupImpl.CallImpl.TriggerDataImpl;

import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_DATUM;
import static org.postgresql.pljava.pg.ModelConstants.Anum_pg_trigger_oid;
import static org.postgresql.pljava.pg.ModelConstants.TriggerOidIndexId;

import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgname;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgfoid;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgtype;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgenabled;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgisinternal;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgisclone;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgconstrrelid;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgconstrindid;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgconstraint;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgdeferrable;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tginitdeferred;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgnargs;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgnattr;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgattr;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgargs;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgqual;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgoldtable;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_TRG_tgnewtable;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Implements {@code Trigger}.
 *<p>
 * This implementation, at least at first, will have an unusual limitation:
 * its accessor methods (other than those of
 * {@link CatalogObject.Addressed Addressed}) may only work
 * when called by a trigger function or its language handler within the scope
 * of the function's preparation and execution. Some may be unimplemented even
 * then, as noted in the documentation of the methods themselves.
 *<p>
 * That spares it from having to deal with getting the content from
 * {@code pg_trigger}, or cache lifetime, or invalidation; it can operate from
 * the copy PostgreSQL supplies for the trigger function call, during the scope
 * of the call.
 *<p>
 * At least for now, then, it simply extends {@code CatalogObjectImpl} directly
 * rather than {@code CatalogObjectImpl.Addressed}, needing none of the caching
 * machinery in the latter.
 */
class TriggerImpl extends CatalogObjectImpl
implements Nonshared<Trigger>, Trigger
{
	@Native private static final char TRIGGER_FIRES_ON_ORIGIN  = 'O';
	@Native private static final char TRIGGER_FIRES_ALWAYS     = 'A';
	@Native private static final char TRIGGER_FIRES_ON_REPLICA = 'R';
	@Native private static final char TRIGGER_DISABLED         = 'D';

	@Native private static final int TRIGGER_TYPE_ROW      = 1 << 0;
	@Native private static final int TRIGGER_TYPE_BEFORE   = 1 << 1;
	@Native private static final int TRIGGER_TYPE_INSERT   = 1 << 2;
	@Native private static final int TRIGGER_TYPE_DELETE   = 1 << 3;
	@Native private static final int TRIGGER_TYPE_UPDATE   = 1 << 4;
	@Native private static final int TRIGGER_TYPE_TRUNCATE = 1 << 5;
	@Native private static final int TRIGGER_TYPE_INSTEAD  = 1 << 6;

	@Native private static final int TRIGGER_TYPE_LEVEL_MASK = TRIGGER_TYPE_ROW;
	@Native private static final int TRIGGER_TYPE_STATEMENT  = 0;

	@Native private static final int TRIGGER_TYPE_TIMING_MASK =
		 TRIGGER_TYPE_BEFORE | TRIGGER_TYPE_INSTEAD;
	@Native private static final int TRIGGER_TYPE_AFTER       = 0;

	@Native private static final int TRIGGER_TYPE_EVENT_MASK =
		TRIGGER_TYPE_INSERT | TRIGGER_TYPE_DELETE |
		TRIGGER_TYPE_UPDATE | TRIGGER_TYPE_TRUNCATE;

	/*
	 * By inspection of the above, event bits are contiguous and can be shifted
	 * right by this amount to make a zero-based index of power sets, which is
	 * relied on below; if that changes, fix whatever needs fixing.
	 */
	private static final int EVENT_SHIFT = 2;

	private static Set<Event> indexToSet(int index)
	{
		int type = index << EVENT_SHIFT;
		EnumSet<Event> s = EnumSet.noneOf(Event.class);
		if ( 0 != (type & TRIGGER_TYPE_INSERT) )
			s.add(Event.INSERT);
		if ( 0 != (type & TRIGGER_TYPE_DELETE) )
			s.add(Event.DELETE);
		if ( 0 != (type & TRIGGER_TYPE_UPDATE) )
			s.add(Event.UPDATE);
		if ( 0 != (type & TRIGGER_TYPE_TRUNCATE) )
			s.add(Event.TRUNCATE);
		return unmodifiableSet(s);
	}

	private static final List<Set<Event>> EVENT_SETS = List.of(
		indexToSet( 0), indexToSet( 1), indexToSet( 2), indexToSet( 3),
		indexToSet( 4), indexToSet( 5), indexToSet( 6), indexToSet( 7),
		indexToSet( 8), indexToSet( 9), indexToSet(10), indexToSet(11),
		indexToSet(12), indexToSet(13), indexToSet(14), indexToSet(15));

	private static Set<Event> typeToSet(int type)
	{
		type &= TRIGGER_TYPE_EVENT_MASK;
		return EVENT_SETS.get(type >>> EVENT_SHIFT);
	}

	static
	{
		assert
			typeToSet(TRIGGER_TYPE_INSERT).equals(EnumSet.of(Event.INSERT)) &&
			typeToSet(TRIGGER_TYPE_DELETE).equals(EnumSet.of(Event.DELETE)) &&
			typeToSet(TRIGGER_TYPE_UPDATE).equals(EnumSet.of(Event.UPDATE)) &&
			typeToSet(TRIGGER_TYPE_TRUNCATE).equals(EnumSet.of(Event.TRUNCATE))
			: "Trigger.events representation has changed";
	}

	private TriggerDataImpl m_td;
	private ByteBuffer m_bb;

	/**
	 * Executes <var>work</var> in a scope during which this instance is
	 * associated with the supplied {@link TriggerDataImpl TriggerDataImpl}
	 * instance and returns any result.
	 *<p>
	 * Used by the dispatcher in a somewhat incestuous arrangement further
	 * described at {@link TriggerDataImpl#m_trigger}.
	 */
	<T, E extends Throwable>
	T withTriggerData(TriggerDataImpl td, Checked.Supplier<T,E> work)
	throws E
	{
		final Object[] save = {null, null};
		try
		{
			doInPG(() ->
			{
				save[0] = m_td;
				save[1] = m_bb;
				m_td = td;
				m_bb = td.m_trigger;
			});
			return work.get();
		}
		finally
		{
			doInPG(() ->
			{
				m_td.m_trigger = m_bb = (ByteBuffer)save[1];
				m_td = (TriggerDataImpl)save[0];
			});
		}
	}

	/* API methods of Addressed */

	@Override
	public RegClass.Known<Trigger> classId()
	{
		return CLASSID;
	}

	/*
	 * The API javadoc does say the methods of Addressed will work even outside
	 * of the trigger-call context, and this is one of those, so give it a
	 * simple if nonoptimal implementation doing an index lookup to cover that.
	 */
	@Override
	public boolean exists()
	{
		if ( null != m_bb )
			return true;

		ByteBuffer heapTuple;
		TupleDescImpl td = (TupleDescImpl)CLASSID.tupleDescriptor();

		try
		{
			return doInPG(() ->
				null != _sysTableGetByOid(
					CLASSID.oid(), oid(), Anum_pg_trigger_oid,
					TriggerOidIndexId, td.address())
			);
		}
		catch ( SQLException e )
		{
			throw unchecked(e);
		}
	}

	/* API method of Named */

	@Override
	public Simple name()
	{
		if ( null == m_bb )
			throw notyet();

		try
		{
			long p = fetchPointer(m_bb, OFFSET_TRG_tgname);
			ByteBuffer b = mapCString(p);
			return Simple.fromCatalog(SERVER_ENCODING.decode(b).toString());
		}
		catch ( CharacterCodingException e )
		{
			throw new AssertionError(e);
		}
	}

	/* API methods */

	@Override
	public RegClass relation()
	{
		if ( null == m_td )
			throw notyet();

		return m_td.relation();
	}

	@Override
	public Trigger parent()
	{
		throw notyet();
	}

	@Override
	public RegProcedure<ForTrigger> function()
	{
		if ( null == m_bb )
			throw notyet();

		int oid = m_bb.getInt(OFFSET_TRG_tgfoid);

		@SuppressWarnings("unchecked")
		RegProcedure<ForTrigger> f =
			(RegProcedure<ForTrigger>)of(RegProcedure.CLASSID, oid);

		return f;
	}

	@Override
	public Called called()
	{
		if ( null == m_bb )
			throw notyet();

		int type = Short.toUnsignedInt(m_bb.getShort(OFFSET_TRG_tgtype));
		type &= TRIGGER_TYPE_TIMING_MASK;
		switch ( type )
		{
		case TRIGGER_TYPE_BEFORE : return Called.BEFORE;
		case TRIGGER_TYPE_AFTER  : return Called.AFTER;
		case TRIGGER_TYPE_INSTEAD: return Called.INSTEAD_OF;
		default:
			throw new AssertionError("Trigger.called enum");
		}
	}

	@Override
	public Set<Event> events()
	{
		if ( null == m_bb )
			throw notyet();

		int type = Short.toUnsignedInt(m_bb.getShort(OFFSET_TRG_tgtype));
		return typeToSet(type);
	}

	@Override
	public Scope scope()
	{
		if ( null == m_bb )
			throw notyet();

		int type = Short.toUnsignedInt(m_bb.getShort(OFFSET_TRG_tgtype));
		type &= TRIGGER_TYPE_LEVEL_MASK;
		switch ( type )
		{
		case TRIGGER_TYPE_ROW      : return Scope.ROW;
		case TRIGGER_TYPE_STATEMENT: return Scope.STATEMENT;
		default:
			throw new AssertionError("Trigger.scope enum");
		}
	}

	@Override
	public ReplicationRole enabled()
	{
		if ( null == m_bb )
			throw notyet();

		char c = (char)(0xff & m_bb.get(OFFSET_TRG_tgenabled));

		switch ( c )
		{
		case TRIGGER_FIRES_ON_ORIGIN  : return ReplicationRole.ON_ORIGIN;
		case TRIGGER_FIRES_ALWAYS     : return ReplicationRole.ALWAYS;
		case TRIGGER_FIRES_ON_REPLICA : return ReplicationRole.ON_REPLICA;
		case TRIGGER_DISABLED         : return ReplicationRole.DISABLED;
		default:
			throw new AssertionError("Trigger.enabled enum");
		}
	}

	@Override
	public boolean internal()
	{
		if ( null == m_bb )
			throw notyet();

		return 0 != m_bb.get(OFFSET_TRG_tgisinternal);
	}

	@Override
	public RegClass constraintRelation()
	{
		if ( null == m_bb )
			throw notyet();

		int oid = m_bb.getInt(OFFSET_TRG_tgconstrrelid);
		return InvalidOid == oid ? null : of(RegClass.CLASSID, oid);
	}

	@Override
	public RegClass constraintIndex()
	{
		if ( null == m_bb )
			throw notyet();

		int oid = m_bb.getInt(OFFSET_TRG_tgconstrindid);
		return InvalidOid == oid ? null : of(RegClass.CLASSID, oid);
	}

	@Override
	public Constraint constraint()
	{
		if ( null == m_bb )
			throw notyet();

		int oid = m_bb.getInt(OFFSET_TRG_tgconstraint);
		return InvalidOid == oid ? null : of(Constraint.CLASSID, oid);
	}

	@Override
	public boolean deferrable()
	{
		if ( null == m_bb )
			throw notyet();

		return 0 != m_bb.get(OFFSET_TRG_tgdeferrable);
	}

	@Override
	public boolean initiallyDeferred()
	{
		if ( null == m_bb )
			throw notyet();

		return 0 != m_bb.get(OFFSET_TRG_tginitdeferred);
	}

	@Override
	public Projection columns()
	{
		if ( null == m_bb )
			throw notyet();

		int nattr = Short.toUnsignedInt(m_bb.get(OFFSET_TRG_tgnattr));

		if ( 0 == nattr )
			return null;

		long attvp = fetchPointer(m_bb, OFFSET_TRG_tgattr);
		ByteBuffer attvb = mapFixedLength(attvp, nattr * Short.BYTES);
		ShortBuffer sb = attvb.asShortBuffer();
		short[] attnums = new short [ nattr ];
		sb.get(attnums);
		return relation().tupleDescriptor().sqlProject(attnums);
	}

	@Override
	public List<String> arguments()
	{
		if ( null == m_bb )
			throw notyet();

		int nargs = Short.toUnsignedInt(m_bb.get(OFFSET_TRG_tgnargs));

		if ( 0 == nargs )
			return List.of();

		long argvp = fetchPointer(m_bb, OFFSET_TRG_tgargs);
		ByteBuffer argvb = mapFixedLength(argvp, nargs * SIZEOF_DATUM);
		String[] argv = new String[nargs];
		for ( int i = 0 ; i < nargs ; ++ i )
		{
			long p = fetchPointer(argvb, i * SIZEOF_DATUM);
			ByteBuffer b = mapCString(p);
			try
			{
				argv[i] = SERVER_ENCODING.decode(b).toString();
			}
			catch ( CharacterCodingException e )
			{
				throw new AssertionError(e);
			}
		}
		return List.of(argv);
	}

	@Override
	public SQLXML when()
	{
		if ( null == m_bb )
			throw notyet();

		long p = fetchPointer(m_bb, OFFSET_TRG_tgqual);

		if ( 0 == p )
			return null;

		ByteBuffer bb = mapCString(p);

		Datum.Input<?> in = asAlwaysCopiedDatum(bb, 0, bb.limit());

		try
		{
			return SQLXMLImpl.newReadable(in, RegType.PG_NODE_TREE, true);
		}
		catch ( SQLException e )
		{
			throw unchecked(e);
		}
	}

	@Override
	public Simple tableOld()
	{
		if ( null == m_bb )
			throw notyet();

		long p = fetchPointer(m_bb, OFFSET_TRG_tgoldtable);

		if ( 0 == p )
			return null;

		ByteBuffer b = mapCString(p);

		try
		{
			return Simple.fromCatalog(SERVER_ENCODING.decode(b).toString());
		}
		catch ( CharacterCodingException e )
		{
			throw new AssertionError(e);
		}
	}

	@Override
	public Simple tableNew()
	{
		if ( null == m_bb )
			throw notyet();

		long p = fetchPointer(m_bb, OFFSET_TRG_tgnewtable);

		if ( 0 == p )
			return null;

		ByteBuffer b = mapCString(p);

		try
		{
			return Simple.fromCatalog(SERVER_ENCODING.decode(b).toString());
		}
		catch ( CharacterCodingException e )
		{
			throw new AssertionError(e);
		}
	}

	@Override
	public boolean isClone()
	{
		if ( null == m_bb )
			throw notyet();

		return 0 != m_bb.get(OFFSET_TRG_tgisclone);
	}
}
