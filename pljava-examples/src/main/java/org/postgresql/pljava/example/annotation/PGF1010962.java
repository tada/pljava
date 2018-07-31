package org.postgresql.pljava.example.annotation;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

/**
 * A gnarly test of TupleDesc reference management, crafted by Johann Oskarsson
 * for bug report 1010962 on pgFoundry.
 *<p>
 * This example relies on {@code implementor} tags reflecting the PostgreSQL
 * version, set up in the {@link ConditionalDDR} example. Before PostgreSQL 8.4,
 * there is no array of {@code RECORD}, which this test requires.
 */
@SQLAction(requires="1010962 func", implementor="postgresql_ge_80400",
	install={
		"CREATE TYPE javatest.B1010962 AS ( b1_val float8, b2_val int)",

		"CREATE TYPE javatest.C1010962 AS ( c1_val float8, c2_val float8)",

		"CREATE TYPE javatest.A1010962 as (" +
		" b B1010962," +
		" c C1010962," +
		" a_val int" +
		")",

		"SELECT javatest.complexParam(array_agg(" +
		" CAST(" +
		"  (" +
		"   CAST((0.1, i)    AS javatest.B1010962)," +
		"   CAST((0.1, 10/i) AS javatest.C1010962)," +
		"   i" +
		"  ) AS javatest.A1010962)" +
		" ))" +
		" FROM generate_series (1,10) i"
	},
	remove={
		"DROP TYPE javatest.A1010962",
		"DROP TYPE javatest.C1010962",
		"DROP TYPE javatest.B1010962"
	}
)
public class PGF1010962
{
	/**
	 * Test for bug 1010962: pass in an array of A1010962, expect no
	 * TupleDesc reference leak warnings.
	 * @param receiver Looks polymorphic, but expects an array of A1010962
	 * @return 0
	 */
	@Function(schema="javatest", provides="1010962 func",
				implementor="postgresql_ge_80400")
	public static int complexParam( ResultSet receiver[] )
	throws SQLException
	{
		for ( int i = 0; i < receiver.length; i++ )
		{
			ResultSet b = (ResultSet)receiver[ i ].getObject( 1 );
			double b1_val = b.getDouble( 1 );
			int b2_val = b.getInt( 2 );

			ResultSet c = (ResultSet)receiver[ i ].getObject( 2 );
			double c1_val = c.getDouble( 1 );
			double c2_val = c.getDouble( 2 );

			int a = receiver[ i ].getInt( 3 );
		}

		return 0;
	}
}
