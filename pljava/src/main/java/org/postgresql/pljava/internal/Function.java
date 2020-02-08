/*
 * Copyright (c) 2016-2020 Tada AB and other contributors, as listed below.
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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.arrayElementGetter;
import static java.lang.invoke.MethodHandles.arrayElementSetter;
import static java.lang.invoke.MethodHandles.collectArguments;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodHandles.publicLookup;
import java.lang.invoke.MethodType;
import static java.lang.invoke.MethodType.methodType;
import java.lang.invoke.WrongMethodTypeException;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import static java.util.Arrays.copyOf;
import static java.util.Collections.addAll;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.postgresql.pljava.ResultSetHandle;
import org.postgresql.pljava.ResultSetProvider;
import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.jdbc.TypeOid.INVALID;
import org.postgresql.pljava.management.Commands;
import org.postgresql.pljava.sqlj.Loader;

/**
 * Methods to look up a PL/Java function and prepare it for invocation.
 *<p>
 * This class contains, mostly, logic that was originally in {@code Function.c}
 * and has been ported to Java for better maintainability and adaptability. Many
 * methods here have similar or identical names to the C functions they replace.
 *<p>
 * When a PL/Java function is called, the C call handler will call the C
 * {@code Function_getFunction}, which will delegate to {@code Function_create}
 * if the function has not already been called. That C function calls the
 * {@link #create create} method here, which ultimately (after all parsing of
 * the {@code CREATE FUNCTION ... AS ...} information, matching up of parameter
 * and return types, etc.) will return a {@code MethodHandle} to use when
 * invoking the function.
 *<p>
 * This remains a hybrid approach, in which PL/Java's legacy C {@code Type}
 * infrastructure is used for converting the parameter and return values, and a
 * C {@code Function_} structure kept in a C hash table holds the details
 * needed for invocation, including the {@code MethodHandle} created here. The
 * methods in this class also use some JNI calls to contribute and retrieve
 * additional details that belong in the C structure.
 *<p>
 * The method handle returned here by {@code create} will have return type of
 * either {@code Object} (for a target method returning any reference type) or
 * {@code void} (for a target method of any other return type, including
 * {@code void}), and no formal parameters. The method handle can contain bound
 * references to the static {@code s_referenceParameters} and
 * {@code s_primitiveParameters} declared in this class, and will fetch the
 * parameters from there (where invocation code in {@code Function.c} will have
 * put them) at the time of invocation. The parameter areas are static, but
 * invocation takes place only on the PG thread, and the method handles created
 * here will have fetched all of the values to push on the stack before the
 * (potentially reentrant) target method is invoked. If the method has a
 * primitive return type, its return value will be placed in the first slot of
 * {@code s_primitiveParameters} and the method handle returns {@code void}.
 * Naturally, the (potentially reentrant) target method has already returned
 * before that value is placed in the static slot.
 */
public class Function
{
	/**
	 * Prevent instantiation.
	 */
	private Function()
	{
	}

	private static class EarlyNatives
	{
		/*
		 * Pass the Java-allocated s_referenceParameters area down to the C code
		 * and obtain the ByteBuffer over the C-allocated primitives area.
		 */
		private static native ByteBuffer _parameterArea(Object[] refs);
	}

	/**
	 * Return null if the {@code prosrc} field in the provided {@code procTup}
	 * does not have the form of a UDT specification; if it does, return the
	 * associated class, loaded with the class loader for {@code schemaName}.
	 */
	public static Class<? extends SQLData> getClassIfUDT(
		ResultSet procTup, String schemaName)
	throws SQLException
	{
		Matcher info = parse(procTup);
		String className = info.group("udtcls");
		if ( null == className )
			return null;
		return
			loadClass(Loader.getSchemaLoader(schemaName), className)
				.asSubclass(SQLData.class);
	}

	/**
	 * Like the original C function of the same name, using effectively the same
	 * inputs, but producing a {@code MethodType} instead of a JNI signature.
	 *<p>
	 * The return type is the last element of {@code jTypes}.
	 */
	private static MethodType buildSignature(
		ClassLoader schemaLoader, String[] jTypes,
		boolean retTypeIsOutParameter, boolean isMultiCall, boolean altForm)
	throws SQLException
	{
		/*
		 * Begin by assuming we won't include the "return" type among the
		 * parameter types.
		 */
		int rtIdx = jTypes.length - 1;
		String retJType = jTypes[rtIdx];

		/*
		 * As things are currently arranged, retTypeIsOutParameter is equivalent
		 * to "the return type is composite" and "the type is ResultSet".
		 */
		assert retTypeIsOutParameter == ("java.sql.ResultSet".equals(retJType));

		/*
		 * And ... if the return type is composite, and this isn't a multi-call,
		 * then it does go at the end of the other parameters.
		 */
		if ( ! isMultiCall  &&  retTypeIsOutParameter )
			++ rtIdx;

		Class<?>[] pTypes = new Class[ rtIdx ];

		for ( int i = 0 ; i < rtIdx ; ++ i )
			pTypes[i] = loadClass(schemaLoader, jTypes[i]);

		Class<?> returnType =
			getReturnSignature(schemaLoader, retJType,
				retTypeIsOutParameter, isMultiCall, altForm);

		return methodType(returnType, pTypes);
	}

	/**
	 * Return a {@code Class} object for the target method's return type.
	 *<p>
	 * The C original in this case was a "virtual method" on {@code Type}, but
	 * only one "subclass" ({@code Composite}) ever overrode the default
	 * behavior. The default (for everything else but {@code Composite}) is to
	 * return the type unchanged in the non-multicall case, or {@code Iterator}
	 * (of that type) for multicall.
	 *<p>
	 * The overridden behavior for a composite type is to return boolean in the
	 * non-multicall case, else one of {@code ResultSetHandle} or
	 * {@code ResultSetProvider} depending on {@code altForm}.
	 */
	private static Class<?> getReturnSignature(
		ClassLoader schemaLoader, String retJType,
		boolean isComposite, boolean isMultiCall, boolean altForm)
	throws SQLException
	{
		if ( ! isComposite )
		{
			if ( isMultiCall )
				return Iterator.class;
			return loadClass(schemaLoader, retJType);
		}

		/* The composite case */
		if ( isMultiCall )
			return altForm ? ResultSetHandle.class : ResultSetProvider.class;
		return boolean.class;
	}

