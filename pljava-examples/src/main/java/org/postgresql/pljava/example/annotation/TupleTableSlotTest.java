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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Arrays.deepToString;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import java.time.LocalDateTime;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.AdapterException;//for now; not planned API
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsBoolean;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.TargetList;
import org.postgresql.pljava.TargetList.Cursor;
import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.annotation.Function;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;
import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.Portal;
import static org.postgresql.pljava.model.Portal.ALL;
import static org.postgresql.pljava.model.Portal.Direction.FORWARD;
import org.postgresql.pljava.model.SlotTester;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

/**
 * A temporary test jig during TupleTableSlot development; intended
 * to be used from a debugger.
 */
@SQLAction(requires = "modelToJDBC", install =
"WITH" +
" result AS (" +
"  SELECT" +
"    * " +
"   FROM" +
"    javatest.modelToJDBC(" +
"     'SELECT DISTINCT' ||" +
"     '  CAST ( relacl AS pg_catalog.text ), relacl' ||" +
"     ' FROM' ||" +
"     '  pg_catalog.pg_class' ||" +
"     ' WHERE' ||" +
"     '  relacl IS NOT NULL'," +
"     'org.postgresql.pljava.pg.adt.TextAdapter',  'INSTANCE'," +
"     'org.postgresql.pljava.pg.adt.GrantAdapter', 'LIST_INSTANCE'" +
"    ) AS r(raw text, cooked text)" +
" )," +
" conformed AS (" +
"  SELECT" +
"    raw, pg_catalog.translate(cooked, '[] ', '{}') AS cooked" +
"   FROM" +
"    result" +
" )" +
" SELECT" +
"   CASE WHEN pg_catalog.every(raw = cooked )" +
"   THEN javatest.logmessage('INFO', 'AclItem[] ok')" +
"   ELSE javatest.logmessage('WARNING', 'AclItem[] ng')" +
"   END" +
"  FROM" +
"   conformed"
)
public class TupleTableSlotTest
{
	/*
	 * Collect some Adapter instances that are going to be useful in the code
	 * below. Is it necessary they be static final? No, they can be obtained at
	 * any time, but collecting these here will keep the example methods tidier
	 * below.
	 *
	 * These are "leaf" adapters: they work from the PostgreSQL types directly.
	 */
	static final AsLong   <       ?> INT8;
	static final AsInt    <       ?> INT4;
	static final AsShort  <       ?> INT2;
	static final AsByte   <       ?> INT1;
	static final AsDouble <       ?> FLOAT8;
	static final AsFloat  <       ?> FLOAT4;
	static final AsBoolean<       ?> BOOL;

	static final As<String       ,?> TEXT;
	static final As<LocalDateTime,?> LDT;  // for the PostgreSQL TIMESTAMP type

	/*
	 * Now some adapters that can be derived from leaf adapters by composing
	 * non-leaf adapters over them.
	 *
	 * By default, the Adapters for primitive types can't fetch a null
	 * value. There is no value in the primitive's value space that could
	 * unambiguously represent null, and a DBMS should not go and reuse an
	 * otherwise-valid value to also mean null, if you haven't said to. But in
	 * a case where that is what you want, it is simple to write an adapter with
	 * the wanted behavior and compose it over the original one.
	 */
	static final AsDouble<?> F8_NaN; // primitive double using NaN for null

	/*
	 * Reference-typed adapters have no trouble with null values by default;
	 * they'll just produce Java null. But suppose it is more convenient to get
	 * an Optional<LocalDateTime> instead of a LocalDateTime that might be null.
	 * An Adapter for that can be obtained by composition.
	 */
	static final As<Optional<LocalDateTime>,?> LDT_O;

	/*
	 * A composing adapter expecting a reference type can also be composed
	 * over one that produces a primitive type. It will see the values
	 * automatically boxed.
	 *
	 * Corollary: should the desired behavior be not to produce Optional,
	 * but simply to enable null handling for a primitive type by producing
	 * its boxed form or null, just one absolutely trivial composing adapter
	 * could add that behavior over any primitive adapter.
	 */
	static final As<Optional<Long>         ,?> INT8_O;

