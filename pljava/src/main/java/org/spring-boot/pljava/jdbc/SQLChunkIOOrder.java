/*
 * Copyright (c) 2016-2025 TADA AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.nio.ByteOrder;

import java.sql.SQLNonTransientException;

import java.util.Properties;

/**
 * Caches the scalar and mirror {@code MappedUDT} byte orders as determined by
 * system properties during PL/Java startup.
 *<p>
 * This class is initialized from native code ahead of the
 * {@link SQLInputFromChunk} and {@link SQLOutputToChunk} classes that depend
 * on it. This happens before {@code InstallHelper} has taken and frozen its
 * defensive copy of the Java system properties, and also before PL/Java user
 * code has potentially run and changed them.
 *<p>
 * This defensive implementation is needed only for the "PL/Java with no
 * security policy enforcement" case, as PL/Java's supplied policy file protects
 * these properties from modification when policy is being enforced.
 */
class SQLChunkIOOrder
{
	private SQLChunkIOOrder() { } // do not instantiate

	/**
	 * Byte order for conversion of "mirror" UDT values in the
	 * Java-to-PostgreSQL direction.
	 */
	static final ByteOrder MIRROR_J2P;

	/**
	 * Byte order for conversion of "mirror" UDT values in the
	 * PostgreSQL-to-Java direction.
	 */
	static final ByteOrder MIRROR_P2J;

	/**
	 * Byte order for conversion of "scalar" UDT values in the
	 * Java-to-PostgreSQL direction.
	 */
	static final ByteOrder SCALAR_J2P;

	/**
	 * Byte order for conversion of "scalar" UDT values in the
	 * PostgreSQL-to-Java direction.
	 */
	static final ByteOrder SCALAR_P2J;

	static
	{
		/*
		 * Set the org.postgresql.pljava.udt.byteorder.{scalar,mirror}.{p2j,j2p}
		 * properties. For shorthand, defaults can be given in shorter property
		 * keys org.postgresql.pljava.udt.byteorder.{scalar,mirror} or even just
		 * org.postgresql.pljava.udt.byteorder for an overall default. These
		 * shorter keys are then removed from the system properties.
		 */
		Properties ps = System.getProperties();

		String orderKey = "org.postgresql.pljava.udt.byteorder";
		String orderAll = ps.getProperty(orderKey);
		String orderMirror = ps.getProperty(orderKey + ".mirror");
		String orderScalar = ps.getProperty(orderKey + ".scalar");

		if ( null == orderMirror )
			orderMirror = null != orderAll ? orderAll : "native";
		if ( null == orderScalar )
			orderScalar = null != orderAll ? orderAll : "big_endian";

		System.clearProperty(orderKey);
		System.clearProperty(orderKey + ".mirror");
		System.clearProperty(orderKey + ".scalar");

		try
		{
			MIRROR_J2P = toByteOrder(ps, orderKey + ".mirror.j2p", orderMirror);
			MIRROR_P2J = toByteOrder(ps, orderKey + ".mirror.p2j", orderMirror);
			SCALAR_J2P = toByteOrder(ps, orderKey + ".scalar.j2p", orderScalar);
			SCALAR_P2J = toByteOrder(ps, orderKey + ".scalar.p2j", orderScalar);
		}
		catch ( SQLNonTransientException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	private static ByteOrder toByteOrder(Properties ps, String k, String dfl)
	throws SQLNonTransientException
	{
		switch ( (String)ps.computeIfAbsent(k, p -> dfl) )
		{
		case "big_endian": return ByteOrder.BIG_ENDIAN;
		case "little_endian": return ByteOrder.LITTLE_ENDIAN;
		case "native": return ByteOrder.nativeOrder();
		default:
			throw new SQLNonTransientException(
				"System property " + k +
				" must be big_endian, little_endian, or native", "F0000");
		}
	}
}
