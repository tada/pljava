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

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;

import java.nio.charset.Charset;

import java.util.concurrent.atomic.AtomicReference;

import org.postgresql.pljava.internal.Backend;
import org.postgresql.pljava.internal.MarkableSequenceInputStream;

import java.sql.SQLNonTransientException;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.DTD;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.SPACE;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import javax.xml.stream.XMLStreamException;

/* ... for SQLXMLImpl.Readable */

import java.io.InputStreamReader;
import java.nio.CharBuffer;

import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.dom.DOMSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import static org.postgresql.pljava.internal.Session.implServerCharset;
import org.postgresql.pljava.internal.VarlenaWrapper;

import java.sql.SQLFeatureNotSupportedException;

/* ... for SQLXMLImpl.DeclProbe */

import java.io.ByteArrayOutputStream;

import java.util.Arrays;

import java.sql.SQLDataException;

/* ... for SQLXMLImpl.Writable */

import java.io.FilterOutputStream;
import java.io.OutputStreamWriter;

import static javax.xml.transform.OutputKeys.ENCODING;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;

import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stax.StAXResult;
import javax.xml.transform.dom.DOMResult;

import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

/* ... for SQLXMLImpl.SAXResultAdapter and .SAXUnwrapFilter */

import javax.xml.transform.Transformer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.XMLFilterImpl;

/* ... for SQLXMLImpl.StAXResultAdapter and .StAXUnwrapFilter */

import java.util.NoSuchElementException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.StreamReaderDelegate;

public abstract class SQLXMLImpl<V extends VarlenaWrapper> implements SQLXML
{
	protected AtomicReference<V> m_backing;

