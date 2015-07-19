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
 * Not strictly part of the API, this package contains the compiler extension
 * itself that recognizes
 * {@linkplain org.postgresql.pljava.annotation PL/Java annotations} and
 * generates the deployment descriptor. It is part of this module so that the
 * <code>pljava-api</code> jar will be all that is needed on the class path
 * when compiling PL/Java code, even with annotations.
 */
package org.postgresql.pljava.sqlgen;
