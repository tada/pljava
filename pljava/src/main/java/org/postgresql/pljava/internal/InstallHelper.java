/*
 * Copyright (c) 2015-2025 Tada AB and other contributors, as listed below.
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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.security.Policy;
import java.security.Security;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.sql.Types.VARCHAR;

import org.postgresql.pljava.jdbc.SQLUtils;
import org.postgresql.pljava.management.SQLDeploymentDescriptor;
import org.postgresql.pljava.nopolicy.FrozenProperties;
import org.postgresql.pljava.policy.TrialPolicy;
import static org.postgresql.pljava.annotation.processing.DDRWriter.eQuote;
import static org.postgresql.pljava.elog.ELogHandler.LOG_WARNING;
import static org.postgresql.pljava.internal.Backend.WITHOUT_ENFORCEMENT;
import static org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Group of methods intended to streamline the PL/Java installation/startup
 * experience.
 *
 * @author Chapman Flack
 */
public class InstallHelper
{
	static final boolean MANAGE_CONTEXT_LOADER;

	static
	{
		String manageLoaderProp = "org.postgresql.pljava.context.loader";
		String s = System.getProperty(manageLoaderProp);
		if ( null == s )
			MANAGE_CONTEXT_LOADER = true;
		else if ( "unmanaged".equals(s) )
			MANAGE_CONTEXT_LOADER = false;
		else
		{
			MANAGE_CONTEXT_LOADER = false;
			Backend.log(LOG_WARNING,
				"value \"" + s + "\" for " + manageLoaderProp +
				" unrecognized; using \"unmanaged\"");
		}
	}

	private static void setPropertyIfNull( String property, String value)
	{
		if ( null == System.getProperty( property) )
			System.setProperty( property, value);
	}

	/**
	 * Perform miscellaneous early PL/Java initialization, and return a string
	 * detailing the versions of PL/Java, PostgreSQL, and Java in use, which the
	 * native caller can use in its "PL/Java loaded" (a/k/a "hello")
	 * triumphant {@code ereport}.
	 *<p>
	 * This method calls {@code beginEnforcing} rather late, so that the policy
	 * needn't be cluttered with permissions for the operations only needed
	 * before that point. Policy is being enforced by the time this method
	 * returns (except in case of JEP 411 fallback as described at
	 * {@code beginEnforcing}).
	 */
	public static String hello(
		String nativeVer, String serverBuiltVer, String serverRunningVer,
		String user, String dbname, String clustername,
		String datadir, String libdir, String sharedir, String etcdir)
		throws SQLException
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

		if ( ! WITHOUT_ENFORCEMENT )
		{
			setPropertyIfNull( "org.postgresql.datadir", datadir);
			setPropertyIfNull( "org.postgresql.libdir", libdir);
			setPropertyIfNull( "org.postgresql.sharedir", sharedir);
			setPropertyIfNull( "org.postgresql.sysconfdir", etcdir);
		}

		setPropertyIfNull( "org.postgresql.pljava.version", implVersion);
		setPropertyIfNull( "org.postgresql.pljava.native.version", nativeVer);
		setPropertyIfNull( "org.postgresql.version",
			Backend.getConfigOption( "server_version"));
		/*
		 * As stipulated by JRT-2003
		 */
		setPropertyIfNull( "sqlj.defaultconnection", "jdbc:default:connection");

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

		/* so it can be granted permissions in the pljava policy */
		if ( ! WITHOUT_ENFORCEMENT )
		{
			System.setProperty( "org.postgresql.pljava.codesource",
				InstallHelper.class.getProtectionDomain().getCodeSource()
					.getLocation().toString());

			setPolicyURLs();
		}

		/*
		 * PL/Java modifies no more system properties beyond this point.
		 * Take a defensive copy here that can be exposed through the Session
		 * API.
		 */
		org.postgresql.pljava.internal.Session.s_properties =
			new FrozenProperties(System.getProperties());

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

		if ( ! WITHOUT_ENFORCEMENT )
		{
			try
			{
				// sqlj scheme must exist when reading policy
				new URI("sqlj", "x", null).toURL();
			}
			catch ( MalformedURLException | URISyntaxException e )
			{
				throw new SecurityException(
				"failed to create sqlj: URL scheme needed for security policy",
					e);
			}

			beginEnforcing();
		}

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

