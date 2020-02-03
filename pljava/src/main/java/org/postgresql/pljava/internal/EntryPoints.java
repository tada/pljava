/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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

import java.lang.invoke.MethodHandle;

import java.nio.ByteBuffer;

import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;

class EntryPoints
{
	private static Object invoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return mh.invokeExact(references, primitives);
	}

	private static void voidInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		mh.invokeExact(references, primitives);
	}

	private static boolean booleanInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (boolean)mh.invokeExact(references, primitives);
	}

	private static byte byteInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (byte)mh.invokeExact(references, primitives);
	}

	private static short shortInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (short)mh.invokeExact(references, primitives);
	}

	private static char charInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (char)mh.invokeExact(references, primitives);
	}

	private static int intInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (int)mh.invokeExact(references, primitives);
	}

	private static float floatInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (float)mh.invokeExact(references, primitives);
	}

	private static long longInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (long)mh.invokeExact(references, primitives);
	}

	private static double doubleInvoke(
		MethodHandle mh, Object[] references, ByteBuffer primitives)
	throws Throwable
	{
		return (double)mh.invokeExact(references, primitives);
	}

	private static void udtWriteInvoke(SQLData o, SQLOutput stream)
	throws Throwable
	{
		o.writeSQL(stream);
	}

	private static String udtToStringInvoke(SQLData o)
	{
		return o.toString();
	}

	/*
	 * Expect a MethodHandle that composes the allocation of a new instance
	 * by its no-arg constructor with the call of readSQL.
	 */
	private static SQLData udtReadInvoke(
		MethodHandle mh, SQLInput stream, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(stream, typeName);
	}

	/*
	 * Expect a MethodHandle to the class's static parse method, which will
	 * allocate and return an instance.
	 */
	private static SQLData udtParseInvoke(
		MethodHandle mh, String stringRep, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(stringRep, typeName);
	}
}
