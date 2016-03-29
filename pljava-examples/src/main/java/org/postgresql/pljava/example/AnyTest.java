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

import java.lang.reflect.Array;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Provides example methods to illustrate the polymorphic types {@code any},
 * {@code anyarray}, and {@code anyelement}.
 */
public class AnyTest {
	private static Logger s_logger = Logger.getAnonymousLogger();

	public static void logAny(Object param) throws SQLException {
		s_logger.info("logAny received an object of class " + param.getClass());
	}

	public static Object logAnyElement(Object param) throws SQLException {
		s_logger.info("logAnyElement received an object of class "
				+ param.getClass());
		return param;
	}

	public static Object[] makeArray(Object param) {
		Object[] result = (Object[]) Array.newInstance(param.getClass(), 1);
		result[0] = param;
		return result;
	}
}
