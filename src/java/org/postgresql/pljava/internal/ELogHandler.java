/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.internal;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

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
		if(level.equals(Level.SEVERE))
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
}
