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
package org.postgresql.pljava.example.annotation;

import java.lang.reflect.Array;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.postgresql.pljava.annotation.Function;
import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Provides example methods to illustrate the polymorphic types {@code any},
 * {@code anyarray}, and {@code anyelement}.
 */
public class AnyTest {
	private static Logger s_logger = Logger.getAnonymousLogger();

	/**
	 * Log (at INFO level) the Java class received for the passed argument.
	 */
	@Function(schema="javatest", effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void logAny(@SQLType("pg_catalog.any") Object param)
	throws SQLException
	{
		s_logger.info("logAny received an object of class " + param.getClass());
	}

	/**
	 * Log (at INFO level) the Java class received for the passed argument, and
	 * return the same value.
	 */
	@Function(schema="javatest", effects=IMMUTABLE, onNullInput=RETURNS_NULL,
		type="pg_catalog.anyelement")
	public static Object logAnyElement(
		@SQLType("pg_catalog.anyelement") Object param)
	throws SQLException
	{
		s_logger.info("logAnyElement received an object of class "
				+ param.getClass());
		return param;
	}

	/**
	 * Return the Java object received for the passed argument, wrapped in a
	 * one-element array with the object's class as its element type.
	 */
	@Function(schema="javatest", effects=IMMUTABLE, onNullInput=RETURNS_NULL,
		type="pg_catalog.anyarray")
	public static Object[] makeArray(
		@SQLType("pg_catalog.anyelement") Object param)
	{
		Object[] result = (Object[]) Array.newInstance(param.getClass(), 1);
		result[0] = param;
		return result;
	}
}
