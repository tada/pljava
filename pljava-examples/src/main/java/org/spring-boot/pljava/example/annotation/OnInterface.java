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

/**
 * Illustrates PL/Java functions on an interface instead of a class.
 *<p>
 * The SQL/JRT standard has always just said "class", but there is no technical
 * obstacle to permitting a PL/Java function to be a static interface method, so
 * that earlier restriction has been relaxed.
 */
public interface OnInterface
{
	/**
	 * Returns the answer.
	 */
	@Function(schema = "javatest")
	static int answer()
	{
		return 42;
	}

	interface A
	{
		/**
		 * Again the answer.
		 */
		@Function(schema = "javatest")
		static int nestedAnswer()
		{
			return 42;
		}
	}

	class B
	{
		/**
		 * Still the answer.
		 */
		@Function(schema = "javatest")
		public static int nestedClassAnswer()
		{
			return 42;
		}

		public static class C
		{
			/**
			 * That answer again.
			 */
			@Function(schema = "javatest")
			public static int moreNestedAnswer()
			{
				return 42;
			}
		}
	}
}
