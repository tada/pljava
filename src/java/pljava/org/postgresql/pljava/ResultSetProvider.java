/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * An implementation of this interface is returned from functions and procedures
 * that are declared to return <code>SET OF</code> a complex type. Functions that
 * return <code>SET OF</code> a simple type should simply return an
 * {@link java.util.Iterator Iterator}.
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
