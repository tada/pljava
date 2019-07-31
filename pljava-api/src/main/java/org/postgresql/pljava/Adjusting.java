/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
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
import javax.xml.stream.XMLStreamReader;
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
	 *<h1>XML parser behavior adjustments</h1>
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
		 * The {@link #get get()} method can only be called once. The other,
		 * adjusting methods can only be called before {@code get()}.
		 *<p>
		 * The adjusting methods are best-effort and do not provide an
		 * indication of whether the requested adjustment was made. Not all of
		 * the adjustments are available for all flavors of {@code Source} or
		 * for all parser implementations or versions the Java runtime may
		 * supply.
		 *<p>
		 * Although this extends {@code javax.xml.transform.Source},
		 * implementing classes will likely throw exceptions from the
		 * {@code Source}-specific methods for getting and setting system IDs.
		 * Those methods, if needed, should be called on the {@code Source}
		 * object obtained from {@code get()}.
		 */
		public interface Source<T extends javax.xml.transform.Source>
		extends javax.xml.transform.Source
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

			/** Whether to allow a DTD at all. */
			Source<T> allowDTD(boolean v);

			/**
			 * Whether to retrieve external "general" entities (those
			 * that can be used in the document body) declared in the DTD.
			 */
			Source<T> externalGeneralEntities(boolean v);

			/**
			 * Whether to retrieve external "parameter" entities (those
			 * declared with a {@code %} and usable only within the DTD)
			 * declared in the DTD.
			 */
			Source<T> externalParameterEntities(boolean v);

			/**
			 * Whether to retrieve any external DTD subset declared in the DTD.
			 */
			Source<T> loadExternalDTD(boolean v);

			/**
			 * Whether to honor XInclude syntax in the document.
			 */
			Source<T> xIncludeAware(boolean v);

			/**
			 * Whether to expand entity references in the document to their
			 * declared replacement content.
			 */
			Source<T> expandEntityReferences(boolean v);

			/**
			 * For a feature that may have been identified by more than one URI
			 * in different parsers or versions, try passing the supplied
			 * <em>value</em> with each URI from <em>names</em> in order until
			 * one is not rejected by the underlying parser.
			 */
			Source<T> setFirstSupportedFeature(boolean value, String... names);

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
			Source<T> defaults();
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
	}
}
