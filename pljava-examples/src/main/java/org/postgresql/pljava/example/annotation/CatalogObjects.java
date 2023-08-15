/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
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
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import java.util.logging.Logger;
import java.util.logging.Level;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.TargetList.Cursor;
import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;

import org.postgresql.pljava.model.CatalogObject;
import org.postgresql.pljava.model.CatalogObject.Addressed;
import org.postgresql.pljava.model.CatalogObject.Named;
import org.postgresql.pljava.model.Portal;
import static org.postgresql.pljava.model.Portal.ALL;
import static org.postgresql.pljava.model.Portal.Direction.FORWARD;
import org.postgresql.pljava.model.RegClass;
import org.postgresql.pljava.model.RegClass.Known;
import org.postgresql.pljava.model.SlotTester;
import org.postgresql.pljava.model.TupleTableSlot;

/**
 * A test that PL/Java's various {@link CatalogObject} implementations are
 * usable.
 *<p>
 * They rely on named attributes, in PostgreSQL's system catalogs, that are
 * looked up at class initialization, so on a PostgreSQL version that may not
 * supply all the expected attributes, the issue may not be detected until
 * an affected {@code CatalogObject} subclass is first used. This test uses as
 * many of them as it can.
 */
@SQLAction(requires="catalogClasses function", install=
	"SELECT javatest.catalogClasses()"
)
public class CatalogObjects {
	static final Logger logr = Logger.getAnonymousLogger();

	static void log(Level v, String m, Object... p)
	{
		logr.log(v, m, p);
	}

	static final As<CatalogObject,?> CatObjAdapter;
	static final As<RegClass     ,?> RegClsAdapter;

	static
	{
		try
		{
			Connection conn = getConnection("jdbc:default:connection");

			// Get access to the hacked-together interim testing API
			SlotTester t = conn.unwrap(SlotTester.class);

			String cls = "org.postgresql.pljava.pg.adt.OidAdapter";

					@SuppressWarnings("unchecked") Object _1 =
			CatObjAdapter =
				(As<CatalogObject,?>)t.adapterPlease(cls, "INSTANCE");
					@SuppressWarnings("unchecked") Object _2 =
			RegClsAdapter =
				(As<RegClass,?>)t.adapterPlease(cls, "REGCLASS_INSTANCE");
		}
		catch ( SQLException | ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	@Function(provides="catalogClasses function")
	public static void catalogClasses() throws SQLException
	{
		String catalogRelationsQuery =
			"SELECT" +
			"  oid" +
			" FROM" +
			"  pg_catalog.pg_class" +
			" WHERE" +
			"   relnamespace = CAST ('pg_catalog' AS pg_catalog.regnamespace)" +
			"  AND" +
			"   relkind = 'r'";

		try (
			Connection conn = getConnection("jdbc:default:connection");
			Statement s = conn.createStatement();
		)
		{
			SlotTester st = conn.unwrap(SlotTester.class);

			List<Known> knownRegClasses;

			try (
				Portal p =
					st.unwrapAsPortal(s.executeQuery(catalogRelationsQuery))
			)
			{
				Projection proj = p.tupleDescriptor();
				List<TupleTableSlot> tups = p.fetch(FORWARD, ALL);

				Class<Known> knownCls = Known.class;

				knownRegClasses =
					proj.applyOver(tups, c0 -> c0.stream()
						.map(c -> c.apply(RegClsAdapter, regcls -> regcls))
						.filter(knownCls::isInstance)
						.map(knownCls::cast)
						.collect(toList())
					);
			}

			int passed = 0;
			int untested = 0;

			for ( Known regc : knownRegClasses )
			{
				String objectQuery =
					"SELECT oid FROM " + regc.qualifiedName() + " LIMIT 1";

				Class<? extends Addressed> classUnderTest = null;

				try (
					Portal p =
						st.unwrapAsPortal(s.executeQuery(objectQuery))
				)
				{
					Projection proj = p.tupleDescriptor();
					List<TupleTableSlot> tups = p.fetch(FORWARD, ALL);
					Optional<CatalogObject> cobj =
						proj.applyOver(tups, c0 -> c0.stream()
							.map(c -> c.apply(CatObjAdapter, o -> o))
							.findAny());

					if ( ! cobj.isPresent() )
					{
						log(INFO,
							"database has no {0} objects " +
							" for representation test", regc.name());
						++ untested;
						continue;
					}

					Addressed aobj = cobj.get().of(regc);

					classUnderTest = aobj.getClass();

					if ( aobj instanceof Named )
					{
						((Named)aobj).name();
						++ passed;
						continue;
					}

					log(INFO,
						"{0} untested, not instance of Named " +
						"(does implement {1})",
						classUnderTest.getCanonicalName().substring(
							1 + classUnderTest.getPackageName().length()),
						Arrays.stream(classUnderTest.getInterfaces())
							.map(Class::getSimpleName)
							.collect(joining(", "))
					);
				}
				catch ( LinkageError e )
				{
					Throwable t = e.getCause();
					if ( null == t )
						t = e;
					log(WARNING,
						"{0} failed initialization: {1}",
						classUnderTest.getName().substring(
							1 + classUnderTest.getPackageName().length()),
						t.getMessage());
				}
			}

			log((knownRegClasses.size() == passed + untested)? INFO : WARNING,
				"of {0} catalog representations, {1} worked " +
				"and {2} could not be tested",
				knownRegClasses.size(), passed, untested);
		}
	}
}
