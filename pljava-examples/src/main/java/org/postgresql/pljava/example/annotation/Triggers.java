/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.example.annotation;

import java.sql.SQLException;

import org.postgresql.pljava.TriggerData;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.Trigger;
import static org.postgresql.pljava.annotation.Trigger.Called.*;
import static org.postgresql.pljava.annotation.Trigger.Event.*;
import static org.postgresql.pljava.annotation.Function.Security.*;

@SQLAction(
	provides = "foobar tables",
	install = {
		"CREATE TABLE javatest.foobar_1 ( username text, stuff text )",
		"CREATE TABLE javatest.foobar_2 ( username text, value numeric )"
	},
	remove = {
		"DROP TABLE javatest.foobar_2",
		"DROP TABLE javatest.foobar_1"
	})
public class Triggers
{
	/**
	 * insert user name in response to a trigger.
	 */
	@Function(
		requires = "foobar tables",
		schema = "javatest",
		security = INVOKER,
		triggers = {
			@Trigger(called = BEFORE, table = "foobar_1", events = { INSERT } ),
			@Trigger(called = BEFORE, table = "foobar_2", events = { INSERT } )
		})

	public static void insertUsername(TriggerData td)
	throws SQLException
	{
	}
}
