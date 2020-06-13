/*
 * Copyright (c) 2019-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava;

import java.io.Reader;
import java.sql.SQLException;
import java.sql.SQLXML;
import javax.xml.stream.XMLInputFactory; // for javadoc
import javax.xml.stream.XMLResolver; // for javadoc
import javax.xml.stream.XMLStreamReader;
import javax.xml.validation.Schema;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

/**
 * Convenience class whose member classes will provide APIs that in some way
 * adjust aspects of PL/Java's behavior.
 *<p>
 * The intention is that a single
 *<pre>
 * import org.postgresql.pljava.Adjusting;
 *</pre>
 * will make various adjusting API classes available with easily readable
 * references like {@code Adjusting.XML.SAXSource}.
 */
public final class Adjusting
{
	private Adjusting() { } // no instances

	/**
	 * Class that collects adjustment APIs for affecting the behavior of
	 * PL/Java's XML support.
	 *<h2>XML parser behavior adjustments</h2>
	 *<p>
	 * Retrieving or verifying the XML content in a JDBC {@code SQLXML} object
	 * can involve applying an XML parser. The full XML specification includes
	 * features that can require an XML parser to retrieve external resources or
	 * consume unexpected amounts of memory. The full feature support may be an
	 * asset in an environment where the XML content will always be from a
	 * known, trusted source, or a liability if less is known about the XML
	 * content being processed.
	 *<p>
	 * The <a
	 * href='https://www.owasp.org/index.php/About_The_Open_Web_Application_Security_Project'
	 *>Open Web Application Security Project</a> (OWASP) advocates for the
	 * default use of settings that strictly limit the related features of Java
	 * XML parsers, as outlined in a
	 * <a
	 * href='https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html#java'
	 *>"cheat sheet"</a> the organization publishes. The strict default settings
	 * can then be selectively relaxed in applications where the features are
	 * needed and the content is sufficiently trusted.
	 *<p>
	 * However, the recommended defaults really are severely restrictive (for
	 * example, disabling document-type declarations by default will cause
	 * PL/Java's {@code SQLXML} implementation to reject all XML values that
	 * contain DTDs). Therefore, there must be a simple and clear way for code
	 * to selectively adjust the settings, or adopting the strictest settings by
	 * default would pose an unacceptable burden to developers.
	 *<p>
	 * The usual way that Java XML parsers expose their settings for adjustment
	 * is through {@code setFeature} or {@code setProperty} methods that must be
	 * passed particular URIs that identify adjustable features, and objects of
	 * appropriate types (often boolean) as the values for those properties. The
	 * supported properties and the URIs that identify them can be different
	 * from one parser implementation to another or one version to another. That
	 * is not the "simple and clear" adjustment mechanism needed here.
	 * Furthermore, the JDBC {@code SQLXML} API conceals much of the complexity
	 * of configuring any underlying XML parser behind a simple
	 * {@code getSource} method whose result can be used directly with other
	 * Java APIs expecting some flavor of {@code Source} object, and for some of
	 * those flavors, the returned object does not even expose the methods one
	 * would need to call to adjust the underlying parser, if any.
	 *<p>
	 * Hence this adjustment API. JDBC already provides for extensibility of the
	 * {@code SQLXML.getSource} method; it is passed the class object for a
	 * desired subtype of {@code Source} and, if the implementation supports it,
	 * returns an object of that type. The subtypes that every conformant
	 * implementation must support are {@code StreamSource}, {@code SAXSource},
	 * {@code StAXSource}, and {@code DOMSource}. If {@code null} is passed, the
	 * implementation will choose which flavor to return, often based on
	 * internal implementation details making one most natural or efficient.
	 *<p>
	 * The types {@link SAXSource}, {@link StAXSource}, and {@link DOMSource}
	 * are used the same way, by passing the corresponding class literal to
	 * {@code SQLXML}'s {@code getSource} method, which will return an object
	 * providing the chainable adjustment methods of {@link Source}, with the
	 * chain ending in a {@link Source#get get} method that returns the
	 * corresponding Java {@code Source} object, configured as adjusted.
	 *<p>
	 * Example:
	 *<pre>
	 *SAXSource src1 = sqx1.getSource(SAXSource.class);
	 *SAXSource src2 = sqx2.getSource(Adjusting.XML.SAXSource.class)
	 *                     .allowDTD(true).get();
	 *</pre>
	 * {@code src1} would be assigned a {@code SAXSource} object configured with
	 * the OWASP-recommended defaults, which will not allow the content to have
	 * a DTD, among other restrictions, while {@code src2} would be assigned a
	 * {@code SAXSource} object configured with the other default restrictions
	 * (as if the {@code allowDTD(true)} is preceded by an implied
	 * {@link Source#defaults defaults()}), but with DTD parsing enabled.
	 *<p>
	 * No {@code Adjusting.XML.StreamSource} is needed or provided, as any
	 * application code that requests a {@code StreamSource} will have to
	 * provide and configure its own parser anyway.
	 *<p>
	 * Like passing {@code null} to {@code getSource}, passing the parent
	 * interface {@code Adjusting.XML.Source.class} will allow the
	 * implementation to choose which subtype of {@code Adjusting.XML.Source} to
	 * return. The object returned by {@link Source#get get} can then be passed
	 * directly to Java APIs like {@code Transformer} that accept several
	 * flavors of {@code Source}, or examined to see of what class it is.
	 */
	public static final class XML
	{
		private XML() { } // no instances

