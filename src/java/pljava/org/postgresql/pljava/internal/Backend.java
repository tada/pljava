/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.io.File;
import java.io.FilePermission;
import java.security.Permission;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.PropertyPermission;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgresql.pljava.management.Commands;


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
	 * Returns the configuration option as read from the Global
	 * Unified Config package (GUC).
	 * @param key The name of the option.
	 * @return The value of the option.
	 */
	public static int getStatementCacheSize()
	{
		synchronized(THREADLOCK)
		{
			return _getStatementCacheSize();
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

	private static class PLJavaSecurityManager extends SecurityManager
	{
		public void checkPermission(Permission perm)
		{
			this.assertPermission(perm);
		}

		public void checkPermission(Permission perm, Object context)
		{
			this.assertPermission(perm);
		}

		void assertPermission(Permission perm)
		{
			if(perm instanceof RuntimePermission)
			{
				String name = perm.getName();
				if("*".equals(name) || "exitVM".equals(name))
					throw new SecurityException();
				else if("setSecurityManager".equals(name) && !s_inSetTrusted)
					//
					// Attempt to set another security manager while not
					// in the setTrusted method
					//
					throw new SecurityException();
			}
			else if(perm instanceof PropertyPermission)
			{
				if(perm.getActions().indexOf("write") >= 0 && perm.getName().startsWith("java."))
					throw new SecurityException();
			}
		}
	}

	private static boolean s_inSetTrusted = false;

	private static final SecurityManager s_untrustedSecurityManager = new PLJavaSecurityManager();

	/**
	 * This security manager will block all attempts to access the file system
	 */
	private static final SecurityManager s_trustedSecurityManager = new PLJavaSecurityManager()
	{
		void assertPermission(Permission perm)
		{
			if(perm instanceof FilePermission)
			{
				String actions = perm.getActions();
				if("read".equals(actions))
				{
					// Must be able to read timezone info etc. in the java
					// installation directory.
					//
					File javaHome = new File(System.getProperty("java.home"));
					File accessedFile = new File(perm.getName());
					File fileDir = accessedFile.getParentFile();
					while(fileDir != null)
					{
						if(fileDir.equals(javaHome))
							return;
						fileDir = fileDir.getParentFile();
					}
				}
				throw new SecurityException(perm.getActions() + " on " + perm.getName());
			}
			super.assertPermission(perm);
		}
	};

	public static void addClassImages(Connection conn, int jarId, String urlString)
	throws SQLException
	{
		if(System.getSecurityManager() == s_trustedSecurityManager)
		{
			try
			{
				setTrusted(false);
				Commands.addClassImages(conn, jarId, urlString);
			}
			finally
			{
				setTrusted(true);
			}
		}
		else
			Commands.addClassImages(conn, jarId, urlString);
	}

	/**
	 * Called when the JVM is first booted and then everytime a switch
	 * is made between calling a trusted function versus an untrusted
	 * function.
	 */
	private static void setTrusted(boolean trusted)
	{
		s_inSetTrusted = true;
		try
		{
			Logger log = Logger.getAnonymousLogger();
			if(log.isLoggable(Level.FINE))
				log.fine("Using SecurityManager for " + (trusted ? "trusted" : "untrusted") + " language");
			System.setSecurityManager(trusted ? s_trustedSecurityManager : s_untrustedSecurityManager);
		}
		finally
		{
			s_inSetTrusted = false;
		}
	}

	/**
	 * Returns <code>true</code> if the backend is awaiting a return from a
	 * call into the JVM. This method will only return <code>false</code>
	 * when called from a thread other then the main thread and the main
	 * thread has returned from the call into the JVM.
	 */
	public native static boolean isCallingJava();

	/**
	 * Returns the value of the GUC custom variable <code>
	 * pljava.release_lingering_savepoints</code>.
	 */
	public native static boolean isReleaseLingeringSavepoints();

	private native static String _getConfigOption(String key);

	private native static int  _getStatementCacheSize();
	private native static void _log(int logLevel, String str);

	private native static void _addEOXactListener(EOXactListener listener);
	private native static void _removeEOXactListener(EOXactListener listener); 
}
