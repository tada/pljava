/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>TriggerData</code> correspons to the internal PostgreSQL
 * <code>TriggerData</code>.
 *
 * @author Thomas Hallgren
 */
public class TriggerData extends NativeStruct
{
	/**
	 * Returns a descriptor for the Tuples exposed by this
	 * trigger.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native Relation getRelation();

	/**
	 * Returns a <code>Tuple</code> reflecting the row for which
	 * the trigger was fired. This is the row being inserted,
	 * updated, or deleted. If this trigger was fired for an <code>
	 * INSERT</code> or <code>DELETE</code> then this is what you
	 * should return to from the method if you don't want to replace
	 * the row with a different one (in the case of <code>INSERT
	 * </code>) or skip the operation. 
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native Tuple getTriggerTuple();

	/**
	 * Returns a <code>Tuple</code> reflecting the new version of
	 * the row, if the trigger was fired for an <code>UPDATE</code>,
	 * and <code>null</code> if it is for an <code>INSERT</code> or
	 * a <code>DELETE</code>. This is what you have to return from
	 * the function if the event is an <code>UPDATE</code> and you
	 * don't want to replace this row by a different one or skip the
	 * operation. 
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native Tuple getNewTuple();

	/**
	 * Returns the arguments for this trigger (as declared
	 * in the <code>CREATE TRIGGER</code> statement. If
	 * the trigger has no arguments, this method will return
	 * an array with size 0.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native String[] getArguments()
	throws SQLException;

	/**
	 * Returns the name of the trigger (as declared
	 * in the <code>CREATE TRIGGER</code> statement).
	 */
	public native String getName()
	throws SQLException;

	/**
	 * Returns <code>true</code> if the trigger was fired after the
	 * statement or row action that it is associated with.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredAfter()
	throws SQLException;

	/**
	 * Returns <code>true</code> if the trigger was fired before the
	 * statement or row action that it is associated with.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredBefore()
	throws SQLException;

	/**
	 * Returns <code>true</code> if this trigger is fired once
	 * for each row (as opposed to once for the entire statement).
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredForEachRow()
	throws SQLException;

	/**
	 * Returns <code>true</code> if this trigger is fired once
	 * for the entire statement (as opposed to once for each row).
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredForStatement()
	throws SQLException;

	/**
	 * Returns <code>true</code> if this trigger was fired
	 * by a <code>DELETE</code>.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredByDelete()
	throws SQLException;

	/**
	 * Returns <code>true</code> if this trigger was fired
	 * by an <code>INSERT</code>.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredByInsert()
	throws SQLException;

	/**
	 * Returns <code>true</code> if this trigger was fired
	 * by an <code>UPDATE</code>.
	 * @throws SQLException if the contained native buffer has gone stale.
	 */
	public native boolean isFiredByUpdate()
	throws SQLException;
}
