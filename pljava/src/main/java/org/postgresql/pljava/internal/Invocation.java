/*
 * Copyright (c) 2004-2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.lang.annotation.Native;

import static java.lang.Integer.highestOneBit;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.postgresql.pljava.internal.Backend;
import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.PgSavepoint;

/**
 * One invocation, from PostgreSQL, of functionality implemented using PL/Java.
 *<p>
 * This class is the Java counterpart of the {@code struct Invocation_} in the
 * C code, but while there is a new stack-allocated C structure on every entry
 * from PG to PL/Java, no instance of this class is created unless requested
 * (with {@link #current current()}; once requested, a reference to it is saved
 * in the C struct for the duration of the invocation.
 * @author Thomas Hallgren
 */
public class Invocation
{
	@Native private static final int OFFSET_nestLevel     = 0;
	@Native private static final int OFFSET_hasDual       = 4;
	@Native private static final int OFFSET_errorOccurred = 5;

	private static final ByteBuffer s_window =
		EarlyNatives._window().order(nativeOrder());

	/**
	 * The current "stack" of invocations.
	 */
	private static Invocation[] s_levels = new Invocation[10];

	/**
	 * Nesting level for this invocation
	 */
	private final int m_nestingLevel;

	/**
	 * Top level savepoint relative to this invocation.
	 */
	private PgSavepoint m_savepoint;

	private Invocation(int level)
	{
		m_nestingLevel = level;
	}

	/**
	 * @return The nesting level of this invocation
	 */
	public int getNestingLevel()
	{
		return m_nestingLevel;
	}

	/**
	 * @return Returns the savePoint.
	 */
	public final PgSavepoint getSavepoint()
	{
		return m_savepoint;
	}

	/**
	 * @param savepoint The savepoint to set.
	 */
	public final void setSavepoint(PgSavepoint savepoint)
	{
		m_savepoint = savepoint;
	}

	/**
	 * Called only from the static {@code onExit} below when the invocation
	 * is popped; should not be invoked any other way.
	 */
	private void onExit(boolean withError)
	throws SQLException
	{
		try
		{
			if(m_savepoint != null)
				m_savepoint.onInvocationExit(withError);
		}
		finally
		{
			m_savepoint = null;
		}
	}

	/**
	 * The actual entry point from JNI, which passes a valid nestLevel.
	 *<p>
	 * Forwards to the instance method at the corresponding level.
	 */
	private static void onExit(int nestLevel, boolean withError)
	throws SQLException
	{
		s_levels[nestLevel].onExit(withError);
	}

	/**
	 * @return The current invocation
	 */
	public static Invocation current()
	{
		return doInPG(() ->
		{
			Invocation curr;
			int level = s_window.getInt(OFFSET_nestLevel);
			int top = s_levels.length;

			if(level >= top)
			{
				int newSize = highestOneBit(level) << 1;
				Invocation[] levels = new Invocation[newSize];
				System.arraycopy(s_levels, 0, levels, 0, top);
				s_levels = levels;
			}

			curr = s_levels[level];
			if ( null == curr )
				s_levels[level] = curr = new Invocation(level);

			s_window.put(OFFSET_hasDual, (byte)1);
			return curr;
		});
	}

	public static void clearErrorCondition()
	{
		doInPG(() -> s_window.put(OFFSET_errorOccurred, (byte)0));
	}

	private static class EarlyNatives
	{
		private static native ByteBuffer _window();
	}
}
