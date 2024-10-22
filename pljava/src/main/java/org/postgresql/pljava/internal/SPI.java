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

import static java.lang.Math.multiplyExact;
import static java.lang.Math.toIntExact;

import java.nio.ByteBuffer;

import java.util.List;

import static org.postgresql.pljava.internal.Backend.doInPG;

import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_DATUM;
import org.postgresql.pljava.pg.TupleList;
import org.postgresql.pljava.pg.TupleTableSlotImpl;

import static org.postgresql.pljava.pg.DatumUtils.asReadOnlyNativeOrder;
import static org.postgresql.pljava.pg.DatumUtils.fetchPointer;

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

	/*
	 * Indices into window array.
	 */
	private static final int SPI_result    = 0;
	private static final int SPI_processed = 1;
	private static final int SPI_tuptable  = 2;

	private static final ByteBuffer[] s_windows;

	static
	{
		ByteBuffer[] bs = EarlyNatives._window(ByteBuffer.class);
		for ( int i = 0; i < bs.length; ++ i )
			bs[i] = asReadOnlyNativeOrder(bs[i]);
		s_windows = bs;
	}

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
	public static int exec(String command, int rowCount)
	{
		return doInPG(() -> _exec(command, rowCount));
	}

	/**
	 * Frees a tuple table returned by SPI.
	 *<p>
	 * This legacy method has no parameter, and frees whatever tuple table the
	 * {@code SPI_tuptable} global points to at the moment; beware if SPI has
	 * returned any newer result since the one you might think you are freeing!
	 */
	public static void freeTupTable()
	{
		doInPG(SPI::_freeTupTable);
	}

	/**
	 * Returns the value of the global variable <code>SPI_processed</code>.
	 */
	public static long getProcessed()
	{
		long count = doInPG(() ->
		{
			assert 8 == s_windows[SPI_processed].capacity() :
				"SPI_processed width change";
			return s_windows[SPI_processed].getLong(0);
		});
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
		return doInPG(() ->
		{
			assert 4 == s_windows[SPI_result].capacity() :
				"SPI_result width change";
			return s_windows[SPI_result].getInt(0);
		});
	}

	/**
	 * Returns a List of the supplied TupleTableSlot covering the tuples pointed
	 * to from the pointer array that the global {@code SPI_tuptable} points to.
	 *<p>
	 * This is an internal, not an API, method, and it does nothing to check
	 * that the supplied ttsi fits the tuples SPI has returned. The caller is to
	 * ensure that.
	 * @return null if the global SPI_tuptable is null
	 */
	public static TupleList getTuples(TupleTableSlotImpl ttsi)
	{
		return doInPG(() ->
		{
			long p = fetchPointer(s_windows[SPI_tuptable], 0);
			if ( 0 == p )
				return null;

			long count = getProcessed();
			if ( 0 == count )
				return TupleList.EMPTY;

			// An assertion in the C code checks SIZEOF_DATUM == SIZEOF_VOID_P
			// XXX catch ArithmeticException, report a "program limit exceeded"
			int sizeToMap = toIntExact(multiplyExact(count, SIZEOF_DATUM));

			return _mapTupTable(ttsi, p, sizeToMap);
		});
	}

	/**
	 * Returns the tuples located by the global variable {@code SPI_tuptable}
	 * as an instance of the legacy {@code TupleTable} class.
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

	private static class EarlyNatives
	{
		/**
		 * Returns an array of ByteBuffer, one covering SPI_result, one for
		 * SPI_processed, and one for the SPI_tuptable pointer.
		 *<p>
		 * Takes a {@code Class<ByteBuffer>} argument, to save the native
		 * code a lookup.
		 */
		private static native ByteBuffer[] _window(
			Class<ByteBuffer> component);
	}

	@Deprecated
	private static native int _exec(String command, int rowCount);

	private static native void _freeTupTable();
	private static native TupleTable _getTupTable(TupleDesc known);
	private static native TupleList _mapTupTable(
		TupleTableSlotImpl ttsi, long p, int sizeToMap);
}
