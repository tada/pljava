/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

/**
 * The <code>ErrorData</code> correspons to the ErrorData obtained
 * using an internal PostgreSQL <code>CopyErrorData</code> call.
 *
 * @author Thomas Hallgren
 */
public class ErrorData extends NativeStruct
{
	/**
	 * Returns The error level
	 */
	public native int getErrorLevel();

	/**
	 * Returns true if the error will be reported to the server log
	 */
	public native boolean isOutputToServer();

	/**
	 * Returns true if the error will be reported to the client
	 */
	public native boolean isOutputToClient();

	/**
	 * Returns true if funcname inclusion is set
	 */
	public native boolean isShowFuncname();

	/**
	 * Returns The file where the error occured
	 */
	public native String getFilename();

	/**
	 * Returns The line where the error occured
	 */
	public native int getLineno();

	/**
	 * Returns the name of the function where the error occured
	 */
	public native String getFuncname();

	/**
	 * Returns the unencoded ERRSTATE
	 */
	public native String getSqlState();

	/**
	 * Returns the primary error message
	 */
	public native String getMessage();
	
	/**
	 * Returns the detailed error message
	 */
	public native String getDetail();
	
	/**
	 * Returns the hint message
	 */
	public native String getHint();
	
	/**
	 * Returns the context message
	 */
	public native String getContextMessage();
	
	/**
	 * Returns the cursor index into the query string
	 */
	public native int getCursorPos();
	
	/**
	 * Returns the cursor index into internal query
	 */
	public native int getInternalPos();
	
	/**
	 * Returns the internally-generated query
	 */
	public native String getInternalQuery();
	
	/**
	 * Returns the errno at entry
	 */
	public native int getSavedErrno();	/* errno at entry */
}