	/**
	 * Set the URLs to be read by Java's Policy implementation according to
	 * pljava.policy_urls. That is a {@code GUC_LIST}-formatted config variable
	 * where each element can be a plain URL, or a URL prefixed with {@code n=}
	 * to set the index of the URL to be set or replaced to <em>n</em>.
	 * If no <em>n</em> is specified, the index following the last one set will
	 * be used; if the <em>first</em> URL in the list has no <em>n</em>, it will
	 * be placed at index 2 (after the presumed JRE installed policy at index
	 * 1).
	 *<p>
	 * An entry with nothing after the {@code =} causes that and subsequent URL
	 * positions not to be processed, in case they had been set in the
	 * systemwide {@code java.security} file. As there is not actually a way to
	 * delete a security property, the code will simply replace any
	 * {@code policy.url.n} entries found at that index and higher with copies
	 * of the URL at the immediately preceding index.
	 */
	private static void setPolicyURLs()
	throws SQLException
	{
		/* This index is incremented before setting each specified policy URL.
		 * Initializing it to 1 means the first URL set (if it does not specify
		 * an index) will be at 2, following the JRE's installed policy. Any URL
		 * entry can begin with n= in order to set URL number n instead (and
		 * any that follow will be in sequence after n, unless another n= is
		 * used).
		 */
		int urlIndex = 1;

		int stopIndex = -1;

		Pattern p = Pattern.compile( "^(?:(\\d++)?+=)?+");

		String prevURL = null;
		for (Simple u : Backend.getListConfigOption( "pljava.policy_urls"))
		{
			if ( -1 != stopIndex )
				throw new SQLNonTransientException(
					"stop (=) entry must be last in pljava.policy_urls",
					"F0000");
			++ urlIndex;
			String s = u.nonFolded();
			Matcher m = p.matcher(s);
			if ( m.find() )
			{
				s = s.substring(m.end());
				String i = m.group(1);
				if ( null != i )
					urlIndex = Integer.parseInt(i);
				if ( s.isEmpty() )
					stopIndex = urlIndex;
			}
			if ( urlIndex < 1 )
				throw new SQLNonTransientException(
					"index (n=) must be >= 1 in pljava.policy_urls",
					"F0000");
			int prevIndex = urlIndex - 1;
			if ( urlIndex > 1 )
			{
				prevURL = Security.getProperty( "policy.url." + prevIndex);
				if ( null == prevURL )
				{
					@SuppressWarnings("deprecation") // Java >= 10: feature()
					boolean hint =
						(2 == urlIndex) && 24 <= Runtime.version().major();

					throw new SQLNonTransientException(String.format(
						"URL at %d in pljava.policy_urls follows an unset URL" +
						(hint ? (". " + jepSuffix) : ""), urlIndex), "F0000");
				}
			}
			if ( -1 != stopIndex )
				continue; /* should be last, but resume loop to make sure */
			Security.setProperty( "policy.url." + urlIndex, s);
		}
		if ( -1 == stopIndex )
			return;

		while ( null != Security.getProperty( "policy.url." + stopIndex) )
		{
			Security.setProperty( "policy.url." + stopIndex, prevURL);
			++ stopIndex;
		}
	}

	/**
	 * From the point of successful call of this method, PL/Java is enforcing
	 * security policy (except in JEP 411 fallback case described below).
	 *<p>
	 * This method handles applying the {@code TrialPolicy} if that has been
	 * selected, and setting the security manager, which thereafter cannot be
	 * unset or changed (unless the policy has been edited to allow it).
	 *<p>
	 * In the advent of JEP 411, this method also must also head off the
	 * layer-inappropriate boilerplate warning message when running on Java 17
	 * or later, and react if the operation has been disallowed or "degraded".
	 *<p>
	 * The expected form of "degradation" as of Java 24 with JEP 486 is for
	 * {@code setSecurityManager} to throw
	 * {@code UnsupportedOperationException}. Nonetheless, we still also check
	 * that {@code getSecurityManager} returns the instance we intended to set.
	 *<p>
	 * JEP 486 explicitly allows the property {@code java.security.manager} to
	 * be set to {@code disallow} at invocation, and this detectably differs
	 * from its null default (despite the semantic equivalence), so that will be
	 * the setting to include in {@code pljava.vmoptions} to indicate that
	 * running without any policy enforcement is ok. When that property is so
	 * set, this method is not even called.
	 */
	private static void beginEnforcing() throws SQLException
	{
		String trialURI = System.getProperty(
			"org.postgresql.pljava.policy.trial");

		if ( null != trialURI )
		{
			try
			{
				Policy.setPolicy( new TrialPolicy( trialURI));
			}
			catch ( NoSuchAlgorithmException e )
			{
				throw new SQLException(e.getMessage(), e);
			}
		}

		@SuppressWarnings("deprecation") // Java >= 10: feature()
		int major = Runtime.version().major();

		if ( 17 <= major )
			Backend.pokeJEP411();

		try
		{
			SecurityManager sm = new SecurityManager();
			System.setSecurityManager( sm);
			if ( sm == System.getSecurityManager() )
				return;
		}
		catch ( UnsupportedOperationException e )
		{
			if ( 18 >= major )
				throw new SQLException(
					"Unexpected failure enabling permission enforcement", e);
			throw new SQLNonTransientException(
				"[JEP 411] The Java version selected, " + Runtime.version() +
				", has not allowed PL/Java to enforce security policy. " +
				( 24 > major ? allowHint : "" ) + jepSuffix, "58000", e);
		}

		throw new SQLNonTransientException(
			"[JEP 411] The Java version selected, " + Runtime.version() +
			", cannot enforce security policy as this PL/Java version " +
			"requires. " + ( 24 > major ? allowHint : "" ) + jepSuffix,
			"58000");
	}