	/**
	 * A Lookup to be used for the few functions inside this module that are
	 * allowed to be declared in SQL.
	 */
	private static Lookup s_lookup =
		lookup().dropLookupMode(Lookup.PACKAGE);

	/**
	 * Return a Lookup appropriate to the target class, which will be a public
	 * Lookup unless the class is {@code Commands} in this module, whose public
	 * methods are the only ones inside this module that SQL is allowed to
	 * declare.
	 */
	private static Lookup lookupFor(Class<?> clazz)
	{
		if ( Commands.class == clazz )
			return s_lookup;
		return publicLookup().in(clazz);
	}

	/**
	 * Replacement for {@code getMethodID} in the C code, but producing a
	 * {@code MethodHandle} instead.
	 *<p>
	 * This is called in the cases where {@code init} would return a non-null
	 * method name: the non-UDT cases. UDT methods are handled their own
	 * special way.
	 *<p>
	 * This may modify the last element (the return type) of the {@code jTypes}
	 * array, in the course of hunting for the right return type of the method.
	 *<p>
	 * For now, this is a near-facsimile of the C implementation. A further step
	 * of refactoring into clearer idiomatic Java can come later.
	 */
	private static MethodHandle getMethodHandle(
		ClassLoader schemaLoader, Class<?> clazz, String methodName,
		String[] jTypes, boolean retTypeIsOutParameter, boolean isMultiCall)
	throws SQLException
	{
		MethodType mt =
			buildSignature(schemaLoader, jTypes, retTypeIsOutParameter,
				isMultiCall, false); // first try altForm = false

		ReflectiveOperationException ex1 = null;
		try
		{
			return lookupFor(clazz).findStatic(clazz, methodName, mt);
		}
		catch ( ReflectiveOperationException e )
		{
			ex1 = e;
		}

		MethodType origMT = mt;
		Class<?> altType = null;
		Class<?> realRetType = loadClass(schemaLoader, jTypes[jTypes.length-1]);

		/* COPIED COMMENT:
		 * One valid reason for not finding the method is when
		 * the return type used in the signature is a primitive and
		 * the true return type of the method is the object class that
		 * corresponds to that primitive.
		 */
		if ( realRetType.isPrimitive() )
		{
			altType = methodType(realRetType).wrap().returnType();
			realRetType = altType;
		}

		/* COPIED COMMENT:
		 * Another reason might be that we expected a ResultSetProvider
		 * but the implementation returns a ResultSetHandle that needs to be
		 * wrapped. The wrapping is internal so we retain the original
		 * return type anyway.
		 */
		if ( ResultSet.class == realRetType )
			altType = realRetType;

		if ( null != altType )
		{
			jTypes[jTypes.length - 1] = altType.getCanonicalName();
			mt = buildSignature(schemaLoader, jTypes, retTypeIsOutParameter,
					isMultiCall, true); // this time altForm = true
			try
			{
				return lookupFor(clazz).findStatic(clazz, methodName, mt);
			}
			catch ( ReflectiveOperationException e )
			{
				SQLException sqle =
					memberException(clazz, methodName, origMT,true/*isStatic*/);
				sqle.initCause(ex1);
				sqle.setNextException((SQLException)
					memberException(clazz, methodName, mt, true /*isStatic*/)
					.initCause(e));
				throw sqle;
			}	
		}

		throw (SQLException)
			memberException(clazz, methodName, origMT, true /*isStatic*/)
			.initCause(ex1);
	}

	/**
	 * Produce an exception for a class member not found, with a message similar
	 * to that of the C {@code PgObject_throwMemberError}.
	 */
	private static SQLException memberException(
		Class<?> clazz, String name, MethodType mt, boolean isStatic)
	{
		return new SQLNonTransientException(
			String.format("Unable to find%s method %s.%s with signature %s",
				(isStatic ? " static" : ""),
				clazz.getCanonicalName(), name, mt),
			"38000");
	}

