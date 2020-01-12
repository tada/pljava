/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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
 * Contains PL/Java's internal implementation.
 */
module org.postgresql.pljava.internal
{
	requires java.base;
	requires java.management;
	requires org.postgresql.pljava;

	provides java.sql.Driver with org.postgresql.pljava.jdbc.SPIDriver;

	provides org.postgresql.pljava.Session
		with org.postgresql.pljava.internal.Session;
}
