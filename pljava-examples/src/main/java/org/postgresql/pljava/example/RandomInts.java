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

import java.sql.SQLException;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Random;

/**
 * Provides a {@link #createIterator function} producing as many rows as
 * requested, each with a random int.
 */
public class RandomInts implements Iterator<Integer> {
	public static Iterator<Integer> createIterator(int rowCount)
			throws SQLException {
		return new RandomInts(rowCount);
	}

	private final int m_rowCount;
	private final Random m_random;

	private int m_currentRow;

	public RandomInts(int rowCount) throws SQLException {
		m_rowCount = rowCount;
		m_random = new Random(System.currentTimeMillis());
	}

	@Override
	public boolean hasNext() {
		return m_currentRow < m_rowCount;
	}

	@Override
	public Integer next() {
		if (m_currentRow < m_rowCount) {
			++m_currentRow;
			return Integer.valueOf(m_random.nextInt());
		}
		throw new NoSuchElementException();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}
}
