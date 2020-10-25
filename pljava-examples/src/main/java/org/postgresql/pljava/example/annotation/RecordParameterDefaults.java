/*
 * Copyright (c) 2018-2020 Tada AB and other contributors, as listed below.
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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import static java.util.Arrays.fill;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Example demonstrating the use of a {@code RECORD} parameter as a way to
 * supply an arbitrary sequence of named, typed parameters to a PL/Java
 * function.
 *<p>
 * Also tests the proper DDR generation of defaults for such parameters.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example.
 */
@SQLAction(
	provides = "paramtypeinfo type", // created in Triggers.java
	install = {
		"CREATE TYPE javatest.paramtypeinfo AS (" +
		" name text, pgtypename text, javaclass text, tostring text" +
		")"
	},
	remove = {
		"DROP TYPE javatest.paramtypeinfo"
	}
)
public class RecordParameterDefaults implements ResultSetProvider
{
	/**
	 * Return the names, types, and values of parameters supplied as a single
	 * anonymous RECORD type; the parameter is given an empty-record default,
	 * allowing it to be omitted in calls, or used with the named-parameter
	 * call syntax.
	 *<p>
	 * For example, this function could be called as:
	 *<pre>
	 * SELECT (paramDefaultsRecord()).*;
	 *</pre>
	 * or as:
	 *<pre>
	 * SELECT (paramDefaultsRecord(params =&gt; s)).*
	 * FROM (SELECT 42 AS a, '42' AS b, 42.0 AS c) AS s;
	 *</pre>
	 */
	@Function(
		requires = "paramtypeinfo type",
		schema = "javatest",
		implementor = "postgresql_ge_80400", // supports function param DEFAULTs
		type = "javatest.paramtypeinfo"
		)
	public static ResultSetProvider paramDefaultsRecord(
		@SQLType(defaultValue={})ResultSet params)
	throws SQLException
	{
		return new RecordParameterDefaults(params);
	}

	/**
	 * Like paramDefaultsRecord but illustrating the use of a named row type
	 * with known structure, and supplying a default for the function
	 * parameter.
	 *<p>
	 *<pre>
	 * SELECT paramDefaultsNamedRow();
	 *
	 * SELECT paramDefaultsNamedRow(userWithNum =&gt; ('fred', 3.14));
	 *</pre>
	 */
	@Function(
		requires = "foobar tables", // created in Triggers.java
		implementor = "postgresql_ge_80400", // supports function param DEFAULTs
		schema = "javatest"
		)
	public static String paramDefaultsNamedRow(
		@SQLType(value="javatest.foobar_2", defaultValue={"bob", "42"})
		ResultSet userWithNum)
	throws SQLException
	{
		return String.format("username is %s and value is %s",
			userWithNum.getObject("username"), userWithNum.getObject("value"));
	}



	private final ResultSetMetaData m_paramrsmd;
	private final Object[] m_values;
	
	RecordParameterDefaults(ResultSet paramrs) throws SQLException
	{
		m_paramrsmd = paramrs.getMetaData();
		/*
		 * Grab the values from the parameter SingleRowResultSet now; it isn't
		 * guaranteed to stay valid for the life of the set-returning function.
		 */
		m_values = new Object [ m_paramrsmd.getColumnCount() ];
		for ( int i = 0; i < m_values.length; ++ i )
			m_values[i] = paramrs.getObject( 1 + i);
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow)
	throws SQLException
	{
		int col = 1 + currentRow;
		if ( col > m_paramrsmd.getColumnCount() )
			return false;
		receiver.updateString("name", m_paramrsmd.getColumnLabel(col));
		receiver.updateString("pgtypename", m_paramrsmd.getColumnTypeName(col));
		Object o = m_values[col - 1];
		receiver.updateString("javaclass", o.getClass().getName());
		receiver.updateString("tostring", o.toString());
		return true;
	}

	@Override
	public void close() throws SQLException
	{
		fill(m_values, null);
	}
}
