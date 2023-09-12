/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLType;

/**
 * Example to return a 2D array of {@code double}.
 */
public class DArray2 {
	private DArray2() { } // do not instantiate

	/**
	 * Returns null as a {@code double[][]}.
	 */
	@Function(
		schema = "javatest", type="double precision[]"
	)
	public static double[][] darray2()
	{
		return null;
	}
}
