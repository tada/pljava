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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Random;
import java.util.logging.Logger;

import org.postgresql.pljava.ResultSetProvider;

/**
 * Example implementing {@code ResultSetProvider} to provide a function that
 * generates and returns a lot of rows (caller passes the desired row count)
 * each containing the row number, a random integer, and a timestamp.
 */
public class HugeResultSet implements ResultSetProvider {
	public static ResultSetProvider executeSelect(int rowCount)
			throws SQLException {
		return new HugeResultSet(rowCount);
	}

	private final int m_rowCount;

	private final Random m_random;

	public HugeResultSet(int rowCount) throws SQLException {
		m_rowCount = rowCount;
		m_random = new Random(System.currentTimeMillis());
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, int currentRow)
			throws SQLException {
		// Stop when we reach rowCount rows.
		//
		if (currentRow >= m_rowCount) {
			Logger.getAnonymousLogger().info("HugeResultSet ends");
			return false;
		}

		receiver.updateInt(1, currentRow);
		receiver.updateInt(2, m_random.nextInt());
		receiver.updateTimestamp(3, new Timestamp(System.currentTimeMillis()));
		return true;
	}

	@Override
	public void close() {
	}
}
