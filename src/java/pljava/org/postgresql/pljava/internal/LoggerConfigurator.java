/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;

/**
 * Provides access to the loggin mechanism of the PostgreSQL server.
 *
 * @author Thomas Hallgren
 */
public class LoggerConfigurator
{
	public LoggerConfigurator()
	{
		Properties props = new Properties();
		props.setProperty("handlers", ELogHandler.class.getName());
		props.setProperty(".level", getPgLevel().toString());
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
		String pgLevel = Backend.getConfigOption("log_min_messages");
		Level level = Level.ALL;
		if(pgLevel != null)
		{	
			pgLevel = pgLevel.toLowerCase().trim();
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
		}
		return level;
	}
}
