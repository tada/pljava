/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.deepToString;
import java.util.Iterator;
import java.util.List;

import java.time.OffsetDateTime;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsBoolean;

import org.postgresql.pljava.annotation.Function;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.SlotTester;
import org.postgresql.pljava.model.TupleTableSlot;

/**
 * A temporary test jig during TupleTableSlot development; intended
 * to be used from a debugger.
 */
public class TupleTableSlotTest
{
	/**
	 * Test retrieval of a PostgreSQL array as a multidimensional Java array.
	 */
	@Function(schema="javatest")
	public static Iterator<String> javaMultiArrayTest()
	throws SQLException, ReflectiveOperationException
	{
		Connection c = getConnection("jdbc:default:connection");
		SlotTester t = c.unwrap(SlotTester.class);

		/*
		 * First obtain an Adapter for the component type. For now, the untyped
		 * adapterPlease method is needed to grovel it out of PL/Java's innards
		 * and then cast it. An adapter manager with proper generic typing will
		 * handle that part some day.
		 */
		AsLong<?> int8 = (AsLong<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "INT8_INSTANCE");
		AsInt<?> int4 = (AsInt<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "INT4_INSTANCE");
		AsShort<?> int2 = (AsShort<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "INT2_INSTANCE");
		AsByte<?> int1 = (AsByte<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "INT1_INSTANCE");
		AsDouble<?> float8 = (AsDouble<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "FLOAT8_INSTANCE");
		AsFloat<?> float4 = (AsFloat<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "FLOAT4_INSTANCE");
		AsBoolean<?> bool = (AsBoolean<?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.Primitives", "BOOLEAN_INSTANCE");

		As<OffsetDateTime,?> odt = (As<OffsetDateTime,?>)t.adapterPlease(
			"org.postgresql.pljava.pg.adt.DateTimeAdapter$JSR310",
			"TIMESTAMPTZ_INSTANCE");

		/*
		 * By default, the Adapters for primitive types can't fetch a null
		 * value. There is no value in the primitive's value space that could
		 * unambiguously represent null, and a DBMS should not go and change
		 * your data if you haven't said to. But in a case where that is what
		 * you want, it is simple to write an adapter with the wanted behavior
		 * and compose it over the original one.
		 */
		float8 = new NullReplacingDouble(float8, Double.NaN);

		/*
		 * Once properly-typed adapters for component types are in hand,
		 * getting properly-typed array adapters is straightforward.
		 * (Java 10+ var can reduce verbosity here.)
		 */
		As<          long[]           ,?> i8x1 = int8            .a1().build();
		As<           int[][]         ,?> i4x2 = int4       .a2()     .build();
		As<         short[][][]       ,?> i2x3 = int2       .a2().a1().build();
		As<          byte[][][][]     ,?> i1x4 = int1  .a4()          .build();
		As<        double[][][][][]   ,?> f8x5 = float8.a4()     .a1().build();
		As<         float[][][][][][] ,?> f4x6 = float4.a4().a2()     .build();
		As<       boolean[][][][][]   ,?>  bx5 = bool  .a4()     .a1().build();
		As<OffsetDateTime[][][][]     ,?> dtx4 = odt   .a4()          .build();

		String query =
			"SELECT" +
			" '{1,2}'::int8[], " +
			" '{{1},{2}}'::int4[], " +
			" '{{{1,2,3}}}'::int2[], " +
			" '{{{{1},{2},{3}}}}'::\"char\"[], " +
			" '{{{{{1,null,3}}}}}'::float8[], " +
			" '{{{{{{1},{2},{3}}}}}}'::float4[], " +
			" '{{{{{t},{f},{t}}}}}'::boolean[], " +
			" '{{{{''now''}}}}'::timestamptz[]";

		List<TupleTableSlot> tups = t.test(query);

		List<String> result = new ArrayList<>();

		/*
		 * Then just pass the right adapter to tts.get.
		 */
		for ( TupleTableSlot tts : tups )
		{
			long           []           v0 = tts.get(0, i8x1);
			int            [][]         v1 = tts.get(1, i4x2);
			short          [][][]       v2 = tts.get(2, i2x3);
			byte           [][][][]     v3 = tts.get(3, i1x4);
			double         [][][][][]   v4 = tts.get(4, f8x5);
			float          [][][][][][] v5 = tts.get(5, f4x6);
			boolean        [][][][][]   v6 = tts.get(6,  bx5);
			OffsetDateTime [][][][]     v7 = tts.get(7, dtx4);

			result.addAll(List.of(
				Arrays.toString(v0), deepToString(v1), deepToString(v2),
				   deepToString(v3), deepToString(v4), deepToString(v5),
				   deepToString(v6), deepToString(v7)));
		}

		return result.iterator();
	}

	/**
	 * An adapter to compose over another one, adding some wanted behavior.
	 *
	 * There should eventually be a built-in set of composing adapters like
	 * this available for ready use, and automatically composed for you by an
	 * adapter manager when you say "I want an adapter for this PG type to this
	 * Java type and behaving this way."
	 *
	 * Until then, let this illustrate the simplicity of writing one.
	 */
	public static class NullReplacingDouble extends AsDouble<Double>
	{
		private final double replacement;

		@Override
		public boolean canFetchNull() { return true; }

		@Override
		public double fetchNull(Attribute a)
		{
			return replacement;
		}

		// It would be nice to let this method be omitted and this behavior
		// assumed, in a composing adapter with the same type for return and
		// parameter. Maybe someday.
		public double adapt(Attribute a, double value)
		{
			return value;
		}

		private static final Adapter.Configuration config =
			Adapter.configure(NullReplacingDouble.class, null);

		NullReplacingDouble(AsDouble<?> over, double valueForNull)
		{
			super(config, over);
			replacement = valueForNull;
		}
	}

	/**
	 * A temporary test jig during TupleTableSlot development; intended
	 * to be used from a debugger.
	 */
	@Function(schema="javatest")
	public static void tupleTableSlotTest(
		String query, String adpClass, String adpInstance)
	throws SQLException, ReflectiveOperationException
	{
		new TupleTableSlotTest().testWith(query, adpClass, adpInstance);
	}

	As<?,?>      adpL;
	AsLong<?>    adpJ;
	AsDouble<?>  adpD;
	AsInt<?>     adpI;
	AsFloat<?>   adpF;
	AsShort<?>   adpS;
	AsChar<?>    adpC;
	AsByte<?>    adpB;
	AsBoolean<?> adpZ;

	void testWith(String query, String adpClass, String adpInstance)
	throws SQLException, ReflectiveOperationException
	{
		Connection c = getConnection("jdbc:default:connection");
		SlotTester t = c.unwrap(SlotTester.class);

		List<TupleTableSlot> tups = t.test(query);

		int ntups = tups.size();

		boolean firstTime = true;

		int form = 8; // set with debugger, 8 selects reference-typed adpL

		boolean go; // true until set false by debugger each time through loop

		/*
		 * Results from adapters of assorted types.
		 */
		long    jj = 0;
		double  dd = 0;
		int     ii = 0;
		float   ff = 0;
		short   ss = 0;
		char    cc = 0;
		byte    bb = 0;
		boolean zz = false;
		Object  ll = null;

		for ( TupleTableSlot tts : tups )
		{
			if ( firstTime )
			{
				firstTime = false;
				Adapter a = t.adapterPlease(adpClass, adpInstance);
				if ( a instanceof As )
					adpL = (As<?,?>)a;
				else if ( a instanceof AsLong )
					adpJ = (AsLong<?>)a;
				else if ( a instanceof AsDouble )
					adpD = (AsDouble<?>)a;
				else if ( a instanceof AsInt )
					adpI = (AsInt<?>)a;
				else if ( a instanceof AsFloat )
					adpF = (AsFloat<?>)a;
				else if ( a instanceof AsShort )
					adpS = (AsShort<?>)a;
				else if ( a instanceof AsChar )
					adpC = (AsChar<?>)a;
				else if ( a instanceof AsByte )
					adpB = (AsByte<?>)a;
				else if ( a instanceof AsBoolean )
					adpZ = (AsBoolean<?>)a;
			}

			for ( Attribute att : tts.descriptor().attributes() )
			{
				go = true;
				while ( go )
				{
					go = false;
					try
					{
						switch ( form )
						{
						case 0: jj = tts.get(att, adpJ); break;
						case 1: dd = tts.get(att, adpD); break;
						case 2: ii = tts.get(att, adpI); break;
						case 3: ff = tts.get(att, adpF); break;
						case 4: ss = tts.get(att, adpS); break;
						case 5: cc = tts.get(att, adpC); break;
						case 6: bb = tts.get(att, adpB); break;
						case 7: zz = tts.get(att, adpZ); break;
						case 8: ll = tts.get(att, adpL); break;
						}
					}
					catch ( SQLException e )
					{
						System.out.println(e);
					}
				}
			}
		}
	}
}