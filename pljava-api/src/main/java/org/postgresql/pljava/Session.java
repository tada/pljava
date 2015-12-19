/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A Session maintains transaction coordinated in-memory data. The data
 * added since the last commit will be lost on a transaction rollback, i.e.
 * the Session state is synchronized with the transaction.
 * 
 * Please note that if nested objects (such as lists and maps) are stored
 * in the session, changes internal to those objects are not subject to
 * the session semantics since the session is unaware of them.
 * 
 * @author Thomas Hallgren
 */
public interface Session
{
	/**
	 * Adds the specified <code>listener</code> to the list of listeners that will
	 * receive savepoint events. This method does nothing if the listener
	 * was already added.
	 * @param listener The listener to be added.
	 */
	void addSavepointListener(SavepointListener listener);

	/**
	 * Adds the specified <code>listener</code> to the list of listeners that will
	 * receive transactional events. This method does nothing if the listener
	 * was already added.
	 * @param listener The listener to be added.
	 */
	void addTransactionListener(TransactionListener listener);

	/**
	 * Obtain an attribute from the current session.
	 * @param attributeName The name of the attribute
	 * @return The value of the attribute
	 */
	Object getAttribute(String attributeName);

	/**
	 * Return an object pool for the given class.
	 * @param cls The class of object to be managed by this pool. It must
	 * implement the interface {@link PooledObject} and have an accessible
	 * constructor for one argument of type <code>ObjectPool</code>.
	 * @return An object pool that pools object of the given class.
	 */
	ObjectPool getObjectPool(Class cls);

	/**
	 * Return the current <em>effective</em> database user name.
	 *<p>
	 * <a href=
'http://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/init/miscinit.c;h=f8cc2d85c18f4e3a21a3e22457ef78d286cd1330;hb=b196a71d88a325039c0bf2a9823c71583b3f9047#l291'
>Definition</a>:
	 * "The one to use for all normal permissions-checking purposes."
	 * Within {@code SECURITY DEFINER} functions and some specialized
	 * commands, it can be different from the
	 * {@linkplain #getOuterUserName outer user name}.
	 */
	String getUserName();

	/**
	 * Return the <em>outer</em> database user name.
	 *<p>
	 * <a href=
'http://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/init/miscinit.c;h=f8cc2d85c18f4e3a21a3e22457ef78d286cd1330;hb=b196a71d88a325039c0bf2a9823c71583b3f9047#l286'
>Definition</a>:
	 * "the current user ID in effect at the 'outer level' (outside any
	 * transaction or function)." The session user id taking into account
	 * any {@code SET ROLE} in effect. This is the ID that a
	 * {@code SECURITY DEFINER} function should revert to if it needs to
	 * operate with the invoker's permissions.
	 * @since 1.5.0
	 */
	String getOuterUserName();

	/**
	 * Deprecated synonym for {@link #getOuterUserName getOuterUserName}.
	 * @deprecated As of 1.5.0, this method is retained only for
	 * compatibility with old code, and returns the same value as
	 * {@link #getOuterUserName getOuterUserName}, which should be used
	 * instead. Previously, it returned the <em>session</em> ID
	 * unconditionally, which is incorrect for any PostgreSQL version newer
	 * than 8.0, because it was unaware of {@code SET ROLE} introduced in
	 * 8.1. Any actual use case for a method that ignores roles and reports
	 * only the session ID should be
	 * <a href='../../../../issue-tracking.html'>reported as an issue.</a>
	 */
	@Deprecated
	String getSessionUserName();

	/**
	 * Execute a statement as the outer user rather than the effective
	 * user. This is useful when functions declared using
	 * <code>SECURITY DEFINER</code> wants to give up the definer
	 * rights.
	 * @param conn The connection used for the execution
	 * @param statement The statement to execute
	 * @throws SQLException if something goes wrong when executing.
	 * @see java.sql.Statement#execute(java.lang.String)
	 */
	void executeAsOuterUser(Connection conn, String statement)
	throws SQLException;

	/**
	 * Deprecated synonym for
	 * {@link #executeAsOuterUser executeAsOuterUser}.
	 * @deprecated As of 1.5.0, this method is retained only for
	 * compatibility with old code, and has the same effect as
	 * {@link #executeAsOuterUser executeAsOuterUser}, which should be used
	 * instead. Previously, it used the <em>session</em> ID unconditionally,
	 * which is incorrect for any PostgreSQL version newer than 8.0, because
	 * it was unaware of {@code SET ROLE} introduced in 8.1. Any actual use
	 * case for a method that ignores roles and uses only the session ID
	 * should be <a href='../../../../issue-tracking.html'>reported as an
	 * issue</a>.
	 */
	@Deprecated
	void executeAsSessionUser(Connection conn, String statement)
	throws SQLException;

	/**
	 * Remove an attribute previously stored in the session. If
	 * no attribute is found, nothing happens.
	 * @param attributeName The name of the attribute.
	 */
	void removeAttribute(String attributeName);

	/**
	 * Removes the specified <code>listener</code> from the list of listeners that will
	 * receive savepoint events. This method does nothing unless the listener is
	 * found.
	 * @param listener The listener to be removed.
	 */
	void removeSavepointListener(SavepointListener listener);

	/**
	 * Removes the specified <code>listener</code> from the list of listeners that will
	 * receive transactional events. This method does nothing unless the listener is
	 * found.
	 * @param listener The listener to be removed.
	 */
	void removeTransactionListener(TransactionListener listener);

	/**
	 * Set an attribute to a value in the current session.
	 * @param attributeName
	 * @param value
	 */
	void setAttribute(String attributeName, Object value);
}
