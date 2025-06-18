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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.ResultSetProvider;

/**
 * Example using {@code ResultSetProvider} to return 100 rows of two
 * {@code bytea} columns each, which should be equal in each row, one being
 * set by {@link java.sql.ResultSet#updateBinaryStream updateBinaryStream}
 * and the other by {@link java.sql.ResultSet#updateBytes updateBytes}.
 */
public class BinaryColumnTest implements ResultSetProvider {
	public static ResultSetProvider getBinaryPairs() {
		return new BinaryColumnTest();
	}

	@Override
	public boolean assignRowValues(ResultSet rs, int rowCount)
			throws SQLException {
		try {
			if (rowCount >= 100)
				return false;

			int offset = rowCount * 100;
			ByteArrayOutputStream bld = new ByteArrayOutputStream();
			DataOutputStream da = new DataOutputStream(bld);
			for (int idx = 0; idx < 100; ++idx)
				da.writeInt(offset + idx);
			byte[] bytes = bld.toByteArray();
			ByteArrayInputStream input = new ByteArrayInputStream(bytes);
			rs.updateBinaryStream(1, input, bytes.length);
			rs.updateBytes(2, bytes);
			return true;
		} catch (IOException e) {
			throw new SQLException(e.getMessage());
		}
	}

	@Override
	public void close() throws SQLException {
	}
}
