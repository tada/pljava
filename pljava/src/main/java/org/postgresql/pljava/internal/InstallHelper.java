/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLNonTransientException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.text.ParseException;
import java.util.Scanner;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.sql.Types.VARCHAR;

import org.postgresql.pljava.jdbc.SQLUtils;
import org.postgresql.pljava.management.SQLDeploymentDescriptor;
import static org.postgresql.pljava.annotation.processing.DDRWriter.eQuote;

/**
 * Group of methods intended to streamline the PL/Java installation/startup
 * experience.
 *
 * @author Chapman Flack
 */
public class InstallHelper
{
	private static void setPropertyIfNull( String property, String value)
	{
		if ( null == System.getProperty( property) )
			System.setProperty( property, value);
	}

	public static String hello(
		String nativeVer, String serverBuiltVer, String serverRunningVer,
		String user, String dbname, String clustername,
		String datadir, String libdir, String sharedir, String etcdir)
	{
		String implVersion =
			InstallHelper.class.getModule().getDescriptor().rawVersion().get();
		/*
		 * visualvm.display.name is not really used as a property. jvisualvm
		 * picks it up by looking for -Dvisualvm.display.name=something in the
		 * JVM invocation arguments, not by looking at actual properties.
		 */
		System.clearProperty( "visualvm.display.name");
		System.setProperty( "user.name", user);
		setPropertyIfNull( "java.awt.headless", "true");
		setPropertyIfNull( "org.postgresql.database", dbname);
		if ( null != clustername )
			setPropertyIfNull( "org.postgresql.cluster", clustername);
		setPropertyIfNull( "org.postgresql.datadir", datadir);
		setPropertyIfNull( "org.postgresql.libdir", libdir);
		setPropertyIfNull( "org.postgresql.sharedir", sharedir);
		setPropertyIfNull( "org.postgresql.sysconfdir", etcdir);
		setPropertyIfNull( "org.postgresql.pljava.version", implVersion);
		setPropertyIfNull( "org.postgresql.pljava.native.version", nativeVer);
		setPropertyIfNull( "org.postgresql.version",
			Backend.getConfigOption( "server_version"));
		/*
		 * As stipulated by JRT-2003
		 */
		setPropertyIfNull( "sqlj.defaultconnection", "jdbc:default:connection");

		/*
		 * Set the org.postgresql.pljava.udt.byteorder.{scalar,mirror}.{p2j,j2p}
		 * properties. For shorthand, defaults can be given in shorter property
		 * keys org.postgresql.pljava.udt.byteorder.{scalar,mirror} or even just
		 * org.postgresql.pljava.udt.byteorder for an overall default. These
		 * shorter keys are then removed from the system properties.
		 */
		String orderKey = "org.postgresql.pljava.udt.byteorder";
		String orderAll = System.getProperty(orderKey);
		String orderScalar = System.getProperty(orderKey + ".scalar");
		String orderMirror = System.getProperty(orderKey + ".mirror");

		if ( null == orderScalar )
			orderScalar = null != orderAll ? orderAll : "big_endian";
		if ( null == orderMirror )
			orderMirror = null != orderAll ? orderAll : "native";

		setPropertyIfNull(orderKey + ".scalar.p2j", orderScalar);
		setPropertyIfNull(orderKey + ".scalar.j2p", orderScalar);

		setPropertyIfNull(orderKey + ".mirror.p2j", orderMirror);
		setPropertyIfNull(orderKey + ".mirror.j2p", orderMirror);

		System.clearProperty(orderKey);
		System.clearProperty(orderKey + ".scalar");
		System.clearProperty(orderKey + ".mirror");

		String encodingKey = "org.postgresql.server.encoding";
		String encName = System.getProperty(encodingKey);
		if ( null == encName )
			encName = Backend.getConfigOption( "server_encoding");
		try
		{
			Charset cs = Charset.forName(encName);
			org.postgresql.pljava.internal.Session.s_serverCharset = cs; // poke
			System.setProperty(encodingKey, cs.name());
		}
		catch ( IllegalArgumentException iae )
		{
			System.clearProperty(encodingKey);
		}

		/*
		 * Construct the strings announcing the versions in use.
		 */
		String jreName = System.getProperty( "java.runtime.name");
		String jreVer = System.getProperty( "java.runtime.version");

		if ( null == jreName || null == jreVer )
		{
			jreName = System.getProperty( "java.vendor");
			jreVer = System.getProperty( "java.version");
		}

		String vmName = System.getProperty( "java.vm.name");
		String vmVer = System.getProperty( "java.vm.version");
		String vmInfo = System.getProperty( "java.vm.info");

		StringBuilder sb = new StringBuilder();
		sb.append( "PL/Java native code (").append( nativeVer).append( ")\n");
		sb.append( "PL/Java common code (").append( implVersion).append( ")\n");
		sb.append( "Built for (").append( serverBuiltVer).append( ")\n");
		sb.append( "Loaded in (").append( serverRunningVer).append( ")\n");
		sb.append( jreName).append( " (").append( jreVer).append( ")\n");
		sb.append( vmName).append( " (").append( vmVer);
		if ( null != vmInfo )
			sb.append( ", ").append( vmInfo);
		sb.append( ')');
		return sb.toString();
	}

