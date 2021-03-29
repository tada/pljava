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
package org.postgresql.pljava.example.annotation;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;

import static java.lang.Math.hypot;

import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;

import java.util.logging.Logger;

import org.postgresql.pljava.annotation.Aggregate;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.Operator;
import static org.postgresql.pljava.annotation.Operator.SELF;
import static org.postgresql.pljava.annotation.Operator.TWIN;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.BaseUDT;

import static org.postgresql.pljava.annotation.Function.Effects.IMMUTABLE;
import static
	org.postgresql.pljava.annotation.Function.OnNullInput.RETURNS_NULL;

/**
 * Complex (re and im parts are doubles) implemented in Java as a scalar UDT.
 *<p>
 * The {@code SQLAction} here demonstrates a {@code requires} tag
 * ("complex relationals"} that has multiple providers, something not allowed
 * prior to PL/Java 1.6.1. It is more succinct to require one tag and have each
 * of the relational operators 'provide' it than to have to define and require
 * several different tags to accomplish the same thing.
 *<p>
 * The operator class created here is not actively used for anything (the
 * examples will not break if it is removed), but the {@code minMagnitude}
 * example aggregate does specify a {@code sortOperator}, which PostgreSQL will
 * not exploit in query optimization without finding it as a member of
 * a {@code btree} operator class.
 *<p>
 * Note that {@code CREATE OPERATOR CLASS} implicitly creates an operator family
 * as well (unless one is explicitly specified), so the correct {@code remove}
 * action to clean everything up is {@code DROP OPERATOR FAMILY} (which takes
 * care of dropping the class).
 */
@SQLAction(requires = { "complex assertHasValues", "complex relationals" },
	install = {
        "CREATE OPERATOR CLASS javatest.complex_ops" +
        "  DEFAULT FOR TYPE javatest.complex USING btree" +
		" AS" +
        "  OPERATOR 1 javatest.<  ," +
        "  OPERATOR 2 javatest.<= ," +
        "  OPERATOR 3 javatest.=  ," +
        "  OPERATOR 4 javatest.>= ," +
        "  OPERATOR 5 javatest.>  ," +
        "  FUNCTION 1 javatest.cmpMagnitude(javatest.complex,javatest.complex)",

		"SELECT javatest.assertHasValues(" +
		" CAST('(1,2)' AS javatest.complex), 1, 2)",

		"SELECT javatest.assertHasValues(" +
		" 2.0 + CAST('(1,2)' AS javatest.complex) + 3.0, 6, 2)",

		"SELECT" +
		" CASE WHEN" +
		"  '(1,2)'::javatest.complex < '(2,2)'::javatest.complex" +
		" AND" +
		"  '(2,2)'::javatest.complex > '(1,2)'::javatest.complex" +
		" AND" +
		"  '(1,2)'::javatest.complex <= '(2,2)'::javatest.complex" +
		" THEN javatest.logmessage('INFO', 'ComplexScalar operators ok')" +
		" ELSE javatest.logmessage('WARNING', 'ComplexScalar operators ng')" +
		" END"
	},

	remove = {
		"DROP OPERATOR FAMILY javatest.complex_ops USING btree"
	}
)
@BaseUDT(schema="javatest", name="complex",
	internalLength=16, alignment=BaseUDT.Alignment.DOUBLE)
public class ComplexScalar implements SQLData {
	private static Logger s_logger = Logger.getAnonymousLogger();

	/**
	 * Return the same 'complex' passed in, logging its contents at level INFO.
	 *<p>
	 * Also create an unnecessary {@code <<} operator for this, with an equally
	 * unnecessary explicit operand type, simply as a regression test
	 * of issue #330.
	 * @param cpl any instance of this UDT
	 * @return the same instance passed in
	 */
	@Operator(
		name = "javatest.<<", right = "javatest.complex"
	)
	@Function(
		schema="javatest", name="logcomplex", effects=IMMUTABLE,
		onNullInput=RETURNS_NULL)
	public static ComplexScalar logAndReturn(ComplexScalar cpl) {
		s_logger.info(cpl.getSQLTypeName() + cpl);
		return cpl;
	}