	private static final String jepSuffix =
		"With Java 24 and later, this version of PL/Java can only operate " +
		"with -Djava.security.manager=disallow set in pljava.vmoptions, " +
		"resulting in no enforcement of any security expectations, no " +
		"distinction between trusted and untrusted, and so on. If that is " +
		"unacceptable, " +
		"pljava.libjvm_location should be pointed to an earlier version " +
		"of Java, or a newer PL/Java version should be used. For more " +
		"explanation, please see " +
		"https://github.com/tada/pljava/wiki/JEP-411";

	private static final String allowHint =
		"To enforce security policy in Java 18 through 23, the setting " +
		"-Djava.security.manager=allow must be added in pljava.vmoptions. ";

	/**
	 * When PL/Java is loaded as an end-in-itself (that is, by {@code LOAD}
	 * on its own or from its extension script on {@code CREATE EXTENSION} or
	 * {@code ALTER EXTENSION}, not just in the course of handling a call of a
	 * Java function), this method will be called to ensure there is
	 * a schema {@code sqlj} and that it contains the right, possibly updated,
	 * stuff.
	 */
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

			if ( asExtension && ! exNihilo )
				preAbsorb(c, s); // handle possible update from unpackaged

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

	/**
	 * Absorb a few key objects into the extension, if they exist, before the
	 * operations that CREATE OR REPLACE them.
	 *
	 * Until postgres/postgres@b9b21ac, CREATE OR REPLACE would silently absorb
	 * the object, if preexisting, into the extension being created. Since that
	 * change, those CREATE OR REPLACE operations now fail if the object exists
	 * but is not yet a member of the extension. Therefore, this method is
	 * called first, to absorb those objects if they exist. Because this only
	 * matters when the objects do not yet belong to the extension (the old
	 * "FROM unpackaged" case), this method first checks and returns with no
	 * effect if javau_call_handler is already an extension member.
	 *
	 * Because it's possible to be updating from an older PL/Java version
	 * (for example, one without the validator functions), failure to add an
	 * expected object to the extension because the object doesn't exist yet
	 * is not treated here as an error.
	 */
	private static void preAbsorb( Connection c, Statement s)
	throws SQLException
	{
		/*
		 * Do nothing if javau_call_handler is already an extension member.
		 */
		try (
			ResultSet rs = s.executeQuery(
				"SELECT d.refobjid" +
				" FROM" +
				" pg_catalog.pg_namespace n" +
				" JOIN pg_catalog.pg_proc p" +
				"  ON pronamespace OPERATOR(pg_catalog.=) n.oid" +
				" JOIN pg_catalog.pg_depend d" +
				"  ON d.classid OPERATOR(pg_catalog.=) p.tableoid" +
				"  AND d.objid OPERATOR(pg_catalog.=) p.oid" +
				" WHERE" +
				"  nspname OPERATOR(pg_catalog.=) 'sqlj'" +
				"  AND proname OPERATOR(pg_catalog.=) 'javau_call_handler'" +
				"  AND deptype OPERATOR(pg_catalog.=) 'e'"
			)
		)
		{
			if ( rs.next() )
				return;
		}

		addExtensionUnless(c, s, "42883", "FUNCTION sqlj.java_call_handler()");
		addExtensionUnless(c, s, "42883", "FUNCTION sqlj.javau_call_handler()");
		addExtensionUnless(c, s, "42883",
			"FUNCTION sqlj.java_validator(pg_catalog.oid)");
		addExtensionUnless(c, s, "42883",
			"FUNCTION sqlj.javau_validator(pg_catalog.oid)");
		addExtensionUnless(c, s, "42704", "LANGUAGE java");
		addExtensionUnless(c, s, "42704", "LANGUAGE javaU");
	}