	public static void groundwork(
		String module_pathname, String loadpath_tbl, String loadpath_tbl_quoted,
		boolean asExtension, boolean exNihilo)
	throws SQLException, ParseException, IOException
	{
		try(Connection c = SQLUtils.getDefaultConnection();
			Statement s = c.createStatement())
		{
			schema(c, s);

			SchemaVariant sv = recognizeSchema(c, s, loadpath_tbl);

			if ( null == sv )
				throw new SQLNonTransientException(
				"Failed to recognize schema of PL/Java installation", "55000");
			if ( asExtension && exNihilo && SchemaVariant.EMPTY != sv )
				throw new SQLNonTransientException(
				"sqlj schema not empty for CREATE EXTENSION pljava", "55000");

			handlers(c, s, module_pathname);
			languages(c, s);
			deployment(c, s, sv);

			if ( asExtension )
				/*
				 * Extension scripts (which create this table before issuing a
				 * LOAD command) need a way to confirm something happened (it
				 * won't, if the library was already loaded in the same
				 * session). As simple confirmation, drop the table here.
				 * Although a simple SQL script can't raise arbitrary errors,
				 * it can certainly confirm this table is gone by trying to
				 * create it again, which will cause a (cryptic, but reliable)
				 * error if this code didn't execute.
				 */
				s.execute("DROP TABLE sqlj." + loadpath_tbl_quoted);
		}
	}

	private static void schema( Connection c, Statement s)
	throws SQLException
	{
		Savepoint p = null;
		try
		{
			p = c.setSavepoint();
			s.execute("CREATE SCHEMA sqlj");
			s.execute("COMMENT ON SCHEMA sqlj IS '"+
			"Schema for objects pertaining to PL/Java, as specified by " +
			"\"SQL/JRT\" part 13 of the SQL standard, Java Routines and Types.'"
			);
			s.execute("GRANT USAGE ON SCHEMA sqlj TO public");
			c.releaseSavepoint(p);
		}
		catch ( SQLException sqle )
		{
			c.rollback(p);
			if ( ! "42P06".equals(sqle.getSQLState()) )
				throw sqle;
		}
	}

