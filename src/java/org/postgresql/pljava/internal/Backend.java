/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

/**
 * Provides access to some useful routines in the PostgreSQL server.
 * @author Thomas Hallgren
 */
public class Backend
{
	/**
	 * Log a message using the internal elog command.
	 * @param logLevel The log level.
	 * @param str The message
	 */
	public native static void log(int logLevel, String str);

	/**
	 * Returns <code>true</code> if the current thread is the main thread
	 * that can be used in the backend.
	 */
	public static native boolean isBackendThread();
}
