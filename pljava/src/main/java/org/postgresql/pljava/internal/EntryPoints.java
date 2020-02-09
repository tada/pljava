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

import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;

/**
 * A class to consolidate entry points from C to PL/Java functions.
 *<p>
 * The methods in this class can be private, as they are invoked only from C
 * via JNI, not from Java.
 *<p>
 * The primary entry points are {@code refInvoke} (for a target method returning
 * any reference type) and {@code invoke} (for a target method with any other
 * return type, including {@code void}). The supplied method handles, as
 * obtained from {@code Function.create}, may have bound references to static
 * parameter areas, and will fetch the actual parameters from there to the
 * stack before invoking the (potentially reentrant) target method. Primitive
 * return values are then stored (after the potentially reentrant method has
 * returned) in the first slot of the static parameter area, to allow a single
 * {@code void}-returning {@code invoke} method to cover those cases, rather
 * than versions for every primitive return type.
 *<p>
 * The remaining methods here are for user-defined type (UDT) support. For now,
 * those are not consolidated into the {@code invoke}/{@code refInvoke} pattern,
 * as UDTs may need to be constructed from the C code while it is populating the
 * static parameter area for the ultimate target method, so the UDT methods
 * must be invocable without using the same area.
 */
class EntryPoints
{
	/**
	 * Entry point for any PL/Java function returning a reference type.
	 * @param mh MethodHandle obtained from Function.create that will push the
	 * actual parameters and call the target method.
	 * @return The value returned by the target method.
	 */
	private static Object refInvoke(MethodHandle mh) throws Throwable
	{
		return mh.invokeExact();
	}

	/**
	 * Entry point for a PL/Java function with any non-reference return type,
	 * including {@code void}.
	 * If the target method's return type is not {@code void}, the value will be
	 * returned in the first slot of the static parameter area.
	 * @param mh MethodHandle obtained from Function.create that will push the
	 * actual parameters and call the target method.
	 */
	private static void invoke(MethodHandle mh) throws Throwable
	{
		mh.invokeExact();
	}

	/**
	 * Entry point for calling the {@code writeSQL} method of a UDT.
	 * @param o the UDT instance
	 * @param stream the SQLOutput stream on which the type's internal
	 * representation will be written
	 */
	private static void udtWriteInvoke(SQLData o, SQLOutput stream)
	throws Throwable
	{
		o.writeSQL(stream);
	}

	/**
	 * Entry point for calling the {@code toString} method of a UDT.
	 * @param o the UDT instance
	 * @return the UDT's text representation
	 */
	private static String udtToStringInvoke(SQLData o)
	{
		return o.toString();
	}

	/**
	 * Entry point for calling the {@code readSQL} method of a UDT, after
	 * constructing an instance first.
	 * @param mh a MethodHandle that composes the allocation of a new instance
	 * by its no-arg constructor with the call of readSQL.
	 * @param stream the SQLInput stream from which to read the UDT's internal
	 * representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtReadInvoke(
		MethodHandle mh, SQLInput stream, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(stream, typeName);
	}

	/**
	 * Entry point for calling the {@code parse} method of a UDT, which will
	 * construct an instance given its text representation.
	 * @param mh a MethodHandle to the class's static parse method, which will
	 * allocate and return an instance.
	 * @param textRep the text representation
	 * @param typeName the SQL type name to be associated with the instance
	 * @return the allocated and initialized instance
	 */
	private static SQLData udtParseInvoke(
		MethodHandle mh, String textRep, String typeName)
	throws Throwable
	{
		return (SQLData)mh.invokeExact(textRep, typeName);
	}
}
