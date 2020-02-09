/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import junit.framework.TestCase;

import static org.junit.Assert.*;
import org.junit.Ignore;
import static org.hamcrest.CoreMatchers.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

@Ignore("Function class has native method now, can't unit test")
public class FunctionCreationTest extends TestCase
{
	public FunctionCreationTest(String name) { super(name); }

	public void testSpecialization() throws Exception
	{
		Method specialization =
			Function.class.getDeclaredMethod(
				"specialization", Type.class, Class.class);
		specialization.setAccessible(true);

		Method m1 = FunctionCreationTest.class.getMethod("testM1");
		Type m1rt = m1.getGenericReturnType();

		assertNull(specialization.invoke(null, m1rt, Number.class));

		Type[] expected = new Type[] { String.class };
		Type[] actual   =
			(Type[])specialization.invoke(null, m1rt, ThreadLocal.class);

		assertArrayEquals(
			"failure - did not find String in ThreadLocal<String>",
			expected, actual);

		Method m2 = FunctionCreationTest.class.getMethod("testM2");
		Type m2rt = m2.getGenericReturnType();

		actual = (Type[])specialization.invoke(null, m2rt, ThreadLocal.class);

		assertArrayEquals(
			"failure - did not find String in Foo extends ThreadLocal<String>",
			expected, actual);

		Method m3 = FunctionCreationTest.class.getMethod("testM3");
		Type m3rt = m3.getGenericReturnType();

		actual = (Type[])specialization.invoke(null, m3rt, ThreadLocal.class);

		assertArrayEquals(
			"failure - on Baz extends Bar<String> extends ThreadLocal<T>",
			expected, actual);
	}

	public ThreadLocal<String> testM1() { return null; }

	public Foo testM2() { return null; }

	public Baz testM3() { return null; }

	static class Foo extends ThreadLocal<String> { }

	static class Bar<T> extends ThreadLocal<T> { }

	static class Baz extends Bar<String> { }
}