	private static void handlers( Connection c, Statement s, String module_path)
	throws SQLException
	{
		s.execute(
			"CREATE OR REPLACE FUNCTION sqlj.java_call_handler()" +
			" RETURNS pg_catalog.language_handler" +
			" AS " + eQuote(module_path) +
			" LANGUAGE C");
		s.execute("REVOKE ALL PRIVILEGES" +
			" ON FUNCTION sqlj.java_call_handler() FROM public");
		ResultSet rs = s.executeQuery(
			"SELECT pg_catalog.obj_description(CAST(" +
			"'sqlj.java_call_handler()' AS pg_catalog.regprocedure), " +
			"'pg_proc')");
		rs.next();
		rs.getString(1);
		boolean noComment = rs.wasNull();
		rs.close();
		if ( noComment )
			s.execute(
				"COMMENT ON FUNCTION sqlj.java_call_handler() IS '" +
				"Function-call handler for PL/Java''s trusted/sandboxed " +
				"language.'");

		s.execute(
			"CREATE OR REPLACE FUNCTION sqlj.javau_call_handler()" +
			" RETURNS pg_catalog.language_handler" +
			" AS " + eQuote(module_path) +
			" LANGUAGE C");
		s.execute("REVOKE ALL PRIVILEGES" +
			" ON FUNCTION sqlj.javau_call_handler() FROM public");
		rs = s.executeQuery(
			"SELECT pg_catalog.obj_description(CAST(" +
			"'sqlj.javau_call_handler()' AS pg_catalog.regprocedure), " +
			"'pg_proc')");
		rs.next();
		rs.getString(1);
		noComment = rs.wasNull();
		rs.close();
		if ( noComment )
			s.execute(
				"COMMENT ON FUNCTION sqlj.javau_call_handler() IS '" +
				"Function-call handler for PL/Java''s untrusted/unsandboxed " +
				"language.'");

		s.execute(
			"CREATE OR REPLACE FUNCTION sqlj.javau_validator(pg_catalog.oid)" +
			" RETURNS pg_catalog.void" +
			" AS " + eQuote(module_path) +
			" LANGUAGE C");
		s.execute("REVOKE ALL PRIVILEGES" +
			" ON FUNCTION sqlj.javau_validator(pg_catalog.oid) FROM public");
		rs = s.executeQuery(
			"SELECT pg_catalog.obj_description(CAST(" +
			"'sqlj.javau_validator(pg_catalog.oid)' " +
			"AS pg_catalog.regprocedure), " +
			"'pg_proc')");
		rs.next();
		rs.getString(1);
		noComment = rs.wasNull();
		rs.close();
		if ( noComment )
			s.execute(
				"COMMENT ON FUNCTION " +
				"sqlj.javau_validator(pg_catalog.oid) IS '" +
				"Function declaration validator for PL/Java''s " +
				"untrusted/unsandboxed language.'");

		s.execute(
			"CREATE OR REPLACE FUNCTION sqlj.java_validator(pg_catalog.oid)" +
			" RETURNS pg_catalog.void" +
			" AS " + eQuote(module_path) +
			" LANGUAGE C");
		s.execute("REVOKE ALL PRIVILEGES" +
			" ON FUNCTION sqlj.java_validator(pg_catalog.oid) FROM public");
		rs = s.executeQuery(
			"SELECT pg_catalog.obj_description(CAST(" +
			"'sqlj.java_validator(pg_catalog.oid)' " +
			"AS pg_catalog.regprocedure), " +
			"'pg_proc')");
		rs.next();
		rs.getString(1);
		noComment = rs.wasNull();
		rs.close();
		if ( noComment )
			s.execute(
				"COMMENT ON FUNCTION " +
				"sqlj.java_validator(pg_catalog.oid) IS '" +
				"Function declaration validator for PL/Java''s " +
				"trusted/sandboxed language.'");
	}

	private static void languages( Connection c, Statement s)
	throws SQLException
	{
		boolean created = false;
		Savepoint p = null;
		try
		{
			p = c.setSavepoint();
			s.execute(
				"CREATE TRUSTED LANGUAGE java HANDLER sqlj.java_call_handler " +
				"VALIDATOR sqlj.java_validator");
			created = true;
			s.execute(
				"COMMENT ON LANGUAGE java IS '" +
				"Trusted/sandboxed language for routines and types in " +
				"Java; http://tada.github.io/pljava/'");
			s.execute("REVOKE USAGE ON LANGUAGE java FROM PUBLIC");
			c.releaseSavepoint(p);
		}
		catch ( SQLException sqle )
		{
			c.rollback(p);
			if ( ! "42710".equals(sqle.getSQLState()) )
				throw sqle;
		}

		if ( ! created ) /* existed already but may need validator added */
			s.execute(
				"CREATE OR REPLACE " +
				"TRUSTED LANGUAGE java HANDLER sqlj.java_call_handler " +
				"VALIDATOR sqlj.java_validator");

		created = false;
		try
		{
			p = c.setSavepoint();
			s.execute(
				"CREATE LANGUAGE javaU HANDLER sqlj.javau_call_handler " +
				"VALIDATOR sqlj.javau_validator");
			created = true;
			s.execute(
				"COMMENT ON LANGUAGE javau IS '" +
				"Untrusted/unsandboxed language for routines and types in " +
				"Java; http://tada.github.io/pljava/'");
			c.releaseSavepoint(p);
		}
		catch ( SQLException sqle )
		{
			c.rollback(p);
			if ( ! "42710".equals(sqle.getSQLState()) )
				throw sqle;
		}

		if ( ! created ) /* existed already but may need validator added */
			s.execute(
				"CREATE OR REPLACE " +
				"LANGUAGE javaU HANDLER sqlj.javau_call_handler " +
				"VALIDATOR sqlj.javau_validator");
	}

