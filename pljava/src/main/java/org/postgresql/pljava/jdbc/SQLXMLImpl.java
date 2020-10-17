/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
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

import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.VarHandle;

import java.nio.charset.Charset;
import static java.nio.charset.StandardCharsets.US_ASCII;

import org.postgresql.pljava.internal.Backend;
import static org.postgresql.pljava.internal.Backend.doInPG;
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

import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.dom.DOMSource;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParserFactory;

import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;

import static org.postgresql.pljava.internal.Session.implServerCharset;
import org.postgresql.pljava.internal.VarlenaWrapper;

import java.sql.SQLFeatureNotSupportedException;

/* ... for SQLXMLImpl.WhitespaceAccumulator */

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* ... for SQLXMLImpl.DeclProbe */

import java.io.BufferedInputStream;
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
import org.xml.sax.ext.DefaultHandler2;
import org.xml.sax.helpers.XMLFilterImpl;

import org.postgresql.pljava.internal.SyntheticXMLReader.SAX2PROPERTY;

/* ... for SQLXMLImpl.StAXResultAdapter and .StAXUnwrapFilter */

import java.util.NoSuchElementException;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.StreamReaderDelegate;

/* ... for static adopt() method, doing low-level copies from foreign objects */

import java.io.BufferedReader;
import java.io.CharArrayReader;
import java.io.FilterReader;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.util.XMLEventConsumer;

import org.postgresql.pljava.internal.MarkableSequenceReader;

import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.ext.LexicalHandler;

/* ... for Adjusting API for Source / Result */

import java.io.StringReader;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.validation.Schema;
import org.postgresql.pljava.Adjusting;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/* ... for error handling */

import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

/* ... for SQLXMLImpl.Readable.Synthetic */

import org.postgresql.pljava.internal.VarlenaXMLRenderer;
import static org.postgresql.pljava.jdbc.TypeOid.PGNODETREEOID;

/**
 * Implementation of {@link SQLXML} for the SPI connection.
 */
public abstract class SQLXMLImpl<V extends VarlenaWrapper> implements SQLXML
{
	private static final VarHandle s_backingVH;
	protected volatile V m_backing;

	static
	{
		try
		{
			s_backingVH = lookup().findVarHandle(
				SQLXMLImpl.class, "m_backing", VarlenaWrapper.class);
		}
		catch ( ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	protected SQLXMLImpl(V backing)
	{
		s_backingVH.set(this, backing);
	}

	@Override
	public void free() throws SQLException
	{
		V backing = (V)s_backingVH.getAndSet(this, null);
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
		V backing = (V)s_backingVH.getAcquire(this);
		if ( null == backing )
			throw new SQLNonTransientException(
				"Attempted use of already-freed SQLXML object", "55000");
		return backing;
	}

	/**
	 * Wrap other checked exceptions in SQLException for methods specified to
	 * throw only that.
	 */
	static SQLException normalizedException(Exception e)
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
			"Exception in XML processing, not otherwise provided for: "
			+ e.getMessage(), "XX000", e);
	}

	/**
	 * Create a new, initially empty and writable, SQLXML instance, whose
	 * backing memory will in a transaction-scoped PostgreSQL memory context.
	 */
	static SQLXML newWritable()
	{
		return doInPG(() -> _newWritable());
	}

	/**
	 * Native code calls this static method to take over an SQLXML object
	 * with its content.
	 *<p>
	 * This is a static method because an {@code SQLXML} object presented to
	 * PostgreSQL need not necessarily be this implementation. If it is, then
	 * the real {@code adopt} method will be called directly; otherwise, a
	 * native {@code SQLXML} object has to be created, and the content copied
	 * to it.
	 * @param sx The SQLXML object to be adopted.
	 * @param oid The PostgreSQL type ID the native code is expecting;
	 * see Readable.adopt for why that can matter.
	 * @return The underlying {@code VarlenaWrapper} (which has its own
	 * {@code adopt} method the native code will call next.
	 * @throws SQLException if this {@code SQLXML} instance is not in the
	 * proper state to be adoptable.
	 */
	private static VarlenaWrapper adopt(SQLXML sx, int oid) throws SQLException
	{
		if ( sx instanceof Readable.PgXML || sx instanceof Writable )
			return ((SQLXMLImpl)sx).adopt(oid);

		Source src = sx.getSource(null);
		SQLXML rx =
			newWritable().setResult(Adjusting.XML.SourceResult.class)
			.set(src).getSQLXML();

		sx.free();
		return ((SQLXMLImpl)rx).adopt(oid);
	}

