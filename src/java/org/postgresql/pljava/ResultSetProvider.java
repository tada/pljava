/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An implementation of this interface is returned from functions and procedures
 * that are declared to return <code>SET OF</code> a type.
 * @author Thomas Hallgren
 */
public interface ResultSetProvider
{
	/**
	 * This method is called once for each row that should be returned from
	 * a procedure that returns a set of rows.
	 * @param receiver Receiver of values for the given row.
	 * @param currentRow Row number. First call will have row number 0.
	 * @return <code>true</code> if a new row was provided, <code>false</code>
	 * if not (end of data).
	 * @throws SQLException
	 */
	boolean assignRowValues(ResultSet receiver, int currentRow)
	throws SQLException;
}
