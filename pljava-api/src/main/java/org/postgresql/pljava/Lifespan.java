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
package org.postgresql.pljava;

import org.postgresql.pljava.model.MemoryContext; // javadoc
import org.postgresql.pljava.model.ResourceOwner; // javadoc

/**
 * Model of any notional object in PostgreSQL or PL/Java that has a definite
 * temporal existence, with a detectable end, and so can be used to scope the
 * lifetime of any PL/Java object that has corresponding native resources.
 *<p>
 * A {@code Lifespan} generalizes over assorted classes that can play that role,
 * such as PostgreSQL's {@link ResourceOwner ResourceOwner} and
 * {@link MemoryContext MemoryContext}. {@code MemoryContext} may see the most
 * use in PL/Java, as the typical reason to scope the lifetime of some PL/Java
 * object is that it refers to some allocation of native memory.
 *<p>
 * The invocation of a PL/Java function is also usefully treated as a resource
 * owner. It is reasonable to depend on the objects passed in the function call
 * to remain usable as long as the call is on the stack, if no other explicit
 * lifespan applies.
 *<p>
 * Java's incubating foreign function and memory API will bring a
 * {@code ResourceScope} object for which some relation to a PL/Java
 * {@code Lifespan} can probably be defined.
 *<p>
 * The history of PostgreSQL <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/mmgr/README;hb=HEAD"
>MemoryContext</a>s
 * (the older mechanism, appearing in PostgreSQL 7.1), and <a href=
"https://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/backend/utils/resowner/README;hb=HEAD"
>ResourceOwner</a>s
 * (introduced in 8.0) is interesting. As the latter's {@code README} puts it,
 * <q>The design of the ResourceOwner API is modeled on our MemoryContext API,
 * which has proven very flexible and successful ... It is tempting to consider
 * unifying ResourceOwners and MemoryContexts into a single object type, but
 * their usage patterns are sufficiently different ...."</q>
 *<p>
 * Only later, in PostgreSQL 9.5, did {@code MemoryContext} gain a callback
 * mechanism for detecting reset or delete, with which it also becomes usable
 * as a kind of lifespan under PL/Java's broadened view of the concept.
 * While not <q>unifying ResourceOwners and MemoryContexts into a single
 * object type</q>, PL/Java here makes them both available as subtypes of a
 * common interface, so either can be chosen to place an appropriate temporal
 * scope on a PL/Java object.
 */
public interface Lifespan
{
}