	/**
	 * Adapt an arbitrary static method's handle to what the C call handler
	 * expects to invoke.
	 *<p>
	 * Java does not allow a {@code MethodHandle} to be invoked directly from
	 * JNI. The invocation has to pass through a Java method that in turn
	 * invokes the handle. So there has to be a common way to pass an arbitrary
	 * method's parameters, whether reference or primitive.
	 *<p>
	 * The convention here will be that the C call handler segregates all of the
	 * incoming parameters into an {@code Object} array for all those of
	 * reference type, and a C array of {@code jvalue} for the primitives. It
	 * then passes the {@code Object} array and a direct {@code ByteBuffer} that
	 * maps the {@code jvalue} array. Either may be null, if there are no
	 * parameters of reference or primitive type, respectively.
	 *<p>
	 * The job of this method is to take any static method handle {@code mh} and
	 * return a method handle that takes exactly two parameters, an
	 * {@code Object[]} and a {@code ByteBuffer}, and invokes the original
	 * handle with the parameter values unpacked to their proper positions.
	 *<p>
	 * The handle's return type will be unchanged if primitive, or, if any
	 * reference type, erased to {@code Object}. The erasure allows a single
	 * wrapper method for reference return types that can be declared to return
	 * {@code Object} and still use {@code invokeExact} on the method handle.
	 */
	private static MethodHandle adaptHandle(MethodHandle mh)
	{
		MethodType mt = mh.type();
		int parameterCount = mt.parameterCount();
		int primitives = (int)
			mt.parameterList().stream().filter(Class::isPrimitive).count();
		int references = parameterCount - primitives;
		short countCheck = (short)((references << 8) | (primitives & 0xff));

		boolean hasPrimitiveParams = 0 < primitives;

		/*
		 * "Erase" any/all reference types, parameter or return, to Object.
		 * Erasing the return type avoids wrong-method-type exceptions when
		 * invoking the handle from a wrapper method that returns Object.
		 * Erasing any reference parameter types is kind of a notational
		 * convenience: alternatively, each refGetter constructed below could
		 * be built to fetch from the Object array and cast to the non-erased
		 * parameter type, but this does the same for all of them in one
		 * swell foop.
		 */
		mh = mh.asType(mt.erase());

		/*
		 * mh represents a method taking some arbitrary arguments a0,...,a(n-1)
		 * where n is parameterCount. It is the ultimate target, invoked as the
		 * last thing that happens at invocation time.
		 *
		 * Each step in this construction produces a new mh that invokes the one
		 * from the step before, after doing something useful first. Therefore,
		 * the mh from the *last* of these construction steps is what will be
		 * invoked *first* when a call is later made. In other words, the order
		 * of these steps has a reverse-chronological flavor, producing handles
		 * whose chronological sequence will be last-to-first during a call.
		 */

		/*
		 * Iterate through the parameter indices in reverse order. Each step
		 * takes a method handle with parameters a0,...,ak
		 * and produces a handle with one fewer: a0,...ak-1.
		 * At invocation time, this handle will fetch the value for ak (from
		 * either the Object[] or the ByteBuffer as ak is of reference or
		 * primitive type), and invoke the next (in construction order, prior)
		 * handle.
		 *
		 * The handle left at the end of this loop will have only the
		 * parameters expected by the invocation target.
		 */
		while ( parameterCount --> 0 )
		{
			Class<?> pt = mt.parameterType(parameterCount);
			if ( pt.isPrimitive() )
			{
				MethodHandle primGetter;
				switch ( pt.getSimpleName() )
				{
				case "boolean": primGetter = s_booleanGetter; break;
				case "byte":    primGetter = s_byteGetter;    break;
				case "short":   primGetter = s_shortGetter;   break;
				case "char":    primGetter = s_charGetter;    break;
				case "int":     primGetter = s_intGetter;     break;
				case "float":   primGetter = s_floatGetter;   break;
				case "long":    primGetter = s_longGetter;    break;
				case "double":  primGetter = s_doubleGetter;  break;
				default:
					throw new AssertionError("unknown Java primitive type");
				}
				/*
				 * Each getter takes one argument: a byte
				 * offset. Use "insertArguments" to bind in the offset for this
				 * parameter, so the resulting getter handle has no arguments.
				 * (That nomenclature again! Here at construction
				 * time, "insertArguments" produces a method handle with *fewer*
				 * than the one it starts with. It's later, at call time, when
				 * the value(s) will get "inserted" as it calls the prior handle
				 * that expects them.)
				 */
				int offset = (--primitives) * s_sizeof_jvalue;
				primGetter = insertArguments(primGetter, 0, offset);
				/*
				 * The "foldArguments" combinator. At this step, let k be
				 * parameterCount, so we are looking at a method handle that
				 * takes a0,...,ak, and foldArguments(..., primGetter) will
				 * produce a shorter one a0,...,ak-1.
				 *
				 * At invocation time, the handle will invoke the primGetter
				 * (which has arity 0) on the corresponding number of parameters
				 * (0) starting at position k (or parameterCount, if you will).
				 * The result of the primGetter will become ak in the invocation
				 * of the next underlying handle.
				 *
				 * *Ahead* of the primGetter here (so, at invocation time,
				 * *after* the prim has been got), fold in a primitiveZeroer
				 * bound to the same offset, so argument values won't lie around
				 * indefinitely in the static area. Because the zeroer has void
				 * return and (once bound) no arguments, it has no effect on the
				 * argument list being constructed here for the target method.
				 */
				mh = foldArguments(mh, 0,
					insertArguments(s_primitiveZeroer, 0, offset));
				mh = foldArguments(mh, parameterCount, primGetter);
			}
			else
			{
				/*
				 * The same drill as above, only for reference-typed arguments,
				 * which will be fetched from the Object[].
				 */
				MethodHandle refGetter = s_refGetter;
				/*
				 * Again, s_refGetter has arity 1 (just the integer index);
				 * bind in the right index for this parameter, producing
				 * a getter with no argument.
				 *
				 * Also again, fold in a referenceNuller, both to prevent the
				 * lingering exposure of argument values in the static area and,
				 * as important, indefinitely holding the reference live. The
				 * nuller, once bound to the index, has no arguments and void
				 * return, so does not affect the argument list being built.
				 */
				int index = --references;
				refGetter = insertArguments(refGetter, 0, index);
				mh = foldArguments(mh, 0,
					insertArguments(s_referenceNuller, 0, index));
				mh = foldArguments(mh, parameterCount, refGetter);
			}
		}

		/*
		 * If the target has a primitive return type, add a filter that stashes
		 * the return value in slot 0 of the primitives static area. A return
		 * value of reference type is simply returned.
		 */
		Class<?> rt = mt.returnType();
		if ( void.class != rt  &&  rt.isPrimitive() )
		{
			MethodHandle primReturn;
			switch ( rt.getSimpleName() )
			{
			case "boolean": primReturn = s_booleanReturn; break;
			case "byte":    primReturn = s_byteReturn;    break;
			case "short":   primReturn = s_shortReturn;   break;
			case "char":    primReturn = s_charReturn;    break;
			case "int":     primReturn = s_intReturn;     break;
			case "float":   primReturn = s_floatReturn;   break;
			case "long":    primReturn = s_longReturn;    break;
			case "double":  primReturn = s_doubleReturn;  break;
			default:
				throw new AssertionError("unknown Java primitive return type");
			}

			mh = filterReturnValue(mh, primReturn);
		}

		/*
		 * The returned method handle will first confirm that it is being called
		 * with the expected numbers of reference and primitive parameters,
		 * throwing an exception if not.
		 */
		return foldArguments(mh,
			insertArguments(s_paramCountsAre, 0, countCheck));
	}

