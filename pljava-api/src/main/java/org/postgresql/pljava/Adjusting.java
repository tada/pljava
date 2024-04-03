/*
 * Copyright (c) 2019-2024 Tada AB and other contributors, as listed below.
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
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.function.Consumer;
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
		 * Attempts a given action (typically to set something) using a given
		 * value, trying one or more supplied keys in order until the action
		 * succeeds with no exception.
		 *<p>
		 * This logic is common to the
		 * {@link Parsing#setFirstSupportedFeature setFirstSupportedFeature}
		 * and
		 * {@link Parsing#setFirstSupportedProperty setFirstSupportedProperty}
		 * methods, and is exposed here because it may be useful for other
		 * tasks in Java's XML APIs, such as configuring {@code Transformer}s.
		 *<p>
		 * If any attempt succeeds, null is returned. If no attempt
		 * succeeds, the first exception caught is returned, with any
		 * exceptions from the subsequent attempts retrievable from it with
		 * {@link Exception#getSuppressed getSuppressed}. The return is
		 * immediate, without any remaining names being tried, if an exception
		 * is caught that is not assignable to a class in the
		 * <var>expected</var> list. Such an exception is returned (or added to
		 * the suppressed list of an exception already to be returned) only if
		 * the <var>onUnexpected</var> handler is null; otherwise, it is passed
		 * to the handler and does not affect the method's return.
		 *<p>
		 * For some purposes, a single call of this method may not suffice: if
		 * alternate means to establish a desired configuration have existed and
		 * are not simply alternate property names that will accept the same
		 * value. For such a case, this method may be called more than once. The
		 * caller abandons the sequence of calls after the first call that
		 * returns null (indicating that it either succeeded, or incurred an
		 * unexpected exception and passed it to the <var>onUnexpected</var>
		 * handler. Otherwise, the exception returned by the first call can be
		 * passed as <var>caught</var> to the next call, instead of passing the
		 * usual null. (When a non-null <var>caught</var> is passed, it will be
		 * returned on failure, even if an unexpected exception has been caught;
		 * therefore, should it ever be necessary to chain more than two of
		 * these calls, the caller should abandon the sequence as soon as a call
		 * returns null <em>or</em> returns its <var>caught</var> argument with
		 * no growth of its suppressed list.)
		 * @param setter typically a method reference for a method that
		 * takes a string key and some value.
		 * @param value the value to pass to the setter
		 * @param expected a list of exception classes that can be foreseen
		 * to indicate that a key was not recognized, and the operation
		 * should be retried with the next possible key.
		 * @param caught null, or an exception returned by a preceding call if
		 * an operation cannot be implemented with one call of this method
		 * @param onUnexpected invoked, if non-null, on an {@code Exception}
		 * that is caught and matches nothing in the expected list, instead
		 * of returning it. If this parameter is null, such an exception is
		 * returned (or added to the suppressed list of the exception to be
		 * returned), just as for expected exceptions, but the return is
		 * immediate, without trying remaining names, if any.
		 * @param names one or more String keys to be tried in order until
		 * the action succeeds.
		 * @return null if any attempt succeeded, or if the first exception
		 * caught was passed to the onUnexpected handler; otherwise the first
		 * exception caught (if the caller supplied a non-null
		 * <var>caught</var>, then that exception), which may have further
		 * exceptions in its suppressed list.
		 */
		public static <T, V extends T> Exception setFirstSupported(
			SetMethod<? super T> setter, V value,
			List<Class<? extends Exception>> expected,
			Exception caught,
			Consumer<? super Exception> onUnexpected, String... names)
		{
			requireNonNull(expected);
			for ( String name : names )
			{
				try
				{
					setter.set(name, value);
					return null;
				}
				catch ( Exception e )
				{
					boolean benign =
						expected.stream().anyMatch(c -> c.isInstance(e));

					if ( benign  ||  null == onUnexpected )
					{
						if ( null == caught )
							caught = e;
						else
							caught.addSuppressed(e);
					}
					else
						onUnexpected.accept(e);

					if ( ! benign )
						break;
				}
			}
			return caught;
		}

		/**
		 * Calls the six-argument overload passing null for <var>caught</var>.
		 */
		public static <T, V extends T> Exception setFirstSupported(
			SetMethod<? super T> setter, V value,
			List<Class<? extends Exception>> expected,
			Consumer<? super Exception> onUnexpected, String... names)
		{
			return setFirstSupported(
				setter, value, expected, null, onUnexpected, names);
		}

		/**
		 * A functional interface fitting various {@code setFeature} or
		 * {@code setProperty} methods in Java XML APIs.
		 *<p>
		 * The XML APIs have a number of methods on various interfaces that can
		 * be used to set some property or feature, and can generally be
		 * assigned to this functional interface by bound method reference, and
		 * used with {@link #setFirstSupported setFirstSupported}.
		 */
		@FunctionalInterface
		public interface SetMethod<T>
		{
			void set(String key, T value) throws Exception;
		}

		/**
		 * Interface with methods to adjust the restrictions on XML parsing
		 * that are commonly considered when XML content might be from untrusted
		 * sources.
		 *<p>
		 * The adjusting methods are best-effort; not all of
		 * the adjustments are available for all flavors of {@code Source} or
		 * {@code Result} or for all parser implementations or versions the Java
		 * runtime may supply. Cases where a requested adjustment has not been
		 * made are handled as follows:
		 *<p>
		 * Any sequence of adjustment calls will ultimately be followed by a
		 * {@code get}. During the sequence of adjustments, exceptions caught
		 * are added to a signaling list or to a quiet list, where "added to"
		 * means that if either list has a first exception, any caught later are
		 * attached to that exception with
		 * {@link Exception#addSuppressed addSuppressed}.
		 *<p>
		 * For each adjustment (and depending on the type of underlying
		 * {@code Source} or {@code Result}), one or more exception types will
		 * be 'expected' as indications that an identifying key or value for
		 * that adjustment was not recognized. This implementation may continue
		 * trying to apply the adjustment, using other keys that have at times
		 * been used to identify it. Expected exceptions caught during these
		 * attempts form a temporary list (a first exception and those attached
		 * to it by {@code addSuppressed}). Once any such attempt succeeds, the
		 * adjustment is considered made, and any temporary expected exceptions
		 * list from the adjustment is discarded. If no attempt succeeded, the
		 * temporary list is retained, by adding its head exception to the quiet
		 * list.
		 *<p>
		 * Any exceptions caught that are not instances of any of the 'expected'
		 * types are added to the signaling list.
		 *<p>
		 * When {@code get} is called, the head exception on the signaling list,
		 * if any, is thrown. Otherwise, the head exception on the quiet list,
		 * if any, is logged at {@code WARNING} level.
		 *<p>
		 * During a chain of adjustments, {@link #lax lax()} can be called to
		 * tailor the handling of the quiet list. A {@code lax()} call applies
		 * to whatever exceptions have been added to the quiet list up to that
		 * point. To discard them, call {@code lax(true)}; to move them to the
		 * signaling list, call {@code lax(false)}.
		 */
		public interface Parsing<T extends Parsing<T>>
		{
			/** Whether to allow a DTD at all. */
			T allowDTD(boolean v);

			/**
			 * Specifies that any DTD should be ignored (neither processed nor
			 * rejected as an error).
			 *<p>
			 * This treatment is available in Java 22 and later.
			 * In earlier Java versions, this will not succeed. Where it is
			 * supported, the most recent call of this method or of
			 * {@link #allowDTD allowDTD} will be honored.
			 */
			T ignoreDTD();

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
			 * in different parsers or versions, tries passing the supplied
			 * <em>value</em> with each URI from <em>names</em> in order until
			 * one is not rejected by the underlying parser.
			 */
			T setFirstSupportedFeature(boolean value, String... names);

			/**
			 * Makes a best effort to apply the recommended, restrictive
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
			 * versions, tries passing the supplied <em>value</em> with each URI
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
			 * Sets an {@link EntityResolver} of the type used by SAX and DOM
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
			 * Sets a {@link Schema} to be applied during SAX or DOM parsing
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

			/**
			 * Tailors the treatment of 'quiet' exceptions during a chain of
			 * best-effort adjustments.
			 *<p>
			 * See {@link Parsing the class description} for an explanation of
			 * the signaling and quiet lists.
			 *<p>
			 * This method applies to whatever exceptions may have been added to
			 * the quiet list by best-effort adjustments made up to that point.
			 * They can be moved to the signaling list with {@code lax(false)},
			 * or simply discarded with {@code lax(true)}. In either case, the
			 * quiet list is left empty when {@code lax} returns.
			 *<p>
			 * At the time a {@code get} method is later called, any exception
			 * at the head of the signaling list will be thrown (possibly
			 * wrapped in an exception permitted by {@code get}'s {@code throws}
			 * clause), with any later exceptions on that list retrievable from
			 * the head exception with
			 * {@link Exception#getSuppressed getSuppressed}. Otherwise, any
			 * exception at the head of the quiet list (again with any later
			 * ones attached as its suppressed list) will be logged at
			 * {@code WARNING} level.
			 */
			T lax(boolean discard);
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
			 * Returns an object of the expected {@code Source} subtype
			 * reflecting any adjustments made with the other methods.
			 *<p>
			 * Refer to {@link Parsing the {@code Parsing} class description}
			 * and the {@link Parsing#lax lax()} method for how any exceptions
			 * caught while applying best-effort adjustments are handled.
			 * @return an implementing object of the expected Source subtype
			 * @throws SQLException for any reason that {@code getSource} might
			 * have thrown when supplying the corresponding non-Adjusting
			 * subtype of Source, or for reasons saved while applying
			 * adjustments.
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
			 * Returns an object of the expected {@code Result} subtype
			 * reflecting any adjustments made with the other methods.
			 * Refer to {@link Parsing the {@code Parsing} class description}
			 * and the {@link Parsing#lax lax()} method for how any exceptions
			 * caught while applying best-effort adjustments are handled.
			 * @return an implementing object of the expected Result subtype
			 * @throws SQLException for any reason that {@code getResult} might
			 * have thrown when supplying the corresponding non-Adjusting
			 * subtype of Result, or for reasons saved while applying
			 * adjustments.
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
			 * Supplies the {@code Source} instance that is the source of the
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
			 * Provides the content to be copied in the form of a
			 * {@code String}.
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
			 * Returns the result {@code SQLXML} instance ready for handing off
			 * to PostgreSQL.
			 *<p>
			 * The handling/logging of exceptions normally handled in a
			 * {@code get} method happens here for a {@code SourceResult}.
			 *<p>
			 * Any necessary calls of the inherited adjustment methods must be
			 * made before this method is called.
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
