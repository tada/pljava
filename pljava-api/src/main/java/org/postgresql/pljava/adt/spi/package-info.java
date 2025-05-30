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
 * Types that will be of interest in the implementation of {@code Adapter}s.
 *<p>
 * First-class PL/Java support for a new PostgreSQL data type entails
 * implementation of an {@link Adapter Adapter}. Unlike non-{@code Adapter}
 * code, an {@code Adapter} implementation may have to concern itself with
 * the facilities in this package, {@code Datum} in particular. An
 * {@code Adapter} should avoid leaking a {@code Datum} to non-{@code Adapter}
 * code.
 *<h2>Adapter manager</h2>
 *<p>
 * There needs to be an {@code Adapter}-manager service to accept application
 * requests to connect <var>x</var> PostgreSQL type with <var>y</var> Java type
 * and find or compose available {@code Adapter}s (built-in or by service
 * loader) to do so. There is some work in that direction (the methods in
 * {@link AbstractType AbstractType} should be helpful), but no such manager
 * yet.
 * @author Chapman Flack
 */
package org.postgresql.pljava.adt.spi;

import org.postgresql.pljava.Adapter;
