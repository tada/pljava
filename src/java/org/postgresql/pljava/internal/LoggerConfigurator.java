/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
		props.setProperty(".level", "INFO");
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