		/**
		 * Interface with methods to adjust the restrictions on XML parsing
		 * that are commonly considered when XML content might be from untrusted
		 * sources.
		 *<p>
		 * The adjusting methods are best-effort and do not provide an
		 * indication of whether the requested adjustment was made. Not all of
		 * the adjustments are available for all flavors of {@code Source} or
		 * {@code Result} or for all parser implementations or versions the Java
		 * runtime may supply.
		 */
		public interface Parsing<T extends Parsing<T>>
		{
			/** Whether to allow a DTD at all. */
			T allowDTD(boolean v);

			/**
			 * Whether to retrieve external "general" entities (those
			 * that can be used in the document body) declared in the DTD.
			 */
			T externalGeneralEntities(boolean v);

			/**
			 * Whether to retrieve external "parameter" entities (those
			 * declared with a {@code %} and usable only within the DTD)
			 * declared in the DTD.
			 */
			T externalParameterEntities(boolean v);

			/**
			 * Whether to retrieve any external DTD subset declared in the DTD.
			 */
			T loadExternalDTD(boolean v);

			/**
			 * Whether to honor XInclude syntax in the document.
			 */
			T xIncludeAware(boolean v);

			/**
			 * Whether to expand entity references in the document to their
			 * declared replacement content.
			 */
			T expandEntityReferences(boolean v);

			/**
			 * For a feature that may have been identified by more than one URI
			 * in different parsers or versions, try passing the supplied
			 * <em>value</em> with each URI from <em>names</em> in order until
			 * one is not rejected by the underlying parser.
			 */
			T setFirstSupportedFeature(boolean value, String... names);

			/**
			 * Make a best effort to apply the recommended, restrictive
			 * defaults from the OWASP cheat sheet, to the extent they are
			 * supported by the underlying parser, runtime, and version.
			 *<p>
			 * Equivalent to:
			 *<pre>
			 * allowDTD(false).externalGeneralEntities(false)
			 * .externalParameterEntities(false).loadExternalDTD(false)
			 * .xIncludeAware(false).expandEntityReferences(false)
			 *</pre>
			 */
			T defaults();

			/**
			 * For a parser property (in DOM parlance, attribute) that may have
			 * been identified by more than one URI in different parsers or
			 * versions, try passing the supplied <em>value</em> with each URI
			 * from <em>names</em> in order until one is not rejected by the
			 * underlying parser.
			 *<p>
			 * A property differs from a feature in taking a value of some
			 * specified type, rather than being simply enabled/disabled with
			 * a boolean.
			 */
			T setFirstSupportedProperty(Object value, String... names);

			/**
			 * Maximum number of attributes on an element, with a negative or
			 * zero value indicating no limit.
			 */
			T elementAttributeLimit(int limit);

