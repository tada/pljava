/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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

	private static Session s_session;

	public static synchronized Session getSession()
	{
		if(s_session == null)
		{
			s_session = new Session();
			synchronized(THREADLOCK)
			{
				_addEOXactListener(s_session);
			}
			Runtime.getRuntime().addShutdownHook(new Thread()
			{
				public void run()
				{
					if(s_session != null)
						_removeEOXactListener(s_session);
					s_session = null;
				}
			});
		}
		return s_session;
	}

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

	private native static void _addEOXactListener(EOXactListener listener);
	private native static void _removeEOXactListener(EOXactListener listener); 
}
