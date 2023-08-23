/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.internal;

import static org.postgresql.pljava.internal.Backend.doInPG;

/**
 * The <code>SPI</code> class provides access to some global
 * variables used by SPI.
 *
 * @author Thomas Hallgren
 */
public class SPI
{
	public static final int ERROR_CONNECT       = -1;
	public static final int ERROR_COPY          = -2;
	public static final int ERROR_OPUNKNOWN     = -3;
	public static final int ERROR_UNCONNECTED   = -4;
	public static final int ERROR_CURSOR        = -5;
	public static final int ERROR_ARGUMENT      = -6;
	public static final int ERROR_PARAM         = -7;
	public static final int ERROR_TRANSACTION   = -8;
	public static final int ERROR_NOATTRIBUTE   = -9;
	public static final int ERROR_NOOUTFUNC     = -10;
	public static final int ERROR_TYPUNKNOWN    = -11;
	public static final int ERROR_REL_DUPLICATE = -12;
	public static final int ERROR_REL_NOT_FOUND = -13;

	public static final int OK_CONNECT          = 1;
	public static final int OK_FINISH           = 2;
	public static final int OK_FETCH            = 3;
	public static final int OK_UTILITY          = 4;
	public static final int OK_SELECT           = 5;
	public static final int OK_SELINTO          = 6;
	public static final int OK_INSERT           = 7;
	public static final int OK_DELETE           = 8;
	public static final int OK_UPDATE           = 9;
	public static final int OK_CURSOR           = 10;
	public static final int OK_INSERT_RETURNING = 11;
	public static final int OK_DELETE_RETURNING = 12;
	public static final int OK_UPDATE_RETURNING = 13;
	public static final int OK_REWRITTEN        = 14;
	public static final int OK_REL_REGISTER     = 15;
	public static final int OK_REL_UNREGISTER   = 16;
	public static final int OK_TD_REGISTER      = 17;
	public static final int OK_MERGE            = 18;

	public static final int OPT_NONATOMIC       = 1 << 0;

	/**
	 * Execute a command using the internal <code>SPI_exec</code> function.
	 * @param command The command to execute.
	 * @param rowCount The maximum number of tuples to create. A value
	 * of <code>rowCount</code> of zero is interpreted as no limit, i.e.,
	 * run to completion.
	 * @return One of the declared status codes.
	 * @deprecated This seems never to have been used in git history of project.
	 */
	@Deprecated
	private static int exec(String command, int rowCount)
	{
		return doInPG(() -> _exec(command, rowCount));
	}

	public static void freeTupTable()
	{
		doInPG(SPI::_freeTupTable);
	}

	/**
	 * Returns the value of the global variable <code>SPI_processed</code>.
	 */
	public static long getProcessed()
	{
		long count = doInPG(SPI::_getProcessed);
		if ( count < 0 )
			throw new ArithmeticException(
				"too many rows processed to count in a Java signed long");
		return count;
	}

	/**
	 * Returns the value of the global variable <code>SPI_result</code>.
	 */
	public static int getResult()
	{
		return doInPG(SPI::_getResult);
	}

	/**
	 * Returns the value of the global variable <code>SPI_tuptable</code>.
	 */
	public static TupleTable getTupTable(TupleDesc known)
	{
		return doInPG(() -> _getTupTable(known));
	}

	/**
	 * Returns a textual representation of a result code.
	 */
	/*
	 * XXX PG 11 introduces a real SPI_result_code_string function.
	 * The strings it returns are like these with SPI_ prepended.
	 */
	public static String getResultText(int resultCode)
	{
		switch(resultCode)
		{
			case ERROR_CONNECT:       return "ERROR_CONNECT";
			case ERROR_COPY:          return "ERROR_COPY";
			case ERROR_OPUNKNOWN:     return "ERROR_OPUNKNOWN";
			case ERROR_UNCONNECTED:   return "ERROR_UNCONNECTED";
			case ERROR_CURSOR:        return "ERROR_CURSOR";
			case ERROR_ARGUMENT:      return "ERROR_ARGUMENT";
			case ERROR_PARAM:         return "ERROR_PARAM";
			case ERROR_TRANSACTION:   return "ERROR_TRANSACTION";
			case ERROR_NOATTRIBUTE:   return "ERROR_NOATTRIBUTE";
			case ERROR_NOOUTFUNC:     return "ERROR_NOOUTFUNC";
			case ERROR_TYPUNKNOWN:    return "ERROR_TYPUNKNOWN";
			case ERROR_REL_DUPLICATE: return "ERROR_REL_DUPLICATE";
			case ERROR_REL_NOT_FOUND: return "ERROR_REL_NOT_FOUND";

			case OK_CONNECT:          return "OK_CONNECT";
			case OK_FINISH:           return "OK_FINISH";
			case OK_FETCH:            return "OK_FETCH";
			case OK_UTILITY:          return "OK_UTILITY";
			case OK_SELECT:           return "OK_SELECT";
			case OK_SELINTO:          return "OK_SELINTO";
			case OK_INSERT:           return "OK_INSERT";
			case OK_DELETE:           return "OK_DELETE";
			case OK_UPDATE:           return "OK_UPDATE";
			case OK_CURSOR:           return "OK_CURSOR";
			case OK_INSERT_RETURNING: return "OK_INSERT_RETURNING";
			case OK_DELETE_RETURNING: return "OK_DELETE_RETURNING";
			case OK_UPDATE_RETURNING: return "OK_UPDATE_RETURNING";
			case OK_REWRITTEN:        return "OK_REWRITTEN";
			case OK_REL_REGISTER:     return "OK_REL_REGISTER";
			case OK_REL_UNREGISTER:   return "OK_REL_UNREGISTER";
			case OK_TD_REGISTER:      return "OK_TD_REGISTER";

			default: return "Unknown result code: " + resultCode;
		}
	}

	@Deprecated
	private native static int _exec(String command, int rowCount);

	private native static long _getProcessed();
	private native static int _getResult();
	private native static void _freeTupTable();
	private native static TupleTable _getTupTable(TupleDesc known);
}
