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
package org.postgresql.pljava.example.annotation;

import static java.lang.Math.fma;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.annotation.Aggregate;
import org.postgresql.pljava.annotation.Function;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;
import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * A class demonstrating several aggregate functions.
 *<p>
 * They are (some of) the same two-variable statistical aggregates already
 * offered in core PostgreSQL, just because they make clear examples. For
 * numerical reasons, they might not produce results identical to PG's built-in
 * ones. These closely follow the "schoolbook" formulas in the HP-11C calculator
 * owner's handbook, while the ones built into PostgreSQL use a more clever
 * algorithm instead to reduce rounding error in the finishers.
 *<p>
 * All these aggregates can be computed by different finishers that share a
 * state that accumulates the count of rows, sum of x, sum of xx, sum of y, sum
 * of yy, and sum of xy. That is easy with finishers that don't need to modify
 * the state, so the default {@code FinishEffect=READ_ONLY} is appropriate.
 *<p>
 * Everything here takes the y parameter first, then x, like the SQL ones.
 */
@SQLAction(requires = { "avgx", "avgy", "slope", "intercept" }, install = {
    "WITH" +
    " data (y, x) AS (VALUES" +
    "  (1.761 ::float8, 5.552::float8)," +
    "  (1.775,          5.963)," +
    "  (1.792,          6.135)," +
    "  (1.884,          6.313)," +
    "  (1.946,          6.713)"  +
    " )," +
    " expected (avgx, avgy, slope, intercept) AS (" +
    "  SELECT 6.1352, 1.8316, 0.1718, 0.7773" +
    " )," +
    " got AS (" +
    "  SELECT" +
    "    round(     avgx(y,x)::numeric, 4) AS avgx," +
    "    round(     avgy(y,x)::numeric, 4) AS avgy," +
    "    round(    slope(y,x)::numeric, 4) AS slope," +
    "    round(intercept(y,x)::numeric, 4) AS intercept" +
    "   FROM" +
    "    data" +
    " )" +
    "SELECT" +
    "  CASE WHEN expected IS NOT DISTINCT FROM got" +
    "  THEN javatest.logmessage('INFO', 'aggregate examples ok')" +
    "  ELSE javatest.logmessage('WARNING', 'aggregate examples ng')" +
    "  END" +
    " FROM" +
    "  expected, got"
})
@Aggregate(provides = "avgx",
	name = "avgx",
	arguments = { "y double precision", "x double precision" },
	plan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		finish = { "javatest", "finishAvgX" }
	)
)
@Aggregate(provides = "avgy",
	name = "avgy",
	arguments = { "y double precision", "x double precision" },
	plan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		finish = { "javatest", "finishAvgY" }
	)
)
@Aggregate(provides = "slope",
	name = "slope",
	arguments = { "y double precision", "x double precision" },
	plan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		finish = { "javatest", "finishSlope" }
	)
)
@Aggregate(provides = "intercept",
	name = "intercept",
	arguments = { "y double precision", "x double precision" },
	plan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		finish = { "javatest", "finishIntercept" }
	)
)
@Aggregate(
	name = "regression",
	arguments = { "y double precision", "x double precision" },
	plan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		finish = { "javatest", "finishRegr" }
	),
	/*
	 * There is no special reason for this aggregate and not the others to have
	 * a movingPlan; one example is enough, that's all.
	 */
	movingPlan = @Aggregate.Plan(
		stateType = "double precision[]",
		stateSize = 82,
		initialState = "{0,0,0,0,0,0}",
		accumulate = { "javatest", "accumulateXY" },
		remove = { "javatest", "removeXY" },
		finish = { "javatest", "finishRegr" }
	)
)
public class Aggregates
{
	private static final int N   = 0;
	private static final int SX  = 1;
	private static final int SXX = 2;
	private static final int SY  = 3;
	private static final int SYY = 4;
	private static final int SXY = 5;

	/**
	 * A common accumulator for two-variable statistical aggregates that
	 * depend on n, Sx, Sxx, Sy, Syy, and Sxy.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static double[] accumulateXY(double[] state, double y, double x)
	{
		state[N  ] += 1.;
		state[SX ] += x;
		state[SXX] = fma(x, x, state[2]);
		state[SY ] += y;
		state[SYY] = fma(y, y, state[4]);
		state[SXY] = fma(x, y, state[5]);
		return state;
	}

	/**
	 * 'Removes' from the state a row previously accumulated, for possible use
	 * in a window with a moving frame start.
	 *<p>
	 * This can be a numerically poor idea for exactly the reasons covered in
	 * the PostgreSQL docs involving loss of significance in long sums, but it
	 * does demonstrate the idea.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static double[] removeXY(double[] state, double y, double x)
	{
		state[N  ] -= 1.;
		state[SX ] -= x;
		state[SXX] = fma(x, -x, state[2]);
		state[SY ] -= y;
		state[SYY] = fma(y, -y, state[4]);
		state[SXY] = fma(x, -y, state[5]);
		return state;
	}

	/**
	 * Finisher that returns the count of non-null rows accumulated.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static long finishCount(double[] state)
	{
		return (long)state[N];
	}

	/**
	 * Finisher that returns the mean of the accumulated x values.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static Double finishAvgX(double[] state)
	{
		if ( 0. == state[N] )
			return null;
		return state[SX] / state[N];
	}

	/**
	 * Finisher that returns the mean of the accumulated y values.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static Double finishAvgY(double[] state)
	{
		if ( 0. == state[N] )
			return null;
		return state[SY] / state[N];
	}

	/**
	 * Finisher that returns the slope of a regression line.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static Double finishSlope(double[] state)
	{
		if ( 2. > state[N] )
			return null;

		double numer = fma(state[SX], -state[SY], state[N] * state[SXY]);
		double denom = fma(state[SX], -state[SX], state[N] * state[SXX]);
		return 0. == denom ? null : numer / denom;
	}

	/**
	 * Finisher that returns the intercept of a regression line.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static Double finishIntercept(double[] state)
	{
		if ( 2 > state[N] )
			return null;

		double numer = fma(state[SY], state[SXX], -state[SX] * state[SXY]);
		double denom = fma(state[SX], -state[SX], state[N] * state[SXX]);
		return 0. == denom ? null : numer / denom;
	}

	/**
	 * A finisher that returns the slope and intercept together.
	 *<p>
	 * An aggregate can be built over this finisher and will return a record
	 * result, but at present (PG 13) access to that record by field doesn't
	 * work, as its tuple descriptor gets lost along the way. Unclear so far
	 * whether it might be feasible to fix that.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL,
		out = { "slope double precision", "intercept double precision" }
	)
	public static boolean finishRegr(double[] state, ResultSet out)
	throws SQLException
	{
		out.updateObject(1, finishSlope(state));
		out.updateObject(2, finishIntercept(state));
		return true;
	}
}