	private static final MethodHandle s_booleanReturn;
	private static final MethodHandle s_byteReturn;
	private static final MethodHandle s_shortReturn;
	private static final MethodHandle s_charReturn;
	private static final MethodHandle s_intReturn;
	private static final MethodHandle s_floatReturn;
	private static final MethodHandle s_longReturn;
	private static final MethodHandle s_doubleReturn;
	private static final MethodHandle s_booleanGetter;
	private static final MethodHandle s_byteGetter;
	private static final MethodHandle s_shortGetter;
	private static final MethodHandle s_charGetter;
	private static final MethodHandle s_intGetter;
	private static final MethodHandle s_floatGetter;
	private static final MethodHandle s_longGetter;
	private static final MethodHandle s_doubleGetter;
	private static final MethodHandle s_refGetter;
	private static final MethodHandle s_referenceNuller;
	private static final MethodHandle s_primitiveZeroer;
	private static final MethodHandle s_paramCountsAre;
	private static final int s_sizeof_jvalue = 8; // Function.c StaticAssertStmt
	private static final MethodHandle s_readSQL_mh;

	/*
	 * Static areas for passing reference and primitive parameters. A Java
	 * method can have no more than 255 parameters, so each area gets the
	 * worst-case allocation. s_primitiveParameters is a direct byte buffer
	 * over an array of 255 JNI jvalues. (It could be made half the size by
	 * taking into account the JVM convention that long/double take two slots
	 * each, but that can be a future optimization.)
	 *
	 * These areas will be bound into MethodHandle trees constructed over
	 * desired invocation targets. Such a constructed method handle will take
	 * care of pushing the arguments from the appropriate static slots. Because
	 * that happens before the target is invoked, and calls must happen on the
	 * PG thread, the static areas are safe for reentrant calls.
	 *
	 * Such a constructed method handle will be passed to EntryPoints.refInvoke
	 * if the invocation target returns a reference type (which becomes the
	 * return value of refInvoke). If the target has a primitive or void return
	 * type, the handle will be passed to EntryPoints.invoke, which has void
	 * return type. If the target returns a primitive value, the last act of the
	 * constructed method handle will be to store that in the first slot of
	 * s_primitiveParameters, where the C code will find it.
	 *
	 * The primitive parameters area is slightly larger than 255 jvalues; the
	 * next two bytes contain the numbers of actual parameters in the call (as
	 * an int16 with the count of reference parameters in the MSB, primitives
	 * in the LSB). Each constructed MethodHandle will have the corresponding
	 * int16 value bound in for comparison, and will throw an exception if
	 * invoked with the wrong parameter counts.
	 */
	private static final Object[] s_referenceParameters = new Object [ 255 ];
	private static final ByteBuffer s_primitiveParameters =
		EarlyNatives._parameterArea(s_referenceParameters)
		.order(ByteOrder.nativeOrder());
	private static final int s_offset_paramCounts = 255 * s_sizeof_jvalue;

