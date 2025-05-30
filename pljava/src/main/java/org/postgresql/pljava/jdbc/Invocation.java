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
package org.postgresql.pljava.jdbc;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.postgresql.pljava.internal.Backend;
import static org.postgresql.pljava.internal.Backend.doInPG;
import org.postgresql.pljava.internal.PgSavepoint;
import org.postgresql.pljava.internal.ServerException; // for javadoc
import org.postgresql.pljava.internal.UnhandledPGException; // for javadoc

/**
 * One invocation, from PostgreSQL, of functionality implemented using PL/Java.
 *<p>
 * This class is the Java counterpart of the {@code struct Invocation_} in the
 * C code, but while there is a new stack-allocated C structure on every entry
 * from PG to PL/Java, no instance of this class is created unless requested
 * (with {@link #current current()}; once requested, a reference to it is saved
 * in the C struct for the duration of the invocation.
 *<p>
 * One further piece of magic applies to set-returning functions. Under the
 * value-per-call protocol, there is technically a new entry into PL/Java, and
 * a new C {@code Invocation_} struct, for every row to be returned, but that
 * low-level complication is hidden at this level: a single instance of this
 * class, if once requested, will be remembered throughout the value-per-call
 * sequence of calls.
 * @author Thomas Hallgren
 */
public class Invocation
{
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
	 * Public access so factory methods of {@code ServerException} and
	 * {@code UnhandledPGException}, in another package, can access it.
	 */
	public static SQLException s_unhandled;

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
	final PgSavepoint getSavepoint()
	{
		return m_savepoint;
	}

	/**
	 * @param savepoint The savepoint to set.
	 */
	final void setSavepoint(PgSavepoint savepoint)
	{
		m_savepoint = savepoint;
	}

	/**
	 * Called from the backend when the invokation exits. Should
	 * not be invoked any other way.
	 */
	public void onExit(boolean withError)
	throws SQLException
	{
		try
		{
			if(m_savepoint != null)
				m_savepoint.onInvocationExit(withError);
		}
		finally
		{
			s_levels[m_nestingLevel] = null;
		}
	}

	/**
	 * @return The current invocation
	 */
	public static Invocation current()
	{
		return doInPG(() ->
		{
			Invocation curr = _getCurrent();
			if(curr != null)
				return curr;

			int level = _getNestingLevel();
			int top = s_levels.length;
			if(level < top)
			{
				curr = s_levels[level];
				if(curr != null)
				{
					curr._register();
					return curr;
				}
			}
			else
			{
				int newSize = top;
				do { newSize <<= 2; } while(newSize <= level);
				Invocation[] levels = new Invocation[newSize];
				System.arraycopy(s_levels, 0, levels, 0, top);
				s_levels = levels;
			}
			curr = new Invocation(level);
			s_levels[level] = curr;
			curr._register();
			return curr;
		});
	}

	static void clearErrorCondition()
	{
		doInPG(() ->
		{
			s_unhandled = null;
			_clearErrorCondition();
		});
	}

	/**
	 * Register this Invocation so that it receives the onExit callback
	 */
	private native void  _register();
	
	/**
	 * Returns the current invocation or null if no invocation has been
	 * registered yet.
	 */
	private native static Invocation  _getCurrent();

	/**
	 * Returns the current nesting level
	 */
	private native static int  _getNestingLevel();
	
	/**
	 * Clears the error condition set by elog(ERROR)
	 */
	private native static void  _clearErrorCondition();
}
