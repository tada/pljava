/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava.internal;

import java.io.InputStream;
import java.io.IOException;

import java.sql.SQLException;
import java.sql.SQLDataException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.pljava.elog.ELogHandler; // for javadoc

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;
import static
	org.postgresql.pljava.sqlgen.Lexicals.ISO_AND_PG_IDENTIFIER_CAPTURING;

/**
 * Provides access to some useful routines in the PostgreSQL server.
 * @author Thomas Hallgren
 */
public class Backend
{
	/**
	 * All native calls synchronize on this object.
	 */
	public static final Object THREADLOCK;

	/**
	 * Will be {@code Boolean.TRUE} on the one primordial thread first entered
	 * from PG, and null on any other thread.
	 */
	public static final ThreadLocal<Boolean> IAMPGTHREAD = new ThreadLocal<>();

	public static final boolean WITHOUT_ENFORCEMENT =
		"disallow".equals(System.getProperty("java.security.manager"));

	@SuppressWarnings("deprecation") // Java >= 10: .feature()
	static final int JAVA_MAJOR = Runtime.version().major();

	static
	{
		IAMPGTHREAD.set(Boolean.TRUE);
		THREADLOCK = EarlyNatives._forbidOtherThreads() ? null : new Object();
		/*
		 * With any luck, the static final null-or-not-ness of THREADLOCK will
		 * cause JIT to quickly specialize the doInPG() methods to one or the
		 * other branch of their code.
		 */

		try  ( InputStream is =
		   Backend.class.getResourceAsStream("EntryPoints.class") )
		{
			byte[] image = is.readAllBytes();
			EarlyNatives._defineClass(
				"org/postgresql/pljava/internal/EntryPoints",
				Backend.class.getClassLoader(), image);
		}
		catch ( IOException e )
		{
			throw new ExceptionInInitializerError(e);
		}
   }

	private static final Pattern s_gucList = Pattern.compile(String.format(
		"\\G(?:%1$s)(?<more>,\\s*+)?+", ISO_AND_PG_IDENTIFIER_CAPTURING));

