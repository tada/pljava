/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
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
	 * All native calls synchronize on this object.
	 */
	public static final Object THREADLOCK = new Object();

	/**
	 * Returns the configuration option as read from the Global
	 * Unified Config package (GUC).
	 * @param key The name of the option.
	 * @return The value of the option.
	 */
	public static String getConfigOption(String key)
	{
		synchronized(THREADLOCK)
		{
			return _getConfigOption(key);
		}
	}

	/**
	 * Log a message using the internal elog command.
	 * @param logLevel The log level.
	 * @param str The message
	 */
	public static void log(int logLevel, String str)
	{
		synchronized(THREADLOCK)
		{
			_log(logLevel, str);
		}
	}

	/**
	 * Returns <code>true</code> if the backend is awaiting a return from a
	 * call into the JVM. This method will only return <code>false</code>
	 * when called from a thread other then the main thread and the main
	 * thread has returned from the call into the JVM.
	 */
	public native static boolean isCallingJava();

	private native static String _getConfigOption(String key);
	private native static void _log(int logLevel, String str);
}
