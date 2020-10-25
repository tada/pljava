/*
 * Copyright (c) 2019-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.SQLXML;

import java.sql.SQLException;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

import static org.postgresql.pljava.example.LoggerTest.logMessage;

/**
 * Class illustrating use of {@link SQLXML} to operate on non-XML data types
 * for which PL/Java provides an XML rendering.
 *<p>
 * Everything mentioning the type XML here needs a conditional implementor tag
 * in case of being loaded into a PostgreSQL instance built without that type.
 * The {@code pg_node_tree} type appears in 9.1.
 */
@SQLAction(implementor="postgresql_ge_90100",
	provides="postgresql_xml_ge91",
	install=
	"SELECT CASE (SELECT 1 FROM pg_type WHERE typname = 'xml') WHEN 1" +
	" THEN set_config('pljava.implementors', 'postgresql_xml_ge91,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)
public class XMLRenderedTypes
{
	@Function(schema="javatest", implementor="postgresql_xml_ge91")
	public static SQLXML pgNodeTreeAsXML(@SQLType("pg_node_tree") SQLXML pgt)
	throws SQLException
	{
		return pgt;
	}
}
