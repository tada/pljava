/*
 * Copyright (c) 2015-2016 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.management;

import java.sql.Connection;
import java.sql.SQLException;

import org.postgresql.pljava.Session;
import org.postgresql.pljava.SessionManager;

import org.postgresql.pljava.internal.Backend;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * Abstract class for executing one deployment descriptor {@code <command>}
 * on a connection.
 * <p>
 * The {@link #forImplementor forImplementor} method returns an executor
 * according to the
 * {@code <implementor name>} associated with the command. The two possibilities
 * that support standard behavior are a "plain" executor, which simply
 * executes the SQL text (with any {@code SECURITY DEFINER} identity dropped),
 * and a "no-op" executor, which (you'll be shocked) does nothing.
 * Normally, a plain executor is returned if the implementor name is
 * null (command is for all implementations) or in the list recognized as being
 * for this implementation (normally just PostgreSQL, case-insensitively, but
 * adjustable as described below). A no-op executor is returned if there
 * is an implementor name and it is anything not on the recognized list.
 * <p><strong>Adjusting the recognized implementor names:</strong>
 * <p>
 * The recognized implementor names are taken from the (comma-separated) string
 * configuration option {@code pljava.implementors}, which can be set from
 * ordinary SQL using
 * {@code SET LOCAL pljava.implementors TO thing, thing, thing}.
 * It is re-parsed each time {@code forImplementor} is called, which happens
 * for every {@code <command>} in a deployment descriptor, so that SQL code
 * early in a deployment descriptor <em>can influence which code blocks later
 * are executed</em>.
 * <p>
 * The {@code SET LOCAL} command shown above only accepts a literal list of
 * elements. It will ordinarily be better for SQL code to <em>add</em> another
 * element to whatever is currently in the list, which is fussier in SQL:
 * <pre>
 *  SELECT set_config('pljava.implementors', 'NewThing,' ||
 *                    current_setting('pljava.implementors'), true)
 * </pre>
 * where the final {@code true} gives the setting the same lifetime as
 * {@code SET LOCAL}, that is, it reverts when the transaction is over.
 * <p>
 * The possibility that, for certain implementor names, {@code forImplementor}
 * could return other subclasses of {@code DDRExecutor} with specialized
 * behavior, is definitely contemplated.
 */
public abstract class DDRExecutor
{
	protected DDRExecutor() { }

	private static final DDRExecutor PLAIN = new Plain();

	private static final DDRExecutor NOOP = new Noop();

	/**
	 * Execute the command {@code sql} using the connection {@code conn},
	 * according to whatever meaning of "execute" the target {@code DDRExecutor}
	 * subclass implements.
	 *
	 * @param sql The command to execute
	 * @param conn The connection to use
	 * @throws SQLException Anything thrown in the course of executing
	 * {@code sql}
	 */
	public abstract void execute( String sql, Connection conn)
	throws SQLException;

	/**
	 * Return a {@code DDRExecutor} instance chosen according to the supplied
	 * implementor name and current {@code pljava.implementors} value.
	 * See the class description for more.
	 *
	 * @param name The {@code <implementor name>} associated with the deployment
	 * descriptor {@code <command>}, or {@code null} if the {@code <command>} is
	 * an unadorned {@code <SQL statement>} instead of an
	 * {@code <implementor block>}.
	 */
	public static DDRExecutor forImplementor( Identifier name)
	throws SQLException
	{
		if ( null == name )
			return PLAIN;

		Iterable<Identifier.Simple> imps =
			Backend.getListConfigOption( "pljava.implementors");

		for ( Identifier i : imps )
			if ( name.equals( i) )
				return PLAIN;

		return NOOP;
	}

	static class Noop extends DDRExecutor
	{
		public void execute( String sql, Connection conn)
		throws SQLException
		{
		}
	}

	static class Plain extends DDRExecutor
	{
		public void execute( String sql, Connection conn)
		throws SQLException
		{
			Session session = SessionManager.current();
			session.executeAsOuterUser( conn, sql);
		}
	}
}
