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
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;

import org.postgresql.pljava.TriggerData;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.Trigger;
import static org.postgresql.pljava.annotation.Trigger.Called.*;
import static org.postgresql.pljava.annotation.Trigger.Constraint.*;
import static org.postgresql.pljava.annotation.Trigger.Event.*;
import static org.postgresql.pljava.annotation.Trigger.Scope.*;
import static org.postgresql.pljava.annotation.Function.Security.*;

import static org.postgresql.pljava.example.LoggerTest.logMessage;

/**
 * Example creating a couple of tables, and a function to be called when
 * triggered by insertion into either table. In PostgreSQL 10 or later,
 * also create a function and trigger that uses transition tables.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example. Constraint triggers
 * appear in PG 9.1, transition tables in PG 10.
 */
@SQLAction(
	provides = "foobar tables",
	install = {
		"CREATE TABLE javatest.foobar_1 ( username text, stuff text )",
		"CREATE TABLE javatest.foobar_2 ( username text, value numeric )"
	},
	remove = {
		"DROP TABLE javatest.foobar_2",
		"DROP TABLE javatest.foobar_1"
	}
)
@SQLAction(
	requires = "constraint triggers",
	install = "INSERT INTO javatest.foobar_2(value) VALUES (45)"
)
@SQLAction(
	requires = "foobar triggers",
	provides = "foobar2_42",
	install = "INSERT INTO javatest.foobar_2(value) VALUES (42)"
)
@SQLAction(
	requires = { "transition triggers", "foobar2_42" },
	install = "UPDATE javatest.foobar_2 SET value = 43 WHERE value = 42"
)
/*
 * Note for another day: this would seem an excellent place to add a
 * regression test for github issue #134 (make sure invocations of a
 * trigger do not fail with SPI_ERROR_UNCONNECTED). However, any test
 * here that runs from the deployment descriptor will be running when
 * SPI is already connected, so a regression would not be caught.
 * A proper test for it will have to wait for a proper testing harness
 * invoking tests from outside PL/Java itself.
 */
public class Triggers
{
	/**
	 * insert user name in response to a trigger.
	 */
	@Function(
		requires = "foobar tables",
		provides = "foobar triggers",
		schema = "javatest",
		security = INVOKER,
		triggers = {
			@Trigger(called = BEFORE, table = "foobar_1", events = { INSERT } ),
			@Trigger(called = BEFORE, scope = ROW, table = "foobar_2",
					 events = { INSERT } )
		})

	public static void insertUsername(TriggerData td)
	throws SQLException
	{
		ResultSet nrs = td.getNew(); // expect NPE in a DELETE/STATEMENT trigger
		String col2asString = nrs.getString(2);
		if ( "43".equals(col2asString) )
			td.suppress();
		nrs.updateString( "username", "bob");
	}

	/**
	 * Examine old and new rows in reponse to a trigger.
	 * Transition tables first became available in PostgreSQL 10.
	 */
	@Function(
		implementor = "postgresql_ge_100000",
		requires = "foobar tables",
		provides = "transition triggers",
		schema = "javatest",
		security = INVOKER,
		triggers = {
			@Trigger(called = AFTER, table = "foobar_2", events = { UPDATE },
			         tableOld = "oldrows", tableNew = "newrows" )
		})

	public static void examineRows(TriggerData td)
	throws SQLException
	{
		Connection co = DriverManager.getConnection("jdbc:default:connection");
		Statement st = co.createStatement();
		ResultSet rs = st.executeQuery(
			"SELECT o.value, n.value" +
			" FROM oldrows o FULL JOIN newrows n USING (username)");
		rs.next();
		int oval = rs.getInt(1);
		int nval = rs.getInt(2);
		if ( 42 == oval && 43 == nval )
			logMessage( "INFO", "trigger transition table test ok");
		else
			logMessage( "WARNING", String.format(
				"trigger transition table oval %d nval %d", oval, nval));
	}

	/**
	 * Throw exception if value to be inserted is 44.
	 * Constraint triggers first became available in PostgreSQL 9.1.
	 */
	@Function(
		implementor = "postgresql_ge_90100",
		requires = "foobar tables",
		provides = "constraint triggers",
		schema = "javatest",
		security = INVOKER,
		triggers = {
			@Trigger(called = AFTER, table = "foobar_2", events = { INSERT },
			         scope = ROW, constraint = NOT_DEFERRABLE )
		})

	public static void disallow44(TriggerData td)
	throws SQLException
	{
		ResultSet nrs = td.getNew();
		if ( 44 == nrs.getInt( "value") )
			throw new SQLIntegrityConstraintViolationException(
				"44 shall not be inserted", "23000");
	}
}
