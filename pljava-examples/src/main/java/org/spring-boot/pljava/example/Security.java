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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;

/**
 * Provides a {@link #createTempFile createTempFile} function, expected to fail
 * if it is declared with the <em>trusted</em> {@code java} language.
 */
public class Security {
	/**
	 * The following method should fail if the language in use is trusted.
	 * 
	 * @return The name of a created temporary file.
	 * @throws SQLException
	 */
	public static String createTempFile() throws SQLException {
		try {
			File tmp = File.createTempFile("pljava", ".test");
			tmp.deleteOnExit();
			return tmp.getAbsolutePath();
		} catch (IOException e) {
			throw new SQLException(e.getMessage());
		}
	}
}
