/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.example;

import java.sql.SQLException;
import java.sql.Timestamp;

import org.postgresql.pljava.AclId;
import org.postgresql.pljava.Relation;
import org.postgresql.pljava.TriggerData;
import org.postgresql.pljava.TriggerException;
import org.postgresql.pljava.Tuple;

/**
 * This class contains some triggers that I found written in C under the
 * contrib/spi directory of the postgres source distribution. Code to create the
 * necessary tables, functions, triggers, and some code to actually
 * execute them can be found in class {@link org.postgres.pljava.test.Tester}.
 *
 * @author Thomas Hallgren
 */
public class Triggers
{
	/**
	 * insert user name in response to a trigger.
	 */
	static Tuple insertUsername(TriggerData td)
	throws SQLException
	{
		if(td.isFiredForStatement())
			throw new TriggerException(td, "can't process STATEMENT events");

		if(td.isFiredAfter())
			throw new TriggerException(td, "must be fired before event");

		Tuple tuple;
		if(td.isFiredByInsert())
			tuple = td.getTriggerTuple();
		else if(td.isFiredByUpdate())
			tuple = td.getNewTuple();
		else
			throw new TriggerException(td, "can't process DELETE events");

		String[] args = td.getArguments();
		if(args.length != 1)
			throw new TriggerException(td, "one argument was expected");

		Relation rel = td.getRelation();
		int attnum = rel.getTupleDesc().getColumnIndex(args[0]);
		if(tuple.getObject(attnum) == null)
		{	
			String userName = AclId.getUser().getName();
			tuple = rel.modifyTuple(tuple, new int[] { attnum }, new Object[] { userName });
		}
		return tuple;
	}

	/**
	 * Update a modification time when the row is updated.
	 */
	static Tuple moddatetime(TriggerData td)
	throws SQLException
	{
		if(td.isFiredForStatement())
			throw new TriggerException(td, "can't process STATEMENT events");

		if(td.isFiredAfter())
			throw new TriggerException(td, "must be fired before event");

		if(!td.isFiredByUpdate())
			throw new TriggerException(td, "can only process UPDATE events");

		String[] args = td.getArguments();
		if(args.length != 1)
			throw new TriggerException(td, "one argument was expected");

		Timestamp now = new Timestamp(System.currentTimeMillis());
		Relation rel = td.getRelation();
		int attnum = rel.getTupleDesc().getColumnIndex(args[0]);
		return rel.modifyTuple(td.getNewTuple(), new int[] { attnum }, new Object[] { now });
	}
}