	/*
	 * Once properly-typed adapters for component types are in hand,
	 * getting properly-typed array adapters is straightforward. (In Java 10+,
	 * a person might prefer to set these up at run time in local variables,
	 * where var could be used instead of these longwinded declarations.)
	 *
	 * For fun, I8x1 will be built over INT8_O, so it will really produce
	 * Optional<Long>[] instead of long[]. F8x5 will be built over F8_NaN, so it
	 * will produce double[][][][][], but null elements won't be rejected,
	 * and will appear as NaN. DTx2 will be built over LDT_O, so it will really
	 * produce Optional<LocalDateTime>[][].
	 */
	static final As<Optional<Long>[]           ,?> I8x1;
	static final As<           int[][]         ,?> I4x2;
	static final As<         short[][][]       ,?> I2x3;
	static final As<          byte[][][][]     ,?> I1x4;
	static final As<        double[][][][][]   ,?> F8x5;
	static final As<         float[][][][][][] ,?> F4x6;
	static final As<       boolean[][][][][]   ,?>  Bx5;
	static final As<Optional<LocalDateTime>[][],?> DTx2;

	static
	{
		/*
		 * This is the very untidy part, while the planned Adapter manager API
		 * is not yet implemented. The extremely temporary adapterPlease method
		 * can be used to grovel some adapters out of PL/Java's innards, as long
		 * as the name of a class and a static final field is known.
		 *
		 * The adapter manager will have generic methods to obtain adapters with
		 * specific compile-time types. The adapterPlease method, not so much.
		 * It needs to be used with ugly casts.
		 */
		try
		{
			Connection conn = getConnection("jdbc:default:connection");
			SlotTester t = conn.unwrap(SlotTester.class);

			String cls = "org.postgresql.pljava.pg.adt.Primitives";
			INT8   = (AsLong   <?>)t.adapterPlease(cls,    "INT8_INSTANCE");
			INT4   = (AsInt    <?>)t.adapterPlease(cls,    "INT4_INSTANCE");
			INT2   = (AsShort  <?>)t.adapterPlease(cls,    "INT2_INSTANCE");
			INT1   = (AsByte   <?>)t.adapterPlease(cls,    "INT1_INSTANCE");
			FLOAT8 = (AsDouble <?>)t.adapterPlease(cls,  "FLOAT8_INSTANCE");
			FLOAT4 = (AsFloat  <?>)t.adapterPlease(cls,  "FLOAT4_INSTANCE");
			BOOL   = (AsBoolean<?>)t.adapterPlease(cls, "BOOLEAN_INSTANCE");

			cls = "org.postgresql.pljava.pg.adt.TextAdapter";

			/*
			 * SuppressWarnings must appear on a declaration, making it hard to
			 * apply here, an initial assignment to a final field declared
			 * earlier. But making this the declaration of a new local variable,
			 * with the actual wanted assignment as a "side effect", works.
			 * (The "unnamed variable" _ previewed in Java 21 would be ideal.)
			 */
					@SuppressWarnings("unchecked") Object _1 =
			TEXT   = (As<String,?>)t.adapterPlease(cls, "INSTANCE");

			cls = "org.postgresql.pljava.pg.adt.DateTimeAdapter$JSR310";

					@SuppressWarnings("unchecked") Object _2 =
			LDT    =
				(As<LocalDateTime,?>)t.adapterPlease(cls, "TIMESTAMP_INSTANCE");
		}
		catch ( SQLException | ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}

		/*
		 * Other than those stopgap uses of adapterPlease, the rest is
		 * not so bad. Instantiate some composing adapters over the leaf
		 * adapters already obtained:
		 */

		F8_NaN = new NullReplacingDouble(FLOAT8, Double.NaN);
		 LDT_O = new AsOptional<>(LDT);
		INT8_O = new AsOptional<>(INT8);

		/*
		 * (Those composing adapters should be provided by PL/Java and known
		 * to the adapter manager so it can compose them for you. For now,
		 * they are just defined in this example file, showing that client
		 * code can easily supply its own.)
		 *
		 * Java array-of-array adapters of various dimensionalities are
		 * easily built from the adapters chosen for their component types.
		 */

		I8x1 = INT8_O             .a1() .build(); // array of Optional<Long>
		I4x2 =   INT4       .a2()       .build();
		I2x3 =   INT2       .a2() .a1() .build();
		I1x4 =   INT1 .a4()             .build();
		F8x5 = F8_NaN .a4()       .a1() .build(); // 5D F8 array, null <-> NaN
		F4x6 = FLOAT4 .a4() .a2()       .build();
		 Bx5 =   BOOL .a4()       .a1() .build();
		DTx2 =  LDT_O       .a2()       .build(); // 2D of optional LDT
	}

