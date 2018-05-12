/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

/* Imports for API */

import java.sql.SQLXML;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import javax.xml.transform.Source;
import javax.xml.transform.Result;

import java.sql.SQLException;

/* Supplemental imports for SQLXMLImpl */

import java.io.Closeable;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicReference;

import java.sql.SQLNonTransientException;

public abstract class SQLXMLImpl<V extends Closeable> implements SQLXML
{
	protected AtomicReference<V> m_backing;

	protected SQLXMLImpl(V backing)
	{
		m_backing = new AtomicReference<V>(backing);
	}

	@Override
	public void free() throws SQLException
	{
		V backing = m_backing.getAndSet(null);
		if ( null == backing )
			return;
		try
		{
			backing.close();
		}
		catch ( IOException e )
		{
			throw normalizedException(e);
		}
	}

	@Override
	public InputStream getBinaryStream() throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of getBinaryStream on an unreadable SQLXML object",
			"55000");
	}

	@Override
	public OutputStream setBinaryStream() throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of setBinaryStream on an unwritable SQLXML object",
			"55000");
	}

	@Override
	public Reader getCharacterStream() throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of getCharacterStream on an unreadable " +
			"SQLXML object", "55000");
	}

	@Override
	public Writer setCharacterStream() throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of setCharacterStream on an unwritable " +
			"SQLXML object", "55000");
	}

	@Override
	public String getString() throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of getString on an unreadable SQLXML object",
			"55000");
	}

	@Override
	public void setString(String value) throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of setString on an unwritable SQLXML object",
			"55000");
	}

	@Override
	public <T extends Source> T getSource(Class<T> sourceClass)
	throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of getSource on an unreadable SQLXML object",
			"55000");
	}

	@Override
	public <T extends Result> T setResult(Class<T> resultClass)
	throws SQLException
	{
		throw new SQLNonTransientException(
			"Attempted use of setResult on an unwritable SQLXML object",
			"55000");
	}

	protected V backingIfNotFreed() throws SQLException
	{
		V backing = m_backing.get();
		if ( null == backing )
			throw new SQLNonTransientException(
				"Attempted use of already-freed SQLXML object", "55000");
		return backing;
	}

	/**
	 * Wrap other checked exceptions in SQLException for methods specified to
	 * throw only that.
	 */
	protected SQLException normalizedException(Exception e)
	{
		if ( e instanceof SQLException )
			return (SQLException) e;
		if ( e instanceof RuntimeException )
			throw (RuntimeException) e;

		if ( e instanceof IOException )
		{
			Throwable cause = e.getCause();
			if ( cause instanceof SQLException )
				return (SQLException)cause;
		}

		return new SQLException(
			"Exception in XML processing, not otherwise provided for",
			"XX000", e);
	}
}