	/**
	 * Execute the deployment descriptor for PL/Java itself, creating the
	 * expected tables, functions, etc. Will be skipped if tables conforming
	 * to the currently expected schema already seem to be there. If an earlier
	 * schema variant is detected, attempt to migrate to the current one.
	 */
	private static void deployment( Connection c, Statement s, SchemaVariant sv)
	throws SQLException, ParseException, IOException
	{
		if ( currentSchema == sv )
			return; // assume (optimistically) that means there's nothing to do

		if ( SchemaVariant.EMPTY != sv )
		{
			currentSchema.migrateFrom( sv, c, s);
			return;
		}

		StringBuilder sb;
		try(InputStream is =
				InstallHelper.class.getResourceAsStream("/pljava.ddr");
			InputStreamReader isr =
				new InputStreamReader(is, UTF_8.newDecoder()))
		{
			sb = new StringBuilder();
			char[] buf = new char[512];
			for ( int got; -1 != (got = isr.read(buf)); )
				sb.append(buf, 0, got);
		}
		SQLDeploymentDescriptor sdd =
			new SQLDeploymentDescriptor(sb.toString());
		sdd.install(c);
	}

	/**
	 * Detect an existing PL/Java sqlj schema. Tests for changes between schema
	 * variants that have appeared in PL/Java's git history and will return a
	 * correct result if the schema actually is any of those, but does no
	 * further verification. So, a known SchemaVariant could be returned for a
	 * messed up schema that never appeared in the git history, if it happened
	 * to match on the tested parts. The variant EMPTY is returned if nothing is
	 * in the schema (based on a direct query of pg_depend, which ought to be
	 * reliable) except an entry for the extension if applicable, or for the
	 * table temporarily created there during CREATE EXTENSION. A null return
	 * indicates that whatever is there didn't match the tests for any known
	 * variant.
	 */
	private static SchemaVariant recognizeSchema(
		Connection c, Statement s, String loadpath_tbl)
	throws SQLException
	{
		DatabaseMetaData md = c.getMetaData();
		ResultSet rs = md.getColumns( null, "sqlj", "jar_descriptor", null);
		boolean seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.UNREL20130301b;

		rs = md.getColumns( null, "sqlj", "jar_descriptors", null);
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.UNREL20130301a;

		rs = md.getColumns( null, "sqlj", "jar_repository", "jarmanifest");
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.REL_1_3_0;

		rs = md.getColumns( null, "sqlj", "typemap_entry", null);
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.UNREL20060212;

		rs = md.getColumns( null, "sqlj", "jar_repository", "jarowner");
		if ( rs.next() )
		{
			int t = rs.getInt("DATA_TYPE");
			rs.close();
			if ( VARCHAR == t )
				return SchemaVariant.UNREL20060125;
			return SchemaVariant.REL_1_1_0;
		}
		rs.close();

		rs = md.getColumns( null, "sqlj", "jar_repository", "deploymentdesc");
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.REL_1_0_0;

		rs = md.getColumns( null, "sqlj", "jar_entry", null);
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.UNREL20040121;

		rs = md.getColumns( null, "sqlj", "jar_repository", "jarimage");
		seen = rs.next();
		rs.close();
		if ( seen )
			return SchemaVariant.UNREL20040120;

		PreparedStatement ps = c.prepareStatement( "SELECT count(*) " +
			"FROM pg_catalog.pg_depend d, pg_catalog.pg_namespace n " +
			"WHERE" +
			" refclassid OPERATOR(pg_catalog.=)" +
			"  'pg_catalog.pg_namespace'::regclass " +
			" AND refobjid OPERATOR(pg_catalog.=) n.oid" +
			" AND nspname OPERATOR(pg_catalog.=) 'sqlj' " +
			" AND deptype OPERATOR(pg_catalog.=) 'n' " +
			" AND NOT EXISTS ( " +
			"  SELECT 1 FROM " +
			"  pg_catalog.pg_class sqc JOIN pg_catalog.pg_namespace sqn " +
			"  ON relnamespace OPERATOR(pg_catalog.=) sqn.oid " +
			"  WHERE " +
			"    nspname OPERATOR(pg_catalog.=) 'pg_catalog'" +
			"    AND relname OPERATOR(pg_catalog.=) 'pg_extension' " +
			"    AND classid OPERATOR(pg_catalog.=) sqc.oid " +
			"	OR " +
			"    nspname OPERATOR(pg_catalog.=) 'sqlj'" +
			"    AND relname OPERATOR(pg_catalog.=) ?" +
			"    AND classid OPERATOR(pg_catalog.=)" +
			"     'pg_catalog.pg_class'::regclass " +
			"    AND objid OPERATOR(pg_catalog.=) sqc.oid)");
		ps.setString(1, loadpath_tbl);
		rs = ps.executeQuery();
		if ( rs.next() && 0 == rs.getInt(1) )
		{
			rs.close();
			ps.close();
			return SchemaVariant.EMPTY;
		}
		rs.close();
		ps.close();

		return null;
	}

