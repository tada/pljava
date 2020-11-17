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
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import org.postgresql.pljava.annotation.Function;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.logging.Logger;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * An example that retrieves a {@code Properties} resource, and returns
 * (key,value) rows from it by implementing the {@code ResultSetProvider}
 * interface.
 * @author Thomas Hallgren
 */
@SQLAction(requires = {"propertyExampleAnno", "propertyExampleRB"}, install = {
	"WITH" +
	" expected AS (VALUES" +
	"  ('adjective' ::varchar(200), 'avaricious' ::varchar(200))," +
	"  ('noun',                     'platypus')" +
	" )" +
	"SELECT" +
	"  CASE WHEN" +
	"   2 = count(prop) AND every(prop IN (SELECT expected FROM expected))" +
	"  THEN javatest.logmessage('INFO',    'get resource passes')" +
	"  ELSE javatest.logmessage('WARNING', 'get resource fails')" +
	"  END" +
	" FROM" +
	"  propertyExampleAnno() AS prop",

	"WITH" +
	" expected AS (VALUES" +
	"  ('adjective' ::varchar(200), 'avaricious' ::varchar(200))," +
	"  ('noun',                     'platypus')" +
	" )" +
	"SELECT" +
	"  CASE WHEN" +
	"   2 = count(prop) AND every(prop IN (SELECT expected FROM expected))" +
	"  THEN javatest.logmessage('INFO',    'get ResourceBundle passes')" +
	"  ELSE javatest.logmessage('WARNING', 'get ResourceBundle fails')" +
	"  END" +
	" FROM" +
	"  propertyExampleRB() AS prop"
})
public class UsingProperties implements ResultSetProvider.Large
{
	private static Logger s_logger = Logger.getAnonymousLogger();
	private final Iterator m_propertyIterator;
	
	public UsingProperties()
	throws IOException
	{
		Properties v = new Properties();
		InputStream propStream =
			this.getClass().getResourceAsStream("example.properties");

		if(propStream == null)
		{
			s_logger.fine("example.properties was null");
			m_propertyIterator = Collections.EMPTY_SET.iterator();
		}
		else
		{
			v.load(propStream);
			propStream.close();
			s_logger.fine("example.properties has " + v.size() + " entries");
			m_propertyIterator = v.entrySet().iterator();
		}
	}

	/**
	 * This constructor (distinguished by signature) reads the same property
	 * file, but using the {@code ResourceBundle} machinery instead of
	 * {@code Properties}.
	 */
	private UsingProperties(Void usingResourceBundle)
	{
		ResourceBundle b =
			ResourceBundle.getBundle(getClass().getPackageName() + ".example");

		Iterator<String> keys = b.getKeys().asIterator();

		m_propertyIterator = new Iterator<Map.Entry<String,String>>()
		{
			public boolean hasNext()
			{
				return keys.hasNext();
			}

			public Map.Entry<String,String> next()
			{
				String k = keys.next();
				return Map.entry(k, b.getString(k));
			}
		};
	}

	public boolean assignRowValues(ResultSet receiver, long currentRow)
			throws SQLException
	{
		if(!m_propertyIterator.hasNext())
		{
			s_logger.fine("no more rows, returning false");
			return false;
		}
		Map.Entry propEntry = (Map.Entry)m_propertyIterator.next();
		receiver.updateString(1, (String)propEntry.getKey());
		receiver.updateString(2, (String)propEntry.getValue());
		// s_logger.fine("next row created, returning true");
		return true;
	}

	/**
	 * Return the contents of the {@code example.properties} resource,
	 * one (key,value) row per entry.
	 */
	@Function(type = "javatest._properties", provides = "propertyExampleAnno")
	public static ResultSetProvider propertyExampleAnno()
	throws SQLException
	{
		try
		{
			return new UsingProperties();
		}
		catch(IOException e)
		{
			throw new SQLException("Error reading properties", e.getMessage());
		}
	}

	/**
	 * Return the contents of the {@code example.properties} resource,
	 * one (key,value) row per entry, using {@code ResourceBundle} to load it.
	 */
	@Function(type = "javatest._properties", provides = "propertyExampleRB")
	public static ResultSetProvider propertyExampleRB()
	throws SQLException
	{
		return new UsingProperties(null);
	}

	public void close()
	{
	}
}
