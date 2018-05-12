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

/* ... for SQLXMLImpl */

import java.io.Closeable;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicReference;

import java.sql.SQLNonTransientException;

/* ... for SQLXMLImpl.Readable */

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

/* ... for SQLXMLImpl.DeclProbe */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.SequenceInputStream;

import java.util.Arrays;

import java.sql.SQLDataException;

/* ... for SQLXMLImpl.Writable */

import java.io.FilterOutputStream;
import java.io.OutputStreamWriter;

import static javax.xml.transform.OutputKeys.ENCODING;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.dom.DOMResult;

import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

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
			try
			{
				return correctedDeclStream(is);
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public Reader getCharacterStream() throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getCharacterStream();
			try
			{
				is = correctedDeclStream(is);
				return new InputStreamReader(is, m_serverCS.newDecoder());
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public String getString() throws SQLException
		{
			InputStream is = backingAndClearReadable();
			if ( null == is )
				return super.getString();

			CharBuffer cb = CharBuffer.allocate(32768);
			StringBuilder sb = new StringBuilder();
			try
			{
				is = correctedDeclStream(is);
				Reader r = new InputStreamReader(is, m_serverCS.newDecoder());
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
						new StreamSource(correctedDeclStream(is)));

				if ( sourceClass.isAssignableFrom(SAXSource.class) )
				{
					XMLReader xr = XMLReaderFactory.createXMLReader();
					xr.setFeature("http://xml.org/sax/features/namespaces",
								  true);
					return sourceClass.cast(
						new SAXSource(xr,
							new InputSource(correctedDeclStream(is))));
				}

				if ( sourceClass.isAssignableFrom(StAXSource.class) )
				{
					XMLInputFactory xif = XMLInputFactory.newFactory();
					xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
					XMLStreamReader xsr =
						xif.createXMLStreamReader(correctedDeclStream(is));
					return sourceClass.cast(new StAXSource(xsr));
				}

				if ( sourceClass.isAssignableFrom(DOMSource.class) )
				{
					DocumentBuilderFactory dbf =
						DocumentBuilderFactory.newInstance();
					dbf.setNamespaceAware(true);
					DocumentBuilder db = dbf.newDocumentBuilder();
					is = correctedDeclStream(is);
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

		/**
		 * Return an InputStream presenting the contents of the underlying
		 * varlena, but with the leading declaration corrected if need be.
		 *<p>
		 * The current stored form in PG for the XML type is a character string
		 * in server encoding, which may or may not still include a declaration
		 * left over from an input or cast operation, which declaration may or
		 * may not be correct (about the encoding, anyway).
		 * @return An InputStream with its original decl, if any, replaced with
		 * a new one known to be correct, or none if the defaults are correct.
		 */
		private InputStream correctedDeclStream(InputStream is)
		throws IOException, SQLException
		{
			byte[] buf = new byte[40];
			int got;
			boolean needMore = false;
			DeclProbe probe = new DeclProbe();

			while ( -1 != ( got = is.read(buf) ) )
			{
				for ( int i = 0 ; i < got ; ++ i )
					needMore = probe.take(buf[i]);
				if ( ! needMore )
					break;
			}

			/*
			 * At this point, for better or worse, the loop is done. There may
			 * or may not be more of m_vwi left to read; the probe may or may
			 * not have found a decl. If it didn't, prefix() will treat whatever
			 * had been read as readahead and hand it all back, so it suffices
			 * here to create a SequenceInputStream of the prefix and whatever
			 * is or isn't left of m_vwi.
			 *   A bonus is that the SequenceInputStream closes each underlying
			 * stream as it reaches EOF. After the last stream is used up, the
			 * SequenceInputStream remains open-at-EOF until explicitly closed,
			 * providing the expected input-stream behavior, but the underlying
			 * resources don't have to stick around for that.
			 */
			InputStream pfx =
				new ByteArrayInputStream(probe.prefix(m_serverCS));
			return new SequenceInputStream(pfx, is);
		}
	}

	static class Writable extends SQLXMLImpl<VarlenaWrapper.Output>
	{
		private AtomicBoolean m_writable = new AtomicBoolean(true);
		private Charset m_serverCS = implServerCharset();

		private Writable(VarlenaWrapper.Output vwo) throws SQLException
		{
			super(vwo);
			if ( null == m_serverCS )
			{
				try
				{
					vwo.free();
				}
				catch ( IOException ioe ) { }
				throw new SQLFeatureNotSupportedException("SQLXML: no Java " +
					"Charset found to match server encoding; perhaps set " +
					"org.postgresql.server.encoding system property to a " +
					"valid Java charset name for the same encoding?", "0A000");
			}
		}

		private OutputStream backingAndClearWritable()
		throws SQLException
		{
			OutputStream backing = backingIfNotFreed();
			return m_writable.getAndSet(false) ? backing : null;
		}

		@Override
		public void free() throws SQLException
		{
			VarlenaWrapper.Output vwo = m_backing.getAndSet(null);
			if ( null == vwo )
				return;
			try
			{
				vwo.free();
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public OutputStream setBinaryStream() throws SQLException
		{
			OutputStream os = backingAndClearWritable();
			if ( null == os )
				return super.setBinaryStream();
			try
			{
				return new DeclCheckedOutputStream(os, m_serverCS);
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public Writer setCharacterStream() throws SQLException
		{
			OutputStream os = backingAndClearWritable();
			if ( null == os )
				return super.setCharacterStream();
			try
			{
				os = new DeclCheckedOutputStream(os, m_serverCS);
				return new OutputStreamWriter(os, m_serverCS.newEncoder());
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public void setString(String value) throws SQLException
		{
			OutputStream os = backingAndClearWritable();
			if ( null == os )
				super.setString(value);
			try
			{
				os = new DeclCheckedOutputStream(os, m_serverCS);
				Writer w = new OutputStreamWriter(os, m_serverCS.newEncoder());
				w.write(value);
				w.close();
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public <T extends Result> T setResult(Class<T> resultClass)
		throws SQLException
		{
			OutputStream os = backingAndClearWritable();
			if ( null == os )
				return super.setResult(resultClass);

			if ( null == resultClass || Result.class == resultClass )
				resultClass = (Class<T>)StreamResult.class; // trust me on this

			try
			{
				if ( resultClass.isAssignableFrom(StreamResult.class) )
					return resultClass.cast(
						new StreamResult(new DeclCheckedOutputStream(
							os, m_serverCS)));

				if ( resultClass.isAssignableFrom(SAXResult.class) )
				{
					SAXTransformerFactory saxtf = (SAXTransformerFactory)
						SAXTransformerFactory.newInstance();
					TransformerHandler th = saxtf.newTransformerHandler();
					th.getTransformer().setOutputProperty(
						ENCODING, m_serverCS.name());
					th.setResult(new StreamResult(new DeclCheckedOutputStream(
										os, m_serverCS)));
					return resultClass.cast(new SAXResult(th));
				}

				if ( resultClass.isAssignableFrom(StAXResult.class) )
				{
					XMLOutputFactory xof = XMLOutputFactory.newFactory();
					XMLStreamWriter xsw = xof.createXMLStreamWriter(
						new DeclCheckedOutputStream(os, m_serverCS),
						m_serverCS.name());
					return resultClass.cast(new StAXResult(xsw));
				}

				if ( resultClass.isAssignableFrom(DOMResult.class) )
				{
					/* leave this grody case to be implemented later */
				}
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}

			throw new SQLFeatureNotSupportedException(
				"No support for SQLXML.setResult(" +
				resultClass.getName() + ".class)", "0A000");
		}

		private VarlenaWrapper.Output adopt() throws SQLException
		{
			VarlenaWrapper.Output vwo = m_backing.getAndSet(null);
			if ( m_writable.get() )
				throw new SQLNonTransientException(
					"Writable SQLXML object has not been written yet", "55000");
			if ( null == vwo )
				backingIfNotFreed(); /* shorthand way to throw the exception */
			return vwo;
		}
	}

	static class DeclCheckedOutputStream extends FilterOutputStream
	{
		private Charset m_serverCS;
		private DeclProbe m_probe;

		private DeclCheckedOutputStream(OutputStream os, Charset cs)
		throws IOException
		{
			super(os);
			os.write(new byte[0]); // is the VarlenaWrapper.Output still alive?
			m_serverCS = cs;
			m_probe = new DeclProbe();
		}

		@Override
		public void write(int b) throws IOException
		{
			synchronized ( out )
			{
				if ( null == m_probe )
				{
					out.write(b);
					return;
				}
				try
				{
					if ( ! m_probe.take((byte)(b & 0xff)) )
						check();
				}
				catch ( SQLException sqe )
				{
					throw normalizedException(sqe);
				}
			}
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException
		{
			synchronized ( out )
			{
				if ( null != m_probe )
				{
					try
					{
						while ( 0 < len -- )
						{
							if ( ! m_probe.take(b[off ++]) )
								break;
						}
						check();
					}
					catch ( SQLException sqe )
					{
						throw normalizedException(sqe);
					}
				}
				out.write(b, off, len);
			}
		}

		@Override
		public void flush() throws IOException
		{
		}

		@Override
		public void close() throws IOException
		{
			synchronized ( out )
			{
				try
				{
					check();
				}
				catch ( SQLException sqe )
				{
					throw normalizedException(sqe);
				}
				out.close();
			}
		}

		private void check() throws IOException, SQLException
		{
			if ( null == m_probe )
				return;
			m_probe.checkEncoding(m_serverCS, false);
			byte[] prefix = m_probe.prefix(m_serverCS);
			m_probe = null; // Do not check more than once.
			out.write(prefix);
		}

		/**
		 * Wrap other checked exceptions in IOException for methods specified to
		 * throw only that.
		 */
		private IOException normalizedException(Exception e)
		{
			if ( e instanceof IOException )
				return (IOException)e;
			if ( e instanceof RuntimeException )
				throw (RuntimeException)e;
			return new IOException("Malformed XML", e);
		}
	}

	/**
	 * A class to parse and, if necessary, check or correct, the
	 * possibly-erroneous XMLDecl or TextDecl syntax found in the stored form
	 * of PG's XML datatype.
	 *<p>
	 * This implementation depends heavily on the (currently dependable) fact
	 * that, in all PG-supported server encodings, the characters that matter
	 * for decls are encoded as in ASCII.
	 */
	static class DeclProbe
	{
		/*
		 * In Python 3, they've achieved a very nice symmetry where they provide
		 * regular expressions with comparable functionality for both character
		 * streams and byte streams. Will Java ever follow suit? It's 2018, I
		 * can ask my phone spoken questions, and I'm writing a DFA by hand.
		 */
		private static enum State
		{
			START,
			MAYBEVER,
			VER, VEQ, VQ, VVAL, VVALTAIL,
			MAYBEENC,
			ENC, EEQ, EQ, EVAL, EVALTAIL,
			MAYBESA,
			SA , SEQ, SQ, SVAL, SVALTAIL,
			TRAILING, END, MATCHED, UNMATCHED, ABANDONED
		};
		private State m_state = State.START;
		private int m_idx = 0;
		private byte m_q = 0;
		private ByteArrayOutputStream m_save = new ByteArrayOutputStream();
		private static final byte[] s_tpl = {
			'<', '?', 'x', 'm', 'l',     0,                           // 0 - 5
			'v', 'e', 'r', 's', 'i', 'o', 'n',     0,                 // 6 - 13
			'1', '.',     0,                                          // 14 - 16
			'e', 'n', 'c', 'o', 'd', 'i', 'n', 'g',     0,            // 17 - 25
			's', 't', 'a', 'n', 'd', 'a', 'l', 'o', 'n', 'e',     0,  // 26 - 36
			'y', 'e', 's',     0,                                     // 37 - 40
			'n', 'o',     0                                           // 41 - 43
		};

		private boolean m_saving = true;
		private int m_pos = 0;
		private boolean m_mustBeDecl = false;
		private boolean m_mustBeXmlDecl = false;
		private boolean m_mustBeTextDecl = false;
		private boolean m_xml1_0 = false;
		private Boolean m_standalone = null;

		private int m_versionStart, m_versionEnd;
		private int m_encodingStart, m_encodingEnd;
		private int m_readaheadStart;

		/**
		 * Parse for an initial declaration (XMLDecl or TextDecl) in a stream
		 * made available a byte at a time.
		 *<p>
		 * Pass bytes in as long as this method keeps returning {@code true};
		 * once it returns {@code false}, it has either parsed a declaration
		 * successfully or determined none to be present. The results of parsing
		 * are remembered in the instance and available to the
		 * {@link #prefix prefix()} method to generate a suitable decl with the
		 * encoding corrected as needed.
		 *<p>
		 * It is not an error to pass some more bytes after the method has
		 * returned {@code false}; they will simply be buffered as readahead
		 * and included in the result of {@code prefix()}. If no decl
		 * was found, the readahead will include all bytes passed in. If a
		 * partial or malformed decl was found, an exception is thrown.
		 * @param b The next byte of the stream.
		 * @return True if more input is needed to fully parse a decl or be sure
		 * that none is present; false when enough input has been seen.
		 * @throws SQLDataException If a partial or malformed decl is found.
		 */
		boolean take(byte b) throws SQLException
		{
			if ( m_saving )
			{
				m_save.write(b);
				++ m_pos;
			}
			byte tpl = s_tpl[m_idx];
			switch ( m_state )
			{
			case START:
				if ( 0 == tpl  &&  isSpace(b) )
				{
					m_mustBeDecl = true;
					m_saving = false;
					m_state = State.MAYBEVER;
					return true;
				}
				if ( tpl != b )
				{
					m_state = State.UNMATCHED;
					return false;
				}
				++ m_idx;
				return true;
			case MAYBEVER:
				if ( isSpace(b) )
					return true;
				switch ( b )
				{
				case 'v':
					m_state = State.VER;
					m_idx = 7;
					return true;
				case 'e':
					m_mustBeTextDecl = true;
					m_state = State.ENC;
					m_idx = 18;
					return true;
				default:
				}
				break;
			case VER:
				if ( 0 == tpl )
				{
					if ( isSpace(b) )
					{
						m_state = State.VEQ;
						return true;
					}
					if ( '=' == b )
					{
						m_state = State.VQ;
						return true;
					}
				}
				if ( tpl != b )
					break;
				++ m_idx;
				return true;
			case VEQ:
				if ( isSpace(b) )
					return true;
				if ( '=' != b )
					break;
				m_state = State.VQ;
				return true;
			case VQ:
				if ( isSpace(b) )
					return true;
				if ( '\'' != b   &&  '"' != b)
					break;
				m_q = b;
				m_state = State.VVAL;
				m_idx = 14;
				m_saving = true;
				m_versionStart = m_pos;
				return true;
			case VVAL:
				if ( 0 == tpl )
				{
					if ( '0' > b  ||  b > '9' )
						break;
					if ( '0' == b )
						m_xml1_0 = true;
					m_state = State.VVALTAIL;
					return true;
				}
				if ( tpl != b )
					break;
				++ m_idx;
				return true;
			case VVALTAIL:
				if ( '0' <= b  &&  b <= '9' )
				{
					m_xml1_0 = false;
					return true;
				}
				if ( m_q != b )
					break;
				m_state = State.MAYBEENC;
				m_saving = false;
				m_versionEnd = m_pos - 1;
				return true;
			case MAYBEENC:
				if ( isSpace(b) )
					return true;
				if ( 'e' == b )
				{
					m_state = State.ENC;
					m_idx = 18;
					return true;
				}
				if ( m_mustBeTextDecl )
					break;
				m_mustBeXmlDecl = true;
				if ( 's' == b )
				{
					m_state = State.SA;
					m_idx = 27;
					return true;
				}
				if ( '?' != b )
					break;
				m_state = State.END;
				return true;
			case ENC:
				if ( 0 == tpl )
				{
					if ( isSpace(b) )
					{
						m_state = State.EEQ;
						return true;
					}
					if ( '=' == b )
					{
						m_state = State.EQ;
						return true;
					}
				}
				if ( tpl != b )
					break;
				++ m_idx;
				return true;
			case EEQ:
				if ( isSpace(b) )
					return true;
				if ( '=' != b )
					break;
				m_state = State.EQ;
				return true;
			case EQ:
				if ( isSpace(b) )
					return true;
				if ( '\'' != b   &&  '"' != b)
					break;
				m_q = b;
				m_state = State.EVAL;
				m_saving = true;
				m_encodingStart = m_pos;
				return true;
			case EVAL:
				if ( ( 'A' > b || b > 'Z' ) && ( 'a' > b || b > 'z' ) )
					break;
				m_state = State.EVALTAIL;
				return true;
			case EVALTAIL:
				if ( ( 'A' <= b && b <= 'Z' ) || ( 'a' <= b && b <= 'z' ) ||
					 ( '0' <= b && b <= '9' ) || ( '.' == b ) || ( '_' == b ) ||
					 ( '-' == b ) )
					return true;
				if ( m_q != b )
					break;
				m_state = m_mustBeTextDecl ? State.TRAILING : State.MAYBESA;
				m_saving = false;
				m_encodingEnd = m_pos - 1;
				return true;
			case MAYBESA:
				if ( isSpace(b) )
					return true;
				switch ( b )
				{
				case 's':
					m_mustBeXmlDecl = true;
					m_state = State.SA;
					m_idx = 27;
					return true;
				case '?':
					m_state = State.END;
					return true;
				default:
				}
				break;
			case SA:
				if ( 0 == tpl )
				{
					if ( isSpace(b) )
					{
						m_state = State.SEQ;
						return true;
					}
					if ( '=' == b )
					{
						m_state = State.SQ;
						return true;
					}
				}
				if ( tpl != b )
					break;
				++ m_idx;
				return true;
			case SEQ:
				if ( isSpace(b) )
					return true;
				if ( '=' != b )
					break;
				m_state = State.SQ;
				return true;
			case SQ:
				if ( isSpace(b) )
					return true;
				if ( '\'' != b   &&  '"' != b)
					break;
				m_q = b;
				m_state = State.SVAL;
				return true;
			case SVAL:
				if ( 'y' == b )
				{
					m_idx = 38;
					m_standalone = Boolean.TRUE;
				}
				else if ( 'n' == b )
				{
					m_idx = 42;
					m_standalone = Boolean.FALSE;
				}
				else
					break;
				m_state = State.SVALTAIL;
				return true;
			case SVALTAIL:
				if ( 0 == tpl )
				{
					if ( m_q != b )
						break;
					m_state = State.TRAILING;
					return true;
				}
				if ( tpl != b )
					break;
				++ m_idx;
				return true;
			case TRAILING:
				if ( isSpace(b) )
					return true;
				if ( '?' != b )
					break;
				m_state = State.END;
				return true;
			case END:
				if ( '>' != b )
					break;
				m_state = State.MATCHED;
				m_readaheadStart = m_pos;
				m_saving = true;
				return false;
			case MATCHED:     // no more input needed for a determination;
			case UNMATCHED:   // whatever more is provided, just buffer it
				return false; // as readahead
			case ABANDONED:
			}
			m_state = State.ABANDONED;
			String m = "Invalid XML/Text declaration";
			if ( m_mustBeXmlDecl )
				m = "Invalid XML declaration";
			else if ( m_mustBeTextDecl )
				m = "Invalid text declaration";
			throw new SQLDataException(m, "2200N");
		}

		private boolean isSpace(byte b)
		{
			return (0x20 == b) || (0x09 == b) || (0x0D == b) || (0x0A == b);
		}

		/**
		 * Generate a declaration, if necessary, with the XML version and
		 * standalone status determined in declaration parsing and the name of
		 * the server encoding, followed always by any readahead buffered during
		 * a nonmatching parse or following a matching one.
		 * @param serverCharset The encoding to be named in the declaration if
		 * one is generated (which is forced if the encoding isn't UTF-8).
		 * @return A byte array representing the declaration if any, followed
		 * by any readahead.
		 */
		byte[] prefix(Charset serverCharset) throws IOException
		{
			/*
			 * Will this be DOCUMENT or CONTENT ?
			 * Without some out-of-band indication, we just don't know yet.
			 * For now, get DOCUMENT working.
			 */
			// boolean mightBeDocument = true;
			// boolean mightBeContent  = true;

			/*
			 * Defaults for when no declaration was matched:
			 */
			boolean canOmitVersion = true; // no declaration => 1.0
			byte[] version = new byte[] { '1', '.', '0' };
			boolean canOmitEncoding = "UTF-8".equals(serverCharset.name());
			boolean canOmitStandalone = true;

			byte[] parseResult = m_save.toByteArray();

			if ( State.MATCHED == m_state )
			{
				/*
				 * Parsing the decl could have turned up a non-1.0 XML version,
				 * which would mean we can't neglect to declare it, As for any
				 * encoding found in the varlena, the value doesn't matter (PG
				 * always uses the server encoding, whatever the stored decl
				 * might say). Its presence or absence in the decl can influence
				 * m_mustBeXmlDecl: the grammar productions XMLDecl and TextDecl
				 * are slightly different, which in a better world could help
				 * distinguish DOCUMENT from CONTENT, but PG doesn't preserve
				 * the distinction, instead always omitting the encoding in
				 * xml_out, which only XMLDecl can match. This code isn't
				 * reading from xml_out ... but if the value has ever been put
				 * through PG expressions that involved casting, xml_out may
				 * have eaten the encoding at that time.
				 *   So, for now, all that can be done here is refinement of
				 * canOmitVersion and canOmitStandalone. Also, PG's hand-laid
				 * parse_xml_decl always insists on the version being present,
				 * so if we produce a decl at all, it had better not have either
				 * the version or the encoding omitted.
				 */
				canOmitVersion = m_xml1_0; //  &&  ! m_mustBeXmlDecl;
				// canOmitEncoding &&= ! m_mustBeTextDecl;
				canOmitStandalone = null == m_standalone;
				if ( ! m_xml1_0  &&  m_versionEnd > m_versionStart )
					version = Arrays.copyOfRange(parseResult,
						m_versionStart, m_versionEnd);
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			if ( ! ( canOmitVersion && canOmitEncoding && canOmitStandalone ) )
			{
				baos.write(s_tpl, 0, 5); // <?xml
				baos.write(' ');
				baos.write(s_tpl, 6, 7); // version
				baos.write('=');
				baos.write('"');
				baos.write(version);
				baos.write('"');
				baos.write(' ');
				baos.write(s_tpl, 17, 8); // encoding
				baos.write('=');
				baos.write('"');
				/*
				 * This is no different from all the rest of this class in
				 * relying on the current fact that all supported PG server
				 * encodings match ASCII as far as the characters for decls go.
				 * It's just a bit more explicit here.
				 */
				baos.write(serverCharset.name().getBytes(serverCharset));
				baos.write('"');
				if ( ! canOmitStandalone )
				{
					baos.write(' ');
					baos.write(s_tpl, 26, 10); // standalone
					baos.write('=');
					baos.write('"');
					if ( m_standalone )
						baos.write(s_tpl, 37, 3); // yes
					else
						baos.write(s_tpl, 41, 2); // no
					baos.write('"');
				}
				baos.write('?');
				baos.write('>');
			}

			baos.write(parseResult,
				m_readaheadStart, parseResult.length - m_readaheadStart);

			return baos.toByteArray();
		}

		/**
		 * Throw an exception if a decl was matched and specified an encoding
		 * that isn't the server encoding, or if a decl was malformed, or if
		 * strict is specified, no encoding was declared, and the server
		 * encoding is not UTF-8.
		 * @param serverCharset The encoding used by the server; any encoding
		 * specified in the stream must resolve (possibly as an alias) to this
		 * encoding.
		 * @param strict if true, a decl may only be absent, or lack encoding
		 * information, if the server charset is UTF-8. If false, the check
		 * passes regardless of server encoding if the stream contains no decl
		 * or the decl does not declare an encoding.
		 */
		void checkEncoding(Charset serverCharset, boolean strict)
		throws SQLException
		{
			if ( State.MATCHED == m_state )
			{
				if ( m_encodingEnd > m_encodingStart )
				{
					byte[] parseResult = m_save.toByteArray();
					/*
					 * The assumption that the serverCharset can be used in
					 * constructing this String rests again on all supported
					 * server charsets matching on the characters used in decls.
					 */
					String encName = new String(parseResult,
						m_encodingStart, m_encodingEnd - m_encodingStart,
						serverCharset);
					try
					{
						Charset cs = Charset.forName(encName);
						if ( serverCharset.equals(cs) )
							return;
					}
					catch ( IllegalArgumentException iae ) { }
					throw new SQLDataException(
						"XML declares character set \"" + encName +
						"\" which does not match server encoding", "2200N");
				}
			}
			else if ( State.UNMATCHED != m_state )
				throw new SQLDataException(
					"XML begins with an incomplete declaration", "2200N");

			if ( ! strict  ||  "UTF-8".equals(serverCharset.name()) )
				return;
			throw new SQLDataException(
				"XML does not declare a character set, and server encoding " +
				"is not UTF-8", "2200N");
		}
	}
}
