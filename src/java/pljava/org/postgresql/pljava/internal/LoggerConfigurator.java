/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Properties;
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
}
