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

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.postgresql.pljava.internal.Backend.doInPG;
import static org.postgresql.pljava.jdbc.TypeOid.INVALID;
import org.postgresql.pljava.sqlj.Loader;

public class Function
{
	public static Object create(
		long wrappedPtr, ResultSet procTup, String langName, String schemaName,
		boolean calledAsTrigger)
	throws SQLException
	{
		Matcher info = parse(procTup);

		init(wrappedPtr, info, procTup, schemaName, calledAsTrigger);
		return null;
	}

	private static Matcher parse(ResultSet procTup) throws SQLException
	{
		String spec = getAS(procTup);

		Matcher m = specForms.matcher(spec);
		if ( ! m.matches() )
		{
			System.err.println("huh? " + spec);
			return null;
		}

		say(m, "udtcls");
		say(m, "udtfun");
		say(m, "ret");
		say(m, "cls");
		say(m, "meth");
		say(m, "sig");
		System.err.println("----");
		return m;
	}

	private static void init(
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
			return;
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
			null /* typeMap */, retType, retJType, paramTypes, paramJTypes);
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

		storeToNonUDT(wrappedPtr, clazz, readOnly, isMultiCall,
			typeMap, returnType, null, paramTypes, null);
	}

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


	private static void say(Matcher m, String grpname)
	{
		String s = m.group(grpname);
		if ( null != s )
			System.err.println(grpname+": "+s);
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

	private static void storeToNonUDT(
		long wrappedPtr, Class<?> clazz, boolean readOnly, boolean isMultiCall,
		Map<Oid,Class<? extends SQLData>> typeMap,
		Oid returnType, String returnJType, Oid[] paramTypes, String[] pJTypes)
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

		doInPG(() -> _storeToNonUDT(
				wrappedPtr, clazz, readOnly, isMultiCall, typeMap, numParams,
				returnType.intValue(), returnJType, paramOids, pJTypes));
		}
	}

	private static native void _storeToNonUDT(
		long wrappedPtr, Class<?> clazz, boolean readOnly, boolean isMultiCall,
		Map<Oid,Class<? extends SQLData>> typeMap,
		int numParams, int returnType, String returnJType,
		int[] paramTypes, String[] paramJTypes);

	private static native void _storeToUDT(
		long wrappedPtr, Class<? extends SQLData> clazz,
		boolean readOnly, int funcInitial, int udtOid);
}
