/*
 * Copyright (c) 2016-2025 Tada AB and other contributors, as listed below.
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
import static java.lang.invoke.MethodHandles.constant;
import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.empty;
import static java.lang.invoke.MethodHandles.exactInvoker;
import static java.lang.invoke.MethodHandles.explicitCastArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.permuteArguments;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodHandles.zero;
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

import java.security.AccessControlContext;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.security.Principal;
import java.security.ProtectionDomain;

import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.regex.Pattern.compile;

import javax.security.auth.Subject;
import javax.security.auth.SubjectDomainCombiner;

import org.postgresql.pljava.PLPrincipal;
import org.postgresql.pljava.ResultSetHandle;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.internal.Backend.getListConfigOption;
import static org.postgresql.pljava.internal.Backend.WITHOUT_ENFORCEMENT;
import static org.postgresql.pljava.internal.Backend.allowingUnenforcedUDT;
import org.postgresql.pljava.internal.EntryPoints;
import org.postgresql.pljava.internal.EntryPoints.Invocable;
import static org.postgresql.pljava.internal.EntryPoints.invocable;
import static org.postgresql.pljava.internal.EntryPoints.loadAndInitWithACC;
import static org.postgresql.pljava.internal.Privilege.doPrivileged;
import static org.postgresql.pljava.jdbc.TypeOid.INVALID;
import static org.postgresql.pljava.jdbc.TypeOid.TRIGGEROID;
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
 * and return types, etc.) will return an {@code Invocable} to use when
 * invoking the function.
 *<p>
 * This remains a hybrid approach, in which PL/Java's legacy C {@code Type}
 * infrastructure is used for converting the parameter and return values, and a
 * C {@code Function_} structure kept in a C hash table holds the details
 * needed for invocation, including the {@code Invocable} created here. The
 * methods in this class also use some JNI calls to contribute and retrieve
 * additional details that belong in the C structure.
 *<p>
 * The {@code Invocable} returned here by {@code create} will have return type
 * {@code Object} in all cases, and no formal parameters. It can
 * contain bound references to the static {@code s_referenceParameters} and
 * {@code s_primitiveParameters} declared in this class, and will fetch the
 * parameters from there (where invocation code in {@code Function.c} will have
 * put them) at the time of invocation. The parameter areas are static, but
 * invocation takes place only on the PG thread, and the method handles created
 * here will have fetched all of the values to push on the stack before the
 * (potentially reentrant) target method is invoked. If the method has a
 * primitive return type, its return value will be placed in the first slot of
 * {@code s_primitiveParameters} and the {@code Invocable} returns null.
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

		Identifier.Simple schema = Identifier.Simple.fromCatalog(schemaName);
		return
			loadClass(Loader.getSchemaLoader(schema), className, null)
				.asSubclass(SQLData.class);
	}

	/**
	 * Like the original C function of the same name, using effectively the same
	 * inputs, but producing a {@code MethodType} instead of a JNI signature.
	 *<p>
	 * The return type is the last element of {@code jTypes}.
	 *<p>
	 * {@code acc} is non-null if validating and class initializers should
	 * be run for parameter and return-type classes; in any other case it is
	 * null (see {@code loadClass}).
	 */
	private static MethodType buildSignature(
		ClassLoader schemaLoader, String[] jTypes, AccessControlContext acc,
		boolean commute,
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

		Class<?>[] pTypes = new Class<?>[ rtIdx ];

		for ( int i = 0 ; i < rtIdx ; ++ i )
			pTypes[i] = loadClass(schemaLoader, jTypes[i], acc);

		if ( commute )
		{
			Class<?> t = pTypes[0];
			pTypes[0] = pTypes[1];
			pTypes[1] = t;
		}

		Class<?> returnType =
			getReturnSignature(schemaLoader, retJType, acc,
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
	 *<p>
	 * {@code acc} is non-null if validating and class initializers should
	 * be run for parameter and return-type classes; in any other case it is
	 * null (see {@code loadClass}).
	 */
	private static Class<?> getReturnSignature(
		ClassLoader schemaLoader, String retJType, AccessControlContext acc,
		boolean isComposite, boolean isMultiCall, boolean altForm)
	throws SQLException
	{
		if ( ! isComposite )
		{
			if ( isMultiCall )
				return Iterator.class;
			return loadClass(schemaLoader, retJType, acc);
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
	 *<p>
	 * {@code acc} is non-null if validating and class initializers should
	 * be run for parameter and return-type classes; in any other case it is
	 * null (see {@code loadClass}).
	 */
	private static MethodHandle getMethodHandle(
		ClassLoader schemaLoader, Class<?> clazz, String methodName,
		AccessControlContext acc, boolean commute,
		String[] jTypes, boolean retTypeIsOutParameter, boolean isMultiCall)
	throws SQLException
	{
		MethodType mt =
			buildSignature(schemaLoader, jTypes, acc, commute,
				retTypeIsOutParameter, isMultiCall, false); // try altForm false

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
		Class<?> realRetType =
			loadClass(schemaLoader, jTypes[jTypes.length-1], acc);

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
			mt = buildSignature(schemaLoader, jTypes, acc, commute,
				retTypeIsOutParameter, isMultiCall, true); // retry altForm true
			try
			{
				MethodHandle h =
					lookupFor(clazz).findStatic(clazz, methodName, mt);
				return filterReturnValue(h, s_wrapWithPicker);
			}
			catch ( ReflectiveOperationException e )
			{
				SQLException sqe1 =
					memberException(clazz, methodName, origMT, ex1,
						true /*isStatic*/);
				SQLException sqe2 =
					memberException(clazz, methodName, mt, e,
						true /*isStatic*/);

				/*
				 * If one of the exceptions is NoSuchMethodException and the
				 * other isn't, then the one that isn't carries news about
				 * a problem with a method that actually was found. If that's
				 * the second one, we'll just lie a little about the order and
				 * report it first. (We never promised what order we'd do the
				 * lookups in anyway, and the current Java-to-PG exception
				 * translation only preserves the "first" one's details.)
				 */
				if ( ex1 instanceof NoSuchMethodException
					&& ! (e instanceof NoSuchMethodException) )
				{
					sqe2.setNextException(sqe1);
					throw sqe2;
				}

				sqe1.setNextException(sqe2);
				throw sqe1;
			}	
		}

		throw
			memberException(clazz, methodName, origMT, ex1, true /*isStatic*/);
	}

	/**
	 * Produce an exception for a class member not found, with a message that
	 * may include details from further down an exception's chain of causes.
	 */
	private static SQLException memberException(
		Class<?> clazz, String name, MethodType mt,
		ReflectiveOperationException e, boolean isStatic)
	{
		/*
		 * The most useful detail message to include may not be that
		 * of e itself, but further down the chain of causes, particularly
		 * if e is IllegalAccessException, which handle lookup can throw even
		 * for causes that aren't illegal access but rather linkage errors.
		 */
		Throwable t, prev;
		t = prev = e;
		for ( Class<?> c : List.of(
			IllegalAccessException.class, LinkageError.class,
			ClassNotFoundException.class, Void.class) )
		{
			if ( ! c.isInstance(t) )
			{
				t = prev;
				break;
			}
			prev = t;
			t = t.getCause();
		}

		String detail = (null == t) ? "" : (": " + t);

		return new SQLNonTransientException(
			String.format("resolving %smethod %s.%s with signature %s%s",
				(isStatic ? "static " : ""),
				clazz.getCanonicalName(), name, mt, detail),
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
	 * reference type, and a C array of {@code jvalue} for the primitives.
	 * Those arrays are static, and will be bound into the method handle
	 * produced here, which will fetch values from them when invoked.
	 *<p>
	 * The job of this method is to take any static method handle {@code mh} and
	 * return a method handle that takes no parameters, and invokes the original
	 * handle with the parameter values unpacked to their proper positions.
	 *<p>
	 * The handle's return type will always be {@code Object}. If the target
	 * has {@code void} or a primitive return type, null will be returned. Any
	 * primitive value returned by the target will be found in the first static
	 * primitive parameter slot. This convention allows a single wrapper method
	 * for all return types.
	 */
	private static MethodHandle adaptHandle(MethodHandle mh)
	{
		MethodType mt = mh.type();
		int parameterCount = mt.parameterCount();
		int primitives = (int)
			mt.parameterList().stream().filter(Class::isPrimitive).count();
		int references = parameterCount - primitives;
		short countCheck = (short)((references << 8) | (primitives & 0xff));

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
		 *
		 * As a first construction step (and therefore the last thing to happen
		 * before the target method is ultimately invoked), add a countsZeroer
		 * (if there were nonzero parameter counts) to announce that the values
		 * have all been fetched and the static parameter area is free for use.
		 * The countsZeroer has void return and no parameters, so it doesn't
		 * affect the constructed parameter list.
		 */
		if ( 0 != countCheck )
			mh = foldArguments(mh, 0, s_countsZeroer);

		/*
		 * Iterate through the parameter indices in reverse order. Each step
		 * takes a method handle with parameters a0,...,ak
		 * and produces a handle with one fewer: a0,...ak-1.
		 * At invocation time, this handle will fetch the value for ak (from
		 * either the Object[] or the ByteBuffer as ak is of reference or
		 * primitive type), and invoke the next (in construction order, prior)
		 * handle.
		 *
		 * The handle left at the end of this loop will expect no parameters.
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
		if ( void.class == rt )
			mh = filterReturnValue(mh, s_voidToNull);
		else if ( rt.isPrimitive() )
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
		 * with the expected numbers of reference and primitive parameters ready
		 * in the static parameter areas, throwing an exception if not.
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
	private static final MethodHandle s_voidToNull;
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
	private static final MethodHandle s_countsZeroer;
	private static final MethodHandle s_nonNull;
	private static final MethodHandle s_not;
	private static final MethodHandle s_boxedNot;

	/*
	 * Handles used to retrieve rows using SFRM_ValuePerCall protocol, from a
	 * function that returns an Iterator or ResultSetProvider, respectively.
	 * (One that returns a ResultSetHandle gets its return value wrapped with a
	 * ResultSetPicker and is then treated as in the ResultSetProvider case.)
	 */
	private static final MethodHandle s_iteratorVPC;
	private static final MethodHandle s_resultSetProviderVPC;
	private static final MethodHandle s_wrapWithPicker;

	private static final int s_sizeof_jvalue = 8; // Function.c StaticAssertStmt

	/**
	 * An {@code AccessControlContext} representing "nobody special": it should
	 * enjoy whatever permissions the {@code Policy} grants to everyone, but no
	 * others.
	 *
	 * This will be clapped on top of any {@code Invocable} whose target isn't
	 * in a PL/Java-managed jar or in PL/Java itself; PL/Java has always allowed
	 * {@code CREATE FUNCTION} to name some Java library class directly, but in
	 * such a case, the permissions should still be limited to what the policy
	 * would allow a PL/Java function.
	 */
	private static final AccessControlContext s_lid;

	/**
	 * An {@code AccessControlContext} representing "no other restrictions":
	 * it will be used to build the initial context for any {@code Invocable}
	 * whose target is in a PL/Java-managed jar, so that it will enjoy whatever
	 * permissions the policy grants to its jar directly.
	 */
	private static final AccessControlContext s_noLid;

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
	 * PG thread, the static areas are safe for reentrant calls (but for an edge
	 * case involving UDTs among the parameters and a UDT function that incurs
	 * a reentrant call; it may never happen, but that's what the ParameterFrame
	 * class is for, below).
	 *
	 * Such a constructed method handle will be passed, wrapped in
	 * an Invocable, to EntryPoints.invoke, which is declared to return
	 * Object always. If the target returns a primitive value, the last act of
	 * the constructed method handle will be to store that in the first slot of
	 * s_primitiveParameters, where the C code will find it, and return null as
	 * its "Object" return value.
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

	/**
	 * Class used to stack parameters for an in-construction call if needed for
	 * the (unlikely) re-entrant use of the static parameter area.
	 *<p>
	 * The need for this should be rare, as the only obvious cases for PL/Java
	 * upcalls that can occur while assembling another call's parameter list
	 * will be for UDTs that appear among those parameters. On the other hand,
	 * nothing restricts what a UDT method is allowed to do, so in the unlikely
	 * case it does something heavy enough to involve another upcall, there has
	 * to be a way for that to work.
	 */
	static class ParameterFrame
	{
		private static ParameterFrame s_stack = null;
		private ParameterFrame m_prev;
		private Object[] m_refs;
		private byte[] m_prims;

		/**
		 * Construct a copy of the current in-progress parameter area.
		 */
		private ParameterFrame()
		{
			short counts = s_primitiveParameters.getShort(s_offset_paramCounts);
			assert 0 != counts : "ParameterFrame() called when no parameters";

			int refs = counts >>> 8;
			int prims = counts & 0xff;

			if ( 0 < refs )
				m_refs = copyOf(s_referenceParameters, refs);

			if ( 0 < prims )
			{
				m_prims = new byte [ prims * s_sizeof_jvalue ];
				// Java 13: s_primitiveParameters.get(0, m_prims);
				s_primitiveParameters.get(m_prims).position(0);
			}

			m_prev = s_stack;
		}

		/**
		 * Push a copy of the current in-progress parameter area; called only
		 * via JNI.
		 *<p>
		 * Only happens on the PG thread.
		 */
		private static void push()
		{
			s_stack = new ParameterFrame();
			s_primitiveParameters.putShort(s_offset_paramCounts, (short)0);
		}

		/**
		 * Pop a stacked parameter frame; called only via JNI, only when
		 * the current invocation is known to have pushed one.
		 */
		private static void pop()
		{
			ParameterFrame f = s_stack;
			s_stack = f.m_prev;

			int refs = 0;
			int prims = 0;

			if ( null != f.m_refs )
			{
				refs = f.m_refs.length;
				System.arraycopy(f.m_refs, 0, s_referenceParameters, 0, refs);
			}

			if ( null != f.m_prims )
			{
				int len = f.m_prims.length;
				prims = len / s_sizeof_jvalue;
				// Java 13: s_primitiveParameters.put(0, f.m_prims);
				s_primitiveParameters.put(f.m_prims).position(0);
			}

			s_primitiveParameters.putShort(s_offset_paramCounts,
				(short)((refs << 8) | (prims & 0xff)));
		}
	}

	static
	{
		Lookup l = publicLookup();
		Lookup myL = lookup();
		MethodHandle toVoid = empty(methodType(void.class, ByteBuffer.class));
		MethodHandle toNull = empty(methodType(Object.class, ByteBuffer.class));
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

			s_voidToNull = zero(Object.class);

			mt = methodType(ByteBuffer.class, int.class, byte.class);
			mh = l.findVirtual(ByteBuffer.class, "put", mt)
				.bindTo(s_primitiveParameters);
			s_byteReturn = filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, short.class);
			mh = l.findVirtual(ByteBuffer.class, "putShort", mt)
				.bindTo(s_primitiveParameters);
			s_shortReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, char.class);
			mh = l.findVirtual(ByteBuffer.class, "putChar", mt)
				.bindTo(s_primitiveParameters);
			s_charReturn = filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, int.class);
			mh = l.findVirtual(ByteBuffer.class, "putInt", mt)
				.bindTo(s_primitiveParameters);
			s_intReturn = filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, float.class);
			mh = l.findVirtual(ByteBuffer.class, "putFloat", mt)
				.bindTo(s_primitiveParameters);
			s_floatReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, long.class);
			mh = l.findVirtual(ByteBuffer.class, "putLong", mt)
				.bindTo(s_primitiveParameters);
			longSetter = filterReturnValue(mh, toVoid);
			s_longReturn = filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mt = mt.changeParameterType(1, double.class);
			mh = l.findVirtual(ByteBuffer.class, "putDouble", mt)
				.bindTo(s_primitiveParameters);
			s_doubleReturn =
				filterReturnValue(insertArguments(mh, 0, 0), toNull);

			mh = s_byteReturn;
			s_booleanReturn = guardWithTest(identity(boolean.class),
				dropArguments(
					insertArguments(mh, 0, (byte)1), 0, boolean.class),
				dropArguments(
					insertArguments(mh, 0, (byte)0), 0, boolean.class));

			s_referenceNuller =
				insertArguments(
					arrayElementSetter(Object[].class), 2, (Object)null)
				.bindTo(s_referenceParameters);

			s_primitiveZeroer = insertArguments(longSetter, 1, 0L);

			s_countsZeroer =
				insertArguments(s_primitiveZeroer, 0, s_offset_paramCounts);

			s_nonNull = l.findStatic(Objects.class, "nonNull",
				methodType(boolean.class, Object.class));

			s_not = guardWithTest(identity(boolean.class),
				dropArguments(constant(boolean.class, false), 0, boolean.class),
				dropArguments(constant(boolean.class, true), 0, boolean.class));

			s_boxedNot =
				guardWithTest(
					explicitCastArguments(s_nonNull,
						methodType(boolean.class, Boolean.class)),
					explicitCastArguments(s_not,
						methodType(Boolean.class, Boolean.class)),
					identity(Boolean.class));

			/*
			 * Build a bit of MethodHandle tree for invoking a set-returning
			 * user function that will implement the ValuePerCall protocol.
			 * Such a function will return either an Iterator or a
			 * ResultSetProvider (or a ResultSetHandle, more on that further
			 * below). Its MethodHandle, as obtained from AdaptHandle, of course
			 * has type ()Object (changed to (acc)Object before init() returns
			 * it, but ordinarily it will ignore the acc passed to it).
			 *
			 * The handle tree being built here will go on top of that, and will
			 * also ultimately have type (acc)Object. What it returns will be
			 * a new Invocable, carrying the same acc, and a handle tree built
			 * over the Iterator or ResultSetProvider that the user function
			 * returned. Part of that tree must depend on whether the return
			 * type is Iterator or ResultSet; the part being built here is the
			 * common part. It will have an extra first argument of type
			 * MethodHandle that can be bound to the ResultSetProvider- or
			 * Iterator-specific handle. If the user function returns null, so
			 * will this.
			 */

			MethodHandle invocableMH =
				myL.findStatic(EntryPoints.class, "invocable", methodType(
					Invocable.class,
					MethodHandle.class, AccessControlContext.class));

			mh = l.findVirtual(MethodHandle.class, "bindTo",
					methodType(MethodHandle.class, Object.class));

			mh = collectArguments(invocableMH, 0, mh);
			// (hdl, obj, acc) -> Invocable(hdl-bound-to-obj, acc)

			mh = guardWithTest(dropArguments(s_nonNull, 0, MethodHandle.class),
				mh, empty(methodType(Invocable.class,
					MethodHandle.class, Object.class, AccessControlContext.class
				))
			);

			mh = filterArguments(mh, 1, exactInvoker(methodType(Object.class)));
			/*
			 * We are left with type (MethodHandle,MethodHandle,acc) ->
			 * Invocable. A first bindTo, passing the ResultSetProvider- or
			 * Iterator-specific tree fragment, will leave us with
			 * (MethodHandle,acc)Invocable, and that can be bound to any
			 * set-returning user function handle, leaving (acc)Invocable, which
			 * is just what we want. Keep this as vpcCommon (only erasing its
			 * return type to Object as EntryPoints.invoke will expect).
			 */
			MethodHandle vpcCommon =
				mh.asType(mh.type().changeReturnType(Object.class));

			/*
			 * VALUE-PER-CALL Iterator DRIVER
			 *
			 * Build the ValuePerCall adapter handle for a function that
			 * returns Iterator. ValuePerCall adapters will be invoked through
			 * the general mechanism, and fetch their arguments from the static
			 * area. They'll be passed one reference argument (a "row collector"
			 * for the ResultSetProvider case) and two primitives: a long
			 * call counter (zero on the first call) and a boolean (true when
			 * the caller wants to stop iteration, perhaps early). An Iterator
			 * has no use for the row collector or the call counter, so they
			 * simply won't be fetched; the end-iteration boolean will be
			 * fetched and will cause false+null to be returned, but will not
			 * necessarily release any resources promptly, as Iterator has no
			 * close() method.
			 *
			 * These adapters change up the return-value protocol a bit: they
			 * will return a reference (the value for the row) *and also* a
			 * boolean via the first primitive slot (false if the end of rows
			 * has been reached, in which case the reference returned is simply
			 * null and is not part of the result set). If the boolean is true
			 * and null is returned, the null is part of the result.
			 *
			 * mh1 and mh2 both have type (Iterator)Object and side effect of
			 * storing to primitive slot 0. Let mh1 be the hasNext case,
			 * returning a value and storing true, and mh2 the no-more case,
			 * storing false and returning null. (They don't have a primitive-
			 * zeroer for either argument, as the return will clobber the first
			 * slot anyway, and they can only be reached if the 'close' argument
			 * is already zero. This is the Iterator case, so the row-collector
			 * reference argument is assumed already null.)
			 *
			 * Start with a few constants for parameter getters (else it is
			 * easy to forget the (sizeof jvalue) for the primitive getters!).
			 */
			final int REF0 = 0;
			final int REF1 = 1;
			final int PRIM0 = 0;
			final int PRIM1 = 1 * s_sizeof_jvalue;

			MethodHandle mh1 = identity(Object.class);
			mh1 = dropArguments(mh1, 1, Object.class); // the null from boolRet
			mh1 = collectArguments(mh1, 1,
				insertArguments(s_booleanReturn, 0, true));
			mt = methodType(Object.class);
			mh = l.findVirtual(Iterator.class, "next", mt);
			mh1 = filterArguments(mh1, 0, mh);

			MethodHandle mh2 = insertArguments(s_booleanReturn, 0, false);
			mh2 = dropArguments(mh2, 0, Iterator.class);

			mt = methodType(boolean.class);
			mh = l.findVirtual(Iterator.class, "hasNext", mt);
			mh = guardWithTest(mh, mh1, mh2);
			mh = foldArguments(mh, 0, s_countsZeroer);

			/*
			 * The next (in construction order; first in execution) test is of
			 * the 'close' argument. Tack a primitiveZeroer onto mh2 for this
			 * one, as it'll execute in the argument-isn't-zero case.
			 */
			mh2 = foldArguments(mh2, 0,
				insertArguments(s_primitiveZeroer, 0, PRIM1));
			mh2 = foldArguments(mh2, 0, s_countsZeroer);

			mh = guardWithTest(
				insertArguments(s_booleanGetter, 0, PRIM1), mh2, mh);

			/*
			 * mh now has type (Iterator)Object. Erase the Iterator to Object
			 * (so this and the ResultSetProvider one can have a common type),
			 * give it an acc argument that it will ignore, bind it into
			 * vpcCommon, and we'll have the Iterator VPC adapter.
			 */
			mh = mh.asType(mh.type().erase());
			mh = dropArguments(mh, 1, AccessControlContext.class);
			s_iteratorVPC = vpcCommon.bindTo(mh);

			/*
			 * VALUE-PER-CALL ResultSetProvider DRIVER
			 *
			 * The same drill as above, only to drive a ResultSetProvider.
			 * For now, this will always return a null reference, even when
			 * a row is retrieved; the thing it would return is just the
			 * row collector, which the C caller already has, and must extract
			 * the tuple from. If that could be done in Java, it would be
			 * a different story.
			 */
			mt = methodType(boolean.class, ResultSet.class, long.class);
			mh1 = collectArguments(s_booleanReturn, 0,
				l.findVirtual(ResultSetProvider.class, "assignRowValues", mt));
			mh1 = dropArguments(mh1, 0, boolean.class);

			/*
			 * The next (in construction order; first in execution) test is of
			 * the 'close' argument. If it is true, use mh2 to zero that prim
			 * slot, call close, and return false.
			 */
			mh2 = insertArguments(s_booleanReturn, 0, false);
			mh2 = collectArguments(mh2, 0,
				l.findVirtual(ResultSetProvider.class, "close",
					methodType(void.class)));
			mh2 = foldArguments(mh2, 0,
				insertArguments(s_primitiveZeroer, 0, PRIM1));
			mh2 = dropArguments(mh2, 0, boolean.class);
			mh2 = dropArguments(mh2, 2, ResultSet.class, long.class);

			mh = guardWithTest(identity(boolean.class), mh2, mh1);
			mh = foldArguments(mh, 0, s_countsZeroer);
			mh = foldArguments(mh, 0,
				insertArguments(s_booleanGetter, 0, PRIM1));
			// ^^^ Test the 'close' flag, prim slot 1 (insert as arg 0) ^^^

			mh = foldArguments(mh, 2, insertArguments(s_longGetter, 0, PRIM0));
			// ^^^ Get the row count, prim slot 0; return will clobber ^^^

			/*
			 * mh now has type (ResultSetProvider,ResultSet)Object. Erase both
			 * argument types to Object now (so the ResultSet will match the
			 * refGetter here, and the result will be (Object)Object as expected
			 * below.
			 */
			mh = mh.asType(mh.type().erase());
			mh = foldArguments(mh, 1,
				insertArguments(s_referenceNuller, 0, REF0));
			mh = foldArguments(mh, 1, insertArguments(s_refGetter, 0, REF0));
			// ^^^ Get and then null the row collector, ref slot 0 ^^^

			/*
			 * mh now has type (Object)Object. Give it an acc argument that it
			 * will ignore, bind it into vpcCommon, and we'll have the
			 * ResultSetProvider VPC adapter.
			 */
			mh = dropArguments(mh, 1, AccessControlContext.class);
			s_resultSetProviderVPC = vpcCommon.bindTo(mh);

			/*
			 * WRAPPER for ResultSetHandle to present it as ResultSetProvider
			 */
			mt = methodType(void.class, ResultSetHandle.class);
			mh = myL.findConstructor(ResultSetPicker.class, mt);
			s_wrapWithPicker =
				mh.asType(mh.type().changeReturnType(ResultSetProvider.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}

		/*
		 * An empty ProtectionDomain array is all it takes to make s_noLid.
		 * (As far as doPrivileged is concerned, a null AccessControlContext has
		 * the same effect, but one can't attach a DomainCombiner to that.)
		 *
		 * A lid is a bit more work, but there's a method for that.
		 */
		s_noLid = new AccessControlContext(new ProtectionDomain[] {});
		s_lid = lidWithPrincipals(new Principal[0]);
	}

	/**
	 * Construct a 'lid' {@code AccessControlContext}, optionally with
	 * associated {@code Principal}s.
	 *<p>
	 * A 'lid' is a "nobody special" {@code AccessControlContext}: it isn't
	 * allowed any permission that isn't granted by the Policy to everybody,
	 * unless it also has a nonempty array of principals. With an empty array,
	 * there need be only one such lid, so it can be kept in a static.
	 *<p>
	 * This method also allows creating a lid with associated principals,
	 * because a {@code SubjectDomainCombiner} does not combine its subject into
	 * the domains of its <em>inherited</em> {@code AccessControlContext}, and
	 * that strains the principle of least astonishment if the code is being
	 * invoked through an SQL declaration that one expects would have a
	 * {@code PLPrincipal} associated.
	 *<p>
	 * A null CodeSource is too strict; if your code source is null, you are
	 * somebody special in a bad way: no dynamic permissions for you! At
	 * least according to the default policy provider.
	 *<p>
	 * So, to achieve mere "nobody special"-ness requires a real CodeSource
	 * with null URL and null code signers.
	 *<p>
	 * The ProtectionDomain constructor allows the permissions parameter
	 * to be null, and says so in the javadocs. It seems to allow
	 * the principals parameter to be null too, but doesn't say that, so an
	 * array will always be expected here.
	 */
	private static AccessControlContext lidWithPrincipals(Principal[] ps)
	{
		return new AccessControlContext(new ProtectionDomain[] {
			new ProtectionDomain(
				new CodeSource(null, (CodeSigner[])null),
				null, ClassLoader.getSystemClassLoader(),
				Objects.requireNonNull(ps))
			});
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
	 * Return an {@code Invocable} for the {@code writeSQL} method of
	 * a given UDT class.
	 *<p>
	 * While this is not expected to be used while transforming parameters for
	 * another function call as the UDT-read handle would be, it can still be
	 * used during a function's execution and without being separately wrapped
	 * in {@code pushInvocation}/{@code popInvocation}. The pushing and popping
	 * of {@code ParameterFrame} rely on invocation scoping, so it is better for
	 * the UDT-write method also to avoid using the static parameter area.
	 *<p>
	 * The access control context of the {@code Invocable} returned here is used
	 * at the corresponding entry point; the payload is not.
	 */
	private static Invocable<?> udtWriteHandle(
		Class<? extends SQLData> clazz, String language, boolean trusted)
	throws SQLException
	{
		return invocable(
			null, accessControlContextFor(clazz, language, trusted));
	}

	/**
	 * Return an {@code Invocable} for the {@code toString} method of
	 * a given UDT class (or any class, really).
	 *<p>
	 * The access control context of the {@code Invocable} returned here is used
	 * at the corresponding entry point; the payload is not.
	 */
	private static Invocable<?> udtToStringHandle(
		Class<? extends SQLData> clazz, String language, boolean trusted)
	throws SQLException
	{
		return invocable(
			null, accessControlContextFor(clazz, language, trusted));
	}

	/**
	 * Return a special {@code Invocable} for the {@code readSQL} method of
	 * a given UDT class.
	 *<p>
	 * Because this can commonly be invoked while transforming parameters for
	 * another function call, it has a dedicated corresponding
	 * {@code EntryPoints} method and does not use the static parameter area.
	 * The {@code Invocable} created here is bound to the constructor of the
	 * type, takes no parameters, and simply returns the constructed instance;
	 * the {@code EntryPoints} method will then call {@code readSQL} on it and
	 * pass the stream and type-name arguments. The {@code AccessControlContext}
	 * assigned here will be in effect for both the constructor and the
	 * {@code readSQL} call.
	 */
	private static Invocable<?> udtReadHandle(
		Class<? extends SQLData> clazz, String language, boolean trusted)
	throws SQLException
	{
		Lookup l = lookupFor(clazz);
		MethodHandle ctor;

		try
		{
			ctor =
				l.findConstructor(clazz, methodType(void.class))
				.asType(methodType(SQLData.class)); // invocable() enforces this
		}
		catch ( ReflectiveOperationException e )
		{
			throw new SQLNonTransientException(
				"Java UDT implementing class " + clazz.getCanonicalName() +
				" must have a no-argument public constructor", "38000", e);
		}

		return invocable(
			ctor, accessControlContextFor(clazz, language, trusted));
	}

	/**
	 * Return a "parse" {@code Invocable} for a given UDT class.
	 *<p>
	 * The method can be invoked during the preparation of a parameter that has
	 * a NUL-terminated storage form, so it gets its own dedicated entry point
	 * and does not use the static parameter area.
	 */
	private static Invocable<?> udtParseHandle(
		Class<? extends SQLData> clazz, String language, boolean trusted)
	throws SQLException
	{
		Lookup l = lookupFor(clazz);
		MethodHandle mh;

		try
		{
			mh = l.findStatic(clazz, "parse",
				methodType(clazz, String.class, String.class));
		}
		catch ( ReflectiveOperationException e )
		{
			throw new SQLNonTransientException(
				"Java scalar-UDT implementing class " +
				clazz.getCanonicalName() +
				" must have a public static parse(String,String) method",
				"38000", e);
		}

		return invocable(
			mh.asType(mh.type().changeReturnType(SQLData.class)),
			accessControlContextFor(clazz, language, trusted));
	}

	/**
	 * Parse the function specification in {@code procTup}, initializing most
	 * fields of the C {@code Function} structure, and returning an
	 * {@code Invocable} for invoking the method, or null in the
	 * case of a UDT.
	 */
	public static Invocable<?> create(
		long wrappedPtr, ResultSet procTup, String langName, String schemaName,
		boolean trusted, boolean calledAsTrigger,
		boolean forValidator, boolean checkBody)
	throws SQLException
	{
		Matcher info = parse(procTup);

		/*
		 * Reject any TRANSFORM FOR TYPE clause at validation time, on
		 * the grounds that it will get ignored at invocation time anyway.
		 * The check could be made unconditional, and so catch at invocation
		 * time any function that might have been declared before this validator
		 * check was added. But simply ignoring the clause at invocation time
		 * (as promised...) keeps that path leaner.
		 */
		if ( forValidator  &&  null != procTup.getObject("protrftypes") )
			throw new SQLFeatureNotSupportedException(
				"a PL/Java function will not apply TRANSFORM FOR TYPE","0A000");

		if ( forValidator  &&  ! checkBody )
			return null;

		Identifier.Simple schema = Identifier.Simple.fromCatalog(schemaName);

		return init(wrappedPtr, info, procTup, schema, calledAsTrigger,
				forValidator, langName, trusted);
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
	 * @return an Invocable to invoke the implementing method, or
	 * null in the case of a UDT
	 */
	private static Invocable<?> init(
		long wrappedPtr, Matcher info, ResultSet procTup,
		Identifier.Simple schema, boolean calledAsTrigger, boolean forValidator,
		String language, boolean trusted)
	throws SQLException
	{
		Map<Oid,Class<? extends SQLData>> typeMap = null;
		String className = info.group("udtcls");
		boolean isUDT = (null != className);

		if ( ! isUDT )
		{
			className = info.group("cls");
			typeMap = Loader.getTypeMap(schema);
		}

		boolean readOnly = ((byte)'v' != procTup.getByte("provolatile"));

		ClassLoader schemaLoader = Loader.getSchemaLoader(schema);
		Class<?> clazz = loadClass(schemaLoader, className, null);

		AccessControlContext acc =
			accessControlContextFor(clazz, language, trusted);

		/*
		 * false, to leave initialization until the function's first invocation,
		 * when naturally the right ContextClassLoader and AccessControlContext
		 * will be in place. Overkill to do more just for a low-impact OpenJ9
		 * quirk.
		 */
		if ( false && forValidator
			&& clazz != loadClass(schemaLoader, className, acc) )
			throw new SQLException(
				"Initialization of class \"" + className + "\" produced a " +
				"different class object");

		if ( isUDT )
		{
			setupUDT(wrappedPtr, info, procTup, schemaLoader,
				clazz.asSubclass(SQLData.class), readOnly);
			return null;
		}

		String[] resolvedTypes;
		boolean isMultiCall = false;
		boolean retTypeIsOutParameter = false;
		boolean commute = (null != info.group("com"));
		boolean negate  = (null != info.group("neg"));

		if ( forValidator )
			calledAsTrigger = isTrigger(procTup);

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
				schemaLoader, clazz, readOnly, typeMap, multi, rtiop, commute);
			isMultiCall = multi [ 0 ];
			retTypeIsOutParameter = rtiop [ 0 ];
		}

		String methodName = info.group("meth");

		MethodHandle handle =
			getMethodHandle(schemaLoader, clazz, methodName,
				null, // or acc to initialize parameter classes; overkill.
				commute, resolvedTypes, retTypeIsOutParameter, isMultiCall)
			.asFixedArity();
		MethodType mt = handle.type();

		if ( commute )
		{
			Class<?>[] types = mt.parameterArray();
			mt = mt
				.changeParameterType(0, types[1])
				.changeParameterType(1, types[0]);
			handle = retTypeIsOutParameter
				? permuteArguments(handle, mt, 1, 0, 2)
				: permuteArguments(handle, mt, 1, 0);
		}

		if ( negate )
		{
			MethodHandle inverter = null;
			Class<?> rt = mt.returnType();
			if ( boolean.class == rt )
				inverter = s_not;
			else if ( Boolean.class == rt )
				inverter = s_boxedNot;

			if ( null == inverter  ||  retTypeIsOutParameter )
				throw new SQLSyntaxErrorException(
					"wrong return type for transformation [negate]", "42P13");

			handle = filterReturnValue(handle, inverter);
		}

		handle = adaptHandle(handle);

		if ( isMultiCall )
			handle = (
				retTypeIsOutParameter ? s_resultSetProviderVPC : s_iteratorVPC
			).bindTo(handle);
		else
			handle = dropArguments(handle, 0, AccessControlContext.class);

		return invocable(handle, acc);
	}

	/**
	 * Determine from a function's {@code pg_proc} entry whether it is a
	 * trigger function.
	 *<p>
	 * This is needed to implement a validator, as the function isn't being
	 * called, so "calledAsTrigger" can't be determined from the call context.
	 */
	private static boolean isTrigger(ResultSet procTup)
	throws SQLException
	{
		return 0 == procTup.getInt("pronargs")
			&& TRIGGEROID ==
				procTup.getInt("prorettype"); // type Oid, but implements Number
	}

	/**
	 * Select the {@code AccessControlContext} to be in effect when invoking
	 * a function.
	 *<p>
	 * At present, the only choices are null (no additional restrictions) when
	 * the target class is in a PL/Java-loaded jar file, or the 'lid' when
	 * invoking anything else (such as code of the JRE itself, which would
	 * otherwise have all permissions). The 'lid' is constructed to be 'nobody
	 * special', so will have only those permissions the policy grants without
	 * conditions. No exception is made here for the few functions supplied by
	 * PL/Java's own {@code Commands} class; they get a lid. It is reasonable to
	 * ask them to use {@code doPrivileged} when appropriate.
	 *<p>
	 * When {@code WITHOUT_ENFORCEMENT} is true, any nonnull <var>language</var>
	 * must be named in {@code pljava.allow_unenforced}. PL/Java's own functions
	 * in the {@code Commands} class are exempt from that check.
	 */
	private static AccessControlContext accessControlContextFor(
		Class<?> clazz, String language, boolean trusted)
	throws SQLException
	{
		Identifier.Simple langIdent = null;
		if ( null != language )
			langIdent = Identifier.Simple.fromCatalog(language);

		if ( WITHOUT_ENFORCEMENT  &&  clazz != Commands.class )
		{
			if ( null == langIdent )
			{
				if ( ! allowingUnenforcedUDT() )
					throw new SQLNonTransientException(
						"PL/Java UDT data conversions for " + clazz +
						" cannot execute because pljava.allow_unenforced_udt" +
						" is off", "46000");
			}
			else if ( Optional.ofNullable(
					getListConfigOption("pljava.allow_unenforced")
				).orElseGet(List::of).stream().noneMatch(langIdent::equals) )
				throw new SQLNonTransientException(
					"PL \"" + language + "\" not listed in " +
					"pljava.allow_unenforced configuration setting", "46000");
		}

		Set<Principal> p =
			(null == langIdent)
			? Set.of()
			: Set.of(
				trusted
				? new PLPrincipal.Sandboxed(langIdent)
				: new PLPrincipal.Unsandboxed(langIdent)
			);

		AccessControlContext acc = clazz.getClassLoader() instanceof Loader
			? s_noLid // policy already applies appropriate permissions
			: p.isEmpty() // put a lid on permissions if calling JRE directly
			? s_lid
			: lidWithPrincipals(p.toArray(new Principal[1]));

		/*
		 * A cache to avoid the following machinations might be good.
		 */
		return doPrivileged(() ->
			new AccessControlContext(acc, new SubjectDomainCombiner(
				new Subject(true, p, Set.of(), Set.of()))));
	}

	/**
	 * The initialization specific to a UDT function.
	 */
	/*
	 * A MappedUDT will not have PL/Java I/O functions declared in SQL,
	 * and therefore will never reach this method. Ergo, this is handling a
	 * BaseUDT, which must have all four functions, not just the one
	 * happening to be looked up at this instant. Rather than looking up one
	 * handle here and leaving the C code to find the rest anyway, simply let
	 * the C code look up all four; Function.c already contains logic for doing
	 * that, which it has to have in case the UDT is first encountered by the
	 * Type machinery rather than in an explicit function call.
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
		case 's':
		case 'o':
			udtId = ((Oid[])procTup.getObject("proargtypes"))[0];
			break;
		default:
			throw new SQLException("internal error in PL/Java UDT parsing");
		}

		doInPG(() -> _storeToUDT(wrappedPtr, schemaLoader,
			clazz, readOnly, udtInitial, udtId.intValue()));
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
		boolean[] multi, boolean[] returnTypeIsOP, boolean commute)
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
				isMultiCall, returnTypeIsOutputParameter, commute);
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
		boolean isMultiCall, boolean returnTypeIsOutputParameter,
		boolean commute)
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

		if ( commute )
		{
			if ( explicitTypes.length != (lastIsOut ? 3 : 2) )
				throw new SQLSyntaxErrorException(
					"wrong number of parameters for transformation [commute]",
					"42P13");
			String t = explicitTypes[0];
			explicitTypes[0] = explicitTypes[1];
			explicitTypes[1] = t;
		}

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
	private static final Pattern COMMA = compile("(?<=[^,]),(?=[^,])");

	/**
	 * Return a class given a loader to use and a canonical type name, as used
	 * in explicit signatures in the AS string. Just a bit of gymnastics to
	 * turn that form of name into the right class, including for primitives,
	 * void, and arrays.
	 *
	 * @param valACC if non-null, force initialization of the loaded class, in
	 * an effort to bring forward as many possible errors as can be during
	 * validation. Initialization will run in this access control context.
	 */
	private static Class<?> loadClass(
		ClassLoader schemaLoader, String className, AccessControlContext valACC)
	throws SQLException
	{
		boolean withoutInit = null == valACC;
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
				c = withoutInit
					? Class.forName(className, false, schemaLoader)
					: loadAndInitWithACC(className, schemaLoader, valACC);
			}
			catch ( ClassNotFoundException | LinkageError e )
			{
				throw new SQLNonTransientException(
					"Resolving class " + className + ": " + e, "46103", e);
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
	private static final Pattern stripEarlyWSinAS = compile(
		"^(\\s*+)(\\p{Alnum}++)(\\s*+)(?=\\p{Alpha})"
	);

	/**
	 * Pattern used to strip the remaining whitespace in an "AS" string.
	 */
	private static final Pattern stripOtherWSinAS = compile(
		"\\s*+"
	);

	/**
	 * Uncompiled pattern to recognize a Java identifier.
	 */
	private static final String javaIdentifier = String.format(
		"\\p{%1$sStart}\\p{%1sPart}*+", "javaJavaIdentifier"
	);

	/**
	 * Uncompiled pattern to recognize a Java type name, possibly qualified,
	 * without array brackets.
	 */
	private static final String javaTypeName = String.format(
		"(?:%1$s\\.)*%1$s", javaIdentifier
	);

	/**
	 * Uncompiled pattern to recognize one or more {@code []} array markers (the
	 * match length divided by two is the number of array dimensions).
	 */
	private static final String arrayDims = "(?:\\[\\])++";

	/**
	 * The recognized forms of an "AS" string, distinguishable and broken out
	 * by named capturing groups.
	 *<p>
	 * Array brackets are of course not included in the {@code <cls>} group, so
	 * the caller will not have to check for the receiver class being an array.
	 * A check that it isn't a primitive may be in order, though.
	 */
	private static final Pattern specForms = compile(String.format(
		/* the UDT notation, which is case insensitive */
		"(?i:udt\\[(?<udtcls>%1$s)\\](?<udtfun>input|output|receive|send))" +

		/* or the non-UDT form (which can't begin, insensitively, with UDT) */
		"|(?!(?i:udt\\[))" +
		/* allow a prefix like [commute] or [negate] or [commute,negate] */
		"(?:\\[(?:" +
			"(?:(?:(?<com>commute)|(?<neg>negate))(?:(?=\\])|,(?!\\])))" +
		")++\\])?+" +
		/* and the long-standing method spec syntax */
		"(?:(?<ret>%2$s)=)?+(?<cls>%1$s)\\.(?<meth>%3$s)" +
		"(?:\\((?<sig>(?:(?:%2$s,)*+%2$s)?+)\\))?+",
		javaTypeName,
		javaTypeName + "(?:" + arrayDims + ")?+",
		javaIdentifier
	));

	/**
	 * The recognized form of a Java type name in an "AS" string.
	 * The first capturing group is the canonical name of a type; the second
	 * group, if present, matches one or more {@code []} array markers following
	 * the name (its length divided by two is the number of array dimensions).
	 */
	private static final Pattern typeNameInAS = compile(
		"(" + javaTypeName + ")(" + arrayDims + ")?+"
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
			List<Type> pending = new LinkedList<>();
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
	 * parameters in a {@code GenericDeclaration<Class>}. Implements
	 * {@code Type} so it can be added to the {@code pending} queue in
	 * {@code specialization}.
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

		Type resolve(TypeVariable<?> v)
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
		boolean readOnly, int funcInitial, int udtOid);

	private static native void _reconcileTypes(
		long wrappedPtr, String[] resolvedTypes, String[] explicitTypes, int i);
}