	/**
	 * Allow native code to claim complete control over the
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
		V backing = (V)s_backingVH.getAcquire(this);
		if ( null != backing )
			return backing.toString(o);
		Class<?> c = o.getClass();
		String cn = c.getCanonicalName();
		int pnl = c.getPackageName().length();
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
	 * As a side effect, this method sets {@code m_wrapped} to {@code true}
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
		probe.finish();

		return correctedDeclStream(is, probe, neverWrap, serverCS, wrapping);
	}

	/**
	 * Version of {@code correctedDeclStream} for use when a {@code DeclProbe}
	 * has already been constructed, and early bytes of the stream fed to it.
	 */
	static InputStream correctedDeclStream(
		InputStream is, DeclProbe probe, boolean neverWrap, Charset serverCS,
		boolean[] wrapping)
		throws IOException
	{
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

		if ( neverWrap )
			return new MarkableSequenceInputStream(pfis, rais, is);

		int markLimit = 1048576; // don't assume a markable stream's economical
		if ( ! is.markSupported() )
			is = new BufferedInputStream(is);
		else if ( is instanceof VarlenaWrapper ) // a VarlenaWrapper is, though
			markLimit = Integer.MAX_VALUE;

		InputStream msis = new MarkableSequenceInputStream(pfis, rais, is);
		if ( ! useWrappingElement(msis, markLimit) )
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

	static Reader correctedDeclReader(
		Reader r, DeclProbe probe, Charset impliedCS, boolean[] wrapping)
		throws IOException
	{
		char[] pfx = probe.charPrefix(impliedCS);
		int raLen = probe.readaheadLength();
		int raOff = pfx.length - raLen;
		Reader pfr = new CharArrayReader(pfx, 0, raOff);
		Reader rar = new CharArrayReader(pfx, raOff, raLen);

		if ( ! r.markSupported() )
			r = new BufferedReader(r);

		Reader msr = new MarkableSequenceReader(pfr, rar, r);
		if ( ! useWrappingElement(msr) )
			return msr;

		wrapping[0] = true;
		Reader elemStart = new StringReader("<pljava-content-wrap>");
		Reader elemEnd   = new StringReader("</pljava-content-wrap>");
		msr = new MarkableSequenceReader(
			pfr, elemStart, rar, r, elemEnd);
		return msr;
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
	static boolean useWrappingElement(InputStream is, int markLimit)
	throws IOException
	{
		is.mark(markLimit);

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

		boolean rslt = useWrappingElement(tmpis, null);

		is.reset();
		is.mark(0); // relax any reset-buffer requirement

		return rslt;
	}

	static boolean useWrappingElement(Reader r)
	throws IOException
	{
		r.mark(524288); // don't trust mark-supporting Reader to be economical

		/*
		 * The XMLStreamReader may actually close the input stream if it
		 * reaches the end skipping only whitespace. That is probably a bug;
		 * in any case, protect the original input stream from being closed.
		 */
		Reader tmpr = new FilterReader(r)
		{
			@Override
			public void close() throws IOException { }
		};

		boolean rslt = useWrappingElement(null, tmpr);

		r.reset();
		r.mark(0); // relax any reset-buffer requirement

		return rslt;
	}

	private static boolean useWrappingElement(InputStream is, Reader r)
	throws IOException
	{
		boolean mustBeDocument = false;
		boolean cantBeDocument = false;

		XMLInputFactory xif = XMLInputFactory.newInstance();
		xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
		xif.setProperty(xif.SUPPORT_DTD, false);// will still report one it sees
		xif.setProperty(xif.IS_REPLACING_ENTITY_REFERENCES, false);

		XMLStreamReader xsr = null;
		try
		{
			if ( null != is )
				xsr = xif.createXMLStreamReader(is);
			else
				xsr = xif.createXMLStreamReader(r);
			while ( xsr.hasNext() )
			{
				int evt = xsr.next();

				if ( COMMENT == evt || PROCESSING_INSTRUCTION == evt
					|| START_DOCUMENT == evt )
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

		return ! mustBeDocument;
	}



	static abstract class Readable<V extends VarlenaWrapper>
	extends SQLXMLImpl<V>
	{
		private static final VarHandle s_readableVH;
		protected volatile boolean m_readable = true;
		protected final int m_pgTypeID;
		protected Charset m_serverCS = implServerCharset();
		protected boolean m_wrapped = false;

		static
		{
			try
			{
				s_readableVH = lookup().findVarHandle(
					Readable.class, "m_readable", boolean.class);
			}
			catch ( ReflectiveOperationException e )
			{
				throw new ExceptionInInitializerError(e);
			}
		}

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
		private Readable(V vwi, int oid) throws SQLException
		{
			super(vwi);
			m_pgTypeID = oid;
			if ( null == m_serverCS )
			{
				free();
				throw new SQLFeatureNotSupportedException("SQLXML: no Java " +
					"Charset found to match server encoding; perhaps set " +
					"org.postgresql.server.encoding system property to a " +
					"valid Java charset name for the same encoding?", "0A000");
			}
		}

		private V backingAndClearReadable() throws SQLException
		{
			V backing = backingIfNotFreed();
			return (boolean)s_readableVH.getAndSet(this, false)
				? backing : null;
		}

		protected abstract InputStream toBinaryStream(
			V backing, boolean neverWrap)
		throws SQLException, IOException;

		@Override
		public InputStream getBinaryStream() throws SQLException
		{
			V backing = backingAndClearReadable();
			if ( null == backing )
				return super.getBinaryStream();
			try
			{
				return toBinaryStream(backing, true);
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		protected abstract Reader toCharacterStream(
			V backing, boolean neverWrap)
		throws SQLException, IOException;

		@Override
		public Reader getCharacterStream() throws SQLException
		{
			V backing = backingAndClearReadable();
			if ( null == backing )
				return super.getCharacterStream();
			try
			{
				return toCharacterStream(backing, true);
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public String getString() throws SQLException
		{
			V backing = backingAndClearReadable();
			if ( null == backing )
				return super.getString();

			CharBuffer cb = CharBuffer.allocate(32768);
			StringBuilder sb = new StringBuilder();
			try
			{
				Reader r = toCharacterStream(backing, true);
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

		/**
		 * Return a {@code Class<? extends Source>} object representing the most
		 * natural or preferred presentation if the caller has left it
		 * unspecified.
		 *<p>
		 * Override if the preferred flavor is not {@code SAXSource.class},
		 * which this implementation returns.
		 * @param sourceClass Either null, Source, or Adjusting.XML.Source.
		 * @return A preferred flavor of Adjusting.XML.Source, if sourceClass is
		 * Adjusting.XML.Source, otherwise the corresponding flavor of ordinary
		 * Source.
		 */
		@SuppressWarnings("unchecked")
		protected <T extends Source> Class<? extends T> preferredSourceClass(
			Class<T> sourceClass)
		{
			return Adjusting.XML.Source.class == sourceClass
				? (Class<? extends T>)Adjusting.XML.SAXSource.class
				: (Class<? extends T>)SAXSource.class;
		}

		/**
		 * Return a {@code StreamSource} presenting <em>backing</em> as a binary
		 * or character stream, whichever is most natural.
		 *<p>
		 * This implementation returns the binary stream obtained with
		 * {@code toBinaryStream(backing, true)}.
		 */
		protected StreamSource toStreamSource(V backing)
		throws SQLException, IOException
		{
			return new StreamSource(toBinaryStream(backing, true));
		}

		protected abstract Adjusting.XML.SAXSource toSAXSource(V backing)
		throws SQLException, SAXException, IOException;

		protected abstract Adjusting.XML.StAXSource toStAXSource(V backing)
		throws SQLException, XMLStreamException, IOException;

		protected abstract Adjusting.XML.DOMSource toDOMSource(V backing)
		throws
			SQLException, SAXException, IOException,
			ParserConfigurationException;

		@Override
		public <T extends Source> T getSource(Class<T> sourceClass)
		throws SQLException
		{
			V backing = backingAndClearReadable();
			if ( null == backing )
				return super.getSource(sourceClass);

			Class<? extends T> sc = sourceClass;

			if ( null == sc
				|| Source.class == sc
				|| Adjusting.XML.Source.class.equals(sc) )
				sc = preferredSourceClass(sc);

			try
			{
				if ( sc.isAssignableFrom(StreamSource.class) )
					return sc.cast(toStreamSource(backing));

				if ( sc.isAssignableFrom(SAXSource.class)
					|| sc.isAssignableFrom(AdjustingSAXSource.class) )
				{
					Adjusting.XML.SAXSource ss = toSAXSource(backing);
					/*
					 * Caution: while StAXSource and DOMSource have defaults()
					 * called right here, SAXSource does not, because there is
					 * an irksome ordering constraint such that schema() can't
					 * work if any XMLReader adjustments have been made first.
					 * Instead, SAXSource (and only SAXSource, so much for
					 * consistency) must do its own tracking of whether
					 * defaults() has been called, and do so if it hasn't been,
					 * either before the first explicit adjustment, or at get()
					 * time if none.
					 */
					// ss.defaults();
					if ( Adjusting.XML.Source.class
							.isAssignableFrom(sc) )
						return sc.cast(ss);
					return sc.cast(ss.get());
				}

				if ( sc.isAssignableFrom(StAXSource.class)
					|| sc.isAssignableFrom(AdjustingStAXSource.class) )
				{
					Adjusting.XML.StAXSource ss = toStAXSource(backing);
					ss.defaults();
					if ( Adjusting.XML.Source.class
							.isAssignableFrom(sc) )
						return sc.cast(ss);
					return sc.cast(ss.get());
				}

				if ( sc.isAssignableFrom(DOMSource.class)
					|| sc.isAssignableFrom(AdjustingDOMSource.class) )
				{
					Adjusting.XML.DOMSource ds = toDOMSource(backing);
					ds.defaults();
					if ( Adjusting.XML.Source.class
							.isAssignableFrom(sc) )
						return sc.cast(ds);
					return sc.cast(ds.get());
				}
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}

			throw new SQLFeatureNotSupportedException(
				"No support for SQLXML.getSource(" +
				sc.getName() + ".class)", "0A000");
		}

		@Override
		protected String toString(Object o)
		{
			return String.format("%s %sreadable %swrapped",
				super.toString(o), (boolean)s_readableVH.getAcquire()
					? "" : "not ", m_wrapped ? "" : "not ");
		}

		static class PgXML
		extends Readable<VarlenaWrapper.Input.Stream>
		{
			private PgXML(VarlenaWrapper.Input vwi, int oid)
			throws SQLException
			{
				super(vwi.new Stream(), oid);
			}

			/**
			 * {@inheritDoc}
			 *<p>
			 * This is the <em>readable</em> subclass, most typically used for
			 * data coming from PostgreSQL to Java. The only circumstance in
			 * which it can be {@code adopt}ed is if the Java code has left it
			 * untouched, and simply returned it from a function, or used it
			 * directly as a query parameter.
			 *<p>
			 * That is a very efficient handoff with no superfluous copying of
			 * data. However, the backend is able to associate {@code SQLXML}
			 * instances with more than one PostgreSQL data type (as of this
			 * writing, it will allow XML or text, so that this API is usable in
			 * Java even if the PostgreSQL instance was not built with the XML
			 * type, or if, for some other reason, it is useful to apply Java
			 * XML processing to values in the database as text, without the
			 * overhead of a PG cast).
			 *<p>
			 * It would break type safety to allow a {@code SQLXML} instance
			 * created from text (on which PostgreSQL does not impose any
			 * particular syntax) to be directly assigned to a PostgreSQL XML
			 * type without verifying that it is XML. For generality, the
			 * verification will be done here whenever the PostgreSQL oid at
			 * {@code adopt} time differs from the one saved at creation. Doing
			 * the verification is noticeably slower than not doing it, but that
			 * fast case has to be reserved for when there is no funny business
			 * with the PostgreSQL types.
			 */
			@Override
			protected VarlenaWrapper adopt(int oid) throws SQLException
			{
				VarlenaWrapper.Input.Stream vw = (VarlenaWrapper.Input.Stream)
					s_backingVH.getAndSet(this, null);
				if ( ! (boolean)s_readableVH.getAcquire(this) )
					throw new SQLNonTransientException(
						"SQLXML object has already been read from", "55000");
				if ( null == vw )
					backingIfNotFreed(); /* shorthand to throw the exception */
				if ( m_pgTypeID != oid )
					vw.verify(new Verifier());
				return vw;
			}

			/*
			 * This implementation of toBinaryStream has the side effect of
			 * setting m_wrapped to indicate whether a wrapping element has been
			 * added around the stream contents.
			 */
			@Override
			protected InputStream toBinaryStream(
				VarlenaWrapper.Input.Stream backing, boolean neverWrap)
			throws SQLException, IOException
			{
				boolean[] wrapped = { false };
				InputStream rslt = correctedDeclStream(
									backing, neverWrap, m_serverCS, wrapped);
				m_wrapped = wrapped[0];
				return rslt;
			}

			@Override
			protected Reader toCharacterStream(
				VarlenaWrapper.Input.Stream backing, boolean neverWrap)
			throws SQLException, IOException
			{
				InputStream is = toBinaryStream(backing, neverWrap);
				return new InputStreamReader(is, m_serverCS.newDecoder());
			}

			@Override
			protected Adjusting.XML.SAXSource toSAXSource(
				VarlenaWrapper.Input.Stream backing)
			throws SQLException, SAXException, IOException
			{
				InputStream is = toBinaryStream(backing, false);
				return new AdjustingSAXSource(new InputSource(is), m_wrapped);
			}

			@Override
			protected Adjusting.XML.StAXSource toStAXSource(
				VarlenaWrapper.Input.Stream backing)
			throws SQLException, XMLStreamException, IOException
			{
				InputStream is = toBinaryStream(backing, false);
				return new AdjustingStAXSource(is, m_serverCS, m_wrapped);
			}

			@Override
			protected Adjusting.XML.DOMSource toDOMSource(
				VarlenaWrapper.Input.Stream backing)
			throws
				SQLException, SAXException, IOException,
				ParserConfigurationException
			{
				InputStream is = toBinaryStream(backing, false);
				return new AdjustingDOMSource(is, m_wrapped);
			}
		}

		static class Synthetic extends Readable<VarlenaXMLRenderer>
		{
			private Synthetic(VarlenaWrapper.Input vwi, int oid)
			throws SQLException
			{
				super(xmlRenderer(oid, vwi), oid);
			}

			private static VarlenaXMLRenderer xmlRenderer(
				int oid, VarlenaWrapper.Input vwi)
			throws SQLException
			{
				switch ( oid )
				{
				case PGNODETREEOID: return new PgNodeTreeAsXML(vwi);
				default:
					throw new SQLNonTransientException(
						"no synthetic SQLXML support for Oid " + oid, "0A000");
				}
			}

			@Override
			protected VarlenaWrapper adopt(int oid) throws SQLException
			{
				throw new SQLFeatureNotSupportedException(
					"adopt() on a synthetic SQLXML not yet supported", "0A000");
			}

			@Override
			protected InputStream toBinaryStream(
				VarlenaXMLRenderer backing, boolean neverWrap)
			throws SQLException, IOException
			{
				throw new SQLFeatureNotSupportedException(
					"synthetic SQLXML as binary stream not yet supported",
					"0A000");
			}

			@Override
			protected Reader toCharacterStream(
				VarlenaXMLRenderer backing, boolean neverWrap)
			throws SQLException, IOException
			{
				throw new SQLFeatureNotSupportedException(
					"synthetic SQLXML as character stream not yet supported",
					"0A000");
			}

			@Override
			protected Adjusting.XML.SAXSource toSAXSource(
				VarlenaXMLRenderer backing)
			throws SQLException, SAXException, IOException
			{
				return new AdjustingSAXSource(backing, new InputSource());
			}

			protected Adjusting.XML.StAXSource toStAXSource(
				VarlenaXMLRenderer backing)
			throws SQLException, XMLStreamException, IOException
			{
				throw new SQLFeatureNotSupportedException(
					"synthetic SQLXML as StAXSource not yet supported",
					"0A000");
			}

			protected Adjusting.XML.DOMSource toDOMSource(
				VarlenaXMLRenderer backing)
			throws
				SQLException, SAXException, IOException,
				ParserConfigurationException
			{
				throw new SQLFeatureNotSupportedException(
					"synthetic SQLXML as DOMSource not yet supported",
					"0A000");
			}
		}
	}

	static final Pattern s_entirelyWS = Pattern.compile("\\A[ \\t\\n\\r]*+\\z");

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
	static void domUnwrap(DOMSource ds)
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

		Matcher entirelyWhitespace = s_entirelyWS.matcher("");

		boolean isDocument = true;
		boolean seenElement = false;
		boolean seenText = false;
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
				if ( isDocument )
				{
					seenText = true;
					entirelyWhitespace.reset(n.getNodeValue());
					if ( ! entirelyWhitespace.matches() )
						isDocument = false;
				}
				break;
			default:
				isDocument = false;
			}

			docFrag.appendChild(newDoc.adoptNode(n));
		}

		if ( ! seenElement )
			isDocument = false;

		if ( isDocument )
		{
			if ( seenText )
			{
				/*
				 * At least one text node was seen at top level, but none
				 * containing anything but whitespace (else isDocument would
				 * be false and we wouldn't be here). Such nodes have to go.
				 */
				for ( Node n = docFrag.getFirstChild(), next = null;
					  null != n; n = next )
				{
					next = n.getNextSibling();
					if ( Node.TEXT_NODE == n.getNodeType() )
						docFrag.removeChild(n);
				}
			}

			newDoc.appendChild(docFrag);
			ds.setNode(newDoc);
		}
		else
			ds.setNode(docFrag);
	}



	static class Writable extends SQLXMLImpl<VarlenaWrapper.Output>
	{
		private static final VarHandle s_writableVH;
		private volatile boolean m_writable = true;
		private Charset m_serverCS = implServerCharset();
		private DOMResult m_domResult;

		static
		{
			try
			{
				s_writableVH = lookup().findVarHandle(
					Writable.class, "m_writable", boolean.class);
			}
			catch ( ReflectiveOperationException e )
			{
				throw new ExceptionInInitializerError(e);
			}
		}

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
			return (boolean)s_writableVH.getAndSet(this, false)? backing : null;
		}

		@Override
		public void free() throws SQLException
		{
			VarlenaWrapper.Output vwo =
				(VarlenaWrapper.Output)s_backingVH.getAndSet(this, null);
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
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				return super.setBinaryStream();
			return new AdjustingStreamResult(vwo, m_serverCS)
				.defaults().preferBinaryStream().get().getOutputStream();
		}

		@Override
		public Writer setCharacterStream() throws SQLException
		{
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				return super.setCharacterStream();
			return new AdjustingStreamResult(vwo, m_serverCS)
				.defaults().preferCharacterStream().get().getWriter();
		}

		@Override
		public void setString(String value) throws SQLException
		{
			VarlenaWrapper.Output vwo = backingAndClearWritable();
			if ( null == vwo )
				super.setString(value);
			try
			{
				Writer w = new AdjustingStreamResult(vwo, m_serverCS)
					.defaults().preferCharacterStream().get().getWriter();
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
			return setResult(vwo, resultClass);
		}

		/**
		 * Return a {@code Class<? extends Result>} object representing the most
		 * natural or preferred presentation if the caller has left it
		 * unspecified.
		 *<p>
		 * Override if the preferred flavor is not {@code SAXResult.class},
		 * which this implementation returns.
		 * @param resultClass Either null, Result, or Adjusting.XML.Result.
		 * @return A preferred flavor of Adjusting.XML.Result, if resultClass is
		 * Adjusting.XML.Result, otherwise the corresponding flavor of ordinary
		 * Result.
		 */
		@SuppressWarnings("unchecked")
		protected <T extends Result> Class<? extends T> preferredResultClass(
			Class<T> resultClass)
		{
			return Adjusting.XML.Result.class == resultClass
				? (Class<? extends T>)AdjustingSAXResult.class
				: (Class<? extends T>)SAXResult.class;
		}

		/*
		 * Internal version for use in the implementation of
		 * AdjustingSourceResult, when 'officially' the instance is no longer
		 * writable (because backingAndClearWritable was called in obtaining the
		 * AdjustingSourceResult itself).
		 */
		private <T extends Result> T setResult(
			VarlenaWrapper.Output vwo, Class<T> resultClass)
			throws SQLException
		{
			Class<? extends T> rc = resultClass;

			if ( null == rc
				|| Result.class == rc
				|| Adjusting.XML.Result.class == rc )
				rc = preferredResultClass(rc);

			try
			{
				if ( rc.isAssignableFrom(StreamResult.class)
					|| rc.isAssignableFrom(AdjustingStreamResult.class)
				   )
				{
					/*
					 * As with AdjustingSAXSource, defaults() cannot be called
					 * here, but must be deferred in case schema() is called.
					 */
					AdjustingStreamResult sr =
						new AdjustingStreamResult(vwo, m_serverCS);
					if ( Adjusting.XML.Result.class
							.isAssignableFrom(rc) )
						return rc.cast(sr);
					return rc.cast(sr.get());
				}

				/*
				 * This special case must defer setting the verifier; a later
				 * call to this method with a different result class will be
				 * made, setting it then.
				 */
				if ( rc.isAssignableFrom(AdjustingSourceResult.class) )
				{
					return rc.cast(
						new AdjustingSourceResult(this, m_serverCS));
				}

				/*
				 * The remaining cases all can use the NoOp verifier.
				 */
				vwo.setVerifier(VarlenaWrapper.Verifier.NoOp.INSTANCE);
				OutputStream os = vwo;
				Writer w;

				if ( rc.isAssignableFrom(SAXResult.class)
					|| rc.isAssignableFrom(AdjustingSAXResult.class) )
				{
					SAXTransformerFactory saxtf = (SAXTransformerFactory)
						SAXTransformerFactory.newInstance();
					TransformerHandler th = saxtf.newTransformerHandler();
					th.getTransformer().setOutputProperty(
						ENCODING, m_serverCS.name());
					os = new DeclCheckedOutputStream(os, m_serverCS);
					w = new OutputStreamWriter(os, m_serverCS.newEncoder());
					th.setResult(new StreamResult(w));
					th = SAXResultAdapter.newInstance(th, w);
					SAXResult sr = new SAXResult(th);
					if ( Adjusting.XML.Result.class
							.isAssignableFrom(rc) )
						return rc.cast(new AdjustingSAXResult(sr));
					return rc.cast(sr);
				}

				if ( rc.isAssignableFrom(StAXResult.class) )
				{
					XMLOutputFactory xof = XMLOutputFactory.newInstance();
					os = new DeclCheckedOutputStream(os, m_serverCS);
					XMLStreamWriter xsw = xof.createXMLStreamWriter(
						os, m_serverCS.name());
					xsw = new StAXResultAdapter(xsw, os);
					return rc.cast(new StAXResult(xsw));
				}

				if ( rc.isAssignableFrom(DOMResult.class) )
				{
					return rc.cast(m_domResult = new DOMResult());
				}
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}

			throw new SQLFeatureNotSupportedException(
				"No support for SQLXML.setResult(" +
				rc.getName() + ".class)", "0A000");
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
				Writer w = new OutputStreamWriter(os, m_serverCS.newEncoder());
				StreamResult rlt = new StreamResult(w);
				t.transform(src, rlt);
				w.close();
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		protected VarlenaWrapper adopt(int oid) throws SQLException
		{
			VarlenaWrapper.Output vwo =
				(VarlenaWrapper.Output)s_backingVH.getAndSet(this, null);
			if ( (boolean)s_writableVH.getAcquire(this) )
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
				(boolean)s_writableVH.getAcquire() ? "" : "not ");
		}
	}

	static class Verifier extends VarlenaWrapper.Verifier.Base
	{
		private XMLReader m_xr;

		/**
		 * Constructor called only from {@code adopt()} when an untouched
		 * {@code Readable} is being bounced back to PostgreSQL with a type Oid
		 * different from its original.
		 */
		Verifier() throws SQLException
		{
			try
			{
				/*
				 * Safe to pass false for wrapping; whether the input is wrapped
				 * or not, the verifying parser will have no need to unwrap.
				 */
				m_xr = new AdjustingSAXSource(null, false)
					.defaults().get().getXMLReader();
			}
			catch ( SAXException e )
			{
				throw normalizedException(e);
			}
		}

		/**
		 * Constructor called with an already-constructed {@code XMLReader}.
		 *<p>
		 * Adjustments may have been made to the {@code XMLReader}.
		 */
		Verifier(XMLReader xr)
		{
			m_xr = xr;
		}

		@Override
		protected void verify(InputStream is) throws Exception
		{
			boolean[] wrapped = { false };
			is = correctedDeclStream(
				is, false, implServerCharset(), wrapped);

			/*
			 * The supplied XMLReader is never set up to do unwrapping, which is
			 * ok; it never needs to. But it will have had its error handler set
			 * on that assumption, which must be changed here if wrapping is in
			 * effect, just in case schema validation has been requested.
			 */
			if ( wrapped[0] )
				m_xr.setErrorHandler(SAXDOMErrorHandler.instance(true));

			/*
			 * What does an XMLReader do if no handlers have been set for
			 * content events? Parses everything and discards the events.
			 * Just what you'd want for a verifier.
			 */
			m_xr.parse(new InputSource(is));
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
							{
								check();
								break;
							}
						}
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
			m_probe.finish();
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
			return new IOException("Malformed XML: " + e.getMessage(), e);
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
	 * on what it expects, but testing has shown the JRE-bundled identity
	 * transformer, at least, to accept the input and faithfully reproduce the
	 * non-document content.
	 */
	static class SAXUnwrapFilter extends XMLFilterImpl implements LexicalHandler
	{
		private int m_nestLevel = 0;
		private WhitespaceAccumulator m_wsAcc = new WhitespaceAccumulator();
		private boolean m_topElementSeen = false;
		private boolean m_couldBeDocument = true;

		SAXUnwrapFilter(XMLReader parent)
		{
			super(parent);
		}

		@Override
		public void endDocument() throws SAXException
		{
			if ( m_couldBeDocument  &&  ! m_topElementSeen )
				commitToContent();
			super.endDocument();
		}

		@Override
		public void startElement(
			String uri, String localName, String qName, Attributes atts)
			throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
			{
				if ( m_topElementSeen ) // a second top-level element?
					commitToContent();  // ==> has to be CONTENT.
				else
					m_wsAcc.discard();
				m_topElementSeen = true;
			}

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

		@Override
		public void characters(char[] ch, int start, int length)
			throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
			{
				int mismatchIndex = m_wsAcc.accumulate(ch, start, length);
				if ( -1 == mismatchIndex )
					return;
				commitToContent(); // they weren't all whitespace ==> CONTENT.
				start = mismatchIndex;
			}
			super.characters(ch, start, length);
		}

		@Override
		public void processingInstruction(String target, String data)
			throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
				m_wsAcc.discard();
			super.processingInstruction(target, data);
		}

		@Override
		public void skippedEntity(String name) throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
				commitToContent(); // an entity at the top level? CONTENT.
			super.skippedEntity(name);
		}

		/**
		 * Called whenever, at "top level" (really nesting level 1, inside our
		 * wrapping element), a parse event that could not appear there in
		 * {@code XML(DOCUMENT)} form is encountered.
		 *<p>
		 * Forces {@code m_couldBeDocument} false, and disburses any whitespace
		 * that may be held in the accumulator.
		 *<p>
		 * The occurrence of a parse event that <em>could</em> occur in the
		 * {@code XML(DOCUMENT)} form should be handled not by calling this
		 * method, but by simply discarding any held whitespace instead.
		 */
		private void commitToContent() throws SAXException
		{
			char[] buf = new char [ WhitespaceAccumulator.MAX_RUN ];
			int length;
			m_couldBeDocument = false;
			while ( 0 < (length = m_wsAcc.disburse(buf)) )
				super.characters(buf, 0, length);
		}

		/*
		 * Implementation of the LexicalHandler interface (and the property
		 * interception to set and retrieve the handler). No help from
		 * XMLFilterImpl there.
		 */

		private static final LexicalHandler s_dummy = new DefaultHandler2();
		private LexicalHandler m_consumersLexHandler;
		private LexicalHandler m_realLexHandler;
		private boolean m_lexHandlerIsRegistered = false;

		@Override
		public void setContentHandler(ContentHandler handler)
		{
			super.setContentHandler(handler);
			if ( m_lexHandlerIsRegistered )
				return;

			/*
			 * The downstream consumer might never register a LexicalHandler of
			 * its own, but those events still matter here, so trigger the
			 * registration of 'this' if necessary.
			 */
			try
			{
				setProperty(SAX2PROPERTY.LEXICAL_HANDLER.propertyUri(),
					m_consumersLexHandler);
			}
			catch ( SAXException e )
			{
			}
		}

		@Override
		public Object getProperty(String name)
			throws SAXNotRecognizedException, SAXNotSupportedException
		{
			if ( SAX2PROPERTY.LEXICAL_HANDLER.propertyUri().equals(name) )
				return m_consumersLexHandler;
			return super.getProperty(name);
		}

		@Override
		public void setProperty(String name, Object value)
			throws SAXNotRecognizedException, SAXNotSupportedException
		{
			if ( SAX2PROPERTY.LEXICAL_HANDLER.propertyUri().equals(name) )
			{
				if ( ! SAX2PROPERTY.LEXICAL_HANDLER.valueOk(value) )
					throw new SAXNotSupportedException(name);
				/*
				 * Make sure 'this' is registered as the upstream parser's
				 * lexical handler, done here to avoid publishing 'this' early
				 * from the constructor, and also to make sure the consumer gets
				 * an appropriate exception if it doesn't work for some reason.
				 */
				if ( ! m_lexHandlerIsRegistered )
				{
					super.setProperty(name, this);
					m_lexHandlerIsRegistered = true;
				}
				m_consumersLexHandler = (LexicalHandler)value;
				m_realLexHandler =
					null != value ? m_consumersLexHandler : s_dummy;
				return;
			}
			super.setProperty(name, value);
		}

		@Override
		public void startDTD(String name, String publicId, String systemId)
			throws SAXException
		{
			assert false; // this filter is never used on input with a DTD
		}

		@Override
		public void endDTD() throws SAXException
		{
			assert false; // this filter is never used on input with a DTD
		}

		@Override
		public void startEntity(String name) throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
				commitToContent();
			m_realLexHandler.startEntity(name);
		}

		@Override
		public void endEntity(String name) throws SAXException
		{
			m_realLexHandler.endEntity(name);
		}

		@Override
		public void startCDATA() throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
				commitToContent();
			m_realLexHandler.startCDATA();
		}

		@Override
		public void endCDATA() throws SAXException
		{
			m_realLexHandler.startCDATA();
		}

		@Override
		public void comment(char[] ch, int start, int length)
			throws SAXException
		{
			if ( m_couldBeDocument  &&  1 == m_nestLevel )
				m_wsAcc.discard();
			m_realLexHandler.comment(ch, start, length);
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
		private Writer m_w;
		private TransformerHandler m_th;
		private SAXResultAdapter(TransformerHandler th, Writer w)
		{
			m_w = w;
			m_th = th;
			setContentHandler(th);
			setDTDHandler(th);
		}

		static TransformerHandler newInstance(
				TransformerHandler th, Writer w)
		{
			return new SAXResultAdapter(th, w);
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
				m_w.close();
			}
			catch ( IOException ioe )
			{
				throw new SAXException("Failure closing SQLXML SAXResult", ioe);
			}
			m_w = null;
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
		private WhitespaceAccumulator m_wsAcc = new WhitespaceAccumulator();
		private boolean m_topElementSeen = false;
		private boolean m_couldBeDocument = true;
		private int m_disbursed = 0;
		private int m_disburseOffset = 0;
		private char[] m_disburseBuffer;
		private int m_tailFrom = -1;
		private Matcher m_allWhiteSpace = s_entirelyWS.matcher("");

		StAXUnwrapFilter(XMLStreamReader reader)
		{
			super(reader);
		}

		/**
		 * Wrap upstream {@code hasNext} to account for possible accumulated
		 * whitespace being disbursed.
		 *<p>
		 * This method and {@code wrappedNext} are responsible for the
		 * illusion of additional {@code CHARACTERS} events before the next real
		 * upstream event, if there was accumulated whitespace that is now being
		 * disbursed because the input has been determined to have
		 * {@code CONTENT} form.
		 */
		private boolean wrappedHasNext() throws XMLStreamException
		{
			/*
			 * If we are currently looking at a 'disburse' buffer, return true;
			 * the next event will be either another disburse buffer from the
			 * accumulator, or the upstream event that's still under the cursor.
			 * That one is either a CHARACTERS event (from which some tail
			 * characters still need to be emitted), or whatever following event
			 * triggered the commitToContent.
			 *
			 * Otherwise, defer to the upstream's hasNext().
			 */
			if ( 0 < m_disbursed )
				return true;

			return super.hasNext();
		}

		/**
		 * Wrap upstream {@code next} to account for possible accumulated
		 * whitespace being disbursed.
		 *<p>
		 * This method and {@code wrappedHasNext} are responsible for the
		 * illusion of additional {@code CHARACTERS} events before the next real
		 * upstream event, if there was accumulated whitespace that is now being
		 * disbursed because the input has been determined to have
		 * {@code CONTENT} form.
		 */
		private int wrappedNext() throws XMLStreamException
		{
			/*
			 * If we are currently looking at a 'disburse' buffer and there is
			 * another one, get that one and return CHARACTERS. If there isn't,
			 * and m_tailFrom is -1, then the event now under the upstream
			 * cursor is the one that triggered the commitToContent; return its
			 * event type. A nonnegative m_tailFrom indicates that the event
			 * under the cursor is still the CHARACTERS event that turned out
			 * not to be all whitespace, and still has a tail of characters to
			 * emit. Store a reference to its upstream array in m_disburseBuffer
			 * and the proper offset and length values to fake it up as one last
			 * disburse array; this requires less work in the many other methods
			 * that must be overridden to sustain the illusion. Set m_tailFrom
			 * to another negative value in that case (-2), to be replaced with
			 * -1 on the next iteration and returning to your regularly
			 * scheduled programming.
			 *
			 * Otherwise, defer to the upstream's next().
			 */
			if ( 0 < m_disbursed )
			{
				m_disbursed = m_wsAcc.disburse(m_disburseBuffer);
				if ( 0 < m_disbursed )
					return CHARACTERS;
				if ( -1 == m_tailFrom )
					return super.getEventType();
				if ( 0 <= m_tailFrom )
				{
					m_disburseBuffer = super.getTextCharacters();
					m_disburseOffset = super.getTextStart() + m_tailFrom;
					m_disbursed = super.getTextLength() - m_tailFrom;
					m_tailFrom = -2;
					return CHARACTERS;
				}
				m_tailFrom = -1;
				m_disburseBuffer = null;
				m_disburseOffset = m_disbursed = 0;
			}

			return super.next();
		}

		@Override
		public boolean hasNext() throws XMLStreamException
		{
			if ( m_hasPeeked )
				return true;

			while ( wrappedHasNext() )
			{
				/*
				 * Set hasPeeked = true *just before* peeking. Even if next()
				 * throws an exception, hasNext() must be idempotent: another
				 * call shouldn't try another next(), which could advance
				 * the cursor to the wrong location for the error.
				 */
				m_hasPeeked = true;
				switch ( wrappedNext() )
				{
				case START_ELEMENT:
					if ( m_couldBeDocument  &&  1 == m_nestLevel )
					{
						if ( m_topElementSeen )
						{
							commitToContent();
							if ( 0 < m_disbursed )
								return true; // no nestLevel++; we'll be back
						}
						else
							m_wsAcc.discard();
						m_topElementSeen = true;
					}
					if ( 0 < m_nestLevel++ )
						return true;
					continue;

				case END_ELEMENT:
					if ( 0 < --m_nestLevel )
						return true;
					continue;

				case END_DOCUMENT:
					if ( m_couldBeDocument  &&  ! m_topElementSeen )
						commitToContent();
					return true;

				case CHARACTERS:
					if ( m_couldBeDocument  &&  1 == m_nestLevel )
					{
						int mismatchIndex = m_wsAcc.accumulate(
							super.getTextCharacters(),
							super.getTextStart(), super.getTextLength());
						if ( -1 == mismatchIndex )
							continue;
						commitToContent();
						m_tailFrom = mismatchIndex;
					}
					return true;

				case COMMENT:
				case PROCESSING_INSTRUCTION:
					if ( m_couldBeDocument  &&  1 == m_nestLevel )
						m_wsAcc.discard();
					return true;

				case CDATA:
				case ENTITY_REFERENCE:
					if ( m_couldBeDocument  &&  1 == m_nestLevel )
						commitToContent();
					return true;

				default:
					return true;
				}
			}

			m_hasPeeked = false;
			return false;
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
		public int getEventType()
		{
			if ( 0 < m_disbursed )
				return CHARACTERS;
			return super.getEventType();
		}

		private void commitToContent()
		{
			char[] buf = new char [ WhitespaceAccumulator.MAX_RUN ];
			int got = m_wsAcc.disburse(buf);
			m_couldBeDocument = false;
			if ( 0 == got )
				return;
			m_disburseBuffer = buf;
			m_disbursed = got;
		}

		/*
		 * The methods specific to CHARACTERS events must be overridden here
		 * to handle 'extra' CHARACTERS events after commitToContent. That's
		 * the bare-metal ones getTextCharacters, getTextStart, getTextLength
		 * for sure, but also getText and the copying getTextCharacters, because
		 * the StAX API spec does not guarantee that those are implemented with
		 * virtual calls to the bare ones.
		 */

		@Override
		public char[] getTextCharacters()
		{
			if ( 0 < m_disbursed )
				return m_disburseBuffer;
			return super.getTextCharacters();
		}

		@Override
		public int getTextStart()
		{
			if ( 0 < m_disbursed )
				return m_disburseOffset;
			return super.getTextStart();
		}

		@Override
		public int getTextLength()
		{
			if ( 0 < m_disbursed )
				return m_disbursed;
			return super.getTextLength();
		}

		@Override
		public String getText()
		{
			if ( 0 < m_disbursed )
				return new String(
					m_disburseBuffer, m_disburseOffset, m_disbursed);
			return super.getText();
		}

		@Override
		public int getTextCharacters(
			int sourceStart, char[] target, int targetStart, int length)
			throws XMLStreamException
		{
			int internalStart = getTextStart();
			int internalLength = getTextLength();
			if ( sourceStart < 0 ) // arraycopy might not catch this, check here
				throw new IndexOutOfBoundsException();
			internalStart += sourceStart;
			internalLength -= sourceStart;
			if ( length > internalLength )
				length = internalLength;
			System.arraycopy( // let arraycopy do the other index checks
				getTextCharacters(), internalStart,
				target, targetStart, length);
			return length;
		}

		/*
		 * But wait, there's more: some methods that are valid in "All States"
		 * need adjustments to play along with 'inserted' CHARACTERS events.
		 */

		@Override
		public void require(int type, String namespaceURI, String localName)
			throws XMLStreamException
		{
			if ( 0 < m_disbursed )
				if ( CHARACTERS != type
					|| null != namespaceURI || null != localName )
					throw new XMLStreamException(
						"Another event expected, parsed CHARACTERS");
			super.require(type, namespaceURI, localName);
		}

		@Override
		public String getNamespaceURI()
		{
			if ( 0 < m_disbursed )
				return null;
			return super.getNamespaceURI();
		}

		@Override
		public boolean isStartElement()
		{
			if ( 0 < m_disbursed )
				return false;
			return super.isStartElement();
		}

		@Override
		public boolean isEndElement()
		{
			if ( 0 < m_disbursed )
				return false;
			return super.isEndElement();
		}

		@Override
		public boolean isCharacters()
		{
			if ( 0 < m_disbursed )
				return true;
			return super.isCharacters();
		}

		@Override
		public boolean isWhiteSpace()
		{
			if ( 0 == m_disbursed )
				return super.isWhiteSpace();
			/*
			 * If you are about to change the below to a simple 'return true'
			 * because things are disbursed by the WhitespaceAccumulator, don't
			 * forget that one last 'disbursement' can be faked up containing
			 * the tail of the CHARACTERS event that was not all whitespace.
			 */
			CharBuffer cb = CharBuffer.wrap(
				m_disburseBuffer, m_disburseOffset, m_disbursed);
			m_allWhiteSpace.reset(cb);
			boolean result = m_allWhiteSpace.matches();
			m_allWhiteSpace.reset("");
			return result;
		}

		@Override
		public boolean hasText()
		{
			if ( 0 < m_disbursed )
				return true;
			return super.hasText();
		}

		@Override
		public boolean hasName()
		{
			if ( 0 < m_disbursed )
				return false;
			return super.hasName();
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

		/*
		 * It ain't over till it's over: the methods that must throw
		 * IllegalStateException when positioned on a CHARACTERS event
		 * must do so on an 'inserted' one also.
		 */

		private void illegalForCharacters()
		{
			if ( 0 < m_disbursed )
				throw new IllegalStateException(
					"XML parsing method inappropriate for a CHARACTERS event.");
		}

		@Override
		public String getAttributeValue(String namespaceURI, String localName)
		{
			illegalForCharacters();
			return super.getAttributeValue(namespaceURI, localName);
		}

		@Override
		public int getAttributeCount()
		{
			illegalForCharacters();
			return super.getAttributeCount();
		}

		@Override
		public QName getAttributeName(int index)
		{
			illegalForCharacters();
			return super.getAttributeName(index);
		}

		@Override
		public String getAttributeNamespace(int index)
		{
			illegalForCharacters();
			return super.getAttributeNamespace(index);
		}

		@Override
		public String getAttributeLocalName(int index)
		{
			illegalForCharacters();
			return super.getAttributeLocalName(index);
		}

		@Override
		public String getAttributePrefix(int index)
		{
			illegalForCharacters();
			return super.getAttributePrefix(index);
		}

		@Override
		public String getAttributeType(int index)
		{
			illegalForCharacters();
			return super.getAttributeType(index);
		}

		@Override
		public String getAttributeValue(int index)
		{
			illegalForCharacters();
			return super.getAttributeValue(index);
		}

		@Override
		public boolean isAttributeSpecified(int index)
		{
			illegalForCharacters();
			return super.isAttributeSpecified(index);
		}

		@Override
		public int getNamespaceCount()
		{
			illegalForCharacters();
			return super.getNamespaceCount();
		}

		@Override
		public String getNamespacePrefix(int index)
		{
			illegalForCharacters();
			return super.getNamespacePrefix(index);
		}

		@Override
		public String getNamespaceURI(int index)
		{
			illegalForCharacters();
			return super.getNamespaceURI(index);
		}

		@Override
		public QName getName()
		{
			illegalForCharacters();
			return super.getName();
		}

		@Override
		public String getLocalName()
		{
			illegalForCharacters();
			return super.getLocalName();
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
			m_xsw.writeStartElement(prefix, localName, namespaceURI);
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
			m_xsw.writeEmptyElement(prefix, localName, namespaceURI);
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
	 * Accumulate whitespace at top level (outside any element) pending
	 * determination of what to do with it.
	 *<p>
	 * The handling of whitespace at the top level is a subtle business. Per the
	 * XML spec "Character Data and Markup" section (in either spec version),
	 * whitespace is considered, when at the top level, "markup" rather than
	 * "character data". And the section on "White Space Handling" spells out
	 * that an XML processor "MUST always pass all characters in a document that
	 * are not markup through to the application." A sharp-eyed language lawyer
	 * will see right away that whitespace at the top level does not fall
	 * under that mandate. (It took me longer.) Indeed, a bit of experimenting
	 * with a SAX parser will show that it doesn't invoke any handler callbacks
	 * at all for whitespace at the top level. The whitespace could as well not
	 * even be there. Some applications rely on that and will report an error if
	 * the parser shows them any whitespace outside the element they expect.
	 *<p>
	 * Our application of a wrapping element, to avoid parse errors for the
	 * {@code XML(CONTENT)} form, alters the treatment of whitespace that would
	 * otherwise have been at the top level. As it will now be inside of
	 * an element, Java's parser will want to pass it on, and our unwrap filter
	 * will have to fix that.
	 *<p>
	 * Complicating matters, our determination whether to apply a wrapping
	 * element is lazy. It looks only far enough into the start of the stream
	 * to conclude one of: (1) it is definitely {@code XML(DOCUMENT)},
	 * (2) it is definitely {@code XML(CONTENT)}, or (3) it could be either and
	 * has to be be wrapped in case it turns out to be {@code XML(CONTENT)}.
	 *<p>
	 * The first two cases are simple. In case (1), we apply no wrapping and
	 * no filter, and the underlying parser does the right thing. In case (2)
	 * we know this is not a document, and no whitespace should be filtered out.
	 *<p>
	 * Case (3) is the tricky one, and as long as PostgreSQL does not store any
	 * {@code DOCUMENT}/{@code CONTENT} flag with the value and we have no API
	 * for the application to say what's expected, unless we are willing to
	 * pre-parse what could end up being the whole stream just to decide how
	 * to parse it, we'll have to settle for an approximate behavior.
	 *<p>
	 * What's implemented here is to handle character data reported by the
	 * parser, if it is at "top level" (within our added wrapping element), by
	 * accumulating any whitespace here until we see what comes next.
	 *<p>
	 * This must be applied above the parser (that is, to character events that
	 * the parser reports), because it applies the XML definition of whitespace,
	 * which includes only the four characters " \t\n\r" but recognizes them
	 * <em>after</em> the parser has normalized various newline styles to '\n'.
	 * The exact set of those newline styles depends on the XML version, and the
	 * XML 1.1 set includes non-ASCII characters and therefore depend on the
	 * parser's knowledge of the input stream encoding.
	 *<p>
	 * If the character data includes anything other than whitespace, we emit
	 * it intact including the whitespace, and note that the input is now known
	 * to be {@code CONTENT} and gets no more special whitespace treatment.
	 *<p>
	 * If all whitespace, and followed by the end of input or by an element that
	 * is <em>not</em> the first one to be seen, we emit it intact and turn off
	 * special whitespace handling for the remainder of the stream (if any).
	 *<p>
	 * If all whitespace and followed by a comment, PI, or the first element
	 * to be seen it is discarded.
	 *<p>
	 * This strategy will produce correct results for any case (3) input that
	 * turns out to be {@code XML(DOCUMENT)}. In the case of input that turns
	 * out to be {@code XML(CONTENT)}, it can fail to preserve whitespace ahead
	 * of the first point where the input is definitely known to be
	 * {@code CONTENT}.
	 *<p>
	 * That may be good enough for many cases. To cover those where it isn't,
	 * it may be necessary to offer a nonstandard API to specify what the
	 * application expects, or observe the PostgreSQL {@code XMLOPTION} setting
	 * in case 3, or both.
	 */
	static class WhitespaceAccumulator
	{
		/**
		 * A Pattern to walk through some character data in runs of the same
		 * whitespace character, allowing a rudimentary run-length encoding.
		 */
		static final Pattern s_wsChunk = Pattern.compile(
			"\\G([ \\t\\n\\r])\\1*+(?![^ \\t\\n\\r])");

		private static final char[] s_runValToChar = {' ', '\t', '\n', '\r'};
		static final int MAX_RUN = 1 + (0xff >>> 2);

		byte[] m_rleBuffer = new byte [ 8 ];
		int m_bufPos = 0;
		int m_disbursePos = 0;
		Matcher m_matcher = s_wsChunk.matcher("");

		/**
		 * Given an array with reported character data, return -1 if exclusively
		 * whitespace characters were seen and have been added to the
		 * accumulator; otherwise return an index into the input array from
		 * which the caller should emit the tail unprocessed after using
		 * {@link #disburse disburse} here to emit any earlier-accumulated
		 * whitespace.
		 *<p>
		 * Java's XML parsing APIs generally do not promise to supply all
		 * characters of contiguous text in one parse event, so this method
		 * may be called more than once accumulating whitespace from several
		 * consecutive events.
		 */
		int accumulate(char[] content, int start, int length)
		{
			CharBuffer cb = CharBuffer.wrap(content, start, length);
			int tailPos = 0;

			m_matcher.reset(cb);
			while ( m_matcher.find() )
			{
				tailPos = m_matcher.end();
				char c = m_matcher.group(1).charAt(0);
				int runVal = (c & 3) | (c >>> 1 & 2); // index in s_runValToChar
				int runLength = tailPos - m_matcher.start();

				newRun();
				while ( runLength > MAX_RUN )
				{
					m_rleBuffer [m_bufPos - 1] =
						(byte)(runVal | (MAX_RUN - 1) << 2);
					runLength -= MAX_RUN;
					newRun();
				}
				m_rleBuffer [m_bufPos - 1] =
					(byte)(runVal | (runLength - 1) << 2);
			}

			m_matcher.reset(""); // don't hold a reference to caller's array
			if ( tailPos == length )
				return -1;
			return start + tailPos;
		}

		private final void newRun()
		{
			++ m_bufPos;
			if ( m_rleBuffer.length == m_bufPos )
				m_rleBuffer = Arrays.copyOf(m_rleBuffer, 2*m_rleBuffer.length);
		}

		/**
		 * Retrieve the accumulated whitespace if it is not to be discarded.
		 *<p>
		 * If the caller detects that the whitespace is significant (either
		 * because {@link #accumulate accumulate} returned a nonnegative result
		 * or because the next parse event was a second top-level element or
		 * the end-event of the wrapping element), the caller should allocate
		 * a {@code char} array of length at least {@code MAX_RUN} and supply it
		 * to this method until zero is returned; for each non-zero value
		 * returned, that many {@code char}s at the head of the array should be
		 * passed to the application as a character event.
		 *<p>
		 * After this method has returned zero, if the caller had received a
		 * non-negative result from {@code accumulate}, it should present one
		 * more character event to the application, containing the tail of the
		 * the array that was given to {@code accumulate}, starting at the index
		 * {@code accumulate} returned.
		 */
		int disburse(char[] into)
		{
			assert into.length >= MAX_RUN;
			if ( m_disbursePos == m_bufPos )
			{
				m_bufPos = m_disbursePos = 0;
				return 0;
			}
			int runVal = m_rleBuffer [ m_disbursePos ] & 3;
			int runLength = 1 + ((m_rleBuffer [ m_disbursePos ] & 0xff) >>> 2);
			++ m_disbursePos;
			char c = s_runValToChar [ runVal ];
			Arrays.fill(into, 0, runLength, c);
			return runLength;
		}

		/**
		 * Discard the accumulated whitespace.
		 *<p>
		 * This should be called if some whitespace was successfully accumulated
		 * ({@code accumulate} returned -1) but the following parse event is one
		 * that must be passed to the application and does not force the input
		 * to be classified as {@code XML(CONTENT)}.
		 */
		void discard()
		{
			m_bufPos = m_disbursePos = 0;
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
			TRAILING, END, MATCHED, UNMATCHED, UNMATCHEDCHAR, ABANDONED
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
		/*
		 * Contains, if m_state is UNMATCHEDCHAR, a single, non-ASCII char that
		 * followed whatever ASCII bytes may be saved in m_save.
		 */
		private char m_savedChar;

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
			case UNMATCHEDCHAR: // can't happen; fall into ABANDONED if it does
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

		/**
		 * Version of {@link take(byte)} for use when input is coming from a
		 * character stream.
		 *<p>
		 * Exploits (again) the assumption that in all encodings of interest,
		 * the characters in a decl will have the values they have in ASCII, and
		 * the fact that ASCII characters are all encoded in the low 7 bits
		 * of chars.
		 *<p>
		 * Unlike {@link take(byte)}, this method will not accept further input
		 * after it has returned {@code false} once. A caller should not mix
		 * calls to this method and {@link take(byte)}.
		 * @param c The next char of the stream.
		 * @return True if more input is needed to fully parse a decl or be sure
		 * that none is present; false when enough input has been seen.
		 * @throws SQLDataException If a partial or malformed decl is found.
		 * @throws IllegalStateException if called again after returning false.
		 */
		boolean take(char c) throws SQLException
		{
			byte b = (byte)(c & 0x7f);
			switch ( m_state )
			{
			case START:
				if ( b == c )
					return take(b);
				m_savedChar = c;
				m_state = State.UNMATCHEDCHAR;
				return false;
			case ABANDONED:
			case MATCHED:
			case UNMATCHED:
			case UNMATCHEDCHAR:
				throw new IllegalStateException("too many take(char) calls");
			default:
				if ( b == c )
					return take(b);
			}
			return take((byte)-1); // will throw appropriate SQLDataException
		}

		private boolean isSpace(byte b)
		{
			return (0x20 == b) || (0x09 == b) || (0x0D == b) || (0x0A == b);
		}

		/**
		 * Call after the last call to {@code take} before examining results.
		 */
		void finish() throws SQLException
		{
			switch ( m_state )
			{
			case ABANDONED:
			case MATCHED:
			case UNMATCHED:
			case UNMATCHEDCHAR:
				return;
			case START:
				if ( 0 == m_idx )
				{
					m_state = State.UNMATCHED;
					return;
				}
			/* FALLTHROUGH */
			default:
			}
			throw new SQLDataException(
				"XML begins with an incomplete declaration", "2200N");
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

		char[] charPrefix(Charset serverCharset) throws IOException
		{
			byte[] bpfx = prefix(serverCharset);
			char[] cpfx = new char [
				bpfx.length + (State.UNMATCHEDCHAR == m_state ? 1 : 0) ];
			int i = 0;
			/*
			 * Again the assumption that all supported encodings will match
			 * ASCII for the characters of the decl.
			 */
			for ( byte b : bpfx )
				cpfx [ i++ ] = (char)(b&0x7f);
			if ( i < cpfx.length )
				cpfx [ i ] = m_savedChar;
			return cpfx;
		}

		/**
		 * Return the number of bytes at the end of the {@code prefix} result
		 * that represent readahead, rather than being part of the decl.
		 */
		int readaheadLength()
		{
			/*
			 * If the probing was done as chars, because of the more restrictive
			 * behavior of take(char), the readahead length can be exactly one,
			 * only if the state is UNMATCHEDCHAR, and will otherwise be zero.
			 */
			if ( State.UNMATCHEDCHAR == m_state )
				return 1;
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

			if ( ! strict  ||  "UTF-8".equals(serverCharset.name()) )
				return;
			throw new SQLDataException(
				"XML does not declare a character set, and server encoding " +
				"is not UTF-8", "2200N");
		}

		String queryEncoding() throws SQLException
		{
			if ( State.MATCHED == m_state )
			{
				if ( m_encodingEnd <= m_encodingStart )
					return null;
				byte[] parseResult = m_save.toByteArray();
				return new String(parseResult,
					m_encodingStart, m_encodingEnd - m_encodingStart,
					US_ASCII);
			}
			return null;
		}
	}

	/**
	 * Encapsulation of how to copy from one {@code SQLXML} to another.
	 *<p>
	 * In the case of a source {@code SQLXML} object that prefers to present its
	 * content as a {@code StreamSource}, obtain an instance with
	 * {@code copierFor}, passing the target {@code SQLXML} instance, the server
	 * character set and the encoding name peeked from any declaration at the
	 * front of the source stream. Then supply the {@code DeclProbe} object
	 * representing the peeked initial content, and the {@code InputStream} or
	 * {@code Reader} representing the rest of the source content, to the
	 * appropriate {@code prepare} method. The copy is completed by calling
	 * {@link #finish finish}.
	 *<p>
	 * Between {@code prepare} and {@code finish}, parser restrictions can be
	 * adjusted if needed, using the {@link Adjusting.XML.Source} API on the
	 * object returned by {@link #getAdjustable getAdjustable}.
	 *<p>
	 * For the cases of {@code SQLXML} objects that present their content as
	 * {@code SAXSource}, {@code StAXSource}, or {@code DOMSource}, there are
	 * no {@code prepare} methods, and {@code getAdjustable} returns a dummy
	 * object that doesn't adjust anything. When the source presents XML content
	 * in already-parsed form, there are no parser restrictions to adjust.
	 */
	static abstract class XMLCopier
	{
		protected Writable m_tgt;

		protected XMLCopier(Writable tgt)
		{
			m_tgt = tgt;
		}

		Adjusting.XML.Source getAdjustable()
		{
			return AdjustingSAXSource.Dummy.INSTANCE;
		}

		static abstract class Stream extends XMLCopier
		{
			protected Adjusting.XML.Source<SAXSource> m_adjustable;

			protected Stream(Writable tgt)
			{
				super(tgt);
			}

			@Override
			Adjusting.XML.Source getAdjustable()
			{
				return m_adjustable;
			}

			abstract XMLCopier prepare(DeclProbe probe, InputStream is)
			throws IOException, SQLException;

			abstract XMLCopier prepare(DeclProbe probe, Reader r)
			throws IOException, SQLException;
		}

		/**
		 * Return an {@code XMLCopier} that can copy a stream source that
		 * declares an encoding name <em>srcCSName</em> to a target whose
		 * character set is <em>tgtCS</em> (which is here strongly assumed to
		 * be the PostgreSQL server charset, so will not need to be remembered
		 * in the created {@code XMLStreamCopier}).
		 */
		static Stream copierFor(
			Writable tgt, Charset tgtCS, String srcCSName)
			throws SQLException
		{
			if ( null == srcCSName )
				srcCSName = "UTF-8";

			if ( tgtCS.name().equalsIgnoreCase(srcCSName) )
				return new Direct(tgt);

			Charset srcCS;
			try
			{
				srcCS = Charset.forName(srcCSName);
			}
			catch ( IllegalArgumentException e )
			{
				throw new SQLDataException(
					"XML declares unsupported encoding \"" + srcCSName + "\"",
					"2200N");
			}
			if ( tgtCS.equals(srcCS) )
				return new Direct(tgt);
			if ( tgtCS.contains(srcCS) )
				return new Transcoding(tgt, srcCS);
			return new Transforming(tgt, srcCS);
		}

		abstract Writable finish() throws IOException, SQLException;

		/**
		 * Copier usable when source and target encodings are the same.
		 */
		static class Direct extends Stream
		{
			/* Exactly one of m_is, m_rdr must be non-null */
			private InputStream m_is;
			private Reader m_rdr;
			private DeclProbe m_probe;
			private AdjustingStreamResult m_asr;

			protected Direct(Writable tgt)
			{
				super(tgt);
			}

			@Override
			XMLCopier prepare(DeclProbe probe, InputStream is)
			throws SQLException
			{
				m_is = is;
				return prepare(probe, (Reader)null);
			}

			@Override
			XMLCopier prepare(DeclProbe probe, Reader r)
			throws SQLException
			{
				m_rdr = r;
				m_probe = probe;
				m_asr = m_tgt.setResult(
					m_tgt.backingIfNotFreed(),
					AdjustingStreamResult.class);
				m_adjustable = m_asr.theVerifierSource(false);
				return this;
			}

			@Override
			Writable finish() throws IOException, SQLException
			{
				if ( null != m_is )
				{
					OutputStream os =
						m_asr.preferBinaryStream().get().getOutputStream();
					os.write(m_probe.prefix(null));
					byte[] b = new byte [ 8192 ];
					int got;
					while ( -1 != (got = m_is.read(b)) )
						os.write(b, 0, got);
					m_is.close();
					os.close();
				}
				else
				{
					Writer w = m_asr.preferCharacterStream().get().getWriter();
					w.write(m_probe.charPrefix(null));
					char[] b = new char [ 8192 ];
					int got;
					while ( -1 != (got = m_rdr.read(b)) )
						w.write(b, 0, got);
					m_rdr.close();
					w.close();
				}
				return m_tgt;
			}
		}

		/**
		 * Copier usable when source charset is contained in the target charset.
		 *<p>
		 * Charset containment doesn't guarantee encoding equivalence, so the
		 * stream may have to be transcoded, but there won't be any characters
		 * unrepresentable in the target encoding that need to be escaped. If
		 * the source presented a character stream, it is handled just as for
		 * {@code Direct}; if a binary stream, it is wrapped as a character
		 * stream and then handled the same way.
		 */
		static class Transcoding extends Direct
		{
			private Charset m_srcCS;

			Transcoding(Writable tgt, Charset srcCS)
			{
				super(tgt);
				m_srcCS = srcCS;
			}

			@Override
			XMLCopier prepare(DeclProbe probe, InputStream is)
			throws SQLException
			{
				return prepare(probe, new InputStreamReader(is, m_srcCS));
			}
		}

		/**
		 * Copier usable when source charset may not be contained in the target
		 * charset.
		 *<p>
		 * The stream has to be parsed and serialized so that any characters
		 * not representable in the target encoding can be serialized as the
		 * XML character references.
		 */
		static class Transforming extends Stream
		{
			private Charset m_srcCS;

			Transforming(Writable tgt, Charset srcCS)
			{
				super(tgt);
				m_srcCS = srcCS;
			}

			@Override
			XMLCopier prepare(DeclProbe probe, InputStream is)
			throws IOException, SQLException
			{
				try
				{
					boolean[] wrapping = new boolean[] { false };
					is = correctedDeclStream(
						is, probe, /* neverWrap */ false, m_srcCS, wrapping);
					m_adjustable = /* again without defaults() */
						new AdjustingSAXSource(new InputSource(is),wrapping[0]);
				}
				catch ( SAXException e )
				{
					throw normalizedException(e);
				}
				return this;
			}

			@Override
			XMLCopier prepare(DeclProbe probe, Reader r)
			throws IOException, SQLException
			{
				try
				{
					boolean[] wrapping = new boolean[] { false };
					r = correctedDeclReader(r, probe, m_srcCS, wrapping);
					m_adjustable =
						new AdjustingSAXSource(new InputSource(r), wrapping[0]);
				}
				catch ( SAXException e )
				{
					throw normalizedException(e);
				}
				return this;
			}

			@Override
			Writable finish() throws IOException, SQLException
			{
				saxCopy(m_adjustable.get(),
					m_tgt.setResult(
						m_tgt.backingIfNotFreed(), SAXResult.class));
				return m_tgt;
			}
		}

		/**
		 * Copy from a {@code SAXSource} to a {@code SAXResult}, provided the
		 * {@code SAXSource} supplies its own {@code XMLReader}.
		 *<p>
		 * See {@code XMLCopier.SAX.Parsing} for when it does not.
		 */
		static void saxCopy(SAXSource sxs, SAXResult sxr) throws SQLException
		{
			XMLReader xr = sxs.getXMLReader();
			try
			{
				ContentHandler ch = sxr.getHandler();
				xr.setContentHandler(ch);
				if ( ch instanceof DTDHandler )
					xr.setDTDHandler((DTDHandler)ch);
				LexicalHandler lh = sxr.getLexicalHandler();
				if ( null == lh  &&  ch instanceof LexicalHandler )
					lh = (LexicalHandler)ch;
				if ( null != lh )
					xr.setProperty(
						SAX2PROPERTY.LEXICAL_HANDLER.propertyUri(), lh);
				xr.parse(sxs.getInputSource());
			}
			catch ( SAXException e )
			{
				throw new SQLDataException(e.getMessage(), "22000", e);
			}
			catch ( IOException e )
			{
				throw new SQLException(e.getMessage(), "58030", e);
			}
		}

		/**
		 * Copier for a {@code SAXSource} that supplies its own non-null
		 * {@code XMLReader}.
		 */
		static class SAX extends XMLCopier
		{
			private SAXSource m_source;

			SAX(Writable tgt, SAXSource src)
			{
				super(tgt);
				m_source = src;
			}

			@Override
			Writable finish() throws IOException, SQLException
			{
				saxCopy(m_source,
					m_tgt.setResult(
						m_tgt.backingIfNotFreed(), SAXResult.class));
				return m_tgt;
			}

			/**
			 * Copier for a {@code SAXSource} that does not supply its own
			 * {@code XMLReader}.
			 *<p>
			 * Such a source needs a parser constructed here, which may, like
			 * any parser, require adjustment. Such a source is effectively a
			 * stream source snuck in through the SAX API.
			 */
			static class Parsing extends XMLCopier
			{
				private AdjustingSAXSource m_source;

				Parsing(Writable tgt, SAXSource src) throws SAXException
				{
					super(tgt);
					InputSource is = src.getInputSource();
					/*
					 * No correctedDeclStream, no check for unwrapping: if some
					 * random {@code SQLXML} implementation is passing a stream
					 * to parse, it had better make sense to a vanilla parser.
					 */
					m_source = new AdjustingSAXSource(is, false);
				}

				@Override
				AdjustingSAXSource getAdjustable()
				{
					return m_source;
				}

				@Override
				Writable finish() throws IOException, SQLException
				{
					saxCopy(m_source.get(),
						m_tgt.setResult(
							m_tgt.backingIfNotFreed(), SAXResult.class));
					return m_tgt;
				}
			}
		}

		static class StAX extends XMLCopier
		{
			private StAXSource m_source;

			StAX(Writable tgt, StAXSource src)
			{
				super(tgt);
				m_source = src;
			}

			@Override
			Writable finish() throws IOException, SQLException
			{
				StAXResult str = m_tgt.setResult(
					m_tgt.backingIfNotFreed(), StAXResult.class);
				XMLInputFactory  xif = XMLInputFactory.newInstance();
				xif.setProperty(xif.IS_NAMESPACE_AWARE, true);
				XMLOutputFactory xof = XMLOutputFactory.newInstance();
				/*
				 * The Source has either an event reader or a stream reader. Use
				 * the event reader directly, or create one around the stream
				 * reader.
				 */
				XMLEventReader xer = m_source.getXMLEventReader();
				try
				{
					if ( null == xer )
					{
						XMLStreamReader xsr = m_source.getXMLStreamReader();
						/*
						 * Before wrapping this XMLStreamReader in an
						 * XMLEventReader, wrap it in this trivial delegate
						 * first. The authors of XMLEventReaderImpl found
						 * themselves with a problem to solve, namely that
						 * XMLEventReader's hasNext() method isn't declared to
						 * throw any exceptions (XMLEventReader implements
						 * Iterator). So they solved it by just swallowing any
						 * exception thrown by the stream reader's hasNext, and
						 * returning false, so it just seems the XML abruptly
						 * ends for no reported reason.
						 *
						 * So, just wrap hasNext here to save any exception from
						 * below, and return true, thereby inviting the consumer
						 * to go ahead and call next, where we'll re-throw it.
						 */
						xsr = new StreamReaderDelegate(xsr)
						{
							XMLStreamException savedException;

							@Override
							public boolean hasNext() throws XMLStreamException
							{
								try
								{
									return super.hasNext();
								}
								catch ( XMLStreamException e )
								{
									savedException = e;
									return true;
								}
							}

							@Override
							public int next() throws XMLStreamException
							{
								XMLStreamException e = savedException;
								if ( null != e )
								{
									savedException = null;
									throw e;
								}
								return super.next();
							}
						};
						xer = xif.createXMLEventReader(xsr);
					}
					/*
					 * Were you thinking the above could be simply
					 * createXMLEventReader(m_source) by analogy with
					 * the writer below? Good thought, but the XMLInputFactory
					 * implementation that's included in OpenJDK doesn't
					 * implement the case where the Source argument is a
					 * StAXSource! Two lines would do it. (And anyway, "the
					 * writer below" brings hollow, joyless laughter in Java 9
					 * and later.)
					 */

					/*
					 * Bother. If not for a regression in Java 9 and later, this
					 * would be a simple createXMLEventWriter(str).
					 * XXX This is not fully general, as str is known to be one
					 * of our native StAXResults, which (for now!) can only wrap
					 * a stream writer, never an event writer.
					 */
					XMLEventConsumer xec =
						new XMLEventToStreamConsumer(str.getXMLStreamWriter());

					while ( xer.hasNext() )
						xec.add(xer.nextEvent());

					xer.close();
				}
				catch ( XMLStreamException e )
				{
					throw new SQLDataException(e.getMessage(), "22000", e);
				}
				return m_tgt;
			}
		}

		static class DOM extends XMLCopier
		{
			private DOMSource m_source;

			DOM(Writable tgt, DOMSource src)
			{
				super(tgt);
				m_source = src;
			}

			@Override
			Writable finish() throws IOException, SQLException
			{
				DOMResult dr = m_tgt.setResult(
					m_tgt.backingIfNotFreed(), DOMResult.class);
				dr.setNode(m_source.getNode());
				return m_tgt;
			}
		}
	}

	/**
	 * Implements setters for the later JAXP security properties, which use the
	 * same names for SAX, StAX, and DOM, so the individual setters can all be
	 * here with only the {@code setFirstSupportedProperty} method abstract.
	 */
	abstract static class
	AdjustingJAXPParser<T extends Adjusting.XML.Parsing<T>>
	implements Adjusting.XML.Parsing<T>
	{
		private static final String LIMIT =
			"http://www.oracle.com/xml/jaxp/properties/";

		/*
		 * Can get these from javax.xml.XMLConstants once assuming Java >= 7.
		 */
		private static final String ACCESS =
			"http://javax.xml.XMLConstants/property/accessExternal";

		@Override
		public T defaults()
		{
			return allowDTD(false).externalGeneralEntities(false)
				.externalParameterEntities(false).loadExternalDTD(false)
				.xIncludeAware(false).expandEntityReferences(false);
		}

		@Override
		public T elementAttributeLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "elementAttributeLimit");
		}

		@Override
		public T entityExpansionLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "entityExpansionLimit");
		}

		@Override
		public T entityReplacementLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "entityReplacementLimit");
		}

		@Override
		public T maxElementDepth(int depth)
		{
			return setFirstSupportedProperty(depth,
				LIMIT + "maxElementDepth");
		}

		@Override
		public T maxGeneralEntitySizeLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "maxGeneralEntitySizeLimit");
		}

		@Override
		public T maxParameterEntitySizeLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "maxParameterEntitySizeLimit");
		}

		@Override
		public T maxXMLNameLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "maxXMLNameLimit");
		}

		@Override
		public T totalEntitySizeLimit(int limit)
		{
			return setFirstSupportedProperty(limit,
				LIMIT + "totalEntitySizeLimit");
		}

		@Override
		public T accessExternalDTD(String protocols)
		{
			return setFirstSupportedProperty(protocols, ACCESS + "DTD");
		}

		@Override
		public T accessExternalSchema(String protocols)
		{
			return setFirstSupportedProperty(protocols, ACCESS + "Schema");
		}

		@Override
		public T entityResolver(EntityResolver resolver)
		{
			throw new UnsupportedOperationException(
				"A SAX EntityResolver cannot be set on a " +
				getClass().getCanonicalName());
		}

		@Override
		public T schema(Schema schema)
		{
			throw new UnsupportedOperationException(
				"A Schema cannot be set on a " +
				getClass().getCanonicalName());
		}
	}

	/**
	 * Extends {@code AdjustingJAXPParser} with some of the older adjustments
	 * that can be made the same way for SAX and DOM (StAX should not extend
	 * this class, but just implement the adjustments its own different way).
	 */
	abstract static class SAXDOMCommon<T extends Adjusting.XML.Parsing<T>>
	extends AdjustingJAXPParser<T>
	{
		@Override
		public T allowDTD(boolean v) {
			return setFirstSupportedFeature( !v,
				"http://apache.org/xml/features/disallow-doctype-decl",
				"http://xerces.apache.org/xerces2-j/features.html" +
					"#disallow-doctype-decl");
		}

		@Override
		public T externalGeneralEntities(boolean v)
		{
			return setFirstSupportedFeature( v,
				"http://xml.org/sax/features/external-general-entities",
				"http://xerces.apache.org/xerces2-j/features.html" +
					"#external-general-entities",
				"http://xerces.apache.org/xerces-j/features.html" +
					"#external-general-entities");
		}

		@Override
		public T externalParameterEntities(boolean v)
		{
			return setFirstSupportedFeature( v,
				"http://xml.org/sax/features/external-parameter-entities",
				"http://xerces.apache.org/xerces2-j/features.html" +
					"#external-parameter-entities",
				"http://xerces.apache.org/xerces-j/features.html" +
					"#external-parameter-entities");
		}

		@Override
		public T loadExternalDTD(boolean v)
		{
			return setFirstSupportedFeature( v,
				"http://apache.org/xml/features/" +
					"nonvalidating/load-external-dtd");
		}
	}

	/**
	 * Error handler for SAX/DOM parsing that treats both "error" and
	 * "fatal error" as exception-worthy, and logs warnings at {@code WARNING}
	 * level.
	 */
	static class SAXDOMErrorHandler implements ErrorHandler
	{
		private static final SAXDOMErrorHandler s_nonWrappedInstance =
			new SAXDOMErrorHandler(false);
		/*
		 * Issue #312: localized error messages from the schema validator
		 * don't always use the same punctuation around the offending
		 * element name! Simplest to look for the element name (it's distinctive
		 * enough) and not for any punctuation--and check that only when
		 * wrapping is being applied, and then only once (the wrapping element,
		 * of course, will be first), and so avoid suppressing a later error by
		 * mistake should a document somehow happen to contain an element with
		 * the same name used here.
		 */
		static final Pattern s_wrapelement = Pattern.compile(
			"^cvc-elt\\.1(?:\\.a)?+:.*pljava-content-wrap");
		final Logger m_logger = Logger.getLogger("org.postgresql.pljava.jdbc");
		private int m_wrapCount;

		static SAXDOMErrorHandler instance(boolean wrapped)
		{
			return
				wrapped ? new SAXDOMErrorHandler(true) : s_nonWrappedInstance;
		}

		private SAXDOMErrorHandler(boolean wrap)
		{
			m_wrapCount = wrap ? 1 : 0;
		}

		@Override
		public void error(SAXParseException exception) throws SAXException
		{
			/*
			 * When validating with XML Schema against a value being parsed as
			 * CONTENT, the 'invisible' pljava-content-wrap element may produce
			 * an error. This hack keeps it invisible; however, the validator is
			 * then more lenient if the 'visible' top-level element isn't found,
			 * and simply validates the elements that are declared in the schema
			 * wherever it happens to find them.
			 *
			 * The check is only applied when the input has been wrapped, and
			 * then only once (after all, the wrapping element will be the first
			 * to be seen). The "only once" part may be futile inasmuch as the
			 * validator switches to the lenient mode described above and may
			 * not even report subsequent mismatched elements. But the check
			 * still needs to be conditional (we do know whether we applied a
			 * wrapper or not), so the condition may as well be the right one.
			 */
			if ( 0 == m_wrapCount )
				throw exception;
			Matcher m = s_wrapelement.matcher(exception.getMessage());
			if ( ! m.lookingAt() )
				throw exception;
			-- m_wrapCount;
		}

		@Override
		public void fatalError(SAXParseException exception) throws SAXException
		{
			throw exception;
		}

		@Override
		public void warning(SAXParseException exception) throws SAXException
		{
			m_logger.log(WARNING, exception.getMessage(), exception);
		}
	}

	static class AdjustingSourceResult
	extends
		AdjustingJAXPParser<Adjusting.XML.Result<Adjusting.XML.SourceResult>>
	implements Adjusting.XML.SourceResult
	{
		private Writable m_result;
		private Charset m_serverCS;
		private XMLCopier m_copier;

		AdjustingSourceResult(Writable result, Charset serverCS)
		{
			m_result = result;
			m_serverCS = serverCS;
		}

		@Override
		public AdjustingSourceResult set(Source source) throws SQLException
		{
			if ( source instanceof Adjusting.XML.Source )
				source = ((Adjusting.XML.Source)source).get();

			if ( source instanceof StreamSource )
				return set((StreamSource)source);

			if ( source instanceof SAXSource )
				return set((SAXSource)source);

			if ( source instanceof StAXSource )
				return set((StAXSource)source);

			if ( source instanceof DOMSource )
				return set((DOMSource)source);

			m_result.free();
			throw new SQLDataException(
				"XML source class " + source.getClass().getName() +
				" unsupported");
		}

		@Override
		public AdjustingSourceResult set(StreamSource source)
		throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult too late to set source");

			/*
			 * Foreign implementation also gets its choice whether to supply
			 * an InputStream or a Reader.
			 */
			InputStream is = source.getInputStream();
			Reader       r = source.getReader();
			DeclProbe probe = new DeclProbe();
			try
			{
				if ( null != is )
				{
					int b;
					while ( -1 != (b = is.read()) )
						if ( ! probe.take((byte)b) )
							break;
					String probedEncoding = probe.queryEncoding();
					m_copier = XMLCopier
						.copierFor(m_result, m_serverCS, probedEncoding)
						.prepare(probe, is);
				}
				else if ( null != r )
				{
					int b;
					while ( -1 != (b = r.read()) )
						if ( ! probe.take((char)b) )
							break;
					String probedEncoding = probe.queryEncoding();
					m_copier = XMLCopier
						.copierFor(m_result, m_serverCS, probedEncoding)
						.prepare(probe, r);
				}
				else
					throw new SQLDataException(
						"Foreign SQLXML implementation has " +
						"a broken StreamSource", "22000");
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
			finally
			{
				if ( null == m_copier )
				{
					m_result.free();
					m_result = null;
				}
			}
			return this;
		}

		@Override
		public AdjustingSourceResult set(SAXSource source)
		throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult too late to set source");

			if ( null != source.getXMLReader() )
				m_copier = new XMLCopier.SAX(m_result, source);
			else
			{
				try
				{
					m_copier = new XMLCopier.SAX.Parsing(m_result, source);
				}
				catch ( SAXException e )
				{
					throw normalizedException(e);
				}
			}

			return this;
		}

		@Override
		public AdjustingSourceResult set(StAXSource source)
		throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult too late to set source");

			m_copier = new XMLCopier.StAX(m_result, source);
			return this;
		}

		@Override
		public AdjustingSourceResult set(DOMSource source)
		throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult too late to set source");

			m_copier = new XMLCopier.DOM(m_result, source);
			return this;
		}

		@Override
		public AdjustingSourceResult set(String source)
		throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult too late to set source");

			return set(new StreamSource(new StringReader(source)));
		}

		@Override
		public SQLXML getSQLXML() throws SQLException
		{
			if ( null == m_result )
				throw new IllegalStateException(
					"AdjustingSourceResult getSQLXML called more than once");
			if ( null == m_copier )
				throw new IllegalStateException(
					"AdjustingSourceResult getSQLXML called before set");
			Writable result = null;
			try
			{
				result = m_copier.finish();
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
			finally
			{
				Writable r = m_result;
				m_result = null;
				m_serverCS = null;
				m_copier = null;
				if ( null == result )
					r.free();
			}
			return result;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new UnsupportedOperationException(
				"SourceResult does not support setSystemId");
		}

		@Override
		public String getSystemId()
		{
			throw new UnsupportedOperationException(
				"SourceResult does not support getSystemId");
		}

		private Adjusting.XML.Source theAdjustable()
		{
			if ( null == m_copier )
				throw new IllegalStateException(
					"AdjustingSourceResult too early or late to adjust");
			return m_copier.getAdjustable();
		}

		@Override
		public AdjustingSourceResult get() throws SQLException
		{
			return this; // for this class, get is a noop
		}

		@Override
		public AdjustingSourceResult allowDTD(boolean v)
		{
			theAdjustable().allowDTD(v);
			return this;
		}

		@Override
		public AdjustingSourceResult externalGeneralEntities(boolean v)
		{
			theAdjustable().externalGeneralEntities(v);
			return this;
		}

		@Override
		public AdjustingSourceResult externalParameterEntities(boolean v)
		{
			theAdjustable().externalParameterEntities(v);
			return this;
		}

		@Override
		public AdjustingSourceResult loadExternalDTD(boolean v)
		{
			theAdjustable().loadExternalDTD(v);
			return this;
		}

		@Override
		public AdjustingSourceResult xIncludeAware(boolean v)
		{
			theAdjustable().xIncludeAware(v);
			return this;
		}

		@Override
		public AdjustingSourceResult expandEntityReferences(boolean v)
		{
			theAdjustable().expandEntityReferences(v);
			return this;
		}

		@Override
		public AdjustingSourceResult setFirstSupportedFeature(
			boolean value, String... names)
		{
			theAdjustable().setFirstSupportedFeature(value, names);
			return this;
		}

		@Override
		public AdjustingSourceResult setFirstSupportedProperty(
			Object value, String... names)
		{
			theAdjustable().setFirstSupportedProperty(value, names);
			return this;
		}

		@Override
		public AdjustingSourceResult entityResolver(EntityResolver resolver)
		{
			theAdjustable().entityResolver(resolver);
			return this;
		}

		@Override
		public AdjustingSourceResult schema(Schema schema)
		{
			theAdjustable().schema(schema);
			return this;
		}
	}

	static class AdjustingStreamResult
	extends AdjustingJAXPParser<Adjusting.XML.Result<StreamResult>>
	implements Adjusting.XML.StreamResult
	{
		private VarlenaWrapper.Output m_vwo;
		private Charset m_serverCS;
		private AdjustingSAXSource m_verifierSource;
		private boolean m_preferWriter = false;
		private boolean m_hasCalledDefaults;

		AdjustingStreamResult(VarlenaWrapper.Output vwo, Charset serverCS)
		throws SQLException
		{
			m_vwo = vwo;
			m_serverCS = serverCS;
			try
			{
				/*
				 * When used as a verifier, an AdjustingSAXSource can be created
				 * with wrapping=false unconditionally, as it won't be using the
				 * result for anything and has no need to unwrap it. At verify
				 * time, the presence of wrapping still gets checked, if only to
				 * set up the ErrorHandler correctly in case schema validation
				 * has been requested.
				 */
				m_verifierSource = new AdjustingSAXSource(null, false);
			}
			catch ( SAXException e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new IllegalStateException(
				"AdjustingStreamResult used before get()");
		}

		@Override
		public String getSystemId()
		{
			throw new IllegalStateException(
				"AdjustingStreamResult used before get()");
		}

		private AdjustingSAXSource theVerifierSource()
		{
			return theVerifierSource(true);
		}

		private AdjustingSAXSource theVerifierSource(boolean afterDefaults)
		{
			if ( null == m_verifierSource )
				throw new IllegalStateException(
					"AdjustingStreamResult too late to adjust after get()");

			if ( afterDefaults  &&  ! m_hasCalledDefaults )
			{
				m_hasCalledDefaults = true;
				m_verifierSource.defaults();
				/* Don't touch m_preferWriter here, only in real defaults() */
			}

			return m_verifierSource;
		}

		@Override
		public AdjustingStreamResult preferBinaryStream()
		{
			theVerifierSource(false); // shorthand error check
			m_preferWriter = false;
			return this;
		}

		@Override
		public AdjustingStreamResult preferCharacterStream()
		{
			theVerifierSource(false); // shorthand error check
			m_preferWriter = true;
			return this;
		}

		@Override
		public StreamResult get() throws SQLException
		{
			if ( null == m_verifierSource )
				throw new IllegalStateException(
					"AdjustingStreamResult get() called more than once");

			XMLReader xr = theVerifierSource().get().getXMLReader();
			OutputStream os;
			try
			{
				m_vwo.setVerifier(new Verifier(xr));
				os = new DeclCheckedOutputStream(m_vwo, m_serverCS);
			}
			catch ( IOException e )
			{
				throw normalizedException(e);
			}
			StreamResult sr;
			if ( m_preferWriter )
				sr = new StreamResult(
					new OutputStreamWriter(os, m_serverCS.newEncoder()));
			else
				sr = new StreamResult(os);
			m_vwo = null;
			m_verifierSource = null;
			m_serverCS = null;
			return sr;
		}

		@Override
		public AdjustingStreamResult allowDTD(boolean v)
		{
			theVerifierSource().allowDTD(v);
			return this;
		}

		@Override
		public AdjustingStreamResult externalGeneralEntities(boolean v)
		{
			theVerifierSource().externalGeneralEntities(v);
			return this;
		}

		@Override
		public AdjustingStreamResult externalParameterEntities(boolean v)
		{
			theVerifierSource().externalParameterEntities(v);
			return this;
		}

		@Override
		public AdjustingStreamResult loadExternalDTD(boolean v)
		{
			theVerifierSource().loadExternalDTD(v);
			return this;
		}

		@Override
		public AdjustingStreamResult xIncludeAware(boolean v)
		{
			theVerifierSource().xIncludeAware(v);
			return this;
		}

		@Override
		public AdjustingStreamResult expandEntityReferences(boolean v)
		{
			theVerifierSource().expandEntityReferences(v);
			return this;
		}

		@Override
		public AdjustingStreamResult setFirstSupportedFeature(
			boolean value, String... names)
		{
			theVerifierSource().setFirstSupportedFeature(value, names);
			return this;
		}

		@Override
		public AdjustingStreamResult defaults()
		{
			m_hasCalledDefaults = true;
			theVerifierSource().defaults();
			return preferBinaryStream();
		}

		@Override
		public AdjustingStreamResult setFirstSupportedProperty(
			Object value, String... names)
		{
			theVerifierSource().setFirstSupportedProperty(value, names);
			return this;
		}

		@Override
		public AdjustingStreamResult entityResolver(EntityResolver resolver)
		{
			theVerifierSource().entityResolver(resolver);
			return this;
		}

		@Override
		public AdjustingStreamResult schema(Schema schema)
		{
			theVerifierSource(false).schema(schema);
			return this;
		}
	}

	static class AdjustingSAXSource
	extends SAXDOMCommon<Adjusting.XML.Source<SAXSource>>
	implements Adjusting.XML.SAXSource
	{
		private SAXParserFactory m_spf;
		private XMLReader m_xr;
		private InputSource m_is;
		private boolean m_wrapped;
		private SAXException m_except;
		private boolean m_hasCalledDefaults;

		static class Dummy extends AdjustingSAXSource
		{
			static final Dummy INSTANCE = new Dummy();
			private Dummy() { }

			@Override
			public AdjustingSAXSource setFirstSupportedFeature(
				boolean value, String... names)
			{
				return this;
			}

			@Override
			public AdjustingSAXSource setFirstSupportedProperty(
				Object value, String... names)
			{
				return this;
			}

			@Override
			public AdjustingSAXSource entityResolver(EntityResolver resolver)
			{
				return this;
			}

			@Override
			public AdjustingSAXSource schema(Schema schema)
			{
				return this;
			}
		}

		private AdjustingSAXSource() // only for Dummy
		{
		}

		AdjustingSAXSource(InputSource is, boolean wrapped)
		throws SAXException
		{
			m_is = is;
			m_wrapped = wrapped;
			m_spf = SAXParserFactory.newInstance();
			m_spf.setNamespaceAware(true);
		}

		AdjustingSAXSource(XMLReader xr, InputSource is)
		throws SAXException
		{
			m_xr = xr;
			m_is = is;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new IllegalStateException(
				"AdjustingSAXSource used before get()");
		}

		@Override
		public String getSystemId()
		{
			throw new IllegalStateException(
				"AdjustingSAXSource used before get()");
		}

		private SAXParserFactory theFactory()
		{
			if ( null == m_spf )
				throw new IllegalStateException(
					"AdjustingSAXSource too late to set schema after " +
					"other adjustments");
			return m_spf;
		}

		private XMLReader theReader()
		{
			if ( null != m_except )
				return null;

			if ( null != m_spf )
			{
				try
				{
					m_xr = m_spf.newSAXParser().getXMLReader();
				}
				catch ( SAXException e )
				{
					m_except = e;
					return null;
				}
				catch ( ParserConfigurationException e )
				{
					m_except = new SAXException(e.getMessage(), e);
					return null;
				}
				m_spf = null;
				if ( m_wrapped )
					m_xr = new SAXUnwrapFilter(m_xr);

				/*
				 * If this AdjustingSAXSource has been created for use as a
				 * verifier, it was passed false for m_wrapped unconditionally,
				 * which is mostly harmless, but may mean this is the wrong
				 * error handler, if schema validation has been requested.
				 * That's ok; the verifier checks for wrapping and will set the
				 * right error handler if need be.
				 */
				m_xr.setErrorHandler(SAXDOMErrorHandler.instance(m_wrapped));

				if ( ! m_hasCalledDefaults )
					defaults();
			}

			if ( null == m_xr )
				throw new IllegalStateException(
					"AdjustingSAXSource too late to adjust after get()");

			return m_xr;
		}

		@Override
		public SAXSource get() throws SQLException
		{
			if ( null == m_xr  &&  null == m_spf )
				throw new IllegalStateException(
					"AdjustingSAXSource get() called more than once");

			XMLReader xr;
			if ( null != m_except  ||  null == (xr = theReader()) )
				throw normalizedException(m_except);

			SAXSource ss = new SAXSource(xr, m_is);
			m_xr = null;
			m_is = null;
			return ss;
		}

		@Override
		public AdjustingSAXSource defaults()
		{
			m_hasCalledDefaults = true;
			super.defaults();
			return this;
		}

		@Override
		public AdjustingSAXSource xIncludeAware(boolean v)
		{
			return setFirstSupportedFeature( v,
				"http://apache.org/xml/features/xinclude");
		}

		@Override
		public AdjustingSAXSource expandEntityReferences(boolean v)
		{
			// not a thing in SAX ?
			return this;
		}

		@Override
		public AdjustingSAXSource setFirstSupportedFeature(
			boolean value, String... names)
		{
			XMLReader r = theReader();
			if ( null == r ) // pending exception, nothing to be done
				return this;

			for ( String name : names )
			{
				try
				{
					r.setFeature(name, value);
					break;
				}
				catch ( SAXNotRecognizedException | SAXNotSupportedException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}

		@Override
		public AdjustingSAXSource setFirstSupportedProperty(
			Object value, String... names)
		{
			XMLReader r = theReader();
			if ( null == r ) // pending exception, nothing to be done
				return this;

			for ( String name : names )
			{
				try
				{
					r.setProperty(name, value);
					break;
				}
				catch ( SAXNotRecognizedException e )
				{
					e.printStackTrace(); // XXX
				}
				catch ( SAXNotSupportedException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}

		@Override
		public AdjustingSAXSource entityResolver(EntityResolver resolver)
		{
			XMLReader r = theReader();
			if ( null != r )
				r.setEntityResolver(resolver);
			return this;
		}

		@Override
		public AdjustingSAXSource schema(Schema schema)
		{
			theFactory().setSchema(schema);
			return this;
		}
	}

	/*
	 * For the moment, an AdjustingSAXResult doesn't adjust anything at all,
	 * as a Verifier isn't used when writing through SAX. But it has to be here,
	 * just because if the client asks only for Adjusting.XML.Result, meaning we
	 * get to pick, SAX is the flavor we pick.
	 */
	static class AdjustingSAXResult
	extends SAXDOMCommon<Adjusting.XML.Result<SAXResult>>
	implements Adjusting.XML.SAXResult
	{
		private SAXResult m_sr;

		AdjustingSAXResult(SAXResult sr)
		{
			m_sr = sr;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new IllegalStateException(
				"AdjustingSAXResult used before get()");
		}

		@Override
		public String getSystemId()
		{
			throw new IllegalStateException(
				"AdjustingSAXResult used before get()");
		}

		private AdjustingSAXResult checkedNoOp()
		{
			if ( null == m_sr )
				throw new IllegalStateException(
					"AdjustingSAXResult too late to adjust after get()");
			return this;
		}

		@Override
		public SAXResult get() throws SQLException
		{
			if ( null == m_sr )
				throw new IllegalStateException(
					"AdjustingSAXResult get() called more than once");

			SAXResult sr = m_sr;
			m_sr = null;
			return sr;
		}

		@Override
		public AdjustingSAXResult xIncludeAware(boolean v)
		{
			return checkedNoOp();
		}

		@Override
		public AdjustingSAXResult expandEntityReferences(boolean v)
		{
			return checkedNoOp();
		}

		@Override
		public AdjustingSAXResult setFirstSupportedFeature(
			boolean value, String... names)
		{
			return checkedNoOp();
		}

		@Override
		public AdjustingSAXResult setFirstSupportedProperty(
			Object value, String... names)
		{
			return checkedNoOp();
		}

		@Override
		public AdjustingSAXResult entityResolver(EntityResolver resolver)
		{
			return checkedNoOp();
		}

		@Override
		public AdjustingSAXResult schema(Schema schema)
		{
			return checkedNoOp();
		}
	}

	static class AdjustingStAXSource
	extends AdjustingJAXPParser<Adjusting.XML.Source<StAXSource>>
	implements Adjusting.XML.StAXSource
	{
		private XMLInputFactory m_xif;
		private InputStream m_is;
		private Charset m_serverCS;
		private boolean m_wrapped;

		AdjustingStAXSource(InputStream is, Charset serverCS, boolean wrapped)
		throws XMLStreamException
		{
			m_xif = XMLInputFactory.newInstance();
			m_xif.setProperty(m_xif.IS_NAMESPACE_AWARE, true);
			m_is = is;
			m_serverCS = serverCS;
			m_wrapped = wrapped;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new IllegalStateException(
				"AdjustingStAXSource used before get()");
		}

		@Override
		public String getSystemId()
		{
			throw new IllegalStateException(
				"AdjustingStAXSource used before get()");
		}

		private XMLInputFactory theFactory()
		{
			if ( null == m_xif )
				throw new IllegalStateException(
					"AdjustingStAXSource too late to adjust after get()");
			return m_xif;
		}

		@Override
		public StAXSource get() throws SQLException
		{
			if ( null == m_xif )
				throw new IllegalStateException(
					"AdjustingStAXSource get() called more than once");
			try
			{
				XMLStreamReader xsr = m_xif.createXMLStreamReader(
					m_is, m_serverCS.name());
				if ( m_wrapped )
					xsr = new StAXUnwrapFilter(xsr);
				m_xif = null; // too late for any more adjustments
				return new StAXSource(xsr);
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public AdjustingStAXSource allowDTD(boolean v) {
			return setFirstSupportedFeature( v, XMLInputFactory.SUPPORT_DTD);
		}

		@Override
		public AdjustingStAXSource externalGeneralEntities(boolean v)
		{
			return setFirstSupportedFeature( v,
				XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES);
		}

		@Override
		public AdjustingStAXSource externalParameterEntities(boolean v)
		{
			return this;
		}

		@Override
		public AdjustingStAXSource loadExternalDTD(boolean v)
		{
			return setFirstSupportedFeature( !v,
				"http://java.sun.com/xml/stream/properties/" +
					"ignore-external-dtd");
		}

		@Override
		public AdjustingStAXSource xIncludeAware(boolean v)
		{
			return this;
		}

		@Override
		public AdjustingStAXSource expandEntityReferences(boolean v)
		{
			return setFirstSupportedFeature( v,
				XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES);
		}

		@Override
		public AdjustingStAXSource setFirstSupportedFeature(
			boolean value, String... names)
		{
			XMLInputFactory xif = theFactory();
			for ( String name : names )
			{
				try
				{
					xif.setProperty(name, value);
					break;
				}
				catch ( IllegalArgumentException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}

		@Override
		public AdjustingStAXSource setFirstSupportedProperty(
			Object value, String... names)
		{
			XMLInputFactory xif = theFactory();
			for ( String name : names )
			{
				try
				{
					xif.setProperty(name, value);
					break;
				}
				catch ( IllegalArgumentException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}
	}

	static class AdjustingDOMSource
	extends SAXDOMCommon<Adjusting.XML.Source<DOMSource>>
	implements Adjusting.XML.DOMSource
	{
		private DocumentBuilderFactory m_dbf;
		private InputStream m_is;
		private boolean m_wrapped;
		private EntityResolver m_resolver;

		AdjustingDOMSource(InputStream is, boolean wrapped)
		{
			m_dbf = DocumentBuilderFactory.newInstance();
			m_dbf.setNamespaceAware(true);
			m_is = is;
			m_wrapped = wrapped;
		}

		@Override
		public void setSystemId(String systemId)
		{
			throw new IllegalStateException(
				"AdjustingDOMSource used before get()");
		}

		@Override
		public String getSystemId()
		{
			throw new IllegalStateException(
				"AdjustingDOMSource used before get()");
		}

		private DocumentBuilderFactory theFactory()
		{
			if ( null == m_dbf )
				throw new IllegalStateException(
					"AdjustingDOMSource too late to adjust after get()");
			return m_dbf;
		}

		@Override
		public DOMSource get() throws SQLException
		{
			if ( null == m_dbf )
				throw new IllegalStateException(
					"AdjustingDOMSource get() called more than once");
			try
			{
				DocumentBuilder db = m_dbf.newDocumentBuilder();
				db.setErrorHandler(SAXDOMErrorHandler.instance(m_wrapped));
				if ( null != m_resolver )
					db.setEntityResolver(m_resolver);
				DOMSource ds = new DOMSource(db.parse(m_is));
				if ( m_wrapped )
					domUnwrap(ds);
				m_dbf = null;
				m_is = null;
				return ds;
			}
			catch ( Exception e )
			{
				throw normalizedException(e);
			}
		}

		@Override
		public AdjustingDOMSource xIncludeAware(boolean v)
		{
			theFactory().setXIncludeAware(v);
			return this;
		}

		@Override
		public AdjustingDOMSource expandEntityReferences(boolean v)
		{
			theFactory().setExpandEntityReferences(v);
			return this;
		}

		@Override
		public AdjustingDOMSource setFirstSupportedFeature(
			boolean value, String... names)
		{
			DocumentBuilderFactory dbf = theFactory();
			for ( String name : names )
			{
				try
				{
					dbf.setFeature(name, value);
					break;
				}
				catch ( ParserConfigurationException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}

		@Override
		public AdjustingDOMSource setFirstSupportedProperty(
			Object value, String... names)
		{
			DocumentBuilderFactory dbf = theFactory();
			for ( String name : names )
			{
				try
				{
					dbf.setAttribute(name, value);
					break;
				}
				catch ( IllegalArgumentException e )
				{
					e.printStackTrace(); // XXX
				}
			}
			return this;
		}

		@Override
		public AdjustingDOMSource entityResolver(EntityResolver resolver)
		{
			m_resolver = resolver;
			return this;
		}

		@Override
		public AdjustingDOMSource schema(Schema schema)
		{
			theFactory().setSchema(schema);
			return this;
		}
	}
}
