/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetHandle;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * Example implementing the {@code ResultSetHandle} interface, to return
 * the {@link ResultSet} from any SQL {@code SELECT} query passed as a string
 * to the {@link #executeSelect executeSelect} function.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example. Before PostgreSQL 8.4,
 * there was no {@code =} or {@code DISTINCT FROM} operator between row types.
 */
@SQLAction(requires="selecttorecords fn", implementor="postgresql_ge_80400",
install=
" SELECT " +
"  CASE WHEN r IS DISTINCT FROM ROW('Foo'::varchar, 1::integer, 1.5::float, " +
"       23.67::decimal(8,2), '2005-06-01'::date, '20:56'::time, " +
"       '192.168'::cidr) " +
"  THEN javatest.logmessage('WARNING', 'SetOfRecordTest not ok') " +
"  ELSE javatest.logmessage('INFO', 'SetOfRecordTest ok') " +
"  END " +
" FROM " +
"  javatest.executeselecttorecords( " +
"   'select ''Foo'',  1,  1.5::float,  23.67,  ''2005-06-01'',  " +
"           ''20:56''::time, ''192.168.0''') " +
"  AS r(t_varchar varchar, t_integer integer, t_float float, " +
"      t_decimal decimal(8,2), t_date date, t_time time, t_cidr cidr)"
)
public class SetOfRecordTest implements ResultSetHandle {

	@Function(schema="javatest", name="executeselecttorecords",
	          provides="selecttorecords fn")
	public static ResultSetHandle executeSelect(String selectSQL)
			throws SQLException {
		return new SetOfRecordTest(selectSQL);
	}

	private final PreparedStatement m_statement;

	public SetOfRecordTest(String selectSQL) throws SQLException {
		Connection conn = DriverManager
				.getConnection("jdbc:default:connection");
		m_statement = conn.prepareStatement(selectSQL);
	}

	@Override
	public void close() throws SQLException {
		m_statement.close();
	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		return m_statement.executeQuery();
	}
}