			/**
			 * Maximum number of entity expansions, with a negative or
			 * zero value indicating no limit.
			 */
			T entityExpansionLimit(int limit);

			/**
			 * Limit on total number of nodes in all entity referenced,
			 * with a negative or zero value indicating no limit.
			 */
			T entityReplacementLimit(int limit);

			/**
			 * Maximum element depth,
			 * with a negative or zero value indicating no limit.
			 */
			T maxElementDepth(int depth);

			/**
			 * Maximum size of any general entities,
			 * with a negative or zero value indicating no limit.
			 */
			T maxGeneralEntitySizeLimit(int limit);

			/**
			 * Maximum size of any parameter entities (including the result
			 * of nesting parameter entities),
			 * with a negative or zero value indicating no limit.
			 */
			T maxParameterEntitySizeLimit(int limit);

			/**
			 * Maximum size of XML names (including element and attribute names,
			 * namespace prefix, and namespace URI even though that isn't an
			 * XML name),
			 * with a negative or zero value indicating no limit.
			 */
			T maxXMLNameLimit(int limit);

			/**
			 * Limit on total size of all entities, general or parameter,
			 * with a negative or zero value indicating no limit.
			 */
			T totalEntitySizeLimit(int limit);

			/**
			 * Protocol schemes allowed in the URL of an external DTD to be
			 * fetched.
			 * @param protocols Empty string to deny all external DTD access,
			 * the string "all" to allow fetching by any protocol, or a
			 * comma-separated, case insensitive list of protocols to allow.
			 * A protocol name prefixed with "jar:" is also a protocol name.
			 */
			T accessExternalDTD(String protocols);

			/**
			 * Protocol schemes allowed in the URL of an external schema to be
			 * fetched.
			 * @param protocols Empty string to deny all external DTD access,
			 * the string "all" to allow fetching by any protocol, or a
			 * comma-separated, case insensitive list of protocols to allow.
			 * A protocol name prefixed with "jar:" is also a protocol name.
			 */
			T accessExternalSchema(String protocols);

			/**
			 * Set an {@link EntityResolver} of the type used by SAX and DOM
			 * <em>(optional operation)</em>.
			 *<p>
			 * This method only succeeds for a {@code SAXSource} or
			 * {@code DOMSource} (or a {@code StreamResult}, where the resolver
			 * is set on the parser that will verify the content written).
			 * Unlike the best-effort behavior of most methods in this
			 * interface, this one will report failure with an exception.
			 *<p>
			 * If the StAX API is wanted, a StAX {@link XMLResolver} should be
			 * set instead, using {@code setFirstSupportedProperty} with the
			 * property name {@link XMLInputFactory#RESOLVER}.
			 * @param resolver an instance of org.xml.sax.EntityResolver
			 * @throws UnsupportedOperationException if not supported by the
			 * underlying flavor of source or result.
			 */
			T entityResolver(EntityResolver resolver);

			/**
			 * Set a {@link Schema} to be applied during SAX or DOM parsing
			 *<em>(optional operation)</em>.
			 *<p>
			 * This method only succeeds for a {@code SAXSource} or
			 * {@code DOMSource} (or a {@code StreamResult}, where the schema
			 * is set on the parser that will verify the content written).
			 * Unlike the best-effort behavior of most methods in this
			 * interface, this one will report failure with an exception.
			 *<p>
			 * In the SAX case, this must be called <em>before</em> other
			 * methods of this interface.
			 * @param schema an instance of javax.xml.validation.Schema
			 * @throws UnsupportedOperationException if not supported by the
			 * underlying flavor of source or result.
			 * @throws IllegalStateException if the underlying implementation is
			 * SAX-based and another method from this interface has been called
			 * already.
			 */
			T schema(Schema schema);
		}

