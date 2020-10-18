/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava;

import java.security.AccessControlContext; // linked from javadoc

import java.sql.Connection;
import java.sql.SQLException;

/**
 * A Session brings together some useful methods and data for the current
 * database session. It provides a set of attributes (a
 * {@code String} to {@code Object} map. Until PL/Java 1.2.0, its attribute
 * store had transactional behavior (i.e., the data
 * added since the last commit would be lost on a transaction rollback, or kept
 * after a commit), but in 1.2.0 and later, it has not, and has functioned as a
 * {@code Map} with no awareness of transactions. Java already provides those,
 * so the attribute-related methods of {@code Session} are now deprecated.
 * 
 * {@code TransactionListeners} and {@code SavepointListeners} are available for
 * use by any code that needs to synchronize some state with PostgreSQL
 * transactions.
 * 
 * @author Thomas Hallgren
 */
public interface Session
{
	/**
	 * Adds the specified {@code listener} to the list of listeners that will
	 * receive savepoint events. An {@link AccessControlContext} saved by this
	 * method will be used when the listener is invoked. If the listener was
	 * already registered, it remains registered just once, though the
	 * {@code AccessControlContext} is updated and its order of invocation
	 * relative to other listeners may change.
	 * @param listener The listener to be added.
	 */
	void addSavepointListener(SavepointListener listener);

	/**
	 * Adds the specified {@code listener} to the list of listeners that will
	 * receive transaction events. An {@link AccessControlContext} saved by this
	 * method will be used when the listener is invoked. If the listener was
	 * already registered, it remains registered just once, though the
	 * {@code AccessControlContext} is updated and its order of invocation
	 * relative to other listeners may change.
	 * @param listener The listener to be added.
	 */
	void addTransactionListener(TransactionListener listener);

	/**
	 * Obtain an attribute from the current session.
	 *
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 * @param attributeName The name of the attribute
	 * @return The value of the attribute
	 */
	@Deprecated(since="1.5.3", forRemoval=true)
	Object getAttribute(String attributeName);

	/**
	 * Return an object pool for the given class.
	 * @param cls The class of object to be managed by this pool. It must
	 * implement the interface {@link PooledObject} and have an accessible
	 * constructor for one argument of type <code>ObjectPool</code>.
	 * @return An object pool that pools object of the given class.
	 */
	<T extends PooledObject> ObjectPool<T> getObjectPool(Class<T> cls);

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
	 * only the session ID should be <a href=
'../../../../../../issue-management.html'>reported as an issue.</a>
	 */
	@Deprecated(since="1.5.0", forRemoval=true)
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
	 * should be <a href=
'../../../../../../issue-management.html'>reported as an issue</a>.
	 */
	@Deprecated(since="1.5.0", forRemoval=true)
	void executeAsSessionUser(Connection conn, String statement)
	throws SQLException;

	/**
	 * Remove an attribute previously stored in the session. If
	 * no attribute is found, nothing happens.
	 *
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 * @param attributeName The name of the attribute.
	 */
	@Deprecated(since="1.5.3", forRemoval=true)
	void removeAttribute(String attributeName);

	/**
	 * Removes the specified {@code listener} from the list of listeners that
	 * will receive savepoint events. This method does nothing unless
	 * the listener is found.
	 * @param listener The listener to be removed.
	 */
	void removeSavepointListener(SavepointListener listener);

	/**
	 * Removes the specified {@code listener} from the list of listeners that
	 * will receive transaction events. This method does nothing unless
	 * the listener is found.
	 * @param listener The listener to be removed.
	 */
	void removeTransactionListener(TransactionListener listener);

	/**
	 * Set an attribute to a value in the current session.
	 *
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 * @param attributeName
	 * @param value
	 */
	@Deprecated(since="1.5.3", forRemoval=true)
	void setAttribute(String attributeName, Object value);
}
