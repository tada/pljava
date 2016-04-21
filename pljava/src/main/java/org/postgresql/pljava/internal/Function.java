/*
 * Copyright (c) 2016 Tada AB and other contributors, as listed below.
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

import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.SQLSyntaxErrorException;

import static java.util.Arrays.copyOf;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.jdbc.TypeOid.INVALID;
import org.postgresql.pljava.sqlj.Loader;

public class Function
{
	public static String create(
		long wrappedPtr, ResultSet procTup, String langName, String schemaName,
		boolean calledAsTrigger)
	throws SQLException
	{
		Matcher info = parse(procTup);

		return init(wrappedPtr, info, procTup, schemaName, calledAsTrigger);
	}

	private static Matcher parse(ResultSet procTup) throws SQLException
	{
		String spec = getAS(procTup);

		Matcher m = specForms.matcher(spec);
		if ( ! m.matches() )
			throw new SQLSyntaxErrorException(
				"cannot parse AS string", "42601");

		return m;
	}

	private static String init(
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

		Class<?> clazz = loadClass(schemaName, className);

		if ( isUDT )
		{
			setupUDT(wrappedPtr, info, procTup, clazz, readOnly);
			return null;
		}

		if ( calledAsTrigger )
		{
			typeMap = null;
			setupTriggerParams(wrappedPtr, info, clazz, readOnly);
		}
		else
		{
			setupFunctionParams(wrappedPtr, info, procTup,
				clazz, readOnly, typeMap);
		}

		return info.group("meth");
		/* to do: build signature ... look up method ... store that. */
	}

	private static void setupUDT(
		long wrappedPtr, Matcher info, ResultSet procTup,
		Class<?> clazz, boolean readOnly)
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

		doInPG(() -> _storeToUDT(wrappedPtr, clazz.asSubclass(SQLData.class),
			readOnly, udtInitial, udtId.intValue()));
	}

	private static void setupTriggerParams(
		long wrappedPtr, Matcher info, Class<?> clazz, boolean readOnly)
		throws SQLException
	{
		if ( null != info.group("sig") )
			throw new SQLSyntaxErrorException(
				"Triggers may not have a Java parameter signature", "42601");

		Oid retType = INVALID;
		String retJType = "void";

		Oid[] paramTypes = { INVALID };
		String[] paramJTypes = { "org.postgresql.pljava.TriggerData" };

		storeToNonUDT(wrappedPtr, clazz, readOnly, false /* isMultiCall */,
			null /* typeMap */, retType, retJType, paramTypes, paramJTypes,
			null /* [returnTypeIsOutputParameter] */);
	}

	private static void setupFunctionParams(
		long wrappedPtr, Matcher info, ResultSet procTup, Class<?> clazz,
		boolean readOnly, Map<Oid,Class<? extends SQLData>> typeMap)
		throws SQLException
	{
		int numParams = procTup.getInt("pronargs");
		boolean isMultiCall = procTup.getBoolean("proretset");
		Oid[] paramTypes = null;

		Oid returnType = (Oid)procTup.getObject("prorettype");

		if ( 0 < numParams )
			paramTypes = (Oid[])procTup.getObject("proargtypes");

		boolean[] returnTypeIsOP = new boolean [ 1 ];

		String[] resolvedTypes = storeToNonUDT(wrappedPtr, clazz,
			readOnly, isMultiCall, typeMap, returnType, null, paramTypes, null,
			returnTypeIsOP);

		boolean returnTypeIsOutputParameter = returnTypeIsOP[0];

		String explicitSignature = info.group("sig");
		if ( null != explicitSignature )
			parseParameters( wrappedPtr, resolvedTypes, explicitSignature,
				isMultiCall, returnTypeIsOutputParameter);

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
	}

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

	private static Class<?> loadClass(String schemaName, String className)
	throws SQLException
	{
		try
		{
			return Loader.getSchemaLoader(schemaName).loadClass(className);
		}
		catch ( ClassNotFoundException e )
		{
			throw new SQLNonTransientException(
				"No such class: " + className, "46103", e);
		}
	}


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


	private static final Pattern stripEarlyWSinAS = Pattern.compile(
		"^(\\s*+)(\\p{Alnum}++)(\\s*+)(?=\\p{Alpha})"
	);
	private static final Pattern stripOtherWSinAS = Pattern.compile(
		"\\s*+"
	);

	private static final Pattern specForms = Pattern.compile(
		"(?i:udt\\[(?<udtcls>[^]]++)\\](?<udtfun>input|output|receive|send))" +
		"|(?!(?i:udt\\[))" +
		"(?:(?<ret>[^=]++)=)?+(?<cls>(?:[^.(]++\\.?)+)\\.(?<meth>[^.(]++)" +
		"(?:\\((?<sig>[^)]*+)\\))?+"
	);

	private static String[] storeToNonUDT(
		long wrappedPtr, Class<?> clazz, boolean readOnly, boolean isMultiCall,
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
				wrappedPtr, clazz, readOnly, isMultiCall, typeMap, numParams,
				returnType.intValue(), returnJType, paramOids, pJTypes,
				outJTypes));

		if ( null != returnTypeIsOutParameter )
			returnTypeIsOutParameter[0] = rtiop;

		return outJTypes;
	}

	private static native boolean _storeToNonUDT(
		long wrappedPtr, Class<?> clazz, boolean readOnly, boolean isMultiCall,
		Map<Oid,Class<? extends SQLData>> typeMap,
		int numParams, int returnType, String returnJType,
		int[] paramTypes, String[] paramJTypes, String[] outJTypes);

	private static native void _storeToUDT(
		long wrappedPtr, Class<? extends SQLData> clazz,
		boolean readOnly, int funcInitial, int udtOid);

	private static native void _reconcileTypes(
		long wrappedPtr, String[] resolvedTypes, String[] explicitTypes, int i);
}
