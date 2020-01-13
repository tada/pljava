/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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
 * <p>PL/Java's legacy bridge code between {@code java.util.logging} and
 * PostgreSQL's error logging mechanisms, isolated here in a package that can be
 * exported to the {@code java.logging} module, as that API requires.
 */
package org.postgresql.pljava.elog;
