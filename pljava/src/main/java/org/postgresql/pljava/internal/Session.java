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
package org.postgresql.pljava.internal;

import java.nio.charset.Charset;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import org.postgresql.pljava.ObjectPool;
import org.postgresql.pljava.PooledObject;
import org.postgresql.pljava.SavepointListener;
import org.postgresql.pljava.TransactionListener;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import org.postgresql.pljava.jdbc.SQLUtils;

import org.postgresql.pljava.elog.ELogHandler;

import static org.postgresql.pljava.internal.Backend.doInPG;

/**
 * An instance of this interface reflects the current session. The attribute
 * store is deprecated. It had interesting transactional behavior until
 * PL/Java 1.2.0, but since then it has behaved as any (non-null-allowing) Map.
 * Anyone needing any sort of attribute store with transactional behavior will
 * need to implement one and use a {@link TransactionListener} to keep it
 * sync'd.
 *
 * @author Thomas Hallgren
 */
public class Session implements org.postgresql.pljava.Session
{
	@SuppressWarnings("removal")
	private final TransactionalMap m_attributes = new TransactionalMap(new HashMap());

	/**
	 * The Java charset corresponding to the server encoding, or null if none
	 * such was found. Put here by InstallHelper via package access at startup.
	 */
	static Charset s_serverCharset;

	/**
	 * A static method (not part of the API-exposed Session interface) by which
	 * pljava implementation classes can get hold of the server charset without
	 * the indirection of getting a Session instance. If there turns out to be
	 * demand for client code to obtain it through the API, an interface method
	 * {@code serverCharset} can easily be added later.
	 * @return The Java Charset corresponding to the server's encoding, or null
	 * if no matching Java charset was found. That can happen if a corresponding
	 * Java charset really does exist but is not successfully found using the
	 * name reported by PostgreSQL. That can be worked around by giving the
	 * right name explicitly as the system property
	 * {@code org.postgresql.server.encoding} in {@code pljava.vmoptions} for
	 * the affected database (or cluster-wide, if the same encoding is used).
	 */
	public static Charset implServerCharset()
	{
		return s_serverCharset;
	}

	/**
	 * Adds the specified listener to the list of listeners that will
	 * receive transactional events.
	 */
	public void addTransactionListener(TransactionListener listener)
	{
		XactListener.addListener(listener);
	}

	/**
	 * Adds the specified listener to the list of listeners that will
	 * receive savepoint events.
	 */
	public void addSavepointListener(SavepointListener listener)
	{
		SubXactListener.addListener(listener);
	}

	/**
	 * Get an attribute from the session's attribute store.
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 */
	@Override
	@SuppressWarnings("removal")
	@Deprecated(since="1.5.3", forRemoval=true)
	public Object getAttribute(String attributeName)
	{
		return m_attributes.get(attributeName);
	}

	public <T extends PooledObject> ObjectPool<T> getObjectPool(Class<T> cls)
	{
		return ObjectPoolImpl.getObjectPool(cls);
	}

	@Override
	public String getUserName()
	{
		return AclId.getUser().getName();
	}

	@Override
	public String getOuterUserName()
	{
		return AclId.getOuterUser().getName();
	}

	@Override
	@SuppressWarnings("removal")
	@Deprecated(since="1.5.0", forRemoval=true)
	public String getSessionUserName()
	{
		return getOuterUserName();
	}


	/**
	 * Remove an attribute from the session's attribute store.
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 */
	@Override
	@SuppressWarnings("removal")
	@Deprecated(since="1.5.3", forRemoval=true)
	public void removeAttribute(String attributeName)
	{
		m_attributes.remove(attributeName);
	}

	/**
	 * Set an attribute in the session's attribute store.
	 * @deprecated {@code Session}'s attribute store once had a special, and
	 * possibly useful, transactional behavior, but since PL/Java 1.2.0 it has
	 * lacked that, and offers nothing you don't get with an ordinary
	 * {@code Map} (that forbids nulls). If some kind of store with
	 * transactional behavior is needed, it should be implemented in straight
	 * Java and kept in sync by using a {@link TransactionListener}.
	 */
	@Override
	@SuppressWarnings("removal")
	@Deprecated(since="1.5.3", forRemoval=true)
	public void setAttribute(String attributeName, Object value)
	{
		m_attributes.put(attributeName, value);
	}

	/**
	 * Removes the specified listener from the list of listeners that will
	 * receive transactional events.
	 */
	public void removeTransactionListener(TransactionListener listener)
	{
		XactListener.removeListener(listener);
	}

	/**
	 * Removes the specified listener from the list of listeners that will
	 * receive savepoint events.
	 */
	public void removeSavepointListener(SavepointListener listener)
	{
		SubXactListener.removeListener(listener);
	}

	@Override
	@SuppressWarnings("removal")
	@Deprecated(since="1.5.0", forRemoval=true)
	public void executeAsSessionUser(Connection conn, String statement)
	throws SQLException
	{
		executeAsOuterUser(conn, statement);
	}

	@Override
	public void executeAsOuterUser(Connection conn, String statement)
	throws SQLException
	{
		Statement stmt = conn.createStatement();
		doInPG(() ->
		{
			ResultSet rs = null;
			AclId outerUser = AclId.getOuterUser();
			AclId effectiveUser = AclId.getUser();
			boolean wasLocalChange = false;
			boolean changeSucceeded = false;
			try
			{
				wasLocalChange = _setUser(outerUser, true);
				changeSucceeded = true;
				if(stmt.execute(statement))
				{
					rs = stmt.getResultSet();
					rs.next();
				}
			}
			finally
			{
				SQLUtils.close(rs);
				SQLUtils.close(stmt);
				if ( changeSucceeded )
					_setUser(effectiveUser, wasLocalChange);
			}
		});
	}

	/**
	 * Return current_schema() as the outer user would see it.
	 * Currently used only in Commands.java. Not made visible API yet
	 * because there <em>has</em> to be a more general way to do this.
	 */
	public Identifier.Simple getOuterUserSchema()
	throws SQLException
	{
		Statement stmt = SQLUtils.getDefaultConnection().createStatement();
		return doInPG(() ->
		{
			ResultSet rs = null;
			AclId outerUser = AclId.getOuterUser();
			AclId effectiveUser = AclId.getUser();
			boolean wasLocalChange = false;
			boolean changeSucceeded = false;
			try
			{
				wasLocalChange = _setUser(outerUser, true);
				changeSucceeded = true;
				rs = stmt.executeQuery("SELECT current_schema()");
				if ( rs.next() )
					return Identifier.Simple.fromCatalog(rs.getString(1));
				throw new SQLException("Unable to obtain current schema");
			}
			finally
			{
				SQLUtils.close(rs);
				SQLUtils.close(stmt);
				if ( changeSucceeded )
					_setUser(effectiveUser, wasLocalChange);
			}
		});
	}

	/**
	 * Called from native code when the JVM is instantiated.
	 */
	static void init()
	throws SQLException
	{
		ELogHandler.init();
	}

	private static native boolean _setUser(AclId userId, boolean isLocalChange);
}
