/*
 * Copyright (c) 2004-2021 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import org.postgresql.pljava.internal.Oid;

/**
 * Provides constants for well-known backend OIDs for the types we commonly
 * use.
 */
public class TypeOid
{
	/*
	 * These constants (well, the Oid reference ones) have been here for ages,
	 * so some code auditing is needed to determine where they are used, before
	 * going a different direction with Oid.
	 */
	public static final int InvalidOid     = 0;
	public static final int INT2OID        = 21;
	public static final int INT4OID        = 23;
	public static final int INT8OID        = 20;
	public static final int TEXTOID        = 25;
	public static final int NUMERICOID     = 1700;
	public static final int FLOAT4OID      = 700;
	public static final int FLOAT8OID      = 701;
	public static final int BOOLOID        = 16;
	public static final int DATEOID        = 1082;
	public static final int TIMEOID        = 1083;
	public static final int TIMESTAMPOID   = 1114;
	public static final int TIMESTAMPTZOID = 1184;
	public static final int BYTEAOID       = 17;
	public static final int VARCHAROID     = 1043;
	public static final int OIDOID         = 26;
	public static final int BPCHAROID      = 1042;

	public static final Oid INVALID     = new Oid(InvalidOid);
	public static final Oid INT2        = new Oid(INT2OID);
	public static final Oid INT4        = new Oid(INT4OID);
	public static final Oid INT8        = new Oid(INT8OID);
	public static final Oid TEXT        = new Oid(TEXTOID);
	public static final Oid NUMERIC     = new Oid(NUMERICOID);
	public static final Oid FLOAT4      = new Oid(FLOAT4OID);
	public static final Oid FLOAT8      = new Oid(FLOAT8OID);
	public static final Oid BOOL        = new Oid(BOOLOID);
	public static final Oid DATE        = new Oid(DATEOID);
	public static final Oid TIME        = new Oid(TIMEOID);
	public static final Oid TIMESTAMP   = new Oid(TIMESTAMPOID);
	public static final Oid TIMESTAMPTZ = new Oid(TIMESTAMPTZOID);
	public static final Oid BYTEA       = new Oid(BYTEAOID);
	public static final Oid VARCHAR     = new Oid(VARCHAROID);
	public static final Oid OID         = new Oid(OIDOID);
	public static final Oid BPCHAR      = new Oid(BPCHAROID);

	/*
	 * Added in 2019. The numeric constant will be used, but no need is foreseen
	 * for an Oid-reference constant.
	 */
	public static final int PG_NODE_TREEOID = 194;
	/*
	 * Likewise in 2020.
	 */
	public static final int TRIGGEROID = 2279;

	/*
	 * Before Java 8 with the @Native annotation, a class needs at least one
	 * native method to trigger generation of a .h file.
	 */
	private static native void _dummy();
}