	static
	{
		Lookup l = publicLookup();
		Lookup myL = lookup();
		MethodHandle toVoid = identity(ByteBuffer.class)
			.asType(methodType(void.class, ByteBuffer.class));
		MethodHandle longSetter = null;
		MethodType mt = methodType(byte.class, int.class);
		MethodHandle mh;

		try
		{
			s_byteGetter = l.findVirtual(ByteBuffer.class, "get", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(short.class);
			s_shortGetter = l.findVirtual(ByteBuffer.class, "getShort", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(char.class);
			s_charGetter = l.findVirtual(ByteBuffer.class, "getChar", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(int.class);
			s_intGetter = l.findVirtual(ByteBuffer.class, "getInt", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(float.class);
			s_floatGetter = l.findVirtual(ByteBuffer.class, "getFloat", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(long.class);
			s_longGetter = l.findVirtual(ByteBuffer.class, "getLong", mt)
				.bindTo(s_primitiveParameters);
			mt = mt.changeReturnType(double.class);
			s_doubleGetter = l.findVirtual(ByteBuffer.class, "getDouble", mt)
				.bindTo(s_primitiveParameters);

			mt = mt.changeReturnType(Object.class);
			s_refGetter = arrayElementGetter(Object[].class)
				.bindTo(s_referenceParameters);

			mt = methodType(boolean.class, byte.class);
			mh = myL.findStatic(Function.class, "byteNonZero", mt);
			s_booleanGetter = filterReturnValue(s_byteGetter, mh);

			mt = methodType(void.class, short.class);
			mh = myL.findStatic(Function.class, "paramCountsAre", mt);
			s_paramCountsAre = mh;

			mt = methodType(ByteBuffer.class, int.class, byte.class);
			mh = l.findVirtual(ByteBuffer.class, "put", mt)
				.bindTo(s_primitiveParameters);
			s_byteReturn = filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mt = mt.changeParameterType(1, short.class);
			mh = l.findVirtual(ByteBuffer.class, "putShort", mt)
				.bindTo(s_primitiveParameters);
			s_shortReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mt = mt.changeParameterType(1, char.class);
			mh = l.findVirtual(ByteBuffer.class, "putChar", mt)
				.bindTo(s_primitiveParameters);
			s_charReturn = filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mt = mt.changeParameterType(1, int.class);
			mh = l.findVirtual(ByteBuffer.class, "putInt", mt)
				.bindTo(s_primitiveParameters);
			s_intReturn = filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mt = mt.changeParameterType(1, float.class);
			mh = l.findVirtual(ByteBuffer.class, "putFloat", mt)
				.bindTo(s_primitiveParameters);
			s_floatReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mt = mt.changeParameterType(1, long.class);
			mh = l.findVirtual(ByteBuffer.class, "putLong", mt)
				.bindTo(s_primitiveParameters);
			longSetter = filterReturnValue(mh, toVoid);
			s_longReturn = insertArguments(longSetter, 0, 0);

			mt = mt.changeParameterType(1, double.class);
			mh = l.findVirtual(ByteBuffer.class, "putDouble", mt)
				.bindTo(s_primitiveParameters);
			s_doubleReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toVoid);

			mh = s_byteReturn;
			s_booleanReturn = guardWithTest(identity(boolean.class),
				dropArguments(
					insertArguments(mh, 0, (byte)1), 0, boolean.class),
				dropArguments(
					insertArguments(mh, 0, (byte)0), 0, boolean.class));

			s_readSQL_mh = l.findVirtual(SQLData.class, "readSQL",
					methodType(void.class, SQLInput.class, String.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}

		s_referenceNuller =
			insertArguments(arrayElementSetter(Object[].class), 2, (Object)null)
			.bindTo(s_referenceParameters);

		s_primitiveZeroer = insertArguments(longSetter, 1, 0L);
	}

	/**
	 * Converts a byte value to boolean as the JNI code does, where any nonzero
	 * value is true.
	 *<p>
	 * There is a {@code MethodHandles.explicitCastArguments} that will cast a
	 * byte to boolean by considering only the low bit. I don't trust it.
	 */
	private static boolean byteNonZero(byte b)
	{
		return 0 != b;
	}

	/**
	 * Throw a {@code WrongMethodTypeException} if the parameter counts in
	 * {@code counts} (references in the MSB, primitives in the LSB) do not
	 * match those at offset {@code s_offset_paramCounts} in the static passing
	 * area.
	 */
	private static void paramCountsAre(short counts)
	{
		short got = s_primitiveParameters.getShort(s_offset_paramCounts);
		if ( counts != got )
			throw new WrongMethodTypeException(String.format(
				"PL/Java invocation expects (%d reference/%d primitive) " +
				"parameter count but passed (%d reference/%d primitive)",
				(counts >>> 8), (counts & 0xff),
				(got >>> 8), (got & 0xff)));
	}

	/**
	 * Return a "construct+readSQL" method handle for a given UDT class.
	 *<p>
	 * The method handle will expect the two non-receiver arguments for
	 * {@code readSQL}, construct a new instance of the class, invoke
	 * {@code readSQL} on that instance and the two supplied arguments,
	 * and return the instance.
	 */
	private static MethodHandle udtReadHandle(Class<? extends SQLData> clazz)
	throws SQLException
	{
		Lookup l = lookupFor(clazz);
		MethodHandle ctor;

		try
		{
			ctor = l.findConstructor(clazz, methodType(void.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new SQLNonTransientException(
				"Java UDT implementing class " + clazz.getCanonicalName() +
				" must have a no-argument public constructor", "38000", e);
		}

		MethodHandle mh = identity(SQLData.class); // o -> o
		mh = collectArguments(mh, 1, s_readSQL_mh); // (o, o, stream, type) -> o
		mh = permuteArguments(mh, methodType(
			SQLData.class, SQLData.class, SQLInput.class, String.class),
			0, 0, 1, 2); // (o, stream, type) -> o
		ctor = ctor.asType(methodType(SQLData.class));
		mh = collectArguments(mh, 0, ctor); // (stream, type) -> o
		return mh;
	}

	/**
	 * Return a "parse" method handle for a given UDT class.
	 */
	private static MethodHandle udtParseHandle(Class<? extends SQLData> clazz)
	throws SQLException
	{
		Lookup l = lookupFor(clazz);

		try
		{
			return l.findStatic(clazz, "parse",
				methodType(clazz, String.class, String.class))
				.asType(methodType(SQLData.class, String.class, String.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new SQLNonTransientException(
				"Java scalar-UDT implementing class " +
				clazz.getCanonicalName() +
				" must have a public static parse(String,String) method",
				"38000", e);
		}
	}

	/**
	 * Parse the function specification in {@code procTup}, initializing most
	 * fields of the C {@code Function} structure, and returning a
	 * {@code MethodHandle} for invoking the method, or null in the case of
	 * a UDT.
	 */
	public static MethodHandle create(
		long wrappedPtr, ResultSet procTup, String langName, String schemaName,
		boolean calledAsTrigger)
	throws SQLException
	{
		Matcher info = parse(procTup);

		return init(wrappedPtr, info, procTup, schemaName, calledAsTrigger);
	}

	/**
	 * Retrieve the {@code prosrc} field from the provided {@code procTup}, and
	 * return it parsed as a {@code Matcher} object with named capturing groups.
	 */
	private static Matcher parse(ResultSet procTup) throws SQLException
	{
		String spec = getAS(procTup);

		Matcher m = specForms.matcher(spec);
		if ( ! m.matches() )
			throw new SQLSyntaxErrorException(
				"cannot parse AS string", "42601");

		return m;
	}

	/**
	 * Given the information passed to {@code create} and the {@code Matcher}
	 * object from {@code parse}, determine the type of function being created
	 * (ordinary, UDT, trigger) and initialize most of the C structure
	 * accordingly.
	 * @return a MethodHandle to invoke the implementing method, or null in the
	 * case of a UDT
	 */
	private static MethodHandle init(
		long wrappedPtr, Matcher info, ResultSet procTup, String schemaName,
		boolean calledAsTrigger)
	throws SQLException
	{
		Map<Oid,Class<? extends SQLData>> typeMap = null;
		String className = info.group("udtcls");
		boolean isUDT = (null != className);

		if ( ! isUDT )
		{
			className = info.group("cls");
			typeMap = Loader.getTypeMap(schemaName);
		}

		boolean readOnly = ((byte)'v' != procTup.getByte("provolatile"));

		ClassLoader schemaLoader = Loader.getSchemaLoader(schemaName);
		Class<?> clazz = loadClass(schemaLoader, className);

		if ( isUDT )
		{
			setupUDT(wrappedPtr, info, procTup, schemaLoader,
				clazz.asSubclass(SQLData.class), readOnly);
			return null;
		}

		String[] resolvedTypes;
		boolean isMultiCall = false;
		boolean retTypeIsOutParameter = false;

		if ( calledAsTrigger )
		{
			typeMap = null;
			resolvedTypes =	setupTriggerParams(
				wrappedPtr, info, schemaLoader, clazz, readOnly);
		}
		else
		{
			boolean[] multi = new boolean[] { isMultiCall };
			boolean[] rtiop = new boolean[] { retTypeIsOutParameter };
			resolvedTypes = setupFunctionParams(wrappedPtr, info, procTup,
				schemaLoader, clazz, readOnly, typeMap, multi, rtiop);
			isMultiCall = multi [ 0 ];
			retTypeIsOutParameter = rtiop [ 0 ];
		}

		String methodName = info.group("meth");

		return
			adaptHandle(getMethodHandle(schemaLoader, clazz, methodName,
				resolvedTypes, retTypeIsOutParameter, isMultiCall));
	}

	/**
	 * The initialization specific to a UDT function.
	 */
	private static void setupUDT(
		long wrappedPtr, Matcher info, ResultSet procTup,
		ClassLoader schemaLoader, Class<? extends SQLData> clazz,
		boolean readOnly)
	throws SQLException
	{
		String udtFunc = info.group("udtfun");
		int udtInitial = Character.toLowerCase(udtFunc.charAt(0));
		Oid udtId;
		switch ( udtInitial )
		{
		case 'i':
		case 'r':
			udtId = (Oid)procTup.getObject("prorettype");
			break;
		case 'o':
		case 's':
			udtId = ((Oid[])procTup.getObject("proargtypes"))[0];
			break;
		default:
			throw new SQLException("internal error in PL/Java UDT parsing");
		}

		MethodHandle parseMH = 'i' == udtInitial ? udtParseHandle(clazz) : null;
		MethodHandle readMH = udtReadHandle(clazz);

		doInPG(() -> _storeToUDT(wrappedPtr, schemaLoader,
			clazz, readOnly, udtInitial, udtId.intValue(),
			parseMH, readMH));
	}

	/**
	 * The initialization specific to a trigger function.
	 */
	private static String[] setupTriggerParams(
		long wrappedPtr, Matcher info,
		ClassLoader schemaLoader, Class<?> clazz, boolean readOnly)
	throws SQLException
	{
		if ( null != info.group("sig") )
			throw new SQLSyntaxErrorException(
				"Triggers may not have a Java parameter signature", "42601");

		Oid retType = INVALID;
		String retJType = "void";

		Oid[] paramTypes = { INVALID };
		String[] paramJTypes = { "org.postgresql.pljava.TriggerData" };

		return storeToNonUDT(wrappedPtr, schemaLoader, clazz, readOnly,
			false /* isMultiCall */,
			null /* typeMap */, retType, retJType, paramTypes, paramJTypes,
			null /* [returnTypeIsOutputParameter] */);
	}

	/**
	 * The initialization specific to an ordinary function.
	 */
	private static String[] setupFunctionParams(
		long wrappedPtr, Matcher info, ResultSet procTup,
		ClassLoader schemaLoader, Class<?> clazz,
		boolean readOnly, Map<Oid,Class<? extends SQLData>> typeMap,
		boolean[] multi, boolean[] returnTypeIsOP)
		throws SQLException
	{
		int numParams = procTup.getInt("pronargs");
		boolean isMultiCall = procTup.getBoolean("proretset");
		multi [ 0 ] = isMultiCall;
		Oid[] paramTypes = null;

		Oid returnType = (Oid)procTup.getObject("prorettype");

		if ( 0 < numParams )
			paramTypes = (Oid[])procTup.getObject("proargtypes");

		String[] resolvedTypes = storeToNonUDT(wrappedPtr, schemaLoader, clazz,
			readOnly, isMultiCall, typeMap,
			returnType, null /* returnJType */,
			paramTypes, null /* paramJTypes */,
			returnTypeIsOP);

		boolean returnTypeIsOutputParameter = returnTypeIsOP[0];

		String explicitSignature = info.group("sig");
		if ( null != explicitSignature )
		{
			/*
			 * An explicit signature given for the Java method requires a call
			 * to parseParameters to reconcile those types with the ones in
			 * resolvedTypes that the mapping from SQL types suggested above.
			 */
			parseParameters( wrappedPtr, resolvedTypes, explicitSignature,
				isMultiCall, returnTypeIsOutputParameter);
		}

		/* As in the original C setupFunctionParams, if an explicit Java return
		 * type is included in the AS string, now compare it to the previously
		 * resolved return type and adapt if they are different, like what
		 * happened just above in parseParameters for the parameters. A close
		 * look at parseParameters shows it can *also* have adjusted the return
		 * type ... that happens in the case where a composite value is returned
		 * using an appended OUT parameter and the actual function's return
		 * type is boolean. If that happened, the resolved type examined here
		 * will be the one parseParameters just put in - the actual type of the
		 * appended parameter - and if an explicit return type was also given
		 * in AS, that work just done will be overwritten by this to come.
		 * The case is probably one that has never come up in practice; it's
		 * probably not useful, but at the moment I am trying to duplicate the
		 * original behavior.
		 */

		String explicitReturnType = info.group("ret");
		if ( null != explicitReturnType )
		{
			String resolvedReturnType = resolvedTypes[resolvedTypes.length - 1];
			if ( ! explicitReturnType.equals(resolvedReturnType) )
			{
				/* Once again overload the reconcileTypes native method with a
				 * very slightly different behavior, this one keyed by index -2.
				 * In this case, its explicitTypes parameter will be a one-
				 * element array containing only the return type ... and the
				 * coercer, if needed, will be constructed with getCoerceOut
				 * instead of getCoerceIn.
				 */
				doInPG(() -> _reconcileTypes(wrappedPtr, resolvedTypes,
					new String[] { explicitReturnType }, -2));
			}
		}

		return resolvedTypes;
	}

	/**
	 * Apply the legacy PL/Java rules for matching the types in the SQL
	 * declaration of the function with those in the Java method signature.
	 */
	private static void parseParameters(
		long wrappedPtr, String[] resolvedTypes, String explicitSignature,
		boolean isMultiCall, boolean returnTypeIsOutputParameter)
		throws SQLException
	{
		boolean lastIsOut = ( ! isMultiCall ) && returnTypeIsOutputParameter;
		String[] explicitTypes = explicitSignature.isEmpty() ?
			new String[0] : COMMA.split(explicitSignature);

		int expect = resolvedTypes.length - (lastIsOut ? 0 : 1);

		if ( expect != explicitTypes.length )
			throw new SQLSyntaxErrorException(String.format(
				"AS (Java): expected %1$d parameter types, found %2$d",
				expect, explicitTypes.length), "42601");

		doInPG(() ->
		{
			for ( int i = 0 ; i < resolvedTypes.length - 1 ; ++ i )
			{
				if ( resolvedTypes[i].equals(explicitTypes[i]) )
					continue;
				_reconcileTypes(wrappedPtr, resolvedTypes, explicitTypes, i);
			}
		});

		if ( lastIsOut
			&& ! resolvedTypes[expect-1].equals(explicitTypes[expect-1]) )
		{
			/* Use the same reconcileTypes native method to handle the return
			 * type also ... its behavior must change a bit, so use index -1 to
			 * identify this case.
			 */
			doInPG(() ->
				_reconcileTypes(wrappedPtr, resolvedTypes, explicitTypes, -1));
		}
	}

	/**
	 * Pattern for splitting an explicit signature on commas, relying on
	 * whitespace already being stripped by {@code getAS}. Will not match
	 * consecutive, leading, or trailing commas.
	 */
	private static final Pattern COMMA = Pattern.compile("(?<=[^,]),(?=[^,])");

	/**
	 * Return a class given a loader to use and a canonical type name, as used
	 * in explicit signatures in the AS string. Just a bit of gymnastics to
	 * turn that form of name into the right class, including for primitives,
	 * void, and arrays.
	 */
	private static Class<?> loadClass(
		ClassLoader schemaLoader, String className)
	throws SQLException
	{
		Matcher m = typeNameInAS.matcher(className);
		m.matches();
		className = m.group(1);
		Class<?> c;

		switch ( className )
		{
		case "boolean": c = boolean.class; break;
		case    "byte": c =    byte.class; break;
		case   "short": c =   short.class; break;
		case     "int": c =     int.class; break;
		case    "long": c =    long.class; break;
		case    "char": c =    char.class; break;
		case   "float": c =   float.class; break;
		case  "double": c =  double.class; break;
		case    "void": c =    void.class; break;
		default:
			try
			{
				c = schemaLoader.loadClass(className);
			}
			catch ( ClassNotFoundException e )
			{
				throw new SQLNonTransientException(
					"No such class: " + className, "46103", e);
			}
		}

		if ( -1 != m.start(2) )
		{
			int ndims = (m.end(2) - m.start(2)) / 2;
			c = Array.newInstance(c, new int[ndims]).getClass();
		}

		return c;
	}

	/**
	 * Get the "AS" string (also known as the {@code prosrc} field of the
	 * {@code pg_proc} tuple), with whitespace stripped, and with an {@code =}
	 * separating the return type, if any, from the method name, per the rules
	 * of the earlier C implementation.
	 */
	private static String getAS(ResultSet procTup) throws SQLException
	{
		String spec = procTup.getString("prosrc"); // has NOT NULL constraint

		/* COPIED COMMENT */
		/* Strip all whitespace except the first one if it occures after
		 * some alpha numeric characers and before some other alpha numeric
		 * characters. We insert a '=' when that happens since it delimits
		 * the return value from the method name.
		 */
		/* ANALYZED COMMENT */
		/* Original code skipped every isspace() character encountered while
		 * atStart or passedFirst was true. Initially true, atStart was reset
		 * by the first non-isspace character. Initially false, passedFirst
		 * was set by ANY encounter of a non-isspace non-isalnum, OR of any
		 * non-isspace following at least one isspace AFTER atStart was reset.
		 * The = was added if the non-isspace character satisfied isalpha.
		 */
		spec = stripEarlyWSinAS.matcher(spec).replaceFirst("$2=");
		spec = stripOtherWSinAS.matcher(spec).replaceAll("");
		return spec;
	}


	/**
	 * Pattern used to strip early whitespace in an "AS" string.
	 */
	private static final Pattern stripEarlyWSinAS = Pattern.compile(
		"^(\\s*+)(\\p{Alnum}++)(\\s*+)(?=\\p{Alpha})"
	);

	/**
	 * Pattern used to strip the remaining whitespace in an "AS" string.
	 */
	private static final Pattern stripOtherWSinAS = Pattern.compile(
		"\\s*+"
	);

	/**
	 * The recognized forms of an "AS" string, distinguishable and broken out
	 * by named capturing groups.
	 */
	private static final Pattern specForms = Pattern.compile(
		"(?i:udt\\[(?<udtcls>[^]]++)\\](?<udtfun>input|output|receive|send))" +
		"|(?!(?i:udt\\[))" +
		"(?:(?<ret>[^=]++)=)?+(?<cls>(?:[^.(]++\\.?)+)\\.(?<meth>[^.(]++)" +
		"(?:\\((?<sig>[^)]*+)\\))?+"
	);

	/**
	 * The recognized form of a Java type name in an "AS" string.
	 * The first capturing group is the canonical name of a type; the second
	 * group, if present, matches one or more {@code []} array markers following
	 * the name (its length divided by two is the number of array dimensions).
	 */
	private static final Pattern typeNameInAS = Pattern.compile(
		"([^\\[]++)((?:\\[\\])++)?+"
	);

	/**
	 * Test whether the type {@code t0} is, directly or indirectly,
	 * a specialization of generic type {@code c0}.
	 * @param t0 a type to be checked
	 * @param c0 known generic type to check for
	 * @return null if {@code t0} does not extend {@code c0}, otherwise the
	 * array of type arguments with which it specializes {@code c0}
	 */
	private static Type[] specialization(Type t0, Class<?> c0)
	{
		Type t = t0;
		Class<?> c;
		ParameterizedType pt = null;
		TypeBindings latestBindings = null;
		Type[] actualArgs = null;

		if ( t instanceof Class )
		{
			c = (Class)t;
			if ( ! c0.isAssignableFrom(c) )
				return null;
			if ( c0 == c )
				return new Type[0];
		}
		else if ( t instanceof ParameterizedType )
		{
			pt = (ParameterizedType)t;
			c = (Class)pt.getRawType();
			if ( ! c0.isAssignableFrom(c) )
				return null;
			if ( c0 == c )
				actualArgs = pt.getActualTypeArguments();
			else
				latestBindings = new TypeBindings(null, pt);
		}
		else
			throw new AssertionError(
				"expected Class or ParameterizedType, got: " + t);

		if ( null == actualArgs )
		{
			List<Type> pending = new LinkedList();
			pending.add(c.getGenericSuperclass());
			addAll(pending, c.getGenericInterfaces());

			while ( ! pending.isEmpty() )
			{
				t = pending.remove(0);
				if ( null == t )
					continue;
				if ( t instanceof Class )
				{
					c = (Class)t;
					if ( c0 == c )
						return new Type[0];
				}
				else if ( t instanceof ParameterizedType )
				{
					pt = (ParameterizedType)t;
					c = (Class)pt.getRawType();
					if ( c0 == c )
					{
						actualArgs = pt.getActualTypeArguments();
						break;
					}
					if ( c0.isAssignableFrom(c) )
						pending.add(new TypeBindings(latestBindings, pt));
				}
				else if ( t instanceof TypeBindings )
				{
					latestBindings = (TypeBindings)t;
					continue;
				}
				else
					throw new AssertionError(
						"expected Class or ParameterizedType, got: " + t);
				if ( ! c0.isAssignableFrom(c) )
					continue;
				pending.add(c.getGenericSuperclass());
				addAll(pending, c.getGenericInterfaces());
			}
		}
		if ( null == actualArgs )
			throw new AssertionError(
				"failed checking whether " + t0 + " specializes " + c0);

		for ( int i = 0; i < actualArgs.length; ++ i )
			if ( actualArgs[i] instanceof TypeVariable )
				actualArgs[i] =
					latestBindings.resolve((TypeVariable)actualArgs[i]);

		return actualArgs;
	}

	/**
	 * A class recording the bindings made in a ParameterizedType to the type
	 * parameters in a GenericDeclaration<Class>. Implements {@code Type} so it
	 * can be added to the {@code pending} queue in {@code specialization}.
	 *<p>
	 * In {@code specialization}, the tree of superclasses/superinterfaces will
	 * be searched breadth-first, with all of a node's immediate supers enqueued
	 * before any from the next level. By recording a node's type variable to
	 * type argument bindings in an object of this class, and enqueueing it
	 * before any of the node's supers, any type variables encountered as actual
	 * type arguments to any of those supers should be resolvable in the object
	 * of this class most recently dequeued.
	 */
	static class TypeBindings implements Type
	{
		private final TypeVariable<?>[] formalTypeParams;
		private final Type[] actualTypeArgs;

		TypeBindings(TypeBindings prior, ParameterizedType pt)
		{
			actualTypeArgs = pt.getActualTypeArguments();
			formalTypeParams =
				((GenericDeclaration)pt.getRawType()).getTypeParameters();
			assert actualTypeArgs.length == formalTypeParams.length;

			if ( null == prior )
				return;

			for ( int i = 0; i < actualTypeArgs.length; ++ i )
			{
				Type t = actualTypeArgs[i];
				if ( actualTypeArgs[i] instanceof TypeVariable )
					actualTypeArgs[i] = prior.resolve((TypeVariable)t);
			}
		}

		Type resolve(TypeVariable v)
		{
			for ( int i = 0; i < formalTypeParams.length; ++ i )
				if ( formalTypeParams[i].equals(v) )
					return actualTypeArgs[i];
			throw new AssertionError("type binding not found for " + v);
		}
	}

	/**
	 * Wrap the native method to store the values computed in Java, for a
	 * non-UDT function, into the C {@code Function} structure. Returns an array
	 * of Java type names for the parameters, if any, as suggested by the C code
	 * based on the SQL types, and can indicate whether the method return type
	 * is an out parameter, if a one-element array of boolean is passed to
	 * receive that result.
	 */
	private static String[] storeToNonUDT(
		long wrappedPtr, ClassLoader schemaLoader, Class<?> clazz,
		boolean readOnly, boolean isMultiCall,
		Map<Oid,Class<? extends SQLData>> typeMap,
		Oid returnType, String returnJType, Oid[] paramTypes, String[] pJTypes,
		boolean[] returnTypeIsOutParameter)
	{
		int numParams;
		int[] paramOids;
		if ( null == paramTypes )
		{
			numParams = 0;
			paramOids = null;
		}
		else
		{
			numParams = paramTypes.length;
			paramOids = new int [ numParams ];
			for ( int i = 0 ; i < numParams ; ++ i )
				paramOids[i] = paramTypes[i].intValue();
		}

		String[] outJTypes = new String [ 1 + numParams ];

		boolean rtiop =
			doInPG(() -> _storeToNonUDT(
				wrappedPtr, schemaLoader, clazz, readOnly, isMultiCall, typeMap,
				numParams, returnType.intValue(), returnJType, paramOids,
				pJTypes, outJTypes));

		if ( null != returnTypeIsOutParameter )
			returnTypeIsOutParameter[0] = rtiop;

		return outJTypes;
	}

	private static native boolean _storeToNonUDT(
		long wrappedPtr, ClassLoader schemaLoader, Class<?> clazz,
		boolean readOnly, boolean isMultiCall,
		Map<Oid,Class<? extends SQLData>> typeMap,
		int numParams, int returnType, String returnJType,
		int[] paramTypes, String[] paramJTypes, String[] outJTypes);

	private static native void _storeToUDT(
		long wrappedPtr, ClassLoader schemaLoader,
		Class<? extends SQLData> clazz,
		boolean readOnly, int funcInitial, int udtOid,
		MethodHandle readMH, MethodHandle parseMH);

	private static native void _reconcileTypes(
		long wrappedPtr, String[] resolvedTypes, String[] explicitTypes, int i);
}
