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

/* Supplemental imports for SQLXMLImpl.Readable */

import java.io.InputStreamReader;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.dom.DOMSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.postgresql.pljava.internal.Session.implServerCharset;
import org.postgresql.pljava.internal.VarlenaWrapper;

import java.sql.SQLFeatureNotSupportedException;

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

	static class Readable extends SQLXMLImpl<InputStream>
	{
		private AtomicBoolean m_readable = new AtomicBoolean(true);
		private Charset m_serverCS = implServerCharset();

		private Readable(VarlenaWrapper.Input vwi) throws SQLException
		{
			super(vwi);
			if ( null == m_serverCS )
			{
				try
				{
					vwi.close();
				}
				catch ( IOException ioe ) { }
				throw new SQLFeatureNotSupportedException("SQLXML: no Java " +
					"Charset found to match server encoding; perhaps set " +
					"org.postgresql.server.encoding system property to a " +
					"valid Java charset name for the same encoding?", "0A000");
			}
		}

		private InputStream backingAndClearReadable() throws SQLException
		{
			InputStream backing = backingIfNotFreed();
			return m_readable.getAndSet(false) ? backing : null;
		}

		@Override
		public InputStream getBinaryStream() throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getBinaryStream();
			return is;
		}

		@Override
		public Reader getCharacterStream() throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getCharacterStream();
			return new InputStreamReader(is, m_serverCS.newDecoder());
		}

		@Override
		public String getString() throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getString();

			Reader r = new InputStreamReader(is, m_serverCS.newDecoder());
			CharBuffer cb = CharBuffer.allocate(32768);
			StringBuilder sb = new StringBuilder();
			try {
				while ( -1 != r.read(cb) )
				{
					sb.append((CharBuffer)cb.flip());
					cb.clear();
				}
				r.close();
				return sb.toString();
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public <T extends Source> T getSource(Class<T> sourceClass)
		throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getSource(sourceClass);

			if ( null == sourceClass || Source.class == sourceClass )
				sourceClass = (Class<T>)StreamSource.class; // trust me on this

			try
			{
				if ( sourceClass.isAssignableFrom(StreamSource.class) )
					return sourceClass.cast(
						new StreamSource(is));

				if ( sourceClass.isAssignableFrom(SAXSource.class) )
				{
					XMLReader xr = XMLReaderFactory.createXMLReader();
					xr.setFeature("http://xml.org/sax/features/namespaces",
								  true);
					return sourceClass.cast(
						new SAXSource(xr, new InputSource(is)));
				}

				if ( sourceClass.isAssignableFrom(StAXSource.class) )
				{
					XMLInputFactory xif = XMLInputFactory.newFactory();
					xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
					XMLStreamReader xsr =
						xif.createXMLStreamReader(is);
					return sourceClass.cast(new StAXSource(xsr));
				}

				if ( sourceClass.isAssignableFrom(DOMSource.class) )
				{
					DocumentBuilderFactory dbf =
						DocumentBuilderFactory.newInstance();
					dbf.setNamespaceAware(true);
					DocumentBuilder db = dbf.newDocumentBuilder();
					return sourceClass.cast(new DOMSource(db.parse(is)));
				}
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}

			throw new SQLFeatureNotSupportedException(
				"No support for SQLXML.getSource(" +
				sourceClass.getName() + ".class)", "0A000");
		}
	}
}
