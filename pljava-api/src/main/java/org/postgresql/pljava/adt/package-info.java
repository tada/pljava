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
 * Package containing functional interfaces that document and present
 * PostgreSQL data types abstractly, but clearly enough for faithful mapping.
 *<p>
 * Interfaces in this package are meant to occupy a level between a PL/Java
 * {@link Adapter Adapter} (responsible for PostgreSQL internal details that
 * properly remain encapsulated) and some intended Java representation class
 * (which may encapsulate details of its own).
 *<h2>Example</h2>
 *<p>
 * Suppose an application would like to manipulate
 * a PostgreSQL {@code TIME WITH TIME ZONE} in the form of a Java
 * {@link OffsetTime OffsetTime} instance.
 *<p>
 * The application selects a PL/Java {@link Adapter Adapter} that handles the
 * PostgreSQL {@code TIME WITH TIME ZONE} type and presents it via the
 * functional interface {@link Datetime.TimeTZ Datetime.TimeTZ} in this package.
 *<p>
 * The application can instantiate that {@code Adapter} with some implementation
 * (possibly just a lambda) of that functional interface, which will construct
 * an {@code OffsetTime} instance. That {@code Adapter} instance now maps
 * {@code TIME WITH TIME ZONE} to {@code OffsetTime}, as desired.
 *<p>
 * The PostgreSQL internal details are handled by the {@code Adapter}. The
 * internal details of {@code OffsetTime} are {@code OffsetTime}'s business.
 * In between those two sits the {@link Datetime.TimeTZ Datetime.TimeTZ}
 * interface in this package, with its one simple role: it presents the value
 * in a clear, documented form as consisting of:
 *<ul>
 *<li>microseconds since midnight, and
 *<li>a time zone offset in seconds west of the prime meridian
 *</ul>
 *<p>
 * It serves as a contract for the {@code Adapter} and as a clear starting point
 * for constructing the wanted Java representation.
 *<p>
 * It is important that the interfaces here serve as documentation as
 * well as code, as it turns out that {@code OffsetTime} expects its
 * time zone offsets to be positive <em>east</em> of the prime meridian,
 * so a sign flip is needed. Interfaces in this package must be
 * documented with enough detail to allow a developer to make <em>correct</em>
 * use of the exposed values.
 *<p>
 * The division of labor between what is exposed in these interfaces and what
 * is encapsulated within {@code Adapter}s calls for a judgment of <em>which
 * details are semantically significant</em>. If PostgreSQL somehow changes the
 * internal details needed to retrieve a {@code timetz} value, it should be the
 * {@code Adapter}'s job to make that transparent. If PostgreSQL ever changes
 * <em>the fact that a {@code timetz} is microseconds since midnight with
 * seconds-west as a zone offset</em>, that would require versioning the
 * corresponding interface here; it is something a developer would need to know.
 *<h2>Reference implementations</h2>
 * A few simple reference implementations (including the
 * {@code timetz}-as-{@code OffsetTime} used as the example) can also be found
 * in this package, and {@code Adapter} instances using them are available,
 * so an application would not really have to follow the steps of the example
 * to obtain one.
 * @author Chapman Flack
 */
package org.postgresql.pljava.adt;

import java.time.OffsetTime;

import org.postgresql.pljava.Adapter;
