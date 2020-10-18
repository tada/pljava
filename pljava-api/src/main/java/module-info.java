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
 * Defines the API for PL/Java.
 */
@SuppressWarnings("module") // don't warn that o.p.p.internal's not visible yet
module org.postgresql.pljava
{
	requires java.base;
	requires transitive java.sql;
	requires transitive java.compiler;

	exports org.postgresql.pljava;
	exports org.postgresql.pljava.annotation;
	exports org.postgresql.pljava.sqlgen;

	exports org.postgresql.pljava.annotation.processing
		to  org.postgresql.pljava.internal;

	uses org.postgresql.pljava.Session;

	provides javax.annotation.processing.Processor
		with org.postgresql.pljava.annotation.processing.DDRProcessor;
}
