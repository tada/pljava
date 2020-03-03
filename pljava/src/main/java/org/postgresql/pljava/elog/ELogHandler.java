/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.elog;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.postgresql.pljava.internal.Backend;

/**
 * Provides access to the loggin mechanism of the PostgreSQL server.
 *
 * @author Thomas Hallgren
 */
public class ELogHandler extends Handler
{
	/**
	 * Debugging messages, in categories of
	 * decreasing detail.
	 */
	public static final int LOG_DEBUG5	= 10;
	public static final int LOG_DEBUG4	= 11;
	public static final int LOG_DEBUG3	= 12;
	public static final int LOG_DEBUG2	= 13;
	public static final int LOG_DEBUG1	= 14;

	/**
	 * Server operational messages; sent only
	 * to server log by default.
	 */
	public static final int LOG_LOG		= 15;

	/**
	 * Informative messages that are always
	 * sent to client;	is not affected by
	 * client_min_messages
	 */
	public static final int LOG_INFO	= 17;
	/**
	 * Helpful messages to users about query
	 * operation;  sent to client and server
	 * log by default.
	 */
	public static final int LOG_NOTICE	= 18;
	
	/**
	 * Warnings
	 */
	public static final int LOG_WARNING	= 19;
	
	/**
	 * user error - abort transaction; return
	 * to known state
	 */
	public static final int LOG_ERROR   = 20;

	/**
	 *  fatal error - abort process
	 */
	public static final int LOG_FATAL   = 21;

	/**
	 * take down the other backends with me
	 */
	public static final int LOG_PANIC   = 22;

	/* (non-Javadoc)
	 * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
	 */
	public void publish(LogRecord record)
	{
		Level level = record.getLevel();
		int pgLevel;
		if(level == null)
			pgLevel = LOG_LOG;
		else if(level.equals(Level.SEVERE))
			pgLevel = LOG_ERROR;
		else if(level.equals(Level.WARNING))
			pgLevel = LOG_WARNING;
		else if(level.equals(Level.INFO))
			pgLevel = LOG_INFO;
		else if(level.equals(Level.FINE))
			pgLevel = LOG_DEBUG1;
		else if(level.equals(Level.FINER))
			pgLevel = LOG_DEBUG2;
		else if(level.equals(Level.FINEST))
			pgLevel = LOG_DEBUG3;
		else
			pgLevel = LOG_LOG;
		Backend.log(pgLevel, this.getFormatter().format(record));
	}

	public ELogHandler()
	{
		this.configure();
	}

	/**
	 * This is a no-op.
	 */
	public void flush()
	{
	}

	/**
	 * This is a no-op.
	 */
	public void close() throws SecurityException
	{
	}

	public static void init()
	{
		Properties props = new Properties();
		props.setProperty("handlers", ELogHandler.class.getName());
		props.setProperty(".level", getPgLevel().getName());
		ByteArrayOutputStream po = new ByteArrayOutputStream();
		try
		{
			props.store(po, null);
			LogManager.getLogManager().readConfiguration(
				new ByteArrayInputStream(po.toByteArray()));
		}
		catch(IOException e)
		{
		}
	}

	/**
	 * Obtains the &quot;log_min_messages&quot; configuration variable and
	 * translates it into a {@link Level} object.
	 * @return The Level that corresponds to the configuration variable.
	 */
	public static Level getPgLevel()
	{
		// We use this little trick to provide the correct config option
		// without having to call back into the JNI code the first time
		// around since that call will be a bit premature (it will come
		// during JVM initialization and before the native method is
		// registered).
		//
		String[] options = { "log_min_messages", "client_min_messages" };
		Level finestLevel = null;
		for ( String option : options )
		{	
			String pgLevel = Backend.getConfigOption(option);
			if ( null == pgLevel )
				continue;
			pgLevel = pgLevel.toLowerCase().trim();
			Level level = null;
			if(pgLevel.equals("panic") || pgLevel.equals("fatal"))
				level = Level.OFF;
			else if(pgLevel.equals("error"))
				level = Level.SEVERE;
			else if(pgLevel.equals("warning"))
				level = Level.WARNING;
			else if(pgLevel.equals("notice"))
				level = Level.CONFIG;
			else if(pgLevel.equals("info"))
				level = Level.INFO;
			else if(pgLevel.equals("debug1"))
				level = Level.FINE;
			else if(pgLevel.equals("debug2"))
				level = Level.FINER;
			else if(pgLevel.equals("debug3") || pgLevel.equals("debug4") || pgLevel.equals("debug5"))
				level = Level.FINEST;
			if ( null == level )
				continue;
			if ( null == finestLevel
				|| finestLevel.intValue() > level.intValue() )
				finestLevel = level;
		}
		if ( null == finestLevel )
			finestLevel = Level.ALL;
		return finestLevel;
	}

	// Private method to configure an ELogHandler
	//
	private void configure()
	{
		LogManager mgr = LogManager.getLogManager();
		String cname = ELogHandler.class.getName();

		String val = mgr.getProperty(cname + ".filter");
		if(val != null)
		{	
			try
			{	
				this.setFilter((Filter)
					Class.forName(val.trim())
						.getDeclaredConstructor()
						.newInstance()
				);
			}
			catch (Exception e)
			{
				val = null;
			}
		}
		if(val == null)
			this.setFilter(null);

		val = mgr.getProperty(cname + ".formatter");
		if(val != null)
		{	
			try
			{
				this.setFormatter((Formatter)
					Class.forName(val.trim())
						.getDeclaredConstructor()
						.newInstance()
				);
			}
			catch (Exception e)
			{
				val = null;
			}
		}
		if(val == null)
			this.setFormatter(new ELogFormatter());

		val = mgr.getProperty(cname + ".encoding");
		if(val != null)
		{
			try
			{
				setEncoding(val.trim());
			}
			catch(Exception e)
			{
				val = null;
			}
		}
		if(val == null)
			try { setEncoding(null); } catch (Exception e) { /* ignore */ }
	}	
}