	/**
	 * Assert a 'complex' has given re and im values, to test that its
	 * representation in Java corresponds to what PostgreSQL sees.
	 * @param cpl an instance of this UDT
	 * @param re the 'real' value it should have
	 * @param im the 'imaginary' value it should have
	 * @throws SQLException if the values do not match
	 */
	@Function(schema="javatest",
		provides="complex assertHasValues",
		effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static void assertHasValues(ComplexScalar cpl, double re, double im)
		throws SQLException
	{
		if ( cpl.m_x != re  ||  cpl.m_y != im )
			throw new SQLException("assertHasValues fails");
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	public static ComplexScalar parse(String input, String typeName)
			throws SQLException {
		try {
			StreamTokenizer tz = new StreamTokenizer(new StringReader(input));
			if (tz.nextToken() == '('
					&& tz.nextToken() == StreamTokenizer.TT_NUMBER) {
				double x = tz.nval;
				if (tz.nextToken() == ','
						&& tz.nextToken() == StreamTokenizer.TT_NUMBER) {
					double y = tz.nval;
					if (tz.nextToken() == ')') {
						s_logger.fine(typeName + " from string");
						return new ComplexScalar(x, y, typeName);
					}
				}
			}
			throw new SQLException("Unable to parse complex from string \""
					+ input + '"');
		} catch (IOException e) {
			throw new SQLException(e.getMessage());
		}
	}

	private double m_x;

	private double m_y;

	private String m_typeName;

	public ComplexScalar() {
	}

	/**
	 * Add two instances of {@code ComplexScalar}.
	 */
	@Operator(name = {"javatest","+"}, commutator = SELF)
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static ComplexScalar add(ComplexScalar a, ComplexScalar b)
	{
		return new ComplexScalar(
			a.m_x + b.m_x, a.m_y + b.m_y, a.m_typeName);
	}

	/**
	 * Add a {@code ComplexScalar} and a real (supplied as a {@code double}).
	 */
	@Operator(name = {"javatest","+"}, commutator = TWIN)
	@Operator(name = {"javatest","+"},  synthetic = TWIN)
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static ComplexScalar add(ComplexScalar a, double b)
	{
		return new ComplexScalar(a.m_x + b, a.m_y, a.m_typeName);
	}

	/**
	 * True if the left argument is smaller than the right in magnitude
	 * (Euclidean distance from the origin).
	 */
	@Operator(
		name = "javatest.<",
		commutator = "javatest.>", negator = "javatest.>=",
		provides = "complex relationals"
	)
	@Operator(
		name = "javatest.<=", synthetic = "javatest.magnitudeLE",
		provides = "complex relationals"
	)
	@Operator(
		name = "javatest.>=", synthetic = "javatest.magnitudeGE",
		commutator = "javatest.<=", provides = "complex relationals"
	)
	@Operator(
		name = "javatest.>", synthetic = "javatest.magnitudeGT",
		negator = "javatest.<=", provides = "complex relationals"
	)
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static boolean magnitudeLT(ComplexScalar a, ComplexScalar b)
	{
		return hypot(a.m_x, a.m_y) < hypot(b.m_x, b.m_y);
	}

	/**
	 * True if the left argument and the right are componentwise equal.
	 */
	@Operator(
		name = "javatest.=",
		commutator = SELF, negator = "javatest.<>",
		provides = "complex relationals"
	)
	@Operator(
		name = "javatest.<>", synthetic = "javatest.componentsNE",
		commutator = SELF, provides = "complex relationals"
	)
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static boolean componentsEQ(ComplexScalar a, ComplexScalar b)
	{
		return a.m_x == b.m_x  &&  a.m_y == b.m_y;
	}

	/**
	 * True if the complex argument is real-valued and equal to the real
	 * argument.
	 *<p>
	 * From one equality method on (complex,double) can be synthesized all four
	 * cross-type operators, {@code =} and {@code <>} for that pair of types and
	 * their {@code TWIN} commutators. One of the {@code <>} twins does need to
	 * specify what its synthetic function should be named.
	 */
	@Operator(
		name = "javatest.=",
		commutator = TWIN, negator = "javatest.<>",
		provides = "complex:double relationals"
	)
	@Operator(
		name = "javatest.=",
		synthetic = TWIN, negator = "javatest.<>",
		provides = "complex:double relationals"
	)
	@Operator(
		name = "javatest.<>", synthetic = "javatest.neToReal",
		commutator = TWIN, provides = "complex:double relationals"
	)
	@Operator(
		name = "javatest.<>", synthetic = TWIN,
		provides = "complex:double relationals"
	)
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static boolean eqToReal(ComplexScalar a, double b)
	{
		return a.m_x == b  &&  0. == a.m_y;
	}

	/**
	 * As an ordinary function, returns the lesser in magnitude of two
	 * arguments; as a simple aggregate, returns the least in magnitude over its
	 * aggregated arguments.
	 *<p>
	 * As an aggregate, this is a simple example where this method serves as the
	 * {@code accumulate} function, the state (<em>a</em> here) has the same
	 * type as the argument (here <em>b</em>), there is no {@code finish}
	 * function, and the final value of the state is the result.
	 *<p>
	 * An optimization is available in case there is an index on the aggregated
	 * values based on the {@code <} operator above; in that case, the first
	 * value found in a scan of that index is the aggregate result. That is
	 * indicated here by naming the {@code <} operator as {@code sortOperator}.
	 */
	@Aggregate(sortOperator = "javatest.<")
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL
	)
	public static ComplexScalar minMagnitude(ComplexScalar a, ComplexScalar b)
	{
		return magnitudeLT(a, b) ? a : b;
	}

	/**
	 * An integer-returning comparison function by complex magnitude, usable to
	 * complete an example {@code btree} operator class.
	 */
	@Function(
		schema = "javatest", effects = IMMUTABLE, onNullInput = RETURNS_NULL,
		provides = "complex relationals"
	)
	public static int cmpMagnitude(ComplexScalar a, ComplexScalar b)
	{
		if ( magnitudeLT(a, b) )
			return -1;
		if ( magnitudeLT(b, a) )
			return 1;
		return 0;
	}

	public ComplexScalar(double x, double y, String typeName) {
		m_x = x;
		m_y = y;
		m_typeName = typeName;
	}

	@Override
	public String getSQLTypeName() {
		return m_typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void readSQL(SQLInput stream, String typeName) throws SQLException {
		s_logger.fine(typeName + " from SQLInput");
		m_x = stream.readDouble();
		m_y = stream.readDouble();
		m_typeName = typeName;
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public String toString() {
		s_logger.fine(m_typeName + " toString");
		StringBuffer sb = new StringBuffer();
		sb.append('(');
		sb.append(m_x);
		sb.append(',');
		sb.append(m_y);
		sb.append(')');
		return sb.toString();
	}

	@Function(effects=IMMUTABLE, onNullInput=RETURNS_NULL)
	@Override
	public void writeSQL(SQLOutput stream) throws SQLException {
		s_logger.fine(m_typeName + " to SQLOutput");
		stream.writeDouble(m_x);
		stream.writeDouble(m_y);
	}
}