	/**
	 * Absorb obj into the pljava extension, unless it doesn't exist.
	 * Pass the sqlState expected when an obj of that type doesn't exist.
	 */
	private static void addExtensionUnless(
		Connection c, Statement s, String sqlState, String obj)
	throws SQLException
	{
		Savepoint p = null;
		try
		{
			p = c.setSavepoint();
			s.execute("ALTER EXTENSION pljava ADD " + obj);
			c.releaseSavepoint(p);
		}
		catch ( SQLException sqle )
		{
			c.rollback(p);
			if ( ! sqlState.equals(sqle.getSQLState()) )
				throw sqle;
		}
	}

	/**
	 * Create the {@code sqlj} schema, adding an appropriate comment and
	 * granting {@code USAGE} to {@code public}.
	 *<p>
	 * If the schema already exists, whatever comment and permissions it
	 * may have will not be disturbed.
	 */
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

	/**
	 * Declare PL/Java's language handler functions.
	 *<p>
	 * {@code CREATE OR REPLACE} is used so that the library path can be altered
	 * if this is an upgrade.
	 *<p>
	 * All privileges are unconditionally revoked on the handler functions.
	 * PostgreSQL does not need permissions when it invokes them
	 * as language handlers.
	 *<p>
	 * Each function will have a default comment added if no comment is present.
	 */
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

	/**
	 * Declare PL/Java's basic two (trusted and untrusted) languages.
	 *<p>
	 * If not declared already, they will have default permissions and comments
	 * applied.
	 *<p>
	 * If they exist, {@code CREATE OR REPLACE} will be used, which takes care
	 * of adding the validator handler during an upgrade from a version that
	 * lacked it. No permission or comment changes are made in this case.
	 */
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
	 * expected tables, functions, etc.
	 *<p>
	 * Will be skipped if tables conforming
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