		/**
		 * Adjusting version of {@code javax.xml.transform.Source}, allowing
		 * various parser features to be configured before calling
		 * {@link #get get()} to obtain the usable {@code Source} object.
		 *<p>
		 * Passing this class itself to an {@code SQLXML} object's
		 * {@code getSource} method, as in
		 *<pre>
		 * Source src = sqx.getSource(Adjusting.XML.Source.class);
		 *</pre>
		 * will allow the implementation to choose the particular subtype of
		 * {@code Source} it will return. To obtain a {@code Source} of a
		 * particular desired type, pass the class literal of one of the
		 * subtypes {@link SAXSource}, {@link StAXSource}, or {@link DOMSource}.
		 *<p>
		 * The {@link #get get()} method can only be called once. The adjusting
		 * methods inherited from {@link Parsing} can only be called before
		 * {@code get()}.
		 *<p>
		 * Although this extends {@code javax.xml.transform.Source},
		 * implementing classes will likely throw exceptions from the
		 * {@code Source}-specific methods for getting and setting system IDs.
		 * Those methods, if needed, should be called on the {@code Source}
		 * object obtained from {@code get()}.
		 */
		public interface Source<T extends javax.xml.transform.Source>
		extends Parsing<Source<T>>, javax.xml.transform.Source
		{
			/**
			 * Return an object of the expected {@code Source} subtype
			 * reflecting any adjustments made with the other methods.
			 * @return an implementing object of the expected Source subtype
			 * @throws SQLException for any reason that {@code getSource} might
			 * have thrown when supplying the corresponding non-Adjusting
			 * subtype of Source.
			 */
			T get() throws SQLException;
		}

		/**
		 * Adjusting version of a {@code SAXSource}.
		 */
		public interface SAXSource
			extends Source<javax.xml.transform.sax.SAXSource>
		{
		}

		/**
		 * Adjusting version of a {@code StAXSource}.
		 */
		public interface StAXSource
			extends Source<javax.xml.transform.stax.StAXSource>
		{
		}

		/**
		 * Adjusting version of a {@code DOMSource}.
		 */
		public interface DOMSource
			extends Source<javax.xml.transform.dom.DOMSource>
		{
		}

		/**
		 * Adjusting version of {@code javax.xml.transform.Result}, offering
		 * the adjustment methods of {@link Parsing}, chiefly so that
		 * there is a way to apply those adjustments to any implicitly-created
		 * parser used to verify the content that will be written to the
		 * {@code Result}.
		 */
		public interface Result<T extends javax.xml.transform.Result>
		extends Parsing<Result<T>>, javax.xml.transform.Result
		{
			/**
			 * Return an object of the expected {@code Result} subtype
			 * reflecting any adjustments made with the other methods.
			 * @return an implementing object of the expected Result subtype
			 * @throws SQLException for any reason that {@code getResult} might
			 * have thrown when supplying the corresponding non-Adjusting
			 * subtype of Result.
			 */
			T get() throws SQLException;
		}

		/**
		 * Specialized {@code Result} type for setting a new PL/Java
		 * {@code SQLXML} instance's content from an arbitrary {@code Source}
		 * object of any of the types JDBC requires the {@code SQLXML} type
		 * to support.
		 *<p>
		 * The {@link #set set} method must be called before any of the
		 * inherited adjustment methods, and the {@link #getSQLXML getSQLXML}
		 * method only after any adjustments.
		 *<p>
		 * This is used transparently when another JDBC driver's {@code SQLXML}
		 * instance is returned from a PL/Java function, or passed to a
		 * {@code ResultSet} or {@code PreparedStatement}, to produce the
		 * PL/Java instance that is ultimately needed. In that case, the source
		 * {@code SQLXML} instance's {@code getSource} method is passed a null
		 * {@code sourceClass} argument, allowing the source instance to return
		 * whichever flavor of {@code Source} it efficiently implements, and
		 * that will be passed to this interface's {@code set} method.
		 *<p>
		 * Through explicit use of this interface, code can adjust the parser
		 * restrictions that may be applied in the process, in case the defaults
		 * are too restrictive.
		 */
		public interface SourceResult extends Result<SourceResult>
		{
			/**
			 * Supply the {@code Source} instance that is the source of the
			 * content.
			 *<p>
			 * This method must be called before any of the inherited adjustment
			 * methods. The argument may be a {@code StreamSource},
			 * {@code SAXSource}, {@code StAXSource}, or {@code DOMSource}. If
			 * it is an instance of {@link Source}, its {@code get} method will
			 * be called, and must return one of those four supported types.
			 */
			SourceResult set(javax.xml.transform.Source source)
			throws SQLException;

