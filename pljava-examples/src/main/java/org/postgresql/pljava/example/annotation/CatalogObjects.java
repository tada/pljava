/*
 * Copyright (c) 2023-2025 Tada AB and other contributors, as listed below.
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

import java.lang.reflect.Method;
import static java.lang.reflect.Modifier.isPublic;

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import java.util.logging.Logger;
import java.util.logging.Level;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;

import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.TargetList.Cursor;
import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;

import org.postgresql.pljava.model.CatalogObject;
import org.postgresql.pljava.model.CatalogObject.Addressed;
import org.postgresql.pljava.model.CatalogObject.Named;
import org.postgresql.pljava.model.Portal;
import static org.postgresql.pljava.model.Portal.ALL;
import static org.postgresql.pljava.model.Portal.Direction.FORWARD;
import org.postgresql.pljava.model.ProceduralLanguage;
import org.postgresql.pljava.model.RegClass;
import org.postgresql.pljava.model.RegClass.Known;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.SlotTester;
import org.postgresql.pljava.model.Transform;
import org.postgresql.pljava.model.Trigger;
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
@SQLAction(requires="catalogInval function", install=
	"SELECT javatest.catalogInval()"
)
public class CatalogObjects {
	static final Logger logr = Logger.getAnonymousLogger();

	static void log(Level v, String m, Object... p)
	{
		logr.log(v, m, p);
	}

	static final As<CatalogObject     ,?> CatObjAdapter;
	static final As<ProceduralLanguage,?> PrLangAdapter;
	static final As<RegClass          ,?> RegClsAdapter;
	static final As<RegProcedure<?>   ,?> RegPrcAdapter;
	static final As<RegType           ,?> RegTypAdapter;
	static final As<Transform         ,?> TrnsfmAdapter;

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
			PrLangAdapter =
				(As<ProceduralLanguage,?>)t.adapterPlease(cls,"PLANG_INSTANCE");
			RegClsAdapter =
				(As<RegClass,?>)t.adapterPlease(cls, "REGCLASS_INSTANCE");
			RegPrcAdapter =
				(As<RegProcedure<?>,?>)t.adapterPlease(
					cls, "REGPROCEDURE_INSTANCE");
			RegTypAdapter =
				(As<RegType,?>)t.adapterPlease(cls, "REGTYPE_INSTANCE");
			TrnsfmAdapter =
				(As<Transform,?>)t.adapterPlease(cls, "TRANSFORM_INSTANCE");
		}
		catch ( SQLException | ReflectiveOperationException e )
		{
			throw new ExceptionInInitializerError(e);
		}
	}

	@Function(schema="javatest", provides="catalogInval function")
	public static void catalogInval() throws SQLException
	{
		try (
			Connection conn = getConnection("jdbc:default:connection");
			Statement s = conn.createStatement();
		)
		{
			SlotTester st = conn.unwrap(SlotTester.class);
			CatalogObject.Addressed<?> catObj;
			String description1;
			String description2;
			boolean passing = true;

			s.executeUpdate("CREATE TABLE tbl_a ()");
			catObj = findObj(s, st, RegClsAdapter,
				"SELECT CAST ('tbl_a' AS pg_catalog.regclass)");
			description1 = catObj.toString();
			s.executeUpdate("ALTER TABLE tbl_a RENAME TO tbl_b");
			description2 = catObj.toString();
			if ( ! description2.equals(description1.replace("tbl_a", "tbl_b")) )
			{
				log(WARNING, "RegClass before/after rename: {0} / {1}",
					description1, description2);
				passing = false;
			}
			s.executeUpdate("DROP TABLE tbl_b");
			description1 = catObj.toString();
			if ( ! description2.matches("\\Q"+description1+"\\E(?<=]).*") )
			{
				log(WARNING, "RegClass before/after drop: {1} / {0}",
					description1, description2);
				passing = false;
			}

			s.executeQuery(
				"SELECT sqlj.alias_java_language('lng_a', sandboxed => true)")
				.next();
			catObj = findObj(s, st, PrLangAdapter,
				"SELECT oid FROM pg_catalog.pg_language " +
				"WHERE lanname OPERATOR(pg_catalog.=) 'lng_a'");
			description1 = catObj.toString();
			s.executeUpdate("ALTER LANGUAGE lng_a RENAME TO lng_b");
			description2 = catObj.toString();
			if ( ! description2.equals(description1.replace("lng_a", "lng_b")) )
			{
				log(WARNING,
					"ProceduralLanguage before/after rename: {0} / {1}",
					description1, description2);
				passing = false;
			}
			s.executeUpdate("DROP LANGUAGE lng_b");
			description1 = catObj.toString();
			if ( ! description2.matches("\\Q"+description1+"\\E(?<=]).*") )
			{
				log(WARNING, "ProceduralLanguage before/after drop: {1} / {0}",
					description1, description2);
				passing = false;
			}

			s.executeUpdate(
				"CREATE FUNCTION fn_a() RETURNS INTEGER LANGUAGE SQL " +
				"AS 'SELECT 1'");
			catObj = findObj(s, st, RegPrcAdapter,
				"SELECT CAST ('fn_a()' AS pg_catalog.regprocedure)");
			description1 = catObj.toString();
			s.executeUpdate("ALTER FUNCTION fn_a RENAME TO fn_b");
			description2 = catObj.toString();
			if ( ! description2.equals(description1.replace("fn_a", "fn_b")) )
			{
				log(WARNING, "RegProcedure before/after rename: {0} / {1}",
					description1, description2);
				passing = false;
			}
			s.executeUpdate("DROP FUNCTION fn_b");
			description1 = catObj.toString();
			if ( ! description2.matches("\\Q"+description1+"\\E(?<=]).*") )
			{
				log(WARNING, "RegProcedure before/after drop: {1} / {0}",
					description1, description2);
				passing = false;
			}

			s.executeUpdate("CREATE TYPE typ_a AS ()");
			catObj = findObj(s, st, RegTypAdapter,
				"SELECT CAST ('typ_a' AS pg_catalog.regtype)");
			description1 = catObj.toString();
			s.executeUpdate("ALTER TYPE typ_a RENAME TO typ_b");
			description2 = catObj.toString();
			if ( ! description2.equals(description1.replace("typ_a", "typ_b")) )
			{
				log(WARNING, "RegType before/after rename: {0} / {1}",
					description1, description2);
				passing = false;
			}
			s.executeUpdate("DROP TYPE typ_b");
			description1 = catObj.toString();
			if ( ! description2.matches("\\Q"+description1+"\\E(?<=]).*") )
			{
				log(WARNING, "RegType before/after drop: {1} / {0}",
					description1, description2);
				passing = false;
			}

			s.executeUpdate( // a completely bogus transform, don't use it!
				"CREATE TRANSFORM FOR pg_catalog.circle LANGUAGE sql" +
				" (FROM SQL WITH FUNCTION time_support)");
			catObj = findObj(s, st, TrnsfmAdapter,
				"SELECT CAST (trf.oid AS pg_catalog.oid)" +
				" FROM pg_catalog.pg_transform AS trf" +
				" JOIN pg_catalog.pg_language AS lan ON trflang = lan.oid" +
				" WHERE lanname = 'sql'" +
				" AND trftype = CAST ('circle' AS pg_catalog.regtype)");
			boolean exists1 = catObj.exists();
			s.executeUpdate(
				"DROP TRANSFORM FOR pg_catalog.circle LANGUAGE sql");
			boolean exists2 = catObj.exists();
			if ( exists2 )
			{
				log(WARNING, "Transform.exists() before/after drop: {0} / {1}",
					exists1, exists2);
				passing = false;
			}

			if ( passing )
				log(INFO, "selective invalidation ok");
		}
	}

	private static <T extends CatalogObject.Addressed<T>> T findObj(
		Statement s, SlotTester st, As<? extends T,?> adapter, String query)
	throws SQLException
	{
		try (
			Portal p = st.unwrapAsPortal(s.executeQuery(query))
		)
		{
			return
				p.tupleDescriptor().applyOver(p.fetch(FORWARD, 1), c0 -> c0
					.stream()
					.map(c -> c.apply(adapter, o -> o))
					.findFirst().get());
		}
	}

	@Function(schema="javatest", provides="catalogClasses function")
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
							"for representation test", regc.name());
						++ untested;
						continue;
					}

					Addressed aobj = cobj.get().of(regc);

					classUnderTest = aobj.getClass();

					if ( aobj instanceof Named )
					{
						if ( aobj instanceof Trigger ) // name() won't work here
							aobj.exists();
						else
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

	private static boolean engulfs(Class<?> a, Class<?> b)
	{
		return a.isAssignableFrom(b)  ||  a == b.getDeclaringClass();
	}

	static final Comparator<Class<?>>
		partialByEngulfs = (a,b) -> engulfs(a,b) ? 1 : engulfs(b,a) ? -1 : 0;

	/**
	 * Given a PostgreSQL classid and objid, obtains the corresponding Java
	 * CatalogObject, then finds the no-parameter, non-void-returning methods
	 * of all the CatalogObject interfaces it implements, and returns a table
	 * with the results of calling those methods.
	 */
	@Function(
		schema="javatest",
		out={ "interface text", "method text", "result text", "exception text" }
	)
	public static ResultSetProvider catalogIntrospect(
		@SQLType("regclass") CatalogObject cls, CatalogObject obj)
	throws SQLException
	{
		cls = cls.of(RegClass.CLASSID);
		if ( ! ( cls instanceof Known<?> ) )
			throw new SQLException(
				"Not a supported known catalog class: " + cls);

		Known<?> kcls = (Known<?>)cls;
		Addressed<?> aobj = obj.of(kcls);

		Class clazz = aobj.getClass();

		Stream<Method> s =
			Stream.iterate(
				(new Class<?>[] { clazz }), (a -> 0 < a.length), a ->
				(
					Arrays.stream(a)
					.flatMap(c ->
						Stream.concat(
							(c.isInterface() ?
							Stream.of() : Stream.of(c.getSuperclass())),
							Arrays.stream(c.getInterfaces())
						)
					)
					.filter(Objects::nonNull)
					.toArray(Class<?>[]::new)
				)
			)
			.flatMap(Arrays::stream)
			.filter(c -> c.isInterface() && engulfs(CatalogObject.class, c))
			.sorted(partialByEngulfs.thenComparing(Class::getSimpleName))
			.distinct()
			.filter(i -> CatalogObject.class.getModule().equals(i.getModule()))
			.filter(i -> isPublic(i.getModifiers()))
			.flatMap(i ->
			{
				return Arrays.stream(i.getMethods())
					.filter(m -> i == m.getDeclaringClass());
			})
			.filter(m -> void.class != m.getReturnType())
			.filter(m -> 0 == m.getParameterCount())
			.filter(m -> ! (m.isSynthetic()));

		Iterator<Method> itr = s.iterator();

		return new ResultSetProvider.Large()
		{
			@Override public boolean assignRowValues(ResultSet r, long rownum)
			throws SQLException
			{
				if ( ! itr.hasNext() )
					return false;

				Method m = itr.next();
				r.updateString(1, m.getDeclaringClass().getSimpleName());
				r.updateString(2, m.getName());

				try
				{
					Object v = m.invoke(aobj);
					String text;
					if ( v instanceof SQLXML )
						text = ((SQLXML)v).getString();
					else
						text = Objects.toString(v);
					r.updateString(3, text);
				}
				catch ( Throwable t )
				{
					String s =
						Stream.iterate(t, Objects::nonNull, Throwable::getCause)
						.dropWhile(
							ReflectiveOperationException.class::isInstance)
						.map(Object::toString)
						.collect(joining("\n"));
					r.updateString(4, s);
				}

				return true;
			}

			@Override public void close() { s.close(); }
		};
	}
}
