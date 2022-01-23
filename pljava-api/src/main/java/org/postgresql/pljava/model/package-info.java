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
 * The bulk of this package consists of interfaces corresponding to various
 * database objects represented in the PostgreSQL system catalogs.
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
 *
 * @author Chapman Flack
 */
package org.postgresql.pljava.model;

import org.postgresql.pljava.Adapter;
