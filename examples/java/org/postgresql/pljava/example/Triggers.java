/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import org.postgresql.pljava.TriggerException;
import org.postgresql.pljava.TriggerData;
import org.postgresql.pljava.Server;

/**
 * This class contains some triggers that I found written in C under the
 * contrib/spi directory of the postgres source distribution. Code to create the
 * necessary tables, functions, triggers, and some code to actually
 * execute them can be found in class {@link org.postgresql.pljava.test.Tester}.
 *
 * @author Thomas Hallgren
 */
public class Triggers
{
	/**
	 * insert user name in response to a trigger.
	 */
	static void insertUsername(TriggerData td)
	throws SQLException
	{
		if(td.isFiredForStatement())
			throw new TriggerException(td, "can't process STATEMENT events");

		if(td.isFiredAfter())
			throw new TriggerException(td, "must be fired before event");

		if(td.isFiredByDelete())
			throw new TriggerException(td, "can't process DELETE events");

		ResultSet _new = td.getNew();
		String[] args = td.getArguments();
		if(args.length != 1)
			throw new TriggerException(td, "one argument was expected");

		if(_new.getString(args[0]) == null)
			_new.updateString(args[0], Server.getSession().getUserName());
	}

	/**
	 * Update a modification time when the row is updated.
	 */
	static void moddatetime(TriggerData td)
	throws SQLException
	{
		if(td.isFiredForStatement())
			throw new TriggerException(td, "can't process STATEMENT events");

		if(td.isFiredAfter())
			throw new TriggerException(td, "must be fired before event");

		if(!td.isFiredByUpdate())
			throw new TriggerException(td, "can only process UPDATE events");

		ResultSet _new = td.getNew();
		String[] args = td.getArguments();
		if(args.length != 1)
			throw new TriggerException(td, "one argument was expected");
		_new.updateTimestamp(args[0], new Timestamp(System.currentTimeMillis()));
	}
}