			/**
			 * Specialization of {@link #set(javax.xml.transform.Source) set}
			 * for an argument of type {@code StreamSource}.
			 *<p>
			 * It may encapsulate either an {@code InputStream} or a {@code
			 * Reader}. In either case (even for a {@code Reader}), the start
			 * of the stream will be checked for an encoding declaration and
			 * compared to PostgreSQL's server encoding. If the encoding
			 * matches, a direct copy is done. If the encoding does not match
			 * but the source character set is contained in the server character
			 * set, a transcoding via Unicode is done. In either case, an XML
			 * parser is used to verify that the copied content is XML, and the
			 * parser's restrictions can be adjusted by the methods on this
			 * interface.
			 *<p>
			 * If the source character set is neither the same as nor contained
			 * in the server's, the content will be parsed to SAX events and
			 * reserialized into the server encoding, and this parser's
			 * restrictions can be adjusted by the methods on this interface.
			 */
			SourceResult set(javax.xml.transform.stream.StreamSource source)
			throws SQLException;

			/**
			 * Specialization of {@link #set(javax.xml.transform.Source) set}
			 * for an argument of type {@code SAXSource}.
			 *<p>
			 * Because the content will be received in an already-parsed form,
			 * the parser-adjusting methods will have no effect.
			 */
			SourceResult set(javax.xml.transform.sax.SAXSource source)
			throws SQLException;

			/**
			 * Specialization of {@link #set(javax.xml.transform.Source) set}
			 * for an argument of type {@code StAXSource}.
			 *<p>
			 * Because the content will be received in an already-parsed form,
			 * the parser-adjusting methods will have no effect.
			 */
			SourceResult set(javax.xml.transform.stax.StAXSource source)
			throws SQLException;

			/**
			 * Provide the content to be copied in the form of a {@code String}.
			 *<p>
			 * An exception from the pattern of {@code Source}-typed arguments,
			 * this method simplifies retrofitting adjustments into code that
			 * was using {@code SQLXML}'s {@code setString}. Has the same effect
			 * as {@link #set(javax.xml.transform.stream.StreamSource) set} with
			 * a {@code StreamSource} wrapping a {@code StringReader} over the
			 * {@code String}.
			 */
			SourceResult set(String source)
			throws SQLException;

			/**
			 * Specialization of {@link #set(javax.xml.transform.Source) set}
			 * for an argument of type {@code DOMSource}.
			 *<p>
			 * Because the content will be received in an already-parsed form,
			 * the parser-adjusting methods will have no effect.
			 */
			SourceResult set(javax.xml.transform.dom.DOMSource source)
			throws SQLException;

			/**
			 * Return the result {@code SQLXML} instance ready for handing off
			 * to PostgreSQL.
			 *<p>
			 * This method must be called after any of the inherited adjustment
			 * methods.
			 */
			SQLXML getSQLXML() throws SQLException;
		}

		/**
		 * Adjusting version of a {@code StreamResult}.
		 *<p>
		 * In addition to the adjusting methods inherited from
		 * {@link Result} (which will apply to any XML parser the implementation
		 * constructs to verify the content written, otherwise having no
		 * effect), this interface supplies two methods to influence whether the
		 * constructed {@code StreamResult} will expect a binary stream or a
		 * character stream.
		 */
		public interface StreamResult
			extends Result<javax.xml.transform.stream.StreamResult>
		{
			StreamResult preferBinaryStream();
			StreamResult preferCharacterStream();
		}

		/**
		 * Adjusting version of a {@code SAXResult}.
		 *<p>
		 * The adjusting methods inherited from
		 * {@link Result} will apply to any XML parser the implementation
		 * constructs to verify the content written, otherwise having no
		 * effect.
		 */
		public interface SAXResult
			extends Result<javax.xml.transform.sax.SAXResult>
		{
		}
	}
}
