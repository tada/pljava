/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
/**
 * Interfaces that model a useful subset of the PostgreSQL system catalogs
 * and related PostgreSQL abstractions for convenient Java access.
 *<h2>CatalogObject and its subinterfaces</h2>
 *<p>
 * The bulk of this package consists of interfaces extending
 * {@link CatalogObject CatalogObject}, corresponding to various database
 * objects represented in the PostgreSQL system catalogs.
 *<p>
 * In many of the PostgreSQL catalog tables, each row is identified by an
 * integer {@code oid}. When a row in a catalog table represents an object of
 * some kind, the {@code oid} of that row (plus an identifier for which table
 * it is defined in) will be enough to identify that object.
 *<h3>CatalogObject</h3>
 *<p>
 * In most of the catalog tables, reference to another object is by its bare
 * {@code oid}; the containing table is understood. For example, the
 * {@code prorettype} attribute of a row in {@code pg_proc} (the catalog of
 * procedures and functions) is a bare {@code oid}, understood to identify a row
 * in {@code pg_type}, namely, the data type that the function returns.
 *<p>
 * Such an {@code oid} standing alone, when the containing catalog is only
 * implied in context, is represented in PL/Java by an instance of the root
 * class {@link CatalogObject CatalogObject} itself. Such an object does not
 * carry much information; it can be asked for its {@code oid}, and it can be
 * combined with the {@code oid} of some catalog table to produce a
 * {@link CatalogObject.Addressed CatalogObject.Addressed}.
 *<h3>CatalogObject.Addressed</h3>
 *<p>
 * When the {@code oid} of a row in some catalog table is combined with an
 * identifier for <em>which</em> catalog table, the result is the explicit
 * address of an object. Because catalog tables themselves are defined by rows
 * in one particular catalog table ({@code pg_class}), all that is needed to
 * identify one is the {@code oid} of its defining row in {@code pg_class}.
 * Therefore, a pair of numbers {@code (classId, objectId)} is a complete
 * "object address" for most types of object in PostgreSQL. The {@code classId}
 * identifies a catalog table (by its row in {@code pg_class}), and therefore
 * what <em>kind</em> of object is intended, and the {@code objectId} identifies
 * the specific row in that catalog table, and therefore the specific object.
 *<p>
 * Such an {@code oid} pair is represented in PL/Java by an instance of
 * {@link CatalogObject.Addressed CatalogObject.Addressed}&mdash;or, more
 * likely, one of its specific subinterfaces in this package corresponding to
 * the type of object. A function, for example, may be identified by a
 * {@link RegProcedure RegProcedure} instance ({@code classId} identifies the
 * {@code pg_proc} table, {@code objectId} is the row for the function), and its
 * return type by a {@link RegType RegType} instance ({@code classId} identifies
 * the {@code pg_type} table, and {@code objectId} the row defining the data
 * type).
 *<h3>CatalogObject.Component</h3>
 *<p>
 * The only current exception in PostgreSQL to the
 * two-{@code oid}s-identify-an-object rule is for attributes (columns of tables
 * or components of composite types), which are identified by three numbers,
 * the {@code classId} and {@code objectId} of the parent object, plus a third
 * number {@code subId} for the component's position in the parent.
 * {@link Attribute Attribute}, therefore, is that rare subinterface that also
 * implements {@link CatalogObject.Component CatalogObject.Component}.
 *<p>
 * For the most part, that detail should be of no consequence to a user of this
 * package, who will probably only ever obtain {@code Attribute} instances
 * from a {@link TupleDescriptor TupleDescriptor}.
 *<h3>CatalogObject instances are singletons</h3>
 *<p>
 * Object instances in this catalog model are lazily-populated singletons
 * that exist upon being mentioned, and thereafter reliably identify the same
 * {@code (classId,objectId)} in the PostgreSQL catalogs. (Whether that
 * {@code (classId,objectId)} continues to identify the "same" thing in
 * PostgreSQL can be affected by data-definition commands being issued in
 * the same or some other session.) An instance is born lightweight, with only
 * its identifying triple of numbers. Its methods that further expose properties
 * of the addressed object (including whether any such object even exists)
 * do not obtain that information from the PostgreSQL system caches until
 * requested, and may then cache it in Java until signaled by PostgreSQL that
 * some catalog change has invalidated it.
 *<h2>CharsetEncoding</h2>
 *<p>
 * While not strictly a catalog object (PostgreSQL's supported encodings are
 * a hard-coded set, not represented in the catalogs), they are exposed by
 * {@link CharsetEncoding CharsetEncoding} instances that otherwise behave much
 * like the modeled catalog objects, and are returned by the {@code encoding()}
 * methods on {@link Database Database} and {@link RegCollation RegCollation}.
 * The one in use on the server (an often-needed value) is exposed by the
 * {@link CharsetEncoding#SERVER_ENCODING SERVER_ENCODING} static.
 *<h2>TupleTableSlot, TupleDescriptor, and Adapter</h2>
 *<p>
 * {@code TupleTableSlot} in PostgreSQL is a flexible abstraction that can
 * present several variant forms of native tuples to be manipulated with
 * a common API. Modeled on that, {@link TupleTableSlot TupleTableSlot} is
 * further abstracted, and can present a uniform API in PL/Java even to
 * tuple-like things&mdash;anything with a sequence of typed, possibly named
 * values&mdash;that might not be in the form of PostgreSQL native tuples.
 *<p>
 * The key to the order, types, and names of the components of a tuple is
 * its {@link TupleDescriptor TupleDescriptor}, which in broad strokes is little
 * more than a {@code List} of {@link Attribute Attribute}.
 *<p>
 * Given a tuple, and an {@code Attribute} that identifies its PostgreSQL data
 * type, the job of accessing that value as some appropriate Java type falls to
 * an {@link Adapter Adapter}, of which PL/Java provides a selection to cover
 * common types, and there is
 * a {@link org.postgresql.pljava.adt.spi service-provider interface} allowing
 * independent development of others.
 *<p>
 * PL/Java supplies simple adapters when a Java primitive or some existing
 * standard Java class is clearly the appropriate mapping for a PostgreSQL type.
 * Other than that (and excepting the model classes in this package), PL/Java
 * avoids defining new Java classes to represent other PostgreSQL types. Such
 * classes may already have been developed for an application, or may be found
 * in existing Java driver libraries for PostgreSQL, such as PGJDBC or
 * PGJDBC-NG. It would be unhelpful for PL/Java to offer another such,
 * independent and incompatible, set.
 *<p>
 * Instead, for PostgreSQL types that might not have an obvious, appropriate
 * mapping to a standard Java type, or that might have more than one plausible
 * mapping, PL/Java provides a set of <em>functional interfaces</em> in the
 * package {@link org.postgresql.pljava.adt}. An {@code Adapter} (encapsulating
 * internal details of a data type) can then expose the content in a documented,
 * semantically clear form, to a simple application-supplied functional
 * interface implementation or lambda that will produce a result of whatever
 * Java type the application may already wish to use.
 *
 * @author Chapman Flack
 */
package org.postgresql.pljava.model;

import org.postgresql.pljava.Adapter;