	/**
	 * Test {@link TargetList} and its functional API for retrieving values.
	 */
	@Function(schema="javatest")
	public static Iterator<String> targetListTest()
	throws SQLException, ReflectiveOperationException
	{
		try (
			Connection conn = getConnection("jdbc:default:connection");
			Statement s = conn.createStatement();
		)
		{
			SlotTester t = conn.unwrap(SlotTester.class);

			String query =
				"SELECT" +
				"  to_char(stamp, 'DAY') AS day," +
				"  stamp" +
				" FROM" +
				"  generate_series(" +
				"   timestamp 'epoch', timestamp 'epoch' + interval 'P6D'," +
				"   interval 'P1D'" +
				"  ) AS s(stamp)";

			try ( Portal p = t.unwrapAsPortal(s.executeQuery(query)) )
			{
				Projection proj = p.tupleDescriptor();

				/*
				 * A quick glance shows this project(...) to be unneeded, as the
				 * query's TupleDescriptor already has exactly these columns in
				 * this order, and could be used below directly. On the other
				 * hand, this line will keep things working if someone later
				 * changes the query, reordering these columns or adding
				 * to them, and it may give a more explanatory exception if
				 * a change to the query does away with an expected column.
				 */
				proj = proj.project("day", "stamp");

				List<TupleTableSlot> fetched = p.fetch(FORWARD, ALL);

				List<String> results = new ArrayList<>();

				proj.applyOver(fetched, c ->
				{
					/*
					 * This loop demonstrates a straightforward use of two
					 * Adapters and a lambda with two parameters to go through
					 * the retrieved rows.
					 *
					 * Note that applyOver does not, itself, iterate over the
					 * rows; it supplies a Cursor object that can be iterated to
					 * do that. This gives the lambda body of applyOver more
					 * control over how that will happen.
					 *
					 * The Cursor object is mutated during iteration so the
					 * same object represents each row in turn; the iteration
					 * variable is simply the Cursor object itself, so does not
					 * need to be used. Once the "unnamed variable" _ is more
					 * widely available (Java 21 has it, with --enable-preview),
					 * it will be the obvious choice for the iteration variable
					 * here.
					 *
					 * Within the loop, the cursor represents the single current
					 * row as far as its apply(...) methods are concerned.
					 *
					 * Other patterns, such as the streams API, can also be used
					 * (starting with a stream of the cursor object itself,
					 * again for each row), but can involve more fuss when
					 * checked exceptions are involved.
					 */
					for ( Cursor __ : c )
					{
						c.apply(TEXT, LDT,         // the adapters
							(     v0,  v1 ) ->     // the fetched values
							results.add(v0 + " | " + v1.getDayOfWeek())
						);
					}

					/*
					 * This equivalent loop uses two lambdas in curried style
					 * to do the same processing of the same two columns. That
					 * serves no practical need in this example; a perfectly
					 * good method signature for two reference columns was seen
					 * above. This loop illustrates the technique for combining
					 * the available methods when there isn't one that exactly
					 * fits the number and types of the target columns.
					 */
					for ( Cursor __ : c )
					{
						c.apply(TEXT,
								v0 ->
							c.apply(LDT,
									v1 ->
								results.add(v0 + " | " + v1.getDayOfWeek())
							)
						);
					}

					return null;
				});

				return results.iterator();
			}
		}
	}