		deployViaDescriptor( c);
	}

	/**
	 * Only execute the deployment descriptor for PL/Java itself; factored out
	 * of {@code deployment()} so it can be used also from schema migration to
	 * avoid duplicating SQL that appears there.
	 *<p>
	 * Schema migration will use the wrapper method that changes the effective
	 * set of recognized implementor tags.
	 */
	private static void deployViaDescriptor( Connection c)
	throws SQLException
	{
		SQLDeploymentDescriptor sdd;
		try(InputStream is =
				InstallHelper.class.getResourceAsStream("/pljava.ddr"))
		{
			CharBuffer cb =
				UTF_8.newDecoder().decode( ByteBuffer.wrap( is.readAllBytes()));
			sdd = new SQLDeploymentDescriptor(cb.toString());
		}
		catch ( ParseException | IOException e )
		{
			throw new SQLException(
				"Could not load PL/Java's deployment descriptor: " +
				e.getMessage(), "XX000", e);
		}

		sdd.install(c);
	}

	/**
	 * Only execute the deployment descriptor for PL/Java itself, temporarily
	 * replacing the default set of implementor tags with a specified set, to
	 * selectively apply commands appearing in the descriptor.
	 */
	private static void deployViaDescriptor(
		Connection c, Statement s, String implementors)
	throws SQLException
	{
		s.execute( "SET LOCAL pljava.implementors TO " +
			s.enquoteLiteral(implementors));

		deployViaDescriptor( c);

		s.execute( "RESET pljava.implementors");
	}

	/**
	 * Query the database metadata for existence of a column in a table in the
	 * {@code sqlj} schema. Pass null for the column to simply check the table's
	 * existence.
	 */
	private static boolean hasColumn(
		DatabaseMetaData md, String table, String column)
	throws SQLException
	{
		try (
			ResultSet rs = md.getColumns( null, "sqlj", table, column)
		)
		{
			return rs.next();
		}
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
		try (
			ResultSet rs =
				md.getProcedures( null, "sqlj", "alias_java_language")
		)
		{
			if ( rs.next() )
				return SchemaVariant.REL_1_6_0;
		}

		if ( hasColumn( md, "jar_descriptor", null) )
			return SchemaVariant.UNREL20130301b;

		if ( hasColumn( md, "jar_descriptors", null) )
			return SchemaVariant.UNREL20130301a;

		if ( hasColumn( md, "jar_repository", "jarmanifest") )
			return SchemaVariant.REL_1_3_0;

		if ( hasColumn( md, "typemap_entry", null) )
			return SchemaVariant.UNREL20060212;

		try (
			ResultSet rs =
				md.getColumns( null, "sqlj", "jar_repository", "jarowner")
		)
		{
			if ( rs.next() )
			{
				if ( VARCHAR == rs.getInt("DATA_TYPE") )
					return SchemaVariant.UNREL20060125;
				return SchemaVariant.REL_1_1_0;
			}
		}

		if ( hasColumn( md, "jar_repository", "deploymentdesc") )
			return SchemaVariant.REL_1_0_0;

		if ( hasColumn( md, "jar_entry", null) )
			return SchemaVariant.UNREL20040121;

		if ( hasColumn( md, "jar_repository", "jarimage") )
			return SchemaVariant.UNREL20040120;

		try (
			PreparedStatement stmt = Checked.Supplier.use((() ->
				{
					PreparedStatement ps = c.prepareStatement(
						/*
						 * Is the sqlj schema 'empty'? Count the pg_depend
						 * type 'n' dependency entries referring to the sqlj
						 * namespace ...
						 */
						"SELECT count(*)" +
						"FROM" +
						" pg_catalog.pg_depend d, pg_catalog.pg_namespace n " +
						"WHERE" +
						" refclassid OPERATOR(pg_catalog.=) n.tableoid " +
						" AND refobjid OPERATOR(pg_catalog.=) n.oid" +
						" AND nspname OPERATOR(pg_catalog.=) 'sqlj' " +
						" AND deptype OPERATOR(pg_catalog.=) 'n' " +
						/*
						 * ... but exclude from the count, if present:
						 */
						" AND NOT EXISTS ( " +
						"  SELECT 1 FROM " +
						"  pg_catalog.pg_class sqc" +
						"  JOIN pg_catalog.pg_namespace sqn" +
						"  ON relnamespace OPERATOR(pg_catalog.=) sqn.oid " +
						"  WHERE " +
						/*
						 * (1) any dependency that is an extension (d.classid
						 * identifies pg_catalog.pg_extension) ...
						 */
						"    nspname OPERATOR(pg_catalog.=) 'pg_catalog'" +
						"    AND" +
						"     relname OPERATOR(pg_catalog.=) 'pg_extension' " +
						"    AND d.classid OPERATOR(pg_catalog.=) sqc.oid " +
						"	OR " +
						/*
						 * (2) any dependency that is the loadpath_tbl table
						 * we temporarily create in the extension script.
						 */
						"    nspname OPERATOR(pg_catalog.=) 'sqlj'" +
						"    AND relname OPERATOR(pg_catalog.=) ?" +
						"    AND classid OPERATOR(pg_catalog.=) sqc.tableoid" +
						"    AND objid OPERATOR(pg_catalog.=) sqc.oid)");
					ps.setString(1, loadpath_tbl);
					return ps;
				})).get();
			ResultSet rs = stmt.executeQuery();
		)
		{
			if ( rs.next() && 0 == rs.getInt(1) )
				return SchemaVariant.EMPTY;
		}

		return null;
	}

	/**
	 * The SchemaVariant that is used and expected by the current code.
	 * Define additional variants as the schema evolves, and keep this field
	 * up to date.
	 */
	private static final SchemaVariant currentSchema =
		SchemaVariant.REL_1_6_0;

	private enum SchemaVariant
	{
		REL_1_6_0 ("5565a3c9c4b8d6dd0b0f7fff4090d4e8120dc10a")
		{
			@Override
			void migrateFrom( SchemaVariant sv, Connection c, Statement s)
			throws SQLException
			{
				if ( REL_1_5_0 != sv )
					REL_1_5_0.migrateFrom( sv, c, s);

				deployViaDescriptor( c, s, "alias_java_language");
			}
		},
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

		static final SchemaVariant REL_1_6_10      = REL_1_6_0;
		static final SchemaVariant REL_1_6_9       = REL_1_6_0;
		static final SchemaVariant REL_1_6_8       = REL_1_6_0;
		static final SchemaVariant REL_1_6_7       = REL_1_6_0;
		static final SchemaVariant REL_1_6_6       = REL_1_6_0;
		static final SchemaVariant REL_1_6_5       = REL_1_6_0;
		static final SchemaVariant REL_1_6_4       = REL_1_6_0;
		static final SchemaVariant REL_1_6_3       = REL_1_6_0;
		static final SchemaVariant REL_1_6_2       = REL_1_6_0;
		static final SchemaVariant REL_1_6_1       = REL_1_6_0;

		static final SchemaVariant REL_1_5_8       = REL_1_5_0;
		static final SchemaVariant REL_1_5_7       = REL_1_5_0;
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
