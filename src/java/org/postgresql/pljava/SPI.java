/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

/**
 * The <code>SPI</code> class provides access to some global
 * variables used by SPI.
 *
 * @author Thomas Hallgren
 */
public class SPI extends NativeStruct
{
	public static final int ERROR_CONNECT		= -1;
	public static final int ERROR_COPY			= -2;
	public static final int ERROR_OPUNKNOWN		= -3;
	public static final int ERROR_UNCONNECTED	= -4;
	public static final int ERROR_CURSOR		= -5;
	public static final int ERROR_ARGUMENT		= -6;
	public static final int ERROR_PARAM			= -7;
	public static final int ERROR_TRANSACTION	= -8;
	public static final int ERROR_NOATTRIBUTE	= -9;
	public static final int ERROR_NOOUTFUNC		= -10;
	public static final int ERROR_TYPUNKNOWN	= -11;

	public static final int OK_CONNECT			= 1;
	public static final int OK_FINISH			= 2;
	public static final int OK_FETCH			= 3;
	public static final int OK_UTILITY			= 4;
	public static final int OK_SELECT			= 5;
	public static final int OK_SELINTO			= 6;
	public static final int OK_INSERT			= 7;
	public static final int OK_DELETE			= 8;
	public static final int OK_UPDATE			= 9;
	public static final int OK_CURSOR			= 10;

	/**
	 * Execute a command using the internal <code>SPI_exec</code> function.
	 * @param command The command to execute.
	 * @param rowCount The maximum number of tuples to create. A value
	 * of <code>rowCount</code> of zero is interpreted as no limit, i.e.,
	 * run to completion.
	 * @return One of the declared status codes.
	 */
	public native static int exec(String command, int rowCount);

	/**
	 * Returns the value of the global variable <code>SPI_processed</code>.
	 */
	public native static int getProcessed();

	/**
	 * Returns the value of the global variable <code>SPI_result</code>.
	 */
	public native static int getResult();
	
	/**
	 * Returns the value of the global variable <code>SPI_tuptable</code>.
	 */
	public native static TupleTable getTupTable();
}