	/**
	 * The SchemaVariant that is used and expected by the current code.
	 * Define additional variants as the schema evolves, and keep this field
	 * up to date.
	 */
	private static final SchemaVariant currentSchema =
		SchemaVariant.REL_1_5_0;

	private enum SchemaVariant
	{
		REL_1_5_0 ("c51cffa34acd5a228325143ec29563174891a873")
		{
			@Override
			void migrateFrom( SchemaVariant sv, Connection c, Statement s)
			throws SQLException
			{
				switch ( sv )
				{
				case REL_1_3_0:
					s.execute(
						"CREATE TABLE sqlj.jar_descriptor " +
						"(jarId, ordinal, entryId) AS SELECT " +
						"CAST(jarId AS INT), CAST(0 AS pg_catalog.INT2), " +
						"deploymentDesc FROM sqlj.jar_repository " +
						"WHERE deploymentDesc IS NOT NULL");
					s.execute(
						"ALTER TABLE sqlj.jar_repository " +
						"DROP deploymentDesc");
					s.execute(
						"ALTER TABLE sqlj.jar_descriptor " +
						"ADD FOREIGN KEY (jarId) " +
						"REFERENCES sqlj.jar_repository ON DELETE CASCADE, " +
						"ADD PRIMARY KEY (jarId, ordinal), " +
						"ALTER COLUMN entryId SET NOT NULL, " +
						"ADD FOREIGN KEY (entryId) REFERENCES sqlj.jar_entry " +
						"ON DELETE CASCADE");
					s.execute(
						"GRANT SELECT ON sqlj.jar_descriptor TO PUBLIC");
					break;
				case UNREL20130301a:
					s.execute(
						"ALTER TABLE sqlj.jar_descriptors " +
						"RENAME TO jar_descriptor");
					break;
				default:
					super.migrateFrom( sv, c, s);
				}
			}
		},
		UNREL20130301a ("624d78ca98d80ff2ded215eeca92035da5126bc0"),
		REL_1_3_0      ("d23804a7e1154de58181a8aa48bfbbb2c8adf68b"),
		UNREL20060212  ("671eadf7f13a7996af31f1936946bf6677ecdc73"),
		UNREL20060125  ("8afd33ccb8a2a56e92dee9c9ced81185ff0bb34d"),
		REL_1_1_0      ("039db412fa91a23b67ceb8d90d30bc540fef7c5d"),
		REL_1_0_0      ("94e23ba02b55e8008a935fcf3e397db0adb4671b"),
		UNREL20040121  ("67eea979bcd4575f285c30c581fd0d674c13c1fa"),
		UNREL20040120  ("5e4131738cd095b7ff6367d64f809f6cec6a7ba7"),
		EMPTY          (null);

		static final SchemaVariant REL_1_5_6       = REL_1_5_0;
		static final SchemaVariant REL_1_5_5       = REL_1_5_0;
		static final SchemaVariant REL_1_5_4       = REL_1_5_0;
		static final SchemaVariant REL_1_5_3       = REL_1_5_0;
		static final SchemaVariant REL_1_5_2       = REL_1_5_0;
		static final SchemaVariant REL_1_5_1       = REL_1_5_0;
		static final SchemaVariant REL_1_5_1_BETA3 = REL_1_5_0;
		static final SchemaVariant REL_1_5_1_BETA2 = REL_1_5_0;
		static final SchemaVariant REL_1_5_1_BETA1 = REL_1_5_0;
		static final SchemaVariant REL_1_5_0_BETA3 = REL_1_5_0;
		static final SchemaVariant REL_1_5_0_BETA2 = REL_1_5_0_BETA3;
		static final SchemaVariant REL_1_5_0_BETA1 = REL_1_5_0_BETA2;
		static final SchemaVariant UNREL20130301b = REL_1_5_0_BETA1;

		String sha;
		SchemaVariant( String sha)
		{
			this.sha = sha;
		}

		void migrateFrom( SchemaVariant sv, Connection c, Statement s)
		throws SQLException
		{
			throw new SQLNonTransientException(
				"Detected older PL/Java SQLJ schema " + sv.name() +
				", from which no automatic migration is implemented", "55000");
		}
	}
}