	/**
	 * Test retrieval of a PostgreSQL array as a multidimensional Java array.
	 */
	@Function(schema="javatest")
	public static Iterator<String> javaMultiArrayTest()
	throws SQLException, ReflectiveOperationException
	{
		Connection conn = getConnection("jdbc:default:connection");
		SlotTester t = conn.unwrap(SlotTester.class);

		String query =
			"VALUES (" +
			" CAST ( '{1,2}'                 AS      int8 [] ), " +
			" CAST ( '{{1},{2}}'             AS      int4 [] ), " +
			" CAST ( '{{{1,2,3}}}'           AS      int2 [] ), " +
			" CAST ( '{{{{1},{2},{3}}}}'     AS  \"char\" [] ), " + // ASCII
			" CAST ( '{{{{{1,2,3}}}}}'       AS    float8 [] ), " +
			" CAST ( '{{{{{{1},{2},{3}}}}}}' AS    float4 [] ), " +
			" CAST ( '{{{{{t},{f},{t}}}}}'   AS   boolean [] ), " +
			" CAST ( '{{''epoch''}}'         AS timestamp [] )  " +
			"), (" +
			" '{NULL}', NULL, NULL, NULL, '{{{{{1,NULL,3}}}}}', NULL, NULL," +
			" '{{NULL}}'" +
			")";

		Portal p = t.unwrapAsPortal(conn.createStatement().executeQuery(query));
		Projection proj = p.tupleDescriptor();

		List<TupleTableSlot> tups = p.fetch(FORWARD, ALL);

		List<String> result = new ArrayList<>();

		/*
		 * Then just use the right adapter for each column.
		 */
		proj.applyOver(tups, c ->
		{
			for ( Cursor __ : c )
			{
				c.apply(I8x1, I4x2, I2x3, I1x4, F8x5, F4x6, Bx5, DTx2,
					(     v0,   v1,   v2,   v3,   v4,   v5,  v6,   v7 ) ->
					result.addAll(List.of(
						Arrays.toString(v0), deepToString(v1), deepToString(v2),
						   deepToString(v3), deepToString(v4), deepToString(v5),
						   deepToString(v6), deepToString(v7),
						   v7[0][0].orElse(LocalDateTime.MAX).getMonth() + ""
					))
				);
			}
			return null;
		});

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
	 * Another example of a useful composing adapter that should eventually be
	 * part of a built-in set.
	 */
	public static class AsOptional<T> extends As<Optional<T>,T>
	{
		// canFetchNull isn't needed; its default in As<?,?> is true.

		@Override
		public Optional<T> fetchNull(Attribute a)
		{
			return Optional.empty();
		}

		public Optional<T> adapt(Attribute a, T value)
		{
			return Optional.of(value);
		}

		private static final Adapter.Configuration config =
			Adapter.configure(AsOptional.class, null);

		/*
		 * This adapter may be composed over any Adapter<T,?>, including those
		 * of primitive types as well as the reference-typed As<T,?>. When
		 * constructed over a primitive-returning adapter, values will be boxed
		 * when passed to adapt().
		 */
		AsOptional(Adapter<T,?> over)
		{
			super(config, over, null);
		}
	}

	/**
	 * A surprisingly useful composing adapter that should eventually be
	 * part of a built-in set.
	 *<p>
	 * Surprisingly useful, because although it "does" nothing, composing it
	 * over any primitive adapter produces one that returns the boxed form, and
	 * Java null for SQL null.
	 */
	public static class Identity<T> extends As<T,T>
	{
		// the inherited fetchNull returns null, which is just right

		public T adapt(Attribute a, T value)
		{
			return value;
		}

		private static final Adapter.Configuration config =
			Adapter.configure(Identity.class, null);

		/*
		 * Another choice could be to restrict 'over' to extend Primitive, as
		 * there isn't much point composing this adapter over one of reference
		 * type ... unless you want Java null for SQL null and the 'over'
		 * adapter produces something else.
		 */
		Identity(Adapter<T,?> over)
		{
			super(config, over, null);
		}
	}

	/**
	 * Test retrieving results from a query using the PG-model API and returning
	 * them to the caller using the legacy JDBC API.
	 * @param query a query producing some number of columns
	 * @param adapters an array of strings, twice the number of columns,
	 * supplying a class name and static field name for the ugly temporary
	 * {@code adapterPlease} method, one such pair for each result column
	 */
	@Function(
		schema = "javatest", type = "pg_catalog.record", variadic = true,
		onNullInput = RETURNS_NULL, provides = "modelToJDBC"
	)
	public static ResultSetProvider modelToJDBC(String query, String[] adapters)
	throws SQLException, ReflectiveOperationException
	{
		Connection conn = getConnection("jdbc:default:connection");
		SlotTester t = conn.unwrap(SlotTester.class);
		Portal p = t.unwrapAsPortal(conn.createStatement().executeQuery(query));
		TupleDescriptor td = p.tupleDescriptor();

		if ( adapters.length != 2 * td.size() )
			throw new SQLException(String.format(
				"query makes %d columns so 'adapters' should have %d " +
				"elements, not %d", td.size(), 2*td.size(), adapters.length));

		if ( Arrays.stream(adapters).anyMatch(Objects::isNull) )
			throw new SQLException("adapters array has null element");

		As<?,?>[] resolved = new As<?,?>[ td.size() ];

		for ( int i = 0 ; i < resolved.length ; ++ i )
		{
			Adapter<?,?> a =
				t.adapterPlease(adapters[i<<1], adapters[(i<<1) + 1]);
			if ( a instanceof As<?,?> )
				resolved[i] = (As<?,?>)a;
			else
				resolved[i] = new Identity(a);
		}

		return new ResultSetProvider.Large()
		{
			@Override
			public boolean assignRowValues(ResultSet out, long currentRow)
			throws SQLException
			{
				if ( 0 == currentRow )
				{
					int rcols = out.getMetaData().getColumnCount();
					if ( td.size() != rcols )
						throw new SQLException(String.format(
							"query makes %d columns but result descriptor " +
							"has %d", td.size(), rcols));
				}

				/*
				 * This example will fetch one tuple at a time here in the
				 * ResultSetProvider. This is a low-level interface to Postgres.
				 * In the SFRM_ValuePerCall protocol that ResultSetProvider
				 * supports, a fresh call from Postgres is made to retrieve each
				 * row. The Portal lives in a memory context that persists
				 * across the multiple calls, but the fetch result tups only
				 * exist in a child of the SPI context set up for each call.
				 * So here we only fetch as many tups as we can use to make one
				 * result row.
				 *
				 * If the logic involved fetching a bunch of rows and processing
				 * those into Java representations with no further dependence on
				 * the native tuples, then of course that could be done all in
				 * advance.
				 */
				List<TupleTableSlot> tups = p.fetch(FORWARD, 1);
				if ( 0 == tups.size() )
					return false;

				TupleTableSlot tts = tups.get(0);

				for ( int i = 0 ; i < resolved.length ; ++ i )
				{
					Object o = tts.get(i, resolved[i]);
					try
					{
						out.updateObject(1 + i, o);
					}
					catch ( SQLException e )
					{
						try
						{
							out.updateObject(1 + i, o.toString());
						}
						catch ( SQLException e2 )
						{
							e.addSuppressed(e2);
							throw e;
						}
					}
				}

				return true;
			}

			@Override
			public void close()
			{
				p.close();
			}
		};
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

		ResultSet rs = c.createStatement().executeQuery(query);
		Portal p = t.unwrapAsPortal(rs);
		TupleDescriptor td = p.tupleDescriptor();

		List<TupleTableSlot> tups = p.fetch(FORWARD, ALL);

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

			for ( Attribute att : tts.descriptor() )
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
					catch ( AdapterException e )
					{
						System.out.println(e);
					}
				}
			}
		}
	}
}
