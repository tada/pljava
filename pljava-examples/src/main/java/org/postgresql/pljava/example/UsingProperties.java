/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 */
package org.postgresql.pljava.example;

import java.io.IOException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Logger;

import org.postgresql.pljava.ObjectPool;
import org.postgresql.pljava.PooledObject;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.SessionManager;

/**
 * @author Thomas Hallgren
 */
public class UsingProperties implements ResultSetProvider, PooledObject {
	private static Logger s_logger = Logger.getAnonymousLogger();

	public static ResultSetProvider getProperties() throws SQLException {
		ObjectPool pool = SessionManager.current().getObjectPool(
				UsingProperties.class);
		return (ResultSetProvider) pool.activateInstance();
	}

	private final Properties m_properties;

	private final ObjectPool m_pool;

	private Enumeration<?> m_propertyIterator;

	public UsingProperties(ObjectPool pool) throws IOException {
		m_pool = pool;
		m_properties = new Properties();

		s_logger.info("** UsingProperties()");
		InputStream propStream = this.getClass().getResourceAsStream(
				"example.properties");
		if (propStream == null) {
			s_logger.info("example.properties was null");
		} else {
			m_properties.load(propStream);
			propStream.close();
			s_logger.info("example.properties has " + m_properties.size()
					+ " entries");
		}
	}

	@Override
	public void activate() {
		s_logger.info("** UsingProperties.activate()");
		m_propertyIterator = m_properties.propertyNames();
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow)
			throws SQLException {
		if (m_propertyIterator == null || !m_propertyIterator.hasMoreElements()) {
			s_logger.fine("no more rows, returning false");
			return false;
		}
		String propName = (String) m_propertyIterator.nextElement();
		receiver.updateString(1, propName);
		receiver.updateString(2, m_properties.getProperty(propName));
		// s_logger.fine("next row created, returning true");
		return true;
	}

	@Override
	public void close() throws SQLException {
		m_pool.passivateInstance(this);
	}

	@Override
	public void passivate() {
		s_logger.info("** UsingProperties.passivate()");
		m_propertyIterator = null;
	}

	@Override
	public void remove() {
		s_logger.info("** UsingProperties.remove()");
		m_properties.clear();
	}
}
