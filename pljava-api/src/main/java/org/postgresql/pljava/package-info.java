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
 * The PL/Java API for use in writing database procedures, functions, and types
 * using PL/Java.
 *
 * Along with the API in this package, PL/Java code will mosty interact with
 * the database using the specialized, direct version of the
 * {@linkplain java.sql JDBC API}, obtained (as decreed in the SQL/JRT specs)
 * from {@link java.sql.DriverManager#getConnection(String)} by passing it
 * the magic URL <code>jdbc:default:connection</code>.
 * @author Thomas Hallgren
 */
package org.postgresql.pljava;
