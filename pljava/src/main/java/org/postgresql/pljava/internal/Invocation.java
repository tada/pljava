/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
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

import static org.postgresql.pljava.internal.Backend.doInPG;

import org.postgresql.pljava.model.MemoryContext;

import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;
import org.postgresql.pljava.pg.MemoryContextImpl;

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
public class Invocation extends LifespanImpl
{
	@Native private static final int OFFSET_nestLevel     = 0;
	@Native private static final int OFFSET_hasDual       = 4;
	@Native private static final int OFFSET_errorOccurred = 5;
	@Native private static final int OFFSET_upperContext  = 8;

	private static final ByteBuffer s_window =
		EarlyNatives._window().order(nativeOrder());

	/**
	 * The current "stack" of invocations.
	 */
	private static Invocation[] s_levels = new Invocation[10];

	/**
	 * Recent exception representing a PostgreSQL {@code ereport(ERROR} that has
	 * been thrown in Java but not yet resolved (as by rollback of the
	 * transaction or subtransaction / savepoint).
	 *<p>
	 * Mutation happens on "the PG thread".
	 *<p>
	 * This field should be non-null when and only when {@code errorOccurred}
	 * is true in the C {@code Invocation} struct. Both are set when such an
	 * exception is thrown, and cleared by
	 * {@link #clearErrorCondition clearErrorCondition}.
	 *<p>
	 * One static field suffices, not one per invocation nesting level, because
	 * it will always be recognized and cleared on invocation exit (to any
	 * possible outer nest level), and {@code errorOccurred} is meant to prevent
	 * calling into any PostgreSQL functions that could reach an inner nest
	 * level. (On reflection, that reasoning ought to apply also to
	 * {@code errorOccurred} itself, but that has been the way it is for decades
	 * and this can be added without changing that.)
	 *<p>
	 * On the first creation of a {@link ServerException ServerException}, that
	 * exception is stored here. If any later call into PostgreSQL is thwarted
	 * by finding {@code errorOccurred} true, the {@code ServerException} stored
	 * here will be replaced by an
	 * {@link UnhandledPGException UnhandledPGException} that has the original
	 * {@code ServerException} as its {@link Throwable#cause cause} and the new
	 * exception will be thrown. Once this field holds an
	 * {@code UnhandledPGException}, it will be reused and rethrown unchanged if
	 * further attempts to call into PostgreSQL are made.
	 *<p>
	 * At invocation exit, the C {@code popInvocation} code knows whether the
	 * exit is normal or exceptional. If the exit is normal but
	 * {@code errorOccurred} is true, that means the exiting Java function
	 * caught a {@code ServerException} but without rethrowing it (or some
	 * higher-level exception) and also without resolving it (as with a
	 * rollback). That is a bug in the Java function, and the exception stored
	 * here can have its stacktrace logged. If it is the original
	 * {@code ServerException}, the logging will be skipped at levels quieter
	 * than {@code DEBUG1}. If the exception here is already
	 * {@code UnhandledPGException}, then at least one attempted PostgreSQL
	 * operation is known to have been thwarted because of it, and a stacktrace
	 * will be generated at {@code WARNING} level.
	 *<p>
	 * If the invocation is being popped exceptionally, the exception probably
	 * is this one, or has this one in its cause chain, and longstanding code
	 * in {@code JNICalls.c::endCall} will have generated that stack trace at
	 * level {@code DEBUG1}. Should that not be the case, then a stacktrace of
	 * this exception can be obtained from {@code popInvocation} by bumping the
	 * level to {@code DEBUG2}.
	 *<p>
	 * Package access so factory methods of {@code ServerException} and
	 * {@code UnhandledPGException} can access it.
	 */
	static SQLException s_unhandled;

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
			lifespanRelease();
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

	/**
	 * The "upper executor" memory context (that is, the context on entry, prior
	 * to any {@code SPI_connect}) associated with the current (innermost)
	 * invocation.
	 */
	public static MemoryContext upperExecutorContext()
	{
		return
			doInPG(() -> MemoryContextImpl.fromAddress(
				fetchPointer(s_window, OFFSET_upperContext)));
	}

	public static void clearErrorCondition()
	{
		doInPG(() ->
		{
			s_unhandled = null;
			s_window.put(OFFSET_errorOccurred, (byte)0);
		});
	}

	private static class EarlyNatives
	{
		private static native ByteBuffer _window();
	}
}
