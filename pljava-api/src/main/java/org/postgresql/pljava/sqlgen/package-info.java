/*
 * Copyright (c) 2015 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
/**
 * <p>Not strictly part of the API, this package contains the compiler extension
 * itself that recognizes
 * {@linkplain org.postgresql.pljava.annotation PL/Java annotations} and
 * generates the deployment descriptor. It is part of this module so that the
 * <code>pljava-api</code> jar will be all that is needed on the class path
 * when compiling PL/Java code, even with annotations.
 *
 * <p><strong>Limitation note:</strong> A Java bug introoduced in Java 7
 * required a workaround that was added here in
 * <a href="https://github.com/tada/pljava/pull/42">pull #42</a>. The workaround
 * has a limitation: if you are compiling Java sources that also use other
 * annotations and other annotation processors, and if those other processors
 * can write new Java files and cause more than one round of compilation, they
 * must not include <code>org.postgresql.pljava.annotation</code> annotations
 * in those files. This code needs to find all such annotations in round 1.
 *
 * <p>If Oracle fixes the underlying bug, the limitation can be removed.
 * Oracle's bug site suggests that won't happen until Java 9, if then.
 */
package org.postgresql.pljava.sqlgen;