	/**
	 * Do an operation on a thread with serialized access to call into
	 * PostgreSQL, returning a result.
	 */
	public static <T, E extends Throwable> T doInPG(Checked.Supplier<T,E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				return op.get();
			}
		assertThreadMayEnterPG();
		return op.get();
	}

	/**
	 * Specialization of {@link #doInPG(Supplier) doInPG} for operations that
	 * return no result. This version must be present, as the Java compiler will
	 * not automagically match a void lambda or method reference to
	 * {@code Supplier<Void>}.
	 */
	public static <E extends Throwable> void doInPG(Checked.Runnable<E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				op.run();
				return;
			}
		assertThreadMayEnterPG();
		op.run();
	}

	/**
	 * Specialization of {@link #doInPG(Supplier) doInPG} for operations that
	 * return a boolean result. This method need not be present: without it, the
	 * Java compiler will happily match boolean lambdas or method references to
	 * the generic method, at the small cost of some boxing/unboxing; providing
	 * this method simply allows that to be avoided.
	 */
	public static <E extends Throwable> boolean doInPG(
		Checked.BooleanSupplier<E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				return op.getAsBoolean();
			}
		assertThreadMayEnterPG();
		return op.getAsBoolean();
	}

	/**
	 * Specialization of {@link #doInPG(Supplier) doInPG} for operations that
	 * return a double result. This method need not be present: without it, the
	 * Java compiler will happily match double lambdas or method references to
	 * the generic method, at the small cost of some boxing/unboxing; providing
	 * this method simply allows that to be avoided.
	 */
	public static <E extends Throwable> double doInPG(
		Checked.DoubleSupplier<E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				return op.getAsDouble();
			}
		assertThreadMayEnterPG();
		return op.getAsDouble();
	}

	/**
	 * Specialization of {@link #doInPG(Supplier) doInPG} for operations that
	 * return an int result. This method need not be present: without it, the
	 * Java compiler will happily match int lambdas or method references to
	 * the generic method, at the small cost of some boxing/unboxing; providing
	 * this method simply allows that to be avoided.
	 */
	public static <E extends Throwable> int doInPG(Checked.IntSupplier<E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				return op.getAsInt();
			}
		assertThreadMayEnterPG();
		return op.getAsInt();
	}

	/**
	 * Specialization of {@link #doInPG(Supplier) doInPG} for operations that
	 * return a long result. This method need not be present: without it, the
	 * Java compiler will happily match int lambdas or method references to
	 * the generic method, at the small cost of some boxing/unboxing; providing
	 * this method simply allows that to be avoided.
	 */
	public static <E extends Throwable> long doInPG(Checked.LongSupplier<E> op)
	throws E
	{
		if ( null != THREADLOCK )
			synchronized(THREADLOCK)
			{
				return op.getAsLong();
			}
		assertThreadMayEnterPG();
		return op.getAsLong();
	}

	/**
	 * Return true if the current thread may JNI-call into Postgres.
	 *<p>
	 * In PL/Java's threading model, only one thread (or only one thread at a
	 * time, depending on the setting of {@code pljava.java_thread_pg_entry})
	 * may make calls into the native PostgreSQL code.
	 *<p>
	 * <b>Note:</b> The setting {@code pljava.java_thread_pg_entry=error} is an
	 * exception; under that setting this method will return true for any
	 * thread that acquires the {@code THREADLOCK} monitor, but any such thread
	 * that isn't the actual original PG thread will have an exception thrown
	 * if it calls into PG.
	 *<p>
	 * Under the setting {@code pljava.java_thread_pg_entry=throw}, this method
	 * will only return true for the one primordial PG thread (and there is no
	 * {@code THREADLOCK} object to do any monitor operations on).
	 * @return true if the current thread is the one prepared to enter PG.
	 */
	public static boolean threadMayEnterPG()
	{
		if ( null != THREADLOCK )
			return Thread.holdsLock(THREADLOCK);
		return Boolean.TRUE == IAMPGTHREAD.get();
	}

	/**
	 * Throw {@code IllegalStateException} if {@code threadMayEnterPG()} would
	 * return false.
	 *<p>
	 * This method is only called in, and only correct for, the case where no
	 * {@code THREADLOCK} is in use and only the one primordial thread is ever
	 * allowed into PG.
	 */
	private static void assertThreadMayEnterPG()
	{
		if ( null == IAMPGTHREAD.get() )
			throw new IllegalStateException(
				"Attempt by non-initial thread to enter PostgreSQL from Java");
	}

	/**
	 * Returns the configuration option as read from the Global
	 * Unified Config package (GUC).
	 * @param key The name of the option.
	 * @return The value of the option.
	 */
	public static String getConfigOption(String key)
	{
		return doInPG(() -> _getConfigOption(key));
	}

	public static List<Identifier.Simple> getListConfigOption(String key)
	throws SQLException
	{
		String s = getConfigOption(key);
		if ( null == s )
			return null;

		final Matcher m = s_gucList.matcher(s);
		ArrayList<Identifier.Simple> al = new ArrayList<>();
		while ( m.find() )
		{
			al.add(identifierFrom(m));
			if ( null != m.group("more") )
				continue;
			if ( ! m.hitEnd() )
				throw new SQLDataException(String.format(
					"configuration option \"%1$s\" improper list syntax",
					key), "22P02");
		}
		al.trimToSize();
		return Collections.unmodifiableList(al);
	}

	/**
	 * Returns the size of the statement cache.
	 * @return the size of the statement cache.
	 */
	public static int getStatementCacheSize()
	{
		return doInPG(Backend::_getStatementCacheSize);
	}

	/**
	 * Log a message using the internal elog command.
	 * @param logLevel The log level as defined in
	 * {@link ELogHandler}.
	 * @param str The message
	 */
	public static void log(int logLevel, String str)
	{
		doInPG(() -> _log(logLevel, str));
	}

	public static void clearFunctionCache()
	{
		doInPG(Backend::_clearFunctionCache);
	}

	public static boolean isCreatingExtension()
	{
		return doInPG(Backend::_isCreatingExtension);
	}

	public static boolean allowingUnenforcedUDT()
	{
		return doInPG(Backend::_allowingUnenforcedUDT);
	}

	/**
	 * Returns the path of PL/Java's shared library.
	 * @throws SQLException if for some reason it can't be determined.
	 */
	public static String myLibraryPath() throws SQLException
	{
		String result = doInPG(Backend::_myLibraryPath);

		if ( null != result )
			return result;

		throw new SQLException("Unable to retrieve PL/Java's library path");
	}

	/**
	 * Attempt (best effort, unexposed JDK internals) to suppress
	 * the layer-inappropriate JEP 411 warning when {@code InstallHelper}
	 * sets up permission enforcement.
	 */
	static void pokeJEP411()
	{
		_pokeJEP411(InstallHelper.class, true);
	}

	/**
	 * Returns <code>true</code> if the backend is awaiting a return from a
	 * call into the JVM. This method will only return <code>false</code>
	 * when called from a thread other then the main thread and the main
	 * thread has returned from the call into the JVM.
	 */
	public static native boolean isCallingJava();

	/**
	 * Returns the value of the GUC custom variable <code>
	 * pljava.release_lingering_savepoints</code>.
	 */
	public static native boolean isReleaseLingeringSavepoints();

	private static native String _getConfigOption(String key);

	private static native int  _getStatementCacheSize();
	private static native void _log(int logLevel, String str);
	private static native void _clearFunctionCache();
	private static native boolean _isCreatingExtension();
	private static native String _myLibraryPath();
	private static native void _pokeJEP411(Class<?> caller, Object token);
	private static native boolean _allowingUnenforcedUDT();

	private static class EarlyNatives
	{
		private static native boolean _forbidOtherThreads();
		private static native Class<?> _defineClass(
			String name, ClassLoader loader, byte[] buf);
	}
}