	protected SQLXMLImpl(V backing)
	{
		m_backing = new AtomicReference<>(backing);
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

	/**
	 * Create a new, initially empty and writable, SQLXML instance, whose
	 * backing memory will in a transaction-scoped PostgreSQL memory context.
	 */
	static SQLXML newWritable()
	{
		synchronized ( Backend.THREADLOCK )
		{
			return _newWritable();
		}
	}

	/**
	 * Native code calls this method to claim complete control over the
	 * underlying {@code VarlenaWrapper} and dissociate it from Java.
	 * @param oid The PostgreSQL type ID the native code is expecting;
	 * see Readable.adopt for why that can matter.
	 * @return The underlying {@code VarlenaWrapper} (which has its own
	 * {@code adopt} method the native code will call next.
	 * @throws SQLException if this {@code SQLXML} instance is not in the
	 * proper state to be adoptable.
	 */
	protected abstract VarlenaWrapper adopt(int oid) throws SQLException;

	/**
	 * Return a description of this object useful for debugging (not the raw
	 * XML content).
	 */
	@Override
	public String toString()
	{
		return toString(this);
	}

	/**
	 * Return information about this object useful for debugging, prefixed with
	 * a possibly shortened form of the class name of the passed object
	 * {@code o}; the normal Java {@code toString()} will pass {@code this}.
	 *<p>
	 * Subclasses are encouraged to override, call the super method and append
	 * subclass-specific detail.
	 * @param o Object whose class name should be used to prefix the returned
	 * string. Passing {@code null} is the same as passing {@code this}.
	 * @return Description of this object for debugging convenience.
	 */
	protected String toString(Object o)
	{
		if ( null == o )
			o = this;
		V backing = m_backing.get();
		if ( null != backing )
			return backing.toString(o);
		Class<?> c = o.getClass();
		String cn = c.getCanonicalName();
		int pnl = c.getPackage().getName().length();
		return cn.substring(1 + pnl) + " defunct";
	}

	private static native SQLXML _newWritable();

	/**
	 * Return an InputStream presenting the contents of the underlying
	 * varlena, but with the leading declaration corrected if need be.
	 *<p>
	 * The current stored form in PG for the XML type is a character string
	 * in server encoding, which may or may not still include a declaration
	 * left over from an input or cast operation, which declaration may or
	 * may not be correct (about the encoding, anyway). Nothing is stored
	 * to distinguish whether the value is of the {@code DOCUMENT} or
	 * {@code CONTENT} form, to determine which requires a full reparse in
	 * the general case.
	 *<p>
	 * This method only peeks at early parse events in the stream, to see
	 * if a {@code DOCTYPE} is present (must be {@code DOCUMENT}, or there
	 * is any other content before the first element (cannot be
	 * {@code DOCUMENT}). The input will not have a synthetic root element
	 * wrapped around it if a {@code DOCTYPE} is present, as that would
	 * break validation; otherwise (whether the check concluded it can't be
	 * {@code DOCUMENT}, or was simply inconclusive}, a synthetic wrapper
	 * will be added, as it will not break anything.
	 *<p>
	 * As a side effect, this method sets {@code m_wrapped} tp {@code true}
	 * if it applies a wrapper element. When returning a type of
	 * {@code Source} that presents parsed results, it will be configured
	 * to present them with the wrapper element filtered out.
	 *<p>
	 * However, when using the API that exposes the serialized form
	 * directly ({@code getBinaryStream}, {@code getCharacterStream},
	 * {@code getString}), this method is passed {@code true} for
	 * {@code neverWrap}, and no wrapping is done. The application code must
	 * then handle the possibility that the stream may fail to parse as a
	 * {@code DOCUMENT}. (The JDBC spec gives no guidance in this area.)
	 * @param is The InputStream to be corrected.
	 * @param neverWrap When {@code true}, suppresses the wrapping described
	 * above.
	 * @param wrapping An array of one boolean, which will be set true if
	 * the returned stream has had a wrapping document element applied that
	 * will have to be filtered away after parsing.
	 * @return An InputStream with its original decl, if any, replaced with
	 * a new one known to be correct, or none if the defaults are correct,
	 * and with the remaining content wrapped in a synthetic root element,
	 * unless the input is known early (by having a {@code DOCTYPE}) not to
	 * need one.
	 */
	static InputStream correctedDeclStream(
		InputStream is, boolean neverWrap, Charset serverCS, boolean[] wrapping)
	throws IOException, SQLException
	{
		assert null != wrapping && 1 == wrapping.length;

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
		 * or may not be more of m_backing left to read; the probe may or may
		 * not have found a decl. If it didn't, prefix() will treat whatever
		 * had been read as readahead and hand it all back, so it suffices
		 * here to create a SequenceInputStream of the prefix and whatever
		 * is or isn't left of m_backing.
		 *   A bonus is that the SequenceInputStream closes each underlying
		 * stream as it reaches EOF. After the last stream is used up, the
		 * SequenceInputStream remains open-at-EOF until explicitly closed,
		 * providing the expected input-stream behavior, but the underlying
		 * resources don't have to stick around for that.
		 */
		byte[] pfx = probe.prefix(serverCS);
		int raLen = probe.readaheadLength();
		int raOff = pfx.length - raLen;
		InputStream pfis = new ByteArrayInputStream(pfx, 0, raOff);
		InputStream rais = new ByteArrayInputStream(pfx, raOff, raLen);
		InputStream msis = new MarkableSequenceInputStream(pfis, rais, is);

		if ( neverWrap  ||  ! useWrappingElement(msis) )
			return msis;

		wrapping[0] = true;
		InputStream elemStart = new ByteArrayInputStream(
			"<pljava-content-wrap>".getBytes(serverCS));
		InputStream elemEnd = new ByteArrayInputStream(
			"</pljava-content-wrap>".getBytes(serverCS));
		msis = new MarkableSequenceInputStream(
			pfis, elemStart, rais, is, elemEnd);
		return msis;
	}

	/**
	 * Check (incompletely!) whether an {@code InputStream} is in XML
	 * {@code DOCUMENT} form (which Java XML parsers will accept) or
	 * {@code CONTENT} form, (which they won't, unless enclosed in a
	 * wrapping element).
	 *<p>
	 * Proceed by requiring the input stream to support {@code mark} and
	 * {@code reset}, marking it, creating a StAX parser, and pulling some
	 * initial parse events.
	 *<p>
	 * A possible {@code START_DOCUMENT} along with possible {@code SPACE},
	 * {@code COMMENT}, and {@code PROCESSING_INSTRUCTION} events could
	 * allowably begin either the {@code DOCUMENT} or the {@code CONTENT}
	 * form.
	 *<p>
	 * If a {@code DTD} is seen, the input must be in {@code DOCUMENT} form,
	 * and <em>must not</em> have a wrapper element added.
	 *<p>
	 * If anything else is seen before the first {@code START_ELEMENT}, the
	 * input must be in {@code CONTENT} form, and <em>must</em> have
	 * a wrapper element added.
	 *<p>
	 * If a {@code START_ELEMENT} is seen before either of those conclusions
	 * can be reached, this check is inconclusive. The conclusive check
	 * would be to finish parsing that element to see what, if anything,
	 * follows it. But that would often amount to parsing the whole stream
	 * just to determine how to parse it. Instead, just return @code true}
	 * anyway, as without a DTD, the wrapping trick is usable and won't
	 * break anything, even if it may not be necessary.
	 * @param is An {@code InputStream} that must be markable, will be
	 * marked on entry, and reset upon return.
	 * @return {@code true} if a wrapping element should be used.
	 */
	static boolean useWrappingElement(InputStream is)
	throws IOException
	{
		is.mark(Integer.MAX_VALUE);
		XMLInputFactory xif = XMLInputFactory.newFactory();
		xif.setProperty(xif.IS_NAMESPACE_AWARE, true);

		boolean mustBeDocument = false;
		boolean cantBeDocument = false;

		/*
		 * The XMLStreamReader may actually close the input stream if it
		 * reaches the end skipping only whitespace. That is probably a bug;
		 * in any case, protect the original input stream from being closed.
		 */
		InputStream tmpis = new FilterInputStream(is)
		{
			@Override
			public void close() throws IOException { }
		};

		XMLStreamReader xsr = null;
		try
		{
			xsr = xif.createXMLStreamReader(tmpis);
			while ( xsr.hasNext() )
			{
				int evt = xsr.next();

				if ( COMMENT == evt || PROCESSING_INSTRUCTION == evt
					|| SPACE == evt || START_DOCUMENT == evt )
					continue;

				if ( DTD == evt )
				{
					mustBeDocument = true;
					break;
				}

				if ( START_ELEMENT == evt ) // could be DOCUMENT or CONTENT
					break;

				cantBeDocument = true;
				break;
			}
		}
		catch ( XMLStreamException e )
		{
			cantBeDocument = true;
		}

		if ( null != xsr )
		{
			try
			{
				xsr.close();
			}
			catch ( XMLStreamException e )
			{
			}
		}
		is.reset();
		is.mark(0); // relax any reset-buffer requirement

		return ! mustBeDocument;
	}



	static class Readable extends SQLXMLImpl<VarlenaWrapper.Input>
	{
		private AtomicBoolean m_readable = new AtomicBoolean(true);
		private Charset m_serverCS = implServerCharset();
		private boolean m_wrapped = false;
		private final int m_pgTypeID;

		/**
		 * Create a readable instance, when called by native code (the
		 * constructor is otherwise private, after all), passing an initialized
		 * {@code VarlenaWrapper} and the PostgreSQL type ID from which it has
		 * been created.
		 * @param vwi The already-created wrapper for reading the varlena from
		 * native memory.
		 * @param oid The PostgreSQL type ID from which this instance is being
		 * created (for why it matters, see {@code adopt}).
		 */
		private Readable(VarlenaWrapper.Input vwi, int oid) throws SQLException
		{
			super(vwi);
			m_pgTypeID = oid;
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
				return correctedDeclStream(is, true);
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
				is = correctedDeclStream(is, true);
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
				is = correctedDeclStream(is, true);
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
				sourceClass = (Class<T>)SAXSource.class; // trust me on this

			try
			{
				if ( sourceClass.isAssignableFrom(StreamSource.class) )
					return sourceClass.cast(
						new StreamSource(correctedDeclStream(is, true)));

				if ( sourceClass.isAssignableFrom(SAXSource.class) )
				{
					XMLReader xr = XMLReaderFactory.createXMLReader();
					xr.setFeature("http://xml.org/sax/features/namespaces",
								  true);
					is = correctedDeclStream(is, false);
					if ( m_wrapped )
						xr = new SAXUnwrapFilter(xr);
					return sourceClass.cast(
						new SAXSource(xr, new InputSource(is)));
				}

				if ( sourceClass.isAssignableFrom(StAXSource.class) )
				{
					XMLInputFactory xif = XMLInputFactory.newFactory();
					xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
					XMLStreamReader xsr =
						xif.createXMLStreamReader(
							correctedDeclStream(is, false), m_serverCS.name());
					if ( m_wrapped )
						xsr = new StAXUnwrapFilter(xsr);
					return sourceClass.cast(new StAXSource(xsr));
				}

				if ( sourceClass.isAssignableFrom(DOMSource.class) )
				{
					DocumentBuilderFactory dbf =
						DocumentBuilderFactory.newInstance();
					dbf.setNamespaceAware(true);
					DocumentBuilder db = dbf.newDocumentBuilder();
					is = correctedDeclStream(is, false);
					DOMSource ds = new DOMSource(db.parse(is));
					if ( m_wrapped )
						domUnwrap(ds);
					return sourceClass.cast(ds);
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
		 * {@inheritDoc}
		 *<p>
		 * This is the <em>readable</em> subclass, most typically used for data
		 * coming from PostgreSQL to Java. The only circumstance in which it can
		 * be {@code adopt}ed is if the Java code has left it untouched, and
		 * simply returned it from a function, or used it directly as a query
		 * parameter.
		 *<p>
		 * That is a very efficient handoff with no superfluous copying of data.
		 * However, the backend is able to associate {@code SQLXML} instances
		 * with more than one PostgreSQL data type (as of this writing, it will
		 * allow XML or text, so that this API is usable in Java even if the
		 * PostgreSQL instance was not built with the XML type, or if, for some
		 * other reason, it is useful to apply Java XML processing to values in
		 * the database as text, without the overhead of a PG cast).
		 *<p>
		 * It would break type safety to allow a {@code SQLXML} instance created
		 * from text (on which PostgreSQL does not impose any particular syntax)
		 * to be directly assigned to a PostgreSQL XML type without verifying
		 * that it is XML. For generality, the verification will be done here
		 * whenever the PostgreSQL oid at {@code adopt} time differs from the
		 * one saved at creation. Doing the verification is noticeably slower
		 * than not doing it, but that fast case has to be reserved for when
		 * there is no funny business with the PostgreSQL types.
		 */
		@Override
		protected VarlenaWrapper adopt(int oid) throws SQLException
		{
			VarlenaWrapper.Input vw = m_backing.getAndSet(null);
			if ( ! m_readable.get() )
				throw new SQLNonTransientException(
					"SQLXML object has already been read from", "55000");
			if ( null == vw )
				backingIfNotFreed(); /* shorthand way to throw the exception */
			if ( m_pgTypeID != oid )
				vw.verify(new Verifier());
			return vw;
		}

		@Override
		protected String toString(Object o)
		{
			return String.format("%s %sreadable %swrapped",
				super.toString(o), m_readable.get() ? "" : "not ",
				m_wrapped ? "" : "not ");
		}

		/**
		 * An instance method for calling the static {@code
		 * correctedDeclInputStream} and storing its {@code wrapped} indicator
		 * as {@code m_wrapped}.
		 */
		private InputStream correctedDeclStream(
			InputStream is, boolean neverWrap)
		throws IOException, SQLException
		{
			boolean[] wrapped = { false };
			InputStream rslt =
				correctedDeclStream(is, neverWrap, m_serverCS, wrapped);
			m_wrapped = wrapped[0];
			return rslt;
		}

		/**
		 * Unwrap a DOM tree parsed from input that was wrapped in a synthetic
		 * root element in case it had the form of {@code XML(CONTENT)}.
		 *<p>
		 * Because the wrapping is applied pessimistically (it is done whenever
		 * a quick preparse did not conclusively prove the input was
		 * {@code DOCUMENT}), repeat the check here, where it requires only
		 * traversing one list of immediate DOM node children. Produce a
		 * {@code Document} node if possible, a {@code DocumentFragment} only if
		 * the tree really does not have {@code DOCUMENT} form.
		 * @param ds A {@code DOMSource} produced by parsing wrapped input.
		 * The parse result will be retrieved using {@code getNode()}, then
		 * replaced using {@code setNode()} with the unwrapped result, either a
		 * {@code Document} or a {@code DocumentFragment} node.
		 */
		private void domUnwrap(DOMSource ds)
		{
			Document d = (Document)ds.getNode();
			Element wrapper = d.getDocumentElement();
			/*
			 * Wrapping isn't done if the input has a DTD, so if we are here,
			 * the input does not have a DTD, and the null, null, null parameter
			 * list for createDocument is appropriate.
			 */
			Document newDoc =
				d.getImplementation().createDocument(null, null, null);
			DocumentFragment docFrag = newDoc.createDocumentFragment();
			boolean isDocument = true;
			boolean seenElement = false;
			for ( Node n = wrapper.getFirstChild(), next = null;
				  null != n; n = next )
			{
				/*
				 * Grab the next sibling early, before the adoptNode() below,
				 * because that will unlink this node from its source Document,
				 * clearing its nextSibling link.
				 */
				next = n.getNextSibling();

				switch ( n.getNodeType() )
				{
				case Node.ELEMENT_NODE:
					if ( seenElement )
						isDocument = false;
					seenElement = true;
					break;
				case Node.COMMENT_NODE:
				case Node.PROCESSING_INSTRUCTION_NODE:
					break;
				case Node.TEXT_NODE:
					if ( ! ((Text)n).isElementContentWhitespace() )
						isDocument = false;
					break;
				default:
					isDocument = false;
				}

				docFrag.appendChild(newDoc.adoptNode(n));
			}

			if ( isDocument )
			{
				newDoc.appendChild(docFrag);
				ds.setNode(newDoc);
			}
			else
				ds.setNode(docFrag);
		}
	}



	static class Writable extends SQLXMLImpl<VarlenaWrapper.Output>
	{
		private AtomicBoolean m_writable = new AtomicBoolean(true);
		private Charset m_serverCS = implServerCharset();
		private DOMResult m_domResult;

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

		private VarlenaWrapper.Output backingAndClearWritable()
		throws SQLException
		{
			VarlenaWrapper.Output backing = backingIfNotFreed();
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
			VarlenaWrapper.Output os = backingAndClearWritable();
			if ( null == os )
				return super.setBinaryStream();
			try
			{
				os.setVerifier(new Verifier());
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
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				return super.setCharacterStream();
			try
			{
				vwo.setVerifier(new Verifier());
				OutputStream os = new DeclCheckedOutputStream(vwo, m_serverCS);
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
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				super.setString(value);
			try
			{
				vwo.setVerifier(new Verifier());
				OutputStream os = new DeclCheckedOutputStream(vwo, m_serverCS);
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
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				return super.setResult(resultClass);

			if ( null == resultClass || Result.class == resultClass )
				resultClass = (Class<T>)SAXResult.class; // trust me on this

			try
			{
				if ( resultClass.isAssignableFrom(StreamResult.class) )
				{
					vwo.setVerifier(new Verifier());
					return resultClass.cast(
						new StreamResult(new DeclCheckedOutputStream(
							vwo, m_serverCS)));
				}

				/*
				 * The remaining cases all can use the NoOp verifier.
				 */
				vwo.setVerifier(VarlenaWrapper.Verifier.NoOp.INSTANCE);
				OutputStream os = vwo;

				if ( resultClass.isAssignableFrom(SAXResult.class) )
				{
					SAXTransformerFactory saxtf = (SAXTransformerFactory)
						SAXTransformerFactory.newInstance();
					TransformerHandler th = saxtf.newTransformerHandler();
					th.getTransformer().setOutputProperty(
						ENCODING, m_serverCS.name());
					os = new DeclCheckedOutputStream(os, m_serverCS);
					th.setResult(new StreamResult(os));
					th = SAXResultAdapter.newInstance(th, os);
					return resultClass.cast(new SAXResult(th));
				}

				if ( resultClass.isAssignableFrom(StAXResult.class) )
				{
					XMLOutputFactory xof = XMLOutputFactory.newFactory();
					os = new DeclCheckedOutputStream(os, m_serverCS);
					XMLStreamWriter xsw = xof.createXMLStreamWriter(
						os, m_serverCS.name());
					xsw = new StAXResultAdapter(xsw, os);
					return resultClass.cast(new StAXResult(xsw));
				}

				if ( resultClass.isAssignableFrom(DOMResult.class) )
				{
					return resultClass.cast(m_domResult = new DOMResult());
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

		/**
		 * Serialize a {@code DOMResult} to an {@code OutputStream}
		 * <em>and close it</em>.
		 */
		private void serializeDOM(DOMResult r, OutputStream os)
		throws SQLException
		{
			DOMSource src = new DOMSource(r.getNode());
			try
			{
				TransformerFactory tf = TransformerFactory.newInstance();
				Transformer t = tf.newTransformer();
				t.setOutputProperty(ENCODING, m_serverCS.name());
				os = new DeclCheckedOutputStream(os, m_serverCS);
				StreamResult rlt = new StreamResult(os);
				t.transform(src, rlt);
				os.close();
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		protected VarlenaWrapper adopt(int oid) throws SQLException
		{
			VarlenaWrapper.Output vwo = m_backing.getAndSet(null);
			if ( m_writable.get() )
				throw new SQLNonTransientException(
					"Writable SQLXML object has not been written yet", "55000");
			if ( null == vwo )
				backingIfNotFreed(); /* shorthand way to throw the exception */
			if ( null != m_domResult )
			{
				serializeDOM(m_domResult, vwo);
				m_domResult = null;
			}
			return vwo;
		}

		@Override
		protected String toString(Object o)
		{
			return String.format("%s %swritable", super.toString(o),
				m_writable.get() ? "" : "not ");
		}
	}

	static class Verifier extends VarlenaWrapper.Verifier.Base
	{
		@Override
		protected void verify(InputStream is) throws Exception
		{
			boolean[] wrapped = { false };
			XMLReader xr = XMLReaderFactory.createXMLReader();
			xr.setFeature("http://xml.org/sax/features/namespaces", true);
			is = correctedDeclStream(
				is, false, implServerCharset(), wrapped);
			/*
			 * What does an XMLReader do if no handlers have been set for
			 * content events? Parses everything and discards the events.
			 * Just what you'd want for a verifier.
			 */
			xr.parse(new InputSource(is));
		}
	}

	/**
	 * Filter an {@code OutputStream} by ensuring it doesn't begin with a
	 * declaration of a character encoding other than the server encoding, and
	 * passing any declaration along in an edited form more palatable to
	 * PostgreSQL.
	 */
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

		/**
		 * Called after enough bytes have been passed to the {@code DeclProbe}
		 * for it to know whether a decl is present and correct, to throw an
		 * exception if an encoding is declared that is not the server encoding,
		 * and then pass the (possibly edited) decl and any readahead along
		 * to the output.
		 *<p>
		 * It is assumed that the stream is being generated by code that does
		 * encoding declarations properly, so should have one if any code
		 * other than UTF-8 is in use. (For now, in a mood of leniency,
		 * {@code false} is passed to {@code checkEncoding}'s {@code strict}
		 * parameter, so an exception will be generated only if the stream
		 * explicitly declares an encoding that isn't the server encoding. This
		 * could one day be made configurable, perhaps as a {@code Connection}
		 * property.
		 *<p>
		 * It's assumed that the destination of the stream is PostgreSQL's
		 * native XML datatype, where some of the native functions can fall over
		 * if an encoding declaration is present (even if it correctly matches
		 * the server encoding), so any decl generated into the output will be
		 * edited to remove any reference to encoding; this can fall short of
		 * strict conformance, but works better with the PG core implementation.
		 */
		private void check() throws IOException, SQLException
		{
			if ( null == m_probe )
				return;
			m_probe.checkEncoding(m_serverCS, false);
			byte[] prefix = m_probe.prefix(null /* not m_serverCS */);
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
	 * Class to wrap an {@code XMLReader} and pass all of the parse events
	 * except the outermost ("document root") element, in effect producing
	 * {@code XML(CONTENT)} when the underlying stream has had a synthetic
	 * root element wrapped around it to satisfy a JRE-bundled parser that
	 * only accepts {@code XML(DOCUMENT)}.
	 *<p>
	 * The result may be surprising to code consuming the SAX stream, depending
	 * on what it expects, but testing has showed the JRE-bundled identity
	 * transformer, at least, to accept the input and faithfully reproduce the
	 * non-document content.
	 */
	static class SAXUnwrapFilter extends XMLFilterImpl
	{
		private int m_nestLevel = 0;

		SAXUnwrapFilter(XMLReader parent)
		{
			super(parent);
		}

		@Override
		public void startElement(
			String uri, String localName, String qName, Attributes atts)
			throws SAXException
		{
			if ( 0 < m_nestLevel++ )
				super.startElement(uri, localName, qName, atts);
		}

		@Override
		public void endElement(String uri, String localName, String qName)
			throws SAXException
		{
			if ( 0 < --m_nestLevel )
				super.endElement(uri, localName, qName);
		}
	}

	/**
	 * Class to wrap a SAX {@code TransformerHandler} and hook the
	 * {@code endDocument} callback to also close the underlying output stream,
	 * making the {@code SQLXML} object ready to use for storing or returning
	 * the value.
	 */
	static class SAXResultAdapter
		extends XMLFilterImpl implements TransformerHandler
	{
		private OutputStream m_os;
		private TransformerHandler m_th;
		private SAXResultAdapter(TransformerHandler th, OutputStream os)
		{
			m_os = os;
			m_th = th;
			setContentHandler(th);
			setDTDHandler(th);
		}

		static TransformerHandler newInstance(
				TransformerHandler th, OutputStream os)
		{
			return new SAXResultAdapter(th, os);
		}

		/**
		 * Version of {@code endDocument} that also closes the underlying
		 * stream.
		 */
		@Override
		public void endDocument() throws SAXException
		{
			super.endDocument();
			try
			{
				m_os.close();
			}
			catch ( IOException ioe )
			{
				throw new SAXException("Failure closing SQLXML SAXResult", ioe);
			}
			m_os = null;
		}

		/*
		 * XMLFilterImpl provides default pass-through methods for most of the
		 * superinterfaces of TransformerHandler, but not for those of
		 * LexicalHandler, so here goes.
		 */

		@Override
		public void startDTD(String name, String publicId, String systemId)
		throws SAXException
		{
			m_th.startDTD(name, publicId, systemId);
		}

		@Override
		public void endDTD()
		throws SAXException
		{
			m_th.endDTD();
		}

		@Override
		public void startEntity(String name)
		throws SAXException
		{
			/*
			 * For the time being, do NOT pass through startEntity/endEntity.
			 * When we are the result of a transform using the JRE-bundled
			 * Transformer implementation, we may get called by a class
			 * com.sun.org.apache.xml.internal.serializer.ToXMLSAXHandler
			 * that overrides startEntity and gives us those, but neglects to
			 * override endEntity and never gives us those, leaving our
			 * serializer thinking it's stuck in an entity forever. (Insert
			 * Java bug number here if assigned.) Can revisit later if a fixed
			 * Java version is known, or could use a simple test to check for
			 * presence of the bug.
			 */
			//m_th.startEntity(name);
		}

		@Override
		public void endEntity(String name)
		throws SAXException
		{
			/*
			 * See startEntity.
			 */
			// m_th.endEntity(name);
		}

		@Override
		public void startCDATA()
		throws SAXException
		{
			m_th.startCDATA();
		}

		@Override
		public void endCDATA()
		throws SAXException
		{
			m_th.endCDATA();
		}

		@Override
		public void comment(char[] ch, int start, int length)
		throws SAXException
		{
			m_th.comment(ch, start, length);
		}

		@Override
		public void setResult(Result result)
		{
			throw new IllegalArgumentException("Result already set");
		}

		@Override
		public void setSystemId(String systemId)
		{
			m_th.setSystemId(systemId);
		}

		@Override
		public String getSystemId()
		{
			return m_th.getSystemId();
		}

		@Override
		public Transformer getTransformer()
		{
			return m_th.getTransformer();
		}
	}

	/**
	 * Class to wrap an {@code XMLStreamReader} and pass all of the parse events
	 * except the outermost ("document root") element, in effect producing
	 * {@code XML(CONTENT)} when the underlying stream has had a synthetic
	 * root element wrapped around it to satisfy a JRE-bundled parser that
	 * only accepts {@code XML(DOCUMENT)}.
	 *<p>
	 * The result may be surprising to code consuming the StAX stream, depending
	 * on what it expects; testing has shown the JRE-bundled identity
	 * transformer does not faithfully reproduce such input (though, oddly, the
	 * 'same' identity transformer reading the 'same' content through the
	 * {@code SAXUnwrapFilter} does). Code that will be expected to handle
	 * {@code XML(CONTENT)} and not just {@code XML(DOCUMENT)} using this
	 * interface should be tested for correct behavior.
	 */
	static class StAXUnwrapFilter extends StreamReaderDelegate
	{
		private boolean m_hasPeeked;
		private int m_nestLevel = 0;

		StAXUnwrapFilter(XMLStreamReader reader)
		{
			super(reader);
		}

		@Override
		public boolean hasNext() throws XMLStreamException
		{
			if ( m_hasPeeked )
				return true;
			if ( ! super.hasNext() )
				return false;
			int evt = super.next();

			if ( START_ELEMENT == evt )
			{
				if ( 0 < m_nestLevel++ )
				{
					m_hasPeeked = true;
					return true;
				}
				if ( ! super.hasNext() )
					return false;
				evt = super.next();
			}

			/*
			 * If the above if() matched, we saw a START_ELEMENT, and if it
			 * wasn't the hidden one, we returned and are not here. If the if()
			 * matched and we're here, it was the hidden one, and we are looking
			 * at the next event. It could also be a START_ELEMENT, but it can't
			 * be the hidden one, so needs no special treatment other than to
			 * increment nestLevel. It could be an END_ELEMENT, checked next.
			 */

			if ( START_ELEMENT == evt )
				++ m_nestLevel;
			else if ( END_ELEMENT == evt )
			{
				if ( 0 < --m_nestLevel )
				{
					m_hasPeeked = true;
					return true;
				}
				if ( ! super.hasNext() )
					return false;
				evt = super.next();
			}

			/*
			 * If the above if() matched, we saw an END_ELEMENT, and if it
			 * wasn't the hidden one, we returned and are not here. If the if()
			 * matched and we're here, it was the hidden one, and we are looking
			 * at the next event. It can't really be an END_ELEMENT (the hidden
			 * one had better be the last one) at all, much less the hidden one.
			 * It also can't really be a START_ELEMENT. So, no more bookkeeping,
			 * other than to set hasPeeked.
			 */

			m_hasPeeked = true;
			return true;
		}

		@Override
		public int next() throws XMLStreamException
		{
			if ( ! hasNext() )
				throw new NoSuchElementException();
			m_hasPeeked = false;
			return getEventType();
		}

		@Override
		public int nextTag() throws XMLStreamException
		{
			int evt;
			while ( true )
			{
				if ( ! hasNext() )
					throw new NoSuchElementException();
				evt = next();
				if ( ( CHARACTERS == evt || CDATA == evt ) && isWhiteSpace() )
					continue;
				if ( SPACE != evt && PROCESSING_INSTRUCTION != evt
					&& COMMENT != evt )
					break;
			}
			/* if NoSuchElement wasn't thrown, evt is definitely assigned */
			if ( START_ELEMENT != evt && END_ELEMENT != evt )
				throw new XMLStreamException(
					"expected start or end tag", getLocation());
			return evt;
		}
	}

	/**
	 * Class to wrap a StAX {@code XMLStreamWriter} and hook the method
	 * {@code writeEndDocument} to also close the underlying output stream,
	 * making the {@code SQLXML} object ready to use for storing or returning
	 * the value.
	 */
	static class StAXResultAdapter implements XMLStreamWriter
	{
		private XMLStreamWriter m_xsw;
		private OutputStream m_os;

		StAXResultAdapter(XMLStreamWriter xsw, OutputStream os)
		{
			m_xsw = xsw;
			m_os = os;
		}

		@Override
		public void writeStartElement(String localName)
			throws XMLStreamException
		{
			m_xsw.writeStartElement(localName);
		}

		@Override
		public void writeStartElement(String namespaceURI, String localName)
			throws XMLStreamException
		{
			m_xsw.writeStartElement(namespaceURI, localName);
		}

		@Override
		public void writeStartElement(
			String prefix, String localName, String namespaceURI)
			throws XMLStreamException
		{
			m_xsw.writeStartElement(prefix, namespaceURI, localName);
		}

		@Override
		public void writeEmptyElement(String namespaceURI, String localName)
			throws XMLStreamException
		{
			m_xsw.writeEmptyElement(namespaceURI, localName);
		}

		@Override
		public void writeEmptyElement(
			String prefix, String localName, String namespaceURI)
			throws XMLStreamException
		{
			m_xsw.writeEmptyElement(prefix, namespaceURI, localName);
		}

		@Override
		public void writeEmptyElement(String localName)
			throws XMLStreamException
		{
			m_xsw.writeEmptyElement(localName);
		}

		@Override
		public void writeEndElement() throws XMLStreamException
		{
			m_xsw.writeEndElement();
		}

		/**
		 * Version of {@code writeEndDocument} that also closes the underlying
		 * stream.
		 *<p>
		 * Note it does <em>not</em> call this class's own <em>close</em>; a
		 * calling transformer may emit a warning if that is done.
		 */
		@Override
		public void writeEndDocument() throws XMLStreamException
		{
			m_xsw.writeEndDocument();
			m_xsw.flush();

			try
			{
				m_os.close();
			}
			catch ( Exception ioe )
			{
				throw new XMLStreamException(
					"Failure closing SQLXML StAXResult", ioe);
			}
		}

		@Override
		public void close() throws XMLStreamException
		{
			m_xsw.close();
		}

		@Override
		public void flush() throws XMLStreamException
		{
			m_xsw.flush();
		}

		@Override
		public void writeAttribute(String localName, String value)
			throws XMLStreamException
		{
			m_xsw.writeAttribute(localName, value);
		}

		@Override
		public void writeAttribute(
			String prefix, String namespaceURI, String localName, String value)
			throws XMLStreamException
		{
			m_xsw.writeAttribute(prefix, namespaceURI, localName, value);
		}

		@Override
		public void writeAttribute(
			String namespaceURI, String localName, String value)
			throws XMLStreamException
		{
			m_xsw.writeAttribute(namespaceURI, localName, value);
		}

		@Override
		public void writeNamespace(String prefix, String namespaceURI)
			throws XMLStreamException
		{
			m_xsw.writeNamespace(prefix, namespaceURI);
		}

		@Override
		public void writeDefaultNamespace(String namespaceURI)
			throws XMLStreamException
		{
			m_xsw.writeDefaultNamespace(namespaceURI);
		}

		@Override
		public void writeComment(String data) throws XMLStreamException
		{
			m_xsw.writeComment(data);
		}

		@Override
		public void writeProcessingInstruction(String target)
			throws XMLStreamException
		{
			m_xsw.writeProcessingInstruction(target);
		}

		@Override
		public void writeProcessingInstruction(String target, String data)
			throws XMLStreamException
		{
			m_xsw.writeProcessingInstruction(target, data);
		}

		@Override
		public void writeCData(String data) throws XMLStreamException
		{
			m_xsw.writeCData(data);
		}

		@Override
		public void writeDTD(String dtd) throws XMLStreamException
		{
			m_xsw.writeDTD(dtd);
		}

		@Override
		public void writeEntityRef(String name) throws XMLStreamException
		{
			m_xsw.writeEntityRef(name);
		}

		@Override
		public void writeStartDocument() throws XMLStreamException
		{
			m_xsw.writeStartDocument();
		}

		@Override
		public void writeStartDocument(String version) throws XMLStreamException
		{
			m_xsw.writeStartDocument(version);
		}

		@Override
		public void writeStartDocument(String encoding, String version)
			throws XMLStreamException
		{
			m_xsw.writeStartDocument(encoding, version);
		}

		@Override
		public void writeCharacters(String text) throws XMLStreamException
		{
			m_xsw.writeCharacters(text);
		}

		@Override
		public void writeCharacters(char[] text, int start, int len)
			throws XMLStreamException
		{
			m_xsw.writeCharacters(text, start,  len);
		}

		@Override
		public String getPrefix(String uri) throws XMLStreamException
		{
			return m_xsw.getPrefix(uri);
		}

		@Override
		public void setPrefix(String prefix, String uri)
			throws XMLStreamException
		{
			m_xsw.setPrefix(prefix, uri);
		}

		@Override
		public void setDefaultNamespace(String uri) throws XMLStreamException
		{
			m_xsw.setDefaultNamespace(uri);
		}

		@Override
		public void setNamespaceContext(NamespaceContext context)
			throws XMLStreamException
		{
			m_xsw.setNamespaceContext(context);
		}

		@Override
		public NamespaceContext getNamespaceContext()
		{
			return m_xsw.getNamespaceContext();
		}

		@Override
		public Object getProperty(String name) throws IllegalArgumentException
		{
			return m_xsw.getProperty(name);
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
		 * Pass null to force the omission of any encoding declaration; this is
		 * needed when writing to the native PG XML datatype, as some PG native
		 * functions such as IS DOCUMENT can misbehave if the declaration is
		 * present (even if it correctly matches the server encoding).
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
			boolean canOmitEncoding =
				null == serverCharset || "UTF-8".equals(serverCharset.name());
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
				if ( null != serverCharset )
				{
					baos.write(' ');
					baos.write(s_tpl, 17, 8); // encoding
					baos.write('=');
					baos.write('"');
					/*
					 * This is no different from all the rest of this class in
					 * relying on the current fact that all supported PG server
					 * encodings match ASCII as far as the characters for decls
					 * go. It's just a bit more explicit here.
					 */
					baos.write(serverCharset.name().getBytes(serverCharset));
					baos.write('"');
				}
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
		 * Return the number of bytes at the end of the {@code prefix} result
		 * that represent readahead, rather than being part of the decl.
		 */
		int readaheadLength()
		{
			return m_save.size() - m_readaheadStart;
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
