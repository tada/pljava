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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides a function to log, at the specified level, a specified message.
 * The level is a {@link java.util.logging.Level} name, not a PostgreSQL
 * level name.
 */
public class LoggerTest {
	public static void logMessage(String logLevel, String message) {
		Logger.getAnonymousLogger().log(Level.parse(logLevel), message);
	}
}
