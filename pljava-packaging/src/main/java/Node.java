/*
 * Copyright (c) 2015-2024 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack (this file, 2020)
 *   PostgreSQL Global Development Group, Michael Paquier, Alvaro Herrera
 *    (PostgresNode.pm, 2015, of which similar methods here are ports)
 */
package org.postgresql.pljava.packaging;

import org.gjt.cuspy.JarX;

import java.io.InputStream;

import static java.lang.System.getProperty;
import static java.lang.System.setProperty;

import java.nio.ByteBuffer;
import static java.nio.charset.Charset.defaultCharset;

import java.util.regex.Matcher;
import static java.util.regex.Pattern.compile;

/*
 * For "Node" behavior:
 */

import static java.lang.ProcessBuilder.Redirect.INHERIT;
import java.lang.reflect.InvocationHandler; // flexible SAM allowing exceptions
import java.lang.reflect.UndeclaredThrowableException;
import static java.lang.Thread.interrupted;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.explicitCastArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodHandles.publicLookup;
import static java.lang.invoke.MethodType.methodType;

import static java.net.InetAddress.getLoopbackAddress;
import static java.net.URLEncoder.encode;
import java.net.ServerSocket;

import static java.nio.charset.StandardCharsets.US_ASCII;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.deleteIfExists;
import static java.nio.file.Files.exists;
import static java.nio.file.Files.getLastModifiedTime;
import static java.nio.file.Files.lines;
import static java.nio.file.Files.walk;
import static java.nio.file.Files.write;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.*;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;

import java.sql.Connection;
import static java.sql.DriverManager.drivers;
import static java.sql.DriverManager.getConnection;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;

import java.sql.SQLException;
import java.sql.SQLWarning;

import javax.sql.rowset.RowSetProvider;
import javax.sql.rowset.WebRowSet;
import javax.sql.rowset.RowSetMetaDataImpl;

import java.util.ArrayDeque;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import java.util.Properties;
import java.util.Random;
import java.util.Spliterator;
import static java.util.Spliterator.IMMUTABLE;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterators.spliteratorUnknownSize;
import java.util.WeakHashMap;

import java.util.concurrent.Callable; // like a Supplier but allows exceptions!
import java.util.concurrent.CancellationException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import java.util.jar.JarFile;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import static java.util.stream.StreamSupport.stream;

/**
 * Extends the JarX extraction tool to provide a {@code resolve} method that
 * replaces prefixes {@code pljava/foo/} in path names stored in the archive
 * with the result of {@code pg_config --foo}.
 *<p>
 * As this represents a second extra {@code .class} file that has to be added
 * to the installer jar anyway, it will also contain some methods intended to be
 * useful for tasks related to installation and testing. The idea is not to go
 * overboard, but supply a few methods largely modeled on the most basic ones of
 * PostgreSQL's {@code PostgreSQL::Test::Cluster} Perl module (formerly named
 * {@code PostgresNode}, from which the name of this class was taken). The
 * methods can be invoked from {@code jshell} if its classpath includes the
 * installer jar (and one of the PostgreSQL JDBC drivers).
 *<p>
 * An
 * <a href="../../../../../../develop/node.html">introduction with examples</a>
 * is available.
 *<p>
 * Unlike the many capabilities of {@code PostgreSQL::Test::Cluster}, this only
 * deals in TCP sockets bound to {@code localhost}
 * ({@code StandardProtocolFamily.UNIX}
 * finally arrived in Java 16 but this class does not support it yet) and only
 * a few of the most basic operations.
 *<p>
 * As in JarX itself, some liberties with coding style may be taken here to keep
 * this one extra {@code .class} file from proliferating into a bunch of them.
 *<p>
 * As the testing-related methods here are intended for ad-hoc or scripted use
 * in {@code jshell}, they are typically declared to throw any checked
 * exception, without further specifics. There are many overloads of methods
 * named {@code q} and {@code qp} (mnemonic of query and query-print), to make
 * interactive use in {@code jshell} comfortable with just a few static imports.
 */
public class Node extends JarX {

	private Matcher m_prefix;
	private int m_fsepLength;
	private String m_lineSep;
	private boolean m_dryrun = false;

	private static Node s_jarxHelper = new Node(null, 0, null, null);
	private static boolean s_jarProcessed = false;
	private static String s_examplesJar;
	private static String s_sharedObject;

	/**
	 * Performs an ordinary installation, using {@code pg_config} or the
	 * corresponding system properties to learn where the files belong, and
	 * unpacking the files (not including this class or its ancestors) there.
	 */
	public static void main(String[] args) throws Exception
	{
		if ( args.length > 0 )
		{
			System.err.println("usage: java -jar filename.jar");
			System.exit(1);
		}

		s_jarxHelper.extract();
	}

	/**
	 * Extracts the jar contents, just as done in the normal case of running
	 * this class with {@code java -jar}.
	 *<p>
	 * Only to be called on the singleton instance {@code s_jarxHelper}.
	 *<p>
	 * For a version that doesn't really extract anything, but still primes the
	 * {@code resolve} method to know where things <em>should be</em> extracted,
	 * see {@link #dryExtract}.
	 */
	@Override
	public void extract() throws Exception
	{
		super.extract();
		s_jarProcessed = true;
	}

	/**
	 * Prepares the resolver, ignoring the passed string (ordinarily a script or
	 * rules); this resolver's rules are hardcoded.
	 */
	@Override
	public void prepareResolver(String v) throws Exception
	{
		m_prefix = compile("^pljava/([^/]+dir)(?![^/])").matcher("");
		m_fsepLength = getProperty("file.separator").length();
		m_lineSep = getProperty("line.separator");
	}

	/**
	 * Replaces a prefix {@code pljava/}<em>key</em> in a path to be extracted
	 * with the value of the {@code pgconfig.}<em>key</em> system property, or
	 * the result of invoking {@code pg_config} (or the exact executable named
	 * in the {@code pgconfig} system property, if present) with the option
	 * {@code --}<em>key</em>.
	 */
	@Override
	public String resolve(String storedPath, String platformPath)
	throws Exception
	{
		if ( m_prefix.reset(storedPath).lookingAt() )
		{
			int prefixLength = m_prefix.end();
			String key = m_prefix.group(1);
			String propkey = "pgconfig." + key;
			String replacement = getProperty(propkey);
			if ( null == replacement )
			{
				String pgc = getProperty("pgconfig", "pg_config");
				ProcessBuilder pb = new ProcessBuilder(pgc, "--"+key);
				pb.redirectError(ProcessBuilder.Redirect.INHERIT);
				Process proc = pb.start();
				byte[] output;
				try ( InputStream instream = proc.getInputStream() )
				{
					proc.getOutputStream().close();
					output = instream.readAllBytes();
				}
				finally
				{
					int status = proc.waitFor();
					if ( 0 != status )
					{
						System.err.println(
							"ERROR: pg_config status is "+status);
						System.exit(1);
					}
				}
				/*
				 * pg_config output is the saved value followed by one \n only.
				 * However, on Windows, the C library treats stdout as text mode
				 * by default, and pg_config does nothing to change that, so the
				 * single \n written by pg_config gets turned to \r\n before it
				 * arrives here. The earlier use of the trim() method papered
				 * over the problem, but trim() can remove too much. Simply have
				 * to assume that the string will end with line.separator, and
				 * remove that.
				 */
				replacement = defaultCharset().newDecoder()
					.decode(ByteBuffer.wrap(output, 0, output.length))
					.toString();
				assert replacement.endsWith(m_lineSep);
				replacement = replacement.substring(0,
					replacement.length() - m_lineSep.length());
				setProperty(propkey, replacement);
			}
			int plen = m_fsepLength - 1; /* original separator had length 1 */
			plen += prefixLength;
			replacement += platformPath.substring(plen);
			if ( -1 != storedPath.indexOf("/pljava-examples-") )
				s_examplesJar = replacement;
			else if ( storedPath.matches(
						"pljava/pkglibdir/(?:lib)?+pljava-so-.*") )
				s_sharedObject = replacement;
			if ( ! m_dryrun )
				return replacement;
			return null;
		}

		System.err.println("WARNING: extraneous jar entry not extracted: "
			+ storedPath);
		return null;
	}

	/*
	 * Members below this point represent the state and behavior of an instance
	 * of this class that is acting as a "Node" rather than as the JarX helper.
	 */

	/**
	 * True if the platform is determined to be Windows.
	 *<p>
	 * On Windows, {@link #forWindowsCRuntime forWindowsCRuntime} should be
	 * applied to any {@code ProcessBuilder} before invoking it; the details of
	 * the transformation applied by
	 * {@link #asPgCtlInvocation asPgCtlInvocation} change, and
	 * {@link #use_pg_ctl use_pg_ctl} may prove useful, as {@code pg_ctl} on
	 * Windows is able to drop administrative privileges that would otherwise
	 * prevent {@code postgres} from starting.
	 */
	public static final boolean s_isWindows =
		getProperty("os.name").startsWith("Windows");

	/**
	 * The first form of PostgreSQL JDBC driver connection URL found to be
	 * recognized by an available driver, or {@code URL_FORM_NONE}.
	 */
	public static final int s_urlForm;

	/**
	 * Value of {@link #s_urlForm s_urlForm} indicating no available JDBC driver
	 * was found to accept any of the supported connection URL forms.
	 */
	public static final int URL_FORM_NONE = -1;

	/**
	 * Value of {@link #s_urlForm s_urlForm} indicating an available JDBC driver
	 * reported accepting a connection URL in the PGJDBC form starting with
	 * {@code "jdbc:postgresql:"}.
	 */
	public static final int URL_FORM_PGJDBC = 0;

	/**
	 * Value of {@link #s_urlForm s_urlForm} indicating an available JDBC driver
	 * reported accepting a connection URL in the pgjdbc-ng form starting with
	 * {@code "jdbc:pgsql:"}.
	 */
	public static final int URL_FORM_PGJDBCNG = 1;

	/**
	 * A function to map an {@code SQLWarning} to a rough classification
	 * (info, warning) of its severity.
	 *<p>
	 * If the PGJDBC {@code PSQLWarning} class is available for access to the
	 * severity tag from the backend, "warning" will be returned if that tag is
	 * {@code WARNING}, and "info" will be returned in any other case. (The next
	 * more severe backup level is {@code ERROR}, which would not appear here as
	 * an {@code SQLWarning}.)
	 *<p>
	 * If the severity tag is not available, "info" will be returned if the
	 * class (leftmost two positions of SQLState) is 00, otherwise "warning".
	 */
	private static final Function<SQLWarning,String> s_toSeverity;

	private static String s_WARNING_localized = "WARNING";

	/**
	 * Changes the severity string used to recognize when the backend is sending
	 * a {@code WARNING}.
	 *<p>
	 * When the driver is PGJDBC, the classification done here of
	 * {@code SQLWarning} instances into actual warning messages or informative
	 * ones depends on a tag ("WARNING" in English) that the backend delivers
	 * in the local language. For the classification to happen correctly when
	 * a different language is selected, use this method to supply the string
	 * (for example, "PERINGATAN" in Indonesian) that the backend uses for
	 * warnings in that language.
	 */
	public static void set_WARNING_localized(String s)
	{
		s_WARNING_localized = requireNonNull(s);
	}

	static
	{
		String[] candidateURLs = { "jdbc:postgresql:", "jdbc:pgsql:x" };
		s_urlForm =
			IntStream.range(0, candidateURLs.length)
				.filter(i ->
					drivers().anyMatch(d ->
					{
						try
						{
							return d.acceptsURL(candidateURLs[i]);
						}
						catch ( SQLException e )
						{
							throw new ExceptionInInitializerError(e);
						}
					}))
				.findFirst()
				.orElse(URL_FORM_NONE);

		Function<SQLWarning,String> toSeverity = Node::toSeverityFallback;

		try
		{
			Class<?> psqlWarning =
				Class.forName("org.postgresql.util.PSQLWarning");
			Class<?> sErrMessage =
				Class.forName("org.postgresql.util.ServerErrorMessage");

			Lookup pub = publicLookup();

			MethodHandle getserrm =
				pub.findVirtual(psqlWarning, "getServerErrorMessage",
					methodType(sErrMessage));
			MethodHandle getSev =
				pub.findVirtual(sErrMessage, "getSeverity",
					methodType(String.class));

			MethodHandle h = explicitCastArguments(
				filterReturnValue(getserrm, getSev),
				methodType(String.class, Object.class));

			toSeverity = w ->
			{
				if ( psqlWarning.isInstance(w) )
				{
					try
					{
						String s = (String)h.invokeExact(psqlWarning.cast(w));
						if ( null == s  ||  s_WARNING_localized.equals(s) )
							return "warning";
						return "info";
					}
					catch ( Throwable t )
					{
						throw new UndeclaredThrowableException(t, t.getMessage());
					}
				}

				return toSeverityFallback(w);
			};
		}
		catch ( ReflectiveOperationException e )
		{
		}

		s_toSeverity = toSeverity;
	}

	private static String toSeverityFallback(SQLWarning w)
	{
		if ( w.getSQLState().startsWith("00") )
			return "info";
		else
			return "warning";
	}

	/**
	 * A state (see {@link #stateMachine stateMachine}) that expects nothing
	 * (if the driver is pgjdbc-ng) or a zero row count (if the driver is
	 * PGJDBC).
	 *<p>
	 * For some utility statements (such as {@code CREATE EXTENSION}) with no
	 * result, the pgjdbc-ng driver will produce no result, while the PGJDBC
	 * driver produces a zero count, as it would for a DML statement that did
	 * not affect any rows. This state handles either case.
	 *<p>
	 * When {@code URL_FORM_PGJDBCNG == s_urlForm}, this state consumes nothing
	 * and moves to the numerically next state. Otherwise (JDBC), it checks
	 * that the current object is a zero row count, consuming it and moving to
	 * the numerically next state if it is, returning false otherwise.
	 */
	public static final InvocationHandler NOTHING_OR_PGJDBC_ZERO_COUNT=(o,p,q)->
	{
		int myStateNum = (int)q[0];
		if ( URL_FORM_PGJDBCNG == s_urlForm )
			return -(1 + myStateNum);
		return 0 == as(Long.class, o) ? 1 + myStateNum : false;
	};

	/**
	 * Name of a "Node"; null for an ordinary Node instance.
	 */
	private final String m_name;

	/**
	 * A TCP port on {@code localhost} that was free when {@code get_new_node}
	 * was called, and is likeliest to still be free if {@code start} is then
	 * called without undue delay.
	 */
	private final int m_port;

	/**
	 * A temporary base directory chosen and created in {@code java.io.tmpdir}
	 * by {@code get_new_node} and removed by {@code clean_node}.
	 */
	private final Path m_basedir;

	/**
	 * A password generated at {@code get_new_node} time, and used by
	 * {@code init} as the database-superuser password passed to {@code initdb}.
	 */
	private final String m_password;

	/**
	 * The server process handle after a successful {@code start}
	 * via {@code pg_ctl}; null again after a successful {@code stop}.
	 *<p>
	 * If {@code pg_ctl} was not used, this will be null and {@code m_server}
	 * will have a value.
	 */
	private ProcessHandle m_serverHandle;

	/**
	 * The server process after a successful {@code start}; null again after a
	 * successful {@code stop}.
	 *<p>
	 * If {@code pg_ctl} was used to start the server, this will be null and
	 * {@code m_serverHandle} will have a value after {@code wait_for_pid_file}.
	 */
	private Process m_server;

	/**
	 * A count of connections, used to supply a distinct default
	 * {@code ApplicationName} per connection.
	 */
	private long m_connCount = 0;

	/**
	 * Whether to invoke {@code postgres} directly when  starting the server,
	 * or use {@code pg_ctl} to start and stop it.
	 *<p>
	 * On Windows, {@code pg_ctl} is able to drop administrator rights and
	 * start the server from an account that would otherwise trigger
	 * the server's refusal to start from a privileged account.
	 */
	private boolean m_usePostgres = true;

	/**
	 * A weakly-held collection of {@link Connection}s, so that any remaining
	 * unclosed when {@link #stop(UnaryOperator) stop} is called can be closed
	 * then.
	 *<p>
	 * Java takes care of removing {@code Connection}s from this map as they
	 * become unreachable. In case any become unreachable before being closed,
	 * both supported JDBC drivers have cleaner actions that will eventually
	 * close them.
	 */
	private final WeakHashMap<Connection,Void> m_connections;

	/**
	 * True during a {@link #stop(UnaryOperator) stop} call.
	 *<p>
	 * Used to prevent any new unclosed {@code Connection} being added to
	 * {@link m_connections m_connections} undetected.
	 */
	private boolean m_stopping = false;

	/**
	 * Identifying information for a "node" instance, or for the singleton
	 * extractor instance.
	 */
	@Override
	public String toString()
	{
		if ( null == m_name )
			return "Extractor instance";
		return "\"Node\": " + m_name;
	}

	/**
	 * Constructs an instance; all nulls for the parameters are passed by the
	 * static initializer to make the singleton extractor instance, and any
	 * other instance is constructed by {@code get_new_node} for controlling
	 * a PostgreSQL instance.
	 */
	private Node(String nodeName, int port, Path basedir, String password)
	{
		m_name = nodeName;
		m_port = port;
		m_basedir = basedir;
		m_password = password;
		m_connections = null == nodeName ? null : new WeakHashMap<>();
	}

	/**
	 * Returns a new {@code Node} that can be used to initialize and start a
	 * PostgreSQL instance.
	 *<p>
	 * Establishes a VM shutdown hook that will stop the server (if started)
	 * and recursively remove the <em>basedir</em> before the VM exits.
	 */
	public static Node get_new_node(String name) throws Exception
	{
		byte[] pwbytes = new byte [ 6 ];
		new Random().nextBytes(pwbytes);
		Node n = new Node(
			requireNonNull(name),
			get_free_port(),
			createTempDirectory("t_pljava_" + name + "_data"),
			Base64.getEncoder().encodeToString(pwbytes));
		Thread t =
			new Thread(() ->
				{
					try
					{
						n.stop();
						n.clean_node();
					}
					catch ( Exception e )
					{
						e.printStackTrace();
					}
				}, "Node " + name + " shutdown");
		Runtime.getRuntime().addShutdownHook(t);
		return n;
	}

	/**
	 * Returns a TCP port on the loopback interface that is free at the moment
	 * this method is called.
	 */
	public static int get_free_port() throws Exception
	{
		try (ServerSocket s = new ServerSocket(0, 0, getLoopbackAddress()))
		{
			return s.getLocalPort();
		}
	}

	/**
	 * Recursively removes the <em>basedir</em> and its descendants.
	 */
	public void clean_node() throws Exception
	{
		clean_node(false);
	}

	/**
	 * Recursively removes the <em>basedir</em> (unless <var>keepRoot</var>)
	 * and its descendants.
	 * @param keepRoot if true, the descendants are removed, but not the basedir
	 * itself.
	 */
	public void clean_node(boolean keepRoot) throws Exception
	{
		/*
		 * How can Java *still* not have a deleteTree()?
		 */
		ArrayDeque<Path> stk = new ArrayDeque<>();
		for ( Path p : (Iterable<Path>)walk(m_basedir)::iterator )
		{
			while ( ! stk.isEmpty()  &&  ! p.startsWith(stk.peek()) )
			{
				Path toDelete = stk.pop();
				try
				{
					deleteIfExists(toDelete);
				}
				catch ( AccessDeniedException e )
				{
					if (!toDelete.equals(data_dir().resolve("postmaster.pid")))
						throw e;
					/*
					 * See comments for stopViaPgCtl regarding this weirdness.
					 */
					Thread.sleep(500);
					deleteIfExists(toDelete);
				}
			}
			stk.push(p);
		}
		if ( keepRoot )
			stk.pollLast();
		for ( Path p : stk )
			deleteIfExists(p);
	}

	/**
	 * Processes the jar without really extracting, to compute
	 * the path mappings.
	 */
	private static void dryExtract() throws Exception
	{
		if ( s_jarProcessed )
			return;
		try
		{
			s_jarxHelper.m_dryrun = true;
			s_jarxHelper.extract();
		}
		finally
		{
			s_jarxHelper.m_dryrun = false;
		}
	}

	/**
	 * Given a path from the archive, or any path <em>resembling</em> one in
	 * the archive (that is, always {@code /} as the separator, and starting
	 * with {@code pljava/}<em>key</em> where {@code --}<em>key</em> is known
	 * to {@code pg_config}, returns the platform-specific path where it would
	 * be installed.
	 */
	private static String resolve(String archivePath) throws Exception
	{
		return s_jarxHelper.resolve(
			archivePath, Paths.get("", archivePath.split("/")).toString());
	}

	/**
	 * Returns the directory name to be used as the PostgreSQL data directory
	 * for this node.
	 */
	public Path data_dir()
	{
		return m_basedir.resolve("pgdata");
	}

	/**
	 * Like {@code init()} but returns an {@code AutoCloseable} that will
	 * recursively remove the files and directories under the <em>basedir</em>
	 * (but not the <em>basedir</em> itself) on the exit of a calling
	 * try-with-resources scope.
	 */
	public AutoCloseable initialized_cluster()
	throws Exception
	{
		return initialized_cluster(Map.of(), UnaryOperator.identity());
	}

	/**
	 * Like {@code init()} but returns an {@code AutoCloseable} that will
	 * recursively remove the files and directories under the <em>basedir</em>
	 * (but not the <em>basedir</em> itself) on the exit of a calling
	 * try-with-resources scope.
	 */
	public AutoCloseable initialized_cluster(Map<String,String> suppliedOptions)
	throws Exception
	{
		return initialized_cluster(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Like {@link #init(Map,UnaryOperator) init()} but returns
	 * an {@code AutoCloseable} that will
	 * recursively remove the files and directories under the <em>basedir</em>
	 * (but not the <em>basedir</em> itself) on the exit of a calling
	 * try-with-resources scope.
	 */
	public AutoCloseable initialized_cluster(
		UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		return initialized_cluster(Map.of(), tweaks);
	}

	/**
	 * Like {@link #init(Map,UnaryOperator) init()} but returns
	 * an {@code AutoCloseable} that will
	 * recursively remove the files and directories under the <em>basedir</em>
	 * (but not the <em>basedir</em> itself) on the exit of a calling
	 * try-with-resources scope.
	 */
	public AutoCloseable initialized_cluster(
		Map<String,String> suppliedOptions,
		UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		init(suppliedOptions, tweaks);
		return () ->
		{
			clean_node(true);
		};
	}

	/**
	 * Invokes {@code initdb} for the node, passing default options appropriate
	 * for this setting.
	 */
	public void init() throws Exception
	{
		init(Map.of(), UnaryOperator.identity());
	}

	/**
	 * Invokes {@code initdb} for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default.
	 */
	public void init(Map<String,String> suppliedOptions) throws Exception
	{
		init(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Invokes {@code initdb} for the node, passing default options appropriate
	 * for this setting, and {@linkplain #init(Map,UnaryOperator) tweaks} to be
	 * applied to the {@code ProcessBuilder} before it is started.
	 */
	public void init(UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		init(Map.of(), tweaks);
	}

	/**
	 * Invokes {@code initdb} for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default,
	 * and <em>tweaks</em> to be applied to the {@code ProcessBuilder}
	 * before it is started.
	 *<p>
	 * By default, {@code postgres} will be the name of the superuser, UTF-8
	 * will be the encoding, {@code auth-local} will be {@code peer} and
	 * {@code auth-host} will be {@code md5}. The initialization will skip
	 * {@code fsync} for speed rather than safety (if something goes wrong, just
	 * {@code clean_node()} and start over).
	 *<p>
	 * The {@code initdb} that will be run is the one in the {@code bindir}
	 * reported by {@code pg_config} (or set by {@code -Dpgconfig.bindir}).
	 * @param suppliedOptions a Map where each key is an option to initdb
	 * (for example, --encoding), and the value corresponds.
	 * @param tweaks a lambda applicable to the {@code ProcessBuilder} to
	 * further configure it. On Windows, the tweaks will be applied ahead of
	 * transformation of the arguments by
	 * {@link #forWindowsCRuntime forWindowsCRuntime}.
	 */
	public void init(
		Map<String,String> suppliedOptions,
		UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		dryExtract();
		/*
		 * For extract/install purposes, there is already a resolve() method
		 * that expands keys like pljava/bindir to pg_config --bindir output.
		 */
		String initdb = resolve("pljava/bindir/initdb");

		if ( s_isWindows )
		{
			/*
			 * This is irksome. The mingw64 postgresql package has both
			 * initdb.exe and initdb, a bash script that runs it under winpty.
			 * If the script were not there, the .exe suffix would be added
			 * implicitly, but with both there, we try to exec the bash script.
			 */
			Path p1 = Paths.get(initdb);
			Path p2 = Paths.get(initdb + ".exe");
			if ( exists(p1)  &&  exists(p2) )
				initdb = p2.toString();
		}

		Path pwfile = createTempFile(m_basedir, "pw", "");

		Map<String,String> options = new HashMap<>(suppliedOptions);
		options.putIfAbsent("--pgdata", data_dir().toString());
		options.putIfAbsent("--username", "postgres");
		options.putIfAbsent("--encoding", "utf-8");
		options.putIfAbsent("--pwfile", pwfile.toString());
		options.putIfAbsent("--auth-local", "peer");
		options.putIfAbsent("--auth-host", "md5");
		options.putIfAbsent("-N", null);

		String[] args =
			Stream.concat(
				Stream.of(initdb),
				options.entrySet().stream()
				.flatMap(e ->
					null == e.getValue()
					? Stream.of(e.getKey())
					: Stream.of(e.getKey(), e.getValue()))
			)
			.toArray(String[]::new);

		try
		{
			write(pwfile, List.of(m_password), US_ASCII);
			ProcessBuilder pb =
				new ProcessBuilder(args)
				.redirectOutput(INHERIT)
				.redirectError(INHERIT);
			pb = tweaks.apply(pb);

			if ( s_isWindows )
				pb = forWindowsCRuntime(pb);

			Process p = pb.start();
			p.getOutputStream().close();
			if ( 0 != p.waitFor() )
				throw new AssertionError(
					"Nonzero initdb result: " + p.waitFor());
		}
		finally
		{
			deleteIfExists(pwfile);
		}
	}

	/**
	 * Like {@code start()} but returns an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 */
	public AutoCloseable started_server()
	throws Exception
	{
		return started_server(Map.of(), UnaryOperator.identity());
	}

	/**
	 * Like {@code start()} but returns an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 */
	public AutoCloseable started_server(Map<String,String> suppliedOptions)
	throws Exception
	{
		return started_server(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Like {@link #start(Map,UnaryOperator) start()} but returns
	 * an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 *<p>
	 * Supplied <em>tweaks</em> will be applied to the {@code ProcessBuilder}
	 * used to start the server; if {@code pg_ctl} is being used, they will also
	 * be applied when running {@code pg_ctl stop} to stop it.
	 */
	public AutoCloseable started_server(UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		return started_server(Map.of(), tweaks);
	}

	/**
	 * Like {@link #start(Map,UnaryOperator) start()} but returns
	 * an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 *<p>
	 * Supplied <em>tweaks</em> will be applied to the {@code ProcessBuilder}
	 * used to start the server; if {@code pg_ctl} is being used, they will also
	 * be applied when running {@code pg_ctl stop} to stop it.
	 */
	public AutoCloseable started_server(
		Map<String,String> suppliedOptions,
		UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		start(suppliedOptions, tweaks);
		return () ->
		{
			stop(tweaks);
		};
	}

	/**
	 * Starts a PostgreSQL server for the node with default options appropriate
	 * for this setting.
	 */
	public void start() throws Exception
	{
		start(Map.of(), UnaryOperator.identity());
	}

	/**
	 * Starts a PostgreSQL server for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default.
	 */
	public void start(Map<String,String> suppliedOptions) throws Exception
	{
		start(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Starts a PostgreSQL server for the node, passing default options
	 * appropriate for this setting, and
	 * {@linkplain #start(Map,UnaryOperator) tweaks} to be
	 * applied to the {@code ProcessBuilder} before it is started.
	 */
	public void start(UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		start(Map.of(), tweaks);
	}

	/**
	 * Starts a PostgreSQL server for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default, and
	 * <em>tweaks</em> to be applied to the {@code ProcessBuilder} before it
	 * is started.
	 *<p>
	 * By default, the server will listen only on the loopback interface and
	 * not on any Unix-domain socket, on the port selected when this Node was
	 * created, and for a maximum of 16 connections. Its cluster name will be
	 * the name given to this Node, and fsync will be off to favor speed over
	 * durability. The log line prefix will be shortened to just the node name
	 * and (when connected) the {@code application_name}.
	 *<p>
	 * The server that will be run is the one in the {@code bindir}
	 * reported by {@code pg_config} (or set by {@code -Dpgconfig.bindir}).
	 *<p>
	 * If the server is PostgreSQL 10 or later, it is definitely ready to accept
	 * connections when this method returns. If not, it is highly likely to be
	 * ready, but no test connection has been made to confirm it.
	 * @param suppliedOptions a Map where the key is a configuration variable
	 * name as seen in {@code postgresql.conf} or passed to the server with
	 * {@code -c} and the value corresponds.
	 * @param tweaks a lambda applicable to the {@code ProcessBuilder} to
	 * further configure it. Under {@link #use_pg_ctl use_pg_ctl(true)}, the
	 * tweaks are applied after the arguments have been transformed by
	 * {@link #asPgCtlInvocation asPgCtlInvocation}. On Windows, they are
	 * applied ahead of transformation of the arguments by
	 * {@link #forWindowsCRuntime forWindowsCRuntime}.
	 */
	public void start(
		Map<String,String> suppliedOptions,
		UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		if ( null != m_server  &&  m_server.isAlive() )
			throw new IllegalStateException(
				"node \"" + m_name + "\" is already running");

		if ( null != m_serverHandle  &&  m_serverHandle.isAlive() )
			throw new IllegalStateException(
				"node \"" + m_name + "\" is already running");

		dryExtract();

		Stream<String> cmd = Stream.of(resolve("pljava/bindir/postgres"));

		Map<String,String> options = new HashMap<>(suppliedOptions);
		options.putIfAbsent("data_directory", data_dir().toString());
		options.putIfAbsent("listen_addresses",
			getLoopbackAddress().getHostAddress());
		options.putIfAbsent("port", "" + m_port);
		options.putIfAbsent("unix_socket_directories", "");
		options.putIfAbsent("max_connections", "16");
		options.putIfAbsent("fsync", "off");
		options.putIfAbsent("cluster_name", m_name);
		options.putIfAbsent("log_line_prefix",
			m_name.replace("%", "%%") + ":%q%a:");

		String[] args =
			Stream.concat(
				cmd,
				options.entrySet().stream()
				.flatMap(e ->
					"data_directory".equals(e.getKey())
					? Stream.of("-D", e.getValue())
					: Stream.of("-c", e.getKey() + "=" + e.getValue())
				)
			)
			.toArray(String[]::new);

		ProcessBuilder pb =
			new ProcessBuilder(args)
			.redirectOutput(INHERIT)
			.redirectError(INHERIT);

		if ( ! m_usePostgres )
			pb = asPgCtlInvocation(pb);

		pb = tweaks.apply(pb);

		if ( s_isWindows )
			pb = forWindowsCRuntime(pb);

		Process p = pb.start();
		p.getOutputStream().close();
		try
		{
			wait_for_pid_file(p, p.info());
			if ( m_usePostgres )
				m_server = p; // else wait_for_pid_file has set m_serverHandle
		}
		finally
		{
			if ( m_server == p )
				return;
			if ( p.isAlive() )
				p.destroy();
		}
	}


	/**
	 * Stops the server instance associated with this Node.
	 *<p>
	 * Has the effect of {@link #stop(UnaryOperator) stop(tweaks)} without
	 * any tweaks.
	 */
	public void stop() throws Exception
	{
		stop(UnaryOperator.identity());
	}

	/**
	 * Stops the server instance associated with this Node.
	 *<p>
	 * No effect if it has not been started or has already been stopped, but
	 * a message to standard error is logged if the server had been started and
	 * the process is found to have exited unexpectedly.
	 * @param tweaks tweaks to apply to a ProcessBuilder; unused unless
	 * {@code pg_ctl} will be used to stop the server. When used, they are
	 * applied ahead of the transformation of the arguments by
	 * {@link #forWindowsCRuntime forWindowsCRuntime} used on Windows.
	 */
	public void stop(UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		if ( null == ( m_usePostgres ? m_server : m_serverHandle ) )
			return;

		try
		{
			Connection[] connections;

			synchronized ( this )
			{
				m_stopping = true;
				connections = // Java >= 10: use a List and List.copyOf
					m_connections.keySet().stream().toArray(Connection[]::new);
				m_connections.clear();
			}

			for ( Connection c : connections )
			{
				try
				{
					c.close();
				}
				catch ( Exception e )
				{
				}
			}

			if ( ! m_usePostgres )
			{
				stopViaPgCtl(tweaks);
				return;
			}
			if ( m_server.isAlive() )
			{
				m_server.destroy();
				m_server.waitFor();
				m_server = null;
				return;
			}
			System.err.println("Server had already exited with status " +
				m_server.exitValue());
			m_server = null;
		}
		finally
		{
			synchronized ( this )
			{
				m_stopping = false;
			}
		}
	}

	private void stopViaPgCtl(UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		if ( ! m_serverHandle.isAlive() )
		{
			System.err.println("Server had already exited");
			m_serverHandle = null;
			return;
		}

		String pg_ctl = resolve("pljava/bindir/pg_ctl");
		ProcessBuilder pb = new ProcessBuilder(
			pg_ctl, "stop", "-D", data_dir().toString(), "-m", "fast")
			.redirectOutput(INHERIT)
			.redirectError(INHERIT);
		pb = tweaks.apply(pb);

		if ( s_isWindows )
			pb = forWindowsCRuntime(pb);

		Process p = pb.start();
		p.getOutputStream().close();

		if ( 0 != p.waitFor() )
		{
			/*
			 * Here is a complication. On Windows, pg_ctl suffers from a race
			 * condition that can occasionally cause it to exit with a nonzero
			 * status and a "permission denied" message about postmaster.pid,
			 * while the server is otherwise successfully stopped:
			 * www.postgresql.org/message-id/16922.1520722108%40sss.pgh.pa.us
			 *
			 * Without capturing the stderr of the process (too much bother), we
			 * won't know for sure if that is the message, but if the exit value
			 * was nonzero, just wait a bit and see if the server has gone away;
			 * if it has, don't worry about it.
			 */
			Thread.sleep(1000);
			if ( m_serverHandle.isAlive() )
				throw new AssertionError(
					"Nonzero pg_ctl stop result: " + p.waitFor());
		}
		m_serverHandle = null;
	}

	/**
	 * Sets whether to use {@code pg_ctl} to start and stop the server
	 * (if true), or start {@code postgres} and stop it directly (if false,
	 * the default).
	 *<p>
	 * On Windows, {@code pg_ctl} is able to drop administrator rights and
	 * start the server from an account that would otherwise trigger
	 * the server's refusal to start from a privileged account.
	 */
	public void use_pg_ctl(boolean setting)
	{
		if ( null != m_server || null != m_serverHandle )
			throw new IllegalStateException(
				"use_pg_ctl may not be called while server is started");
		m_usePostgres = ! setting;
	}

	/**
	 * Returns a {@code Connection} to the server associated with this Node,
	 * using default properties appropriate for this setting.
	 */
	public Connection connect() throws Exception
	{
		return connect(new Properties());
	}

	/**
	 * Returns a {@code Connection} to the server associated with this Node,
	 * with <em>suppliedProperties</em> overriding or supplementing the ones
	 * that would be passed by default.
	 */
	public Connection connect(Map<String,String> suppliedProperties)
	throws Exception
	{
		Properties p = new Properties();
		p.putAll(suppliedProperties);
		return connect(p);
	}

	/**
	 * Returns a {@code Connection} to the server associated with this Node,
	 * with supplied properties <em>p</em> overriding or supplementing the ones
	 * that would be passed by default.
	 *<p>
	 * By default, the connection is to the {@code postgres} database as the
	 * {@code postgres} user, using the password internally generated for this
	 * node, and with an {@code application_name} generated from a counter of
	 * connections for this node.
	 */
	public Connection connect(Properties p) throws Exception
	{
		String url;
		String dbNameKey;
		String appNameKey;

		switch ( s_urlForm )
		{
		case URL_FORM_PGJDBC:
			url = "jdbc:postgresql://localhost:" + m_port + '/';
			dbNameKey = "PGDBNAME";
			appNameKey = "ApplicationName";
			break;
		case URL_FORM_PGJDBCNG:
			url = "jdbc:pgsql://localhost:" + m_port + '/';
			dbNameKey = "database.name";
			appNameKey = "application.name";
			break;
		default:
			throw new UnsupportedOperationException(
				"no recognized JDBC driver found to connect to the node");
		}

		p = (Properties)p.clone();
		p.putIfAbsent(dbNameKey, "postgres");
		p.putIfAbsent("user", "postgres");
		p.putIfAbsent("password", m_password);
		p.computeIfAbsent(appNameKey, o -> "Conn" + (m_connCount++));

		if ( URL_FORM_PGJDBCNG == s_urlForm )
		{
			/*
			 * Contrary to its documentation, pgjdbc-ng does *not* accept a URL
			 * with the database name omitted. It is no use having it in the
			 * properties here; it must be appended to the URL.
			 */
			url += encode(p.getProperty(dbNameKey), "UTF-8");
		}

		Connection c = getConnection(url, p);

		synchronized ( this )
		{
			if ( m_stopping )
			{
				try
				{
					throw new IllegalStateException(
						"Node " + m_name + " is being stopped");
				}
				finally
				{
					c.close(); // add any exception as 'suppressed' to above
				}
			}
			m_connections.put(c, null);
			return c;
		}
	}

	/**
	 * Sets a configuration variable on the server.
	 *<p>
	 * This deserves a convenience method because the most familiar PostgreSQL
	 * syntax for SET doesn't lend itself to parameterization.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> setConfig(
		Connection c, String settingName, String newValue, boolean isLocal)
	throws Exception
	{
		PreparedStatement ps =
			c.prepareStatement("SELECT pg_catalog.set_config(?,?,?)");
		ps.setString(1, settingName);
		ps.setString(2, newValue);
		ps.setBoolean(3, isLocal);
		return q(ps, ps::execute);
	}

	/**
	 * Loads PL/Java (with a {@code LOAD} command,
	 * not {@code CREATE EXTENSION}).
	 *<p>
	 * This was standard procedure in PostgreSQL versions that pre-dated the
	 * extension support. It is largely obsolete with the advent of
	 * {@code CREATE EXTENSION}, but still has one distinct use case:
	 * this is what will work if you do not have administrative access
	 * to install PL/Java's files in the standard directories where
	 * {@code CREATE EXTENSION} expects them, but can only place them in some
	 * other location the server can read. Then you simply have to make sure
	 * that {@code pljava.module_path} is set correctly to locate the jar files,
	 * and give the correct shared-object path to {@code LOAD} (which this
	 * method does).
	 *<p>
	 * It is also useful to see better diagnostics if something is going wrong,
	 * as PostgreSQL severely suppresses diagnostic messages during
	 * {@code CREATE EXTENSION}.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> loadPLJava(Connection c) throws Exception
	{
		dryExtract();
		Statement s = c.createStatement();
		String whatToLoad = s_sharedObject;

		/*
		 * MinGW-w64 does not fail if the .lib suffix is left in place, but
		 * MSVC does, and MinGW-w64 also allows it to be removed.
		 */
		if ( s_isWindows )
			whatToLoad = whatToLoad.replaceFirst("\\.lib$", "");

		String sql = "LOAD " + s.enquoteLiteral(whatToLoad);
		return q(s, () -> s.execute(sql));
	}

	/**
	 * Installs a jar.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> installJar(
		Connection c, String uri, String jarName, boolean deploy)
	throws Exception
	{
		PreparedStatement ps =
			c.prepareStatement("SELECT sqlj.install_jar(?,?,?)");
		ps.setString(1, uri);
		ps.setString(2, jarName);
		ps.setBoolean(3, deploy);
		return q(ps, ps::execute);
	}

	/**
	 * Removes a jar.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> removeJar(
		Connection c, String jarName, boolean undeploy)
	throws Exception
	{
		PreparedStatement ps =
			c.prepareStatement("SELECT sqlj.remove_jar(?,?)");
		ps.setString(1, jarName);
		ps.setBoolean(2, undeploy);
		return q(ps, ps::execute);
	}

	/**
	 * Sets the class path for a schema.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> setClasspath(
		Connection c, String schema, String... jarNames)
	throws Exception
	{
		PreparedStatement ps =
			c.prepareStatement("SELECT sqlj.set_classpath(?,?)");
		ps.setString(1, schema);
		ps.setString(2, String.join(":", jarNames));
		return q(ps, ps::execute);
	}

	/**
	 * Appends a jar to a schema's class path if not already included.
	 * @return a {@linkplain #q(Statement,Callable) result stream} that
	 * includes, on success, a one-column {@code void} result set with a single
	 * row if the jar was added to the path, and no rows if the jar was already
	 * included.
	 */
	public static Stream<Object> appendClasspathIf(
		Connection c, String schema, String jarName)
	throws Exception
	{
		PreparedStatement ps = c.prepareStatement(
			"SELECT" +
			" sqlj.set_classpath(" +
			"  schema," +
			"  pg_catalog.concat_ws(" +
			"   ':'," +
			"   VARIADIC oldpath OPERATOR(pg_catalog.||) ARRAY[jar]" +
			"  )" +
			" )" +
			"FROM" +
			" (VALUES (?, CAST (? AS pg_catalog.text))) AS p(schema, jar)," +
			" COALESCE(" +
			"  pg_catalog.regexp_split_to_array(" +
			"   sqlj.get_classpath(schema)," +
			"  ':'" +
			"  )," +
			"  CAST (ARRAY[] AS pg_catalog.text[])" +
			" ) AS t(oldpath)" +
			"WHERE" +
			" jar OPERATOR(pg_catalog.<>) ALL (oldpath)"
		);
		ps.setString(1, schema);
		ps.setString(2, jarName);
		return q(ps, ps::execute);
	}

	/**
	 * Executes some arbitrary SQL
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> q(Connection c, String sql) throws Exception
	{
		Statement s = c.createStatement();
		return q(s, () -> s.execute(sql));
	}

	/**
	 * Produces a {@code Stream} of the (in JDBC, possibly multiple) results
	 * from some {@code execute} method on a {@code Statement}.
	 *<p>
	 * This is how, for example, to prepare, then examine the results of, a
	 * {@code PreparedStatement}:
	 *<pre>
	 * PreparedStatement ps = conn.prepareStatement("select foo(?,?)");
	 * ps.setInt(1, 42);
	 * ps.setString(2, "surprise!");
	 * q(ps, ps::execute);
	 *</pre>
	 *<p>
	 * Each result in the stream will be an instance of one of:
	 * {@code ResultSet}, {@code Long} (an update count, positive or zero),
	 * {@code SQLWarning}, or some other {@code SQLException}. A warning or
	 * exception may have others chained to it, which its own {@code iterator}
	 * or {@code forEach} methods should be used to traverse; or, use
	 * {@code flatMap(}{@link #flattenDiagnostics Node::flattenDiagnostics}) to
	 * obtain a stream presenting each diagnostic in a chain in turn. The
	 * {@code Callable} interface supplying the work to be done allows any
	 * checked exception, but any {@code Throwable} outside the
	 * {@code SQLException} hierarchy will simply be rethrown from here rather
	 * than delivered in the stream. Any {@code Throwable} thrown by
	 * <em>work</em> will result in the {@code Statement} being closed.
	 * Otherwise, it remains open until the returned stream is closed.
	 *<p>
	 * Exists mainly to encapsulate the rather fiddly logic of extracting that
	 * sequence of results using the {@code Statement} API.
	 * @param s the Statement from which to extract results
	 * @param work a Callable that will invoke one of the Statement's execute
	 * methods returning a boolean that indicates whether the first result is a
	 * ResultSet. Although the Callable interface requires the boolean result to
	 * be boxed, it must not return null.
	 * @return a Stream as described above.
	 */
	public static Stream<Object> q(final Statement s, Callable<Boolean> work)
	throws Exception
	{
		final Object[] nextHolder = new Object [ 1 ];
		Object seed;
		boolean isResultSet;

		/*
		 * The Statement must not be closed in a finally, or a
		 * try-with-resources, because if successful it needs to remain open
		 * as long as the result stream is being read. It will be closed when
		 * the stream is.
		 *
		 * However, in any exceptional exit, the Statement must be closed here.
		 */
		try
		{
			isResultSet = work.call();
		}
		catch (Throwable t)
		{
			s.close();
			if ( t instanceof SQLException )
				return Stream.of(t);
			throw t;
		}

		final Supplier<Object> resultSet = () ->
		{
			try
			{
				return s.getResultSet();
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		final Supplier<Object> updateCount = () ->
		{
			try
			{
				long count = s.getLargeUpdateCount();
				return ( -1 == count ) ? null : count;
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		final Supplier<Object> warnings = () ->
		{
			try
			{
				SQLWarning w = s.getWarnings();
				if ( null != w )
				{
					try
					{
						s.clearWarnings();
					}
					catch ( SQLException e )
					{
						nextHolder [ 0 ] = e;
					}
				}
				return w;
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		/*
		 * First get warnings, if any.
		 * There is a remote chance this can return an exception rather than a
		 * warning, an even more remote chance it returns a warning and leaves
		 * an exception in nextHolder.
		 * Only if it did neither is there any point in proceeding to get an
		 * update count or result set.
		 * If we do, and there was a warning, we use the warning as the seed and
		 * save the first update count or result set in nextHolder.
		 */
		seed = warnings.get();
		if ( (null == seed || seed instanceof SQLWarning)
			&& null == nextHolder [ 0 ] )
		{
			Object t;
			if ( isResultSet )
				t = resultSet.get();
			else
				t = updateCount.get();
			if ( null == seed )
				seed = t;
			else
				nextHolder [ 0 ] = t;
		}

		UnaryOperator<Object> next = o ->
		{
			if ( o instanceof SQLException && !(o instanceof SQLWarning) )
				return null;

			o = nextHolder [ 0 ];
			if ( null != o )
			{
				nextHolder [ 0 ] = null;
				return o;
			}

			o = warnings.get();
			if ( null != o )
				return o;

			try
			{
				if ( s.getMoreResults() )
					return resultSet.get();
				return updateCount.get();
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		return Stream.iterate(seed, Objects::nonNull, next)
			.onClose(() ->
				{
					try
					{
						s.close();
					}
					catch ( SQLException e )
					{
					}
				}
			);
	}

	/**
	 * Analogously to {@link #q(Statement,Callable) q(Statement,...)}, produces
	 * a {@code Stream} with an element for each row of a {@code ResultSet},
	 * interleaved with any {@code SQLWarning}s reported on the result set, or
	 * an {@code SQLException} if one is thrown.
	 *<p>
	 * This is supplied chiefly for use driving a
	 * {@link #stateMachine state machine} to verify
	 * contents of a result set. For each row, the element in the stream will be
	 * an instance of {@code Long}, counting up from 1 (intended to match the
	 * result set's {@code getRow} but without relying on it, as JDBC does not
	 * require every implementation to support it). By itself, of course, this
	 * does not convey any of the content of the row; the lambdas representing
	 * the machine states should close over the result set and query it for
	 * content, perhaps not even using the object supplied here (except to
	 * detect when it is a warning or exception rather than a row number).
	 * The row position of the result set will have been updated, and should not
	 * be otherwise modified when this method is being used to walk through the
	 * results.
	 *<p>
	 * For the same reason, don't try any funny business like sorting the stream
	 * in any way. The {@code ResultSet} will only be read forward, and each row
	 * only once. Simple filtering, {@code dropWhile}/{@code takeWhile}, and so
	 * on will work, but may be more conveniently rolled into the design of a
	 * {@link #stateMachine state machine}, as nearly any use of a
	 * {@code ResultSet} can throw {@code SQLException} and therefore isn't
	 * convenient in the stream API.
	 *<p>
	 * Passing this result to {@code qp} as if it came from a {@code Statement}
	 * could lead to confusion, as the {@code Long} elements would be printed as
	 * update counts rather than row numbers.
	 * @param rs a ResultSet
	 * @return a Stream as described above
	 */
	public static Stream<Object> q(final ResultSet rs)
	throws Exception
	{
		final Object[] nextHolder = new Object [ 1 ];
		final long[] nextRowNumber = new long [ 1 ];
		nextRowNumber [ 0 ] = 1L;
		Object seed;

		/*
		 * The ResultSet must not be closed in a finally, or a
		 * try-with-resources, because if successful it needs to remain open
		 * as long as the result stream is being read. It will be closed when
		 * the stream is.
		 *
		 * However, in any exceptional exit, the ResultSet must be closed here.
		 */

		final Supplier<Object> row = () ->
		{
			try
			{
				if ( rs.next() )
					return nextRowNumber [ 0 ] ++;
				return null;
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		final Supplier<Object> warnings = () ->
		{
			try
			{
				SQLWarning w = rs.getWarnings();
				if ( null != w )
				{
					try
					{
						rs.clearWarnings();
					}
					catch ( SQLException e )
					{
						nextHolder [ 0 ] = e;
					}
				}
				return w;
			}
			catch ( SQLException e )
			{
				return e;
			}
		};

		/*
		 * First get warnings, if any.
		 * There is a remote chance this can return an exception rather than a
		 * warning, an even more remote chance it returns a warning and leaves
		 * an exception in nextHolder.
		 * Only if it did neither is there any point in proceeding to get a row.
		 * If we do, and there was a warning, we use the warning as the seed and
		 * save the first row in nextHolder.
		 */
		seed = warnings.get();
		if ( (null == seed || seed instanceof SQLWarning)
			&& null == nextHolder [ 0 ] )
		{
			Object t = row.get();
			if ( null == seed )
				seed = t;
			else
				nextHolder [ 0 ] = t;
		}

		UnaryOperator<Object> next = o ->
		{
			if ( o instanceof SQLException && !(o instanceof SQLWarning) )
				return null;

			o = nextHolder [ 0 ];
			if ( null != o )
			{
				nextHolder [ 0 ] = null;
				return o;
			}

			o = warnings.get();
			if ( null != o )
				return o;

			return row.get();
		};

		return Stream.iterate(seed, Objects::nonNull, next)
			.onClose(() ->
				{
					try
					{
						rs.close();
					}
					catch ( SQLException e )
					{
					}
				}
			);
	}

	/**
	 * Produces a {@code Stream} with an element for each column of a
	 * {@code ResultSet}.
	 *<p>
	 * This is another convenience method for use chiefly in driving a
	 * {@link #stateMachine state machine} to check per-column values
	 * or metadata for a {@code ResultSet}. It is, in fact, nothing other than
	 * {@code IntStream.rangeClosed(1, rsmd.getColumnCount()).boxed()} but typed
	 * as {@code Stream<Object>}.
	 *<p>
	 * As with {@link #q(ResultSet) q(ResultSet)}, the column number supplied
	 * here conveys no actual column data or metadata. The lambdas representing
	 * the machine states should close over the {@code ResultSetMetaData} or
	 * corresponding {@code ResultSet} object, or both, and use the column
	 * number from this stream to index them.
	 * @param rsmd a ResultSetMetaData object
	 * @return a Stream as described above
	 */
	public static Stream<Object> q(final ResultSetMetaData rsmd)
	throws Exception
	{
		return
			IntStream.rangeClosed(1, rsmd.getColumnCount())
			.mapToObj(i -> (Object)i);
	}

	/**
	 * Produces a {@code Stream} with an element for each parameter of a
	 * {@code PreparedStatement}.
	 *<p>
	 * This is another convenience method for use chiefly in driving a
	 * {@link #stateMachine state machine} to check per-parameter metadata.
	 * It is, in fact, nothing other than
	 * {@code IntStream.rangeClosed(1, rsmd.getParameterCount()).boxed()}
	 * but typed as {@code Stream<Object>}.
	 *<p>
	 * As with {@link #q(ResultSet) q(ResultSet)}, the column number supplied
	 * here conveys no actual parameter metadata. The lambdas representing
	 * the machine states should close over the {@code ParameterMetaData} object
	 * and use the parameter number from this stream to index it.
	 * @param pmd a ParameterMetaData object
	 * @return a Stream as described above
	 */
	public static Stream<Object> q(final ParameterMetaData pmd)
	throws Exception
	{
		return
			IntStream.rangeClosed(1, pmd.getParameterCount())
			.mapToObj(i -> (Object)i);
	}

	/**
	 * Executes some arbitrary SQL and passes
	 * the {@linkplain #q(Statement,Callable) result stream}
	 * to {@link #qp(Stream)} for printing to standard output.
	 */
	public static void qp(Connection c, String sql) throws Exception
	{
		qp(q(c, sql));
	}

	/**
	 * Invokes some {@code execute} method on a {@code Statement} and passes
	 * the {@linkplain #q(Statement,Callable) result stream}
	 * to {@link #qp(Stream)} for printing to standard output.
	 *<p>
	 * This is how, for example, to prepare, then print the results of, a
	 * {@code PreparedStatement}:
	 *<pre>
	 * PreparedStatement ps = conn.prepareStatement("select foo(?,?)");
	 * ps.setInt(1, 42);
	 * ps.setString(2, "surprise!");
	 * qp(ps, ps::execute);
	 *</pre>
	 * The {@code Statement} will be closed.
	 */
	public static void qp(Statement s, Callable<Boolean> work) throws Exception
	{
		qp(q(s, work));
	}

	/**
	 * Returns true if the examples jar includes the
	 * {@code org.postgresql.pljava.example.saxon.S9} class (meaning the
	 * appropriate Saxon jar must be installed and on the classpath first before
	 * the examples jar can be deployed, unless {@code check_function_bodies}
	 * is {@code off} to skip dependency checking).
	 */
	public static boolean examplesNeedSaxon() throws Exception
	{
		dryExtract();
		try ( JarFile jf = new JarFile(s_examplesJar) )
		{
			return jf.stream().anyMatch(e ->
				"org/postgresql/pljava/example/saxon/S9.class"
				.equals(e.getName()));
		}
	}

	/**
	 * Installs the examples jar, under the name {@code examples}.
	 *<p>
	 * The jar is specified by a {@code file:} URI and the path is the one where
	 * this installer installed (or would have installed) it.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> installExamples(Connection c, boolean deploy)
	throws Exception
	{
		dryExtract();
		String uri = Paths.get(s_examplesJar).toUri()
			.toString().replaceFirst("^file:///", "file:/");
		return installJar(c, uri, "examples", deploy);
	}

	/**
	 * Installs the examples jar, under the name {@code examples}, and appends
	 * it to the class path for schema {@code public}.
	 *<p>
	 * The return of a concatenated result stream from two consecutive
	 * statements might be likely to fail in cases where the first
	 * statement has any appreciable data to return, but the drivers seem to
	 * handle it at least in this case where each statement just returns one
	 * row / one column of {@code void}. And it is convenient.
	 * @return a combined {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statements
	 */
	public static Stream<Object> installExamplesAndPath(
		Connection c, boolean deploy)
	throws Exception
	{
		Stream<Object> s1 = installExamples(c, deploy);
		Stream<Object> s2 = appendClasspathIf(c, "public", "examples");
		return Stream.concat(s1, s2);
	}

	/**
	 * Installs a Saxon jar under the name {@code saxon}, given the path to a
	 * local Maven repo and the needed version of Saxon, assuming the jar has
	 * been downloaded there already.
	 * @return a {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statement
	 */
	public static Stream<Object> installSaxon(
		Connection c, String repo, String version)
	throws Exception
	{
		Path p = Paths.get(
			repo, "net", "sf", "saxon", "Saxon-HE", version,
			"Saxon-HE-" + version + ".jar");
		return installJar(c, "file:" + p, "saxon", false);
	}

	/**
	 * Installs a Saxon jar under the name {@code saxon}, and appends it to the
	 * class path for schema {@code public}.
	 * @return a combined {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statements
	 */
	public static Stream<Object> installSaxonAndPath(
		Connection c, String repo, String version)
	throws Exception
	{
		Stream<Object> s1 = installSaxon(c, repo, version);
		Stream<Object> s2 = appendClasspathIf(c, "public", "saxon");
		return Stream.concat(s1, s2);
	}

	/**
	 * A four-fer: installs Saxon, adds it to the class path, then installs the
	 * examples jar, and updates the classpath to include both.
	 * @param repo the base directory of a local Maven repository into which the
	 * Saxon jar has been downloaded
	 * @param version the needed version of Saxon
	 * @param deploy whether to run the example jar's deployment code
	 * @return a combined {@linkplain #q(Statement,Callable) result stream} from
	 * executing the statements
	 */
	public static Stream<Object> installSaxonAndExamplesAndPath(
		Connection c, String repo, String version, boolean deploy)
	throws Exception
	{
		Stream<Object> s1 = installSaxonAndPath(c, repo, version);
		Stream<Object> s2 = installExamplesAndPath(c, deploy);
		return Stream.concat(s1, s2);
	}

	/**
	 * A flat-mapping function to expand any {@code SQLException} or
	 * {@code SQLWarning} instance in a result stream into the stream of
	 * possibly multiple linked diagnostics and causes in the encounter order
	 * of the {@code SQLException} iterator.
	 *<p>
	 * Any other object is returned in a singleton stream.
	 *<p>
	 * To flatten just the chain of {@code SQLWarning} or {@code SQLException}
	 * but with each of those retaining its own list of {@code cause}s, see
	 * {@link #semiFlattenDiagnostics semiFlattenDiagnostics}.
	 */
	public static Stream<Object> flattenDiagnostics(Object oneResult)
	{
		if ( oneResult instanceof SQLException )
		{
			Spliterator<Object> s = spliteratorUnknownSize(
				((SQLException)oneResult).iterator(),
				IMMUTABLE | NONNULL | ORDERED);
			return stream(s, false);
		}
		return Stream.of(oneResult);
	}

	/**
	 * A flat-mapping function to expand any {@code SQLException} or
	 * {@code SQLWarning} instance in a result stream into the stream of
	 * possibly multiple linked diagnostics in the order produced by
	 * {@code getNextException} or {@code getNextWarning}.
	 *<p>
	 * Unlike {@code flattenDiagnostics}, this method does not descend into
	 * chains of causes; those may be retrieved in the usual way from the
	 * throwables returned on this stream.
	 *<p>
	 * Any other object is returned in a singleton stream.
	 */
	public static Stream<Object> semiFlattenDiagnostics(Object oneResult)
	{
		UnaryOperator<Object> next;

		if ( oneResult instanceof SQLWarning )
			next = o -> ((SQLWarning)o).getNextWarning();
		else if ( oneResult instanceof SQLException )
			next = o -> ((SQLException)o).getNextException();
		else
			return Stream.of(oneResult);

		return Stream.iterate(oneResult, Objects::nonNull, next);
	}

	/**
	 * Prints streamed results of a {@code Statement} in (somewhat) readable
	 * fashion.
	 *<p>
	 * Uses {@code writeXml} of {@code WebRowSet}, which is very verbose, but
	 * about the easiest way to readably dump a {@code ResultSet} in just a
	 * couple lines of code.
	 *<p>
	 * The supplied stream is flattened (see
	 * {@link #semiFlattenDiagnostics semiFlattenDiagnostics}) so that any
	 * chained {@code SQLException}s or {@code SQLWarning}s are printed
	 * in sequence.
	 */
	public static void qp(Stream<Object> s) throws Exception
	{
		qp(s, Node::semiFlattenDiagnostics);
	}

	/**
	 * Prints streamed results of a {@code Statement} in (somewhat) readable
	 * fashion, with a choice of flattener for diagnostics.
	 *<p>
	 * For <em>flattener</em>, see {@link flattenDiagnostics flattenDiagnostics}
	 * or {@link semiFlattenDiagnostics semiFlattenDiagnostics}.
	 */
	public static void qp(
		Stream<Object> s, Function<Object,Stream<Object>> flattener)
	throws Exception
	{
		try ( Stream<Object> flat = s.flatMap(flattener) )
		{
			for ( Object o : (Iterable<Object>)flat::iterator )
				qp(o);
		}
	}

	/**
	 * Overload of {@code qp} for direct application to any one {@code Object}
	 * obtained from a result stream.
	 *<p>
	 * Simply applies the specialized treatment appropriate to the class of
	 * the object.
	 */
	public static void qp(Object o) throws Exception
	{
		if ( o instanceof ResultSet )
		{
			try (ResultSet rs = (ResultSet)o)
			{
				qp(rs);
			}
		}
		else if ( o instanceof Long )
			System.out.println("<success rows='" + o + "'/>");
		else if ( o instanceof Throwable )
			qp((Throwable)o);
		else
			System.out.println("<!-- unexpected "
				+ o.getClass().getName()
				+ " in result stream -->");
	}

	/**
	 * Prints an object in the manner of {@link #qp(Object) qp}, but in a way
	 * suitable for use in {@link Stream#peek Stream.peek}.
	 *<p>
	 * If <em>o</em> is a {@code ResultSet}, only its metadata will be printed;
	 * its position will not be disturbed and it will not be closed. This method
	 * throws no checked exceptions, as the {@code Stream} API requires; any
	 * that is caught will be printed as if by {@link #qp(Throwable) qp}.
	 */
	public static void peek(Object o)
	{
		try
		{
			int[] dims = voidResultSetDims(o, true); // only peek

			if ( null != dims )
			{
				System.out.printf(voidResultSet, dims[0], dims[1]);
				return;
			}

			if ( o instanceof ResultSet )
				qp(((ResultSet)o).getMetaData());
			else
				qp(o);
		}
		catch ( Exception e )
		{
			qp(e);
		}
	}

	private static final String voidResultSet = "<void rows='%d' cols='%d'/>%n";

	/**
	 * Overload of {@code qp} for direct application to a {@code ResultSet}.
	 *<p>
	 * Sometimes one has a {@code ResultSet} that didn't come from executing
	 * a query, such as from a JDBC metadata method. This prints it the same way
	 * {@code qp} on a query result would. The {@code ResultSet} is not closed
	 * (but will have been read through the last row).
	 *<p>
	 * A result set with no columns of type other than {@code void} will be
	 * printed in an abbreviated form, showing its number of rows and columns
	 * as reported by {@link #voidResultSetDims voidResultSetDims}.
	 */
	public static void qp(ResultSet rs) throws Exception
	{
		int[] dims = voidResultSetDims(rs);

		if ( null != dims )
		{
			System.out.printf(voidResultSet, dims[0], dims[1]);
			return;
		}

		WebRowSet wrs = RowSetProvider.newFactory().createWebRowSet();
		try
		{
			wrs.populate(rs);
			wrs.writeXml(System.out);
		}
		finally
		{
			wrs.close();
		}
	}

	/**
	 * Overload of {@code qp} for examining {@code ParameterMetaData}.
	 *<p>
	 * Continuing in the spirit of getting something reasonably usable without
	 * a lot of code, this fakes up a {@code ResultSetMetaData} with the same
	 * values present in the {@code ParameterMetaData} (and nothing for the
	 * ones that aren't, like names), and then uses {@code WebRowSet.writeXml}
	 * as if dumping a result set.
	 *<p>
	 * For getting a quick idea what the parameters are, it's good enough.
	 */
	public static void qp(ParameterMetaData md) throws Exception
	{
		RowSetMetaDataImpl mdi = new RowSetMetaDataImpl();
		mdi.setColumnCount(md.getParameterCount());
		for ( int i = 1; i <= md.getParameterCount(); ++ i )
		{
			mdi.setColumnType(i, md.getParameterType(i));
			mdi.setColumnTypeName(i, md.getParameterTypeName(i));
			int precision = md.getPrecision(i);
			mdi.setPrecision(i, precision > 0 ? precision : 0);
			mdi.setScale(i, md.getScale(i));
			mdi.setNullable(i, md.isNullable(i));
			mdi.setSigned(i, md.isSigned(i));
		}
		qp(mdi);
	}

	/**
	 * Overload of {@code qp} for examining {@code ResultSetMetaData}.
	 *<p>
	 * This makes an empty {@code WebRowSet} with the copied metadata, and dumps
	 * it with {@code writeXml}. Owing to a few missing setters on Java's
	 * {@link RowSetMetaDataImpl}, a few {@code ResultSetMetaData} attributes
	 * will not have been copied; they'll be wrong (unless the real values
	 * happen to match the defaults). That could be fixed by extending that
	 * class, but that would require yet another extra class file added to the
	 * installer jar.
	 */
	public static void qp(ResultSetMetaData md) throws Exception
	{
		RowSetMetaDataImpl mdi = new RowSetMetaDataImpl();
		mdi.setColumnCount(md.getColumnCount());
		for ( int i = 1; i <= md.getColumnCount(); ++ i )
		{
			mdi.setColumnType(i, md.getColumnType(i));
			mdi.setColumnTypeName(i, md.getColumnTypeName(i));
			int precision = md.getPrecision(i);
			mdi.setPrecision(i, precision > 0 ? precision : 0);
			mdi.setScale(i, md.getScale(i));
			mdi.setNullable(i, md.isNullable(i));
			mdi.setSigned(i, md.isSigned(i));

			mdi.setAutoIncrement(i, md.isAutoIncrement(i));
			mdi.setCaseSensitive(i, md.isCaseSensitive(i));
			mdi.setCatalogName(i, md.getCatalogName(i));
			mdi.setColumnDisplaySize(i, md.getColumnDisplaySize(i));
			mdi.setColumnLabel(i, md.getColumnLabel(i));
			mdi.setColumnName(i, md.getColumnName(i));
			mdi.setCurrency(i, md.isCurrency(i));
			mdi.setSchemaName(i, md.getSchemaName(i));
			mdi.setSearchable(i, md.isSearchable(i));
			mdi.setTableName(i, md.getTableName(i));

			/*
			 * Attributes that RowSetMetaDataImpl simply forgets to provide
			 * setters for. It is what it is.
			columnClassName
			isDefinitelyWritable
			isReadOnly
			isWritable
			 */
		}
		qp(mdi);
	}

	private static void qp(RowSetMetaDataImpl mdi) throws Exception
	{
		try (WebRowSet wrs = RowSetProvider.newFactory().createWebRowSet())
		{
			wrs.setMetaData(mdi);
			wrs.writeXml(System.out);
		}
	}

	/**
	 * Prints a {@code Throwable} retrieved from a result stream, with
	 * special handling for {@code SQLException} and {@code SQLWarning}.
	 *<p>
	 * In keeping with the XMLish vibe established by
	 * {@link #qp(Stream) qp} for other items in a result
	 * stream, this will render a {@code Throwable} as an {@code error},
	 * {@code warning}, or {@code info} element (PostgreSQL's finer
	 * distinctions of severity are not exposed by every JDBC driver's API.)
	 *<p>
	 * An element will have a {@code message} attribute if it has a message.
	 * It will have a {@code code} attribute containing the SQLState, if it is
	 * an instance of {@code SQLException}, unless it is rendered as an
	 * {@code info} element and the state is {@code 00000}. An instance of
	 * {@code SQLWarning} will be rendered as a {@code warning} unless its class
	 * (two leftmost code positions) is {@code 00}, in which case it will be
	 * {@code info}. Anything else is an {@code error}.
	 */
	public static void qp(Throwable t)
	{
		String[] parts = classify(t);
		StringBuilder b = new StringBuilder("<" + parts[0]);
		if ( null != parts[1] )
			b.append(" code=").append(asAttribute(parts[1]));
		if ( null != parts[2] )
			b.append(" message=").append(asAttribute(parts[2]));
		System.out.println(b.append("/>"));
	}

	/**
	 * Returns an array of three {@code String}s, element, sqlState,
	 * and message, as would be printed by {@link #qp(Throwable)}.
	 *<p>
	 * The first string will be: (1) if the throwable is an {@code SQLWarning},
	 * "info" if its class (leftmost two positions of SQLState) is 00, otherwise
	 * "warning"; (2) for any other throwable, "error". These are constant
	 * strings and therefore interned.
	 *<p>
	 * The second string will be null if the throwable is outside the
	 * {@code SQLException} hierarchy, or if the first string is "info" and the
	 * SQLState is exactly 00000; otherwise it will be the SQLState.
	 *<p>
	 * The third string will be as returned by {@code getMessage}, and may be
	 * null if the throwable was not constructed with a message.
	 *<p>
	 * If an {@code SQLWarning} is of the PGJDBC driver's {@code PSQLWarning}
	 * class and the backend's severity tag is available, it will be used to
	 * determine the first string, in place of the "starts with 00" rule. A tag
	 * of "WARNING" (or null) produces "warning", while any other tag produces
	 * "info".
	 */
	public static String[] classify(Throwable t)
	{
		String msg = t.getMessage();
		String sqlState = null;
		String element = "error";
		if ( t instanceof SQLException )
		{
			sqlState = ((SQLException)t).getSQLState();
			if ( t instanceof SQLWarning )
			{
				element = s_toSeverity.apply((SQLWarning)t);
				if ( "info".equals(element) && "00000".equals(sqlState) )
						sqlState = null;
			}
		}
		return new String[] { element, sqlState, msg };
	}

	/**
	 * Escapes a string as an XML attribute.
	 *<p>
	 * Right on the borderline of trivial enough to implement here rather than
	 * using `java.xml` APIs (even though those are available in `jshell` too,
	 * transitively supplied by our reliance on `java.sql`).
	 */
	private static String asAttribute(String s)
	{
		int[] aposquot = new int[2];
		s.codePoints().forEach(c ->
		{
			if ( '\'' == c )
				++ aposquot[0];
			else if ( '"' == c )
				++ aposquot[1];
		});
		char delim = aposquot[0] > aposquot[1] ? '"' : '\'';
		Matcher m = compile('"' == delim ? "[<&\"]" : "[<&']").matcher(s);
		s = m.replaceAll(r ->
		{
			switch (r.group())
			{
			case "<": return "&lt;";
			case "&": return "&amp;";
			case "'": return "&apos;";
			case "\"": return "&quot;";
			}
			throw new AssertionError();
		});
		return delim + s + delim;
	}

	/**
	 * Determines whether an object is a {@code ResultSet} with no columns of
	 * any type other than {@code void}, to allow abbreviated output of result
	 * sets produced by the common case of queries that call {@code void}
	 * functions.
	 *<p>
	 * Returns null if <em>o</em> is not a {@code ResultSet}, or if its columns
	 * are not all of {@code void} type. Otherwise, returns a two-element
	 * integer array giving the rows (index 0 in the array) and columns (index
	 * 1) of the result set.
	 *<p>
	 * If this method returns non-null, the result set is left positioned on its
	 * last row.
	 * @param o Object to check
	 * @param peek whether to avoid moving the row cursor. If true, and all of
	 * the columns are indeed void, the result array will have the column count
	 * at index 1 and -1 at index 0.
	 * @return null or a two-element int[], as described above
	 */
	public static int[] voidResultSetDims(Object o, boolean peek)
	throws Exception
	{
		if ( ! (o instanceof ResultSet) )
			return null;

		ResultSet rs = (ResultSet)o;
		ResultSetMetaData md = rs.getMetaData();
		int cols = md.getColumnCount();
		int rows = 0;

		for ( int c = 1; c <= cols; ++c )
			if ( Types.OTHER != md.getColumnType(c)
				||  ! "void".equals(md.getColumnTypeName(c)) )
				return null;

		if ( peek )
			rows = -1;
		else if ( URL_FORM_PGJDBCNG == s_urlForm )
		{
			rs.last(); // last(), getRow() appears to work, in pgjdbc-ng
			rows = rs.getRow();
		}
		else
		{
			while ( rs.next() ) // PGJDBC requires this unless rs is scrollable
				++ rows;
		}

		return new int[] { rows, cols };
	}

	/**
	 * Equivalent to
	 * {@link #voidResultSetDims(Object,boolean) voidResultSetDims(o,false)};
	 */
	public static int[] voidResultSetDims(Object o)	throws Exception
	{
		return voidResultSetDims(o, false);
	}

	/**
	 * A predicate testing that an object is a {@code ResultSet} that has only
	 * columns of {@code void} type, and the expected number of rows
	 * and columns.
	 *<p>
	 * The expected result of a query that calls one {@code void}-typed,
	 * non-set-returning function could be checked with
	 * {@code isVoidResultSet(rs, 1, 1)}.
	 */
	public static boolean isVoidResultSet(Object o, int rows, int columns)
	throws Exception
	{
		int[] dims = voidResultSetDims(o);

		return null != dims && rows == dims[0] && columns == dims[1];
	}

	/**
	 * Executes a state machine specified in the form of
	 * a list of lambdas representing its states, to verify that a
	 * {@linkplain #q(Statement,Callable) result stream} is as expected.
	 *<p>
	 * Treats the list of lambdas as a set of consecutively-numbered states
	 * (the first in the list is state number 1, and is the initial state).
	 * At each step of the machine, the current state is applied to the current
	 * input object, and may return an {@code Integer} or a {@code Boolean}.
	 *<p>
	 * If an integer, its absolute value selects the next state. A positive
	 * integer consumes the current input item, so the next state will be
	 * applied to the next item of input. A negative integer transitions to the
	 * selected next state without consuming the current input item, so it will
	 * be examined again in the newly selected state.
	 *<p>
	 * If boolean, {@code false} indicates that the machine cannot proceed; the
	 * supplied <em>reporter</em> will be passed an explanatory string and this
	 * method returns false. A state that returns {@code true} indicates the
	 * machine has reached an accepting state.
	 *<p>
	 * No item of input is allowed to be null; null is reserved to be the
	 * end-of-input symbol. If a state returns {@code true} (accept)
	 * when applied to null at the end of input, the machine has matched and
	 * this method returns true. A state may also return a negative integer in
	 * this case, to shift to another state while looking at the end of input.
	 * A positive integer (attempting to consume the end of input), or a false,
	 * return will cause an explanatory message to the <em>reporter</em> and a
	 * false return from this method.
	 *<p>
	 * A state may return {@code true} (accept) when looking at a non-null
	 * input item, but the input will be checked to confirm it has no more
	 * elements. Otherwise, the machine has tried to accept before matching
	 * all the input, and this method will return false.
	 *<p>
	 * To avoid defining a new functional interface, each state is represented
	 * by {@link InvocationHandler}, an existing functional interface with a
	 * versatile argument list and permissive {@code throws} clause. Each state
	 * must be represented as a lambda with three parameters (the convention
	 * {@code (o,p,q)} is suggested), of which only the first is normally used.
	 * If Java ever completes the transition to {@code _} as an unused-parameter
	 * marker, the suggested convention will be {@code (o,_,_)}, unless the
	 * third (<var>q</var>) is also needed for special purposes (more below).
	 *<p>
	 * As the input item passed to each state is typed {@code Object}, and as
	 * null can only represent the end of input, it may be common for a state to
	 * both cast an input to an expected type and confirm it is not null.
	 * The {@link #as as} method combines those operations. If its argument
	 * either is null or cannot be cast to the wanted type, {@code as} will
	 * throw a specific instance of {@code ClassCastException}, which will be
	 * treated, when caught by {@code stateMachine}, just as if the state
	 * had returned {@code false}.
	 *<p>
	 * The third parameter to an {@code InvocationHandler} is an {@code Object}
	 * array, and is here used to pass additional information that may at times
	 * be of use in a state. The first element of the array holds the boxed form
	 * of the current (1-based) state number. As a state must indicate the next
	 * state by returning an absolute state number, having the state's own
	 * number available opens the possibility of reusable presupplied state
	 * implementations that do not depend on their absolute position.
	 * @param name A name for this state machine, used only in exception
	 * messages if it fails to match all the input
	 * @param reporter a Consumer to accept a diagnostic string if the machine
	 * fails to match, defaulting if null to {@code System.err::println}
	 * @param input A Stream of input items, of which none may be null
	 * @param states Lambdas representing states of the machine
	 * @return true if an accepting state was reached coinciding with the end
	 * of input
	 * @throws Exception Anything that could be thrown during evaluation of the
	 * input stream or any state
	 */
	public static boolean stateMachine(
		String name, Consumer<String> reporter, Stream<Object> input,
		InvocationHandler... states)
	throws Exception
	{
		if ( null == reporter )
			reporter = System.err::println;

		try ( input )
		{
			Iterator<Object> in = input.iterator();
			int currentState = 0;
			int stepCount = 0;
			int inputCount = 0;
			Object currentInput = null;
			boolean hasCurrent = false;
			Object result;

			while ( hasCurrent  ||  in.hasNext() )
			{
				++ stepCount;
				if ( ! hasCurrent )
				{
					currentInput = in.next();
					++ inputCount;
					if ( null == currentInput )
						throw new UnsupportedOperationException(
							"Input to stateMachine() must " +
							"not contain null values");
					hasCurrent = true;
				}

				result =
					invoke(states[currentState], currentState, currentInput);

				if ( result instanceof Boolean )
				{
					if ( (Boolean)result  &&  ! in.hasNext() )
						return true;
					reporter.accept(String.format(
						"stateMachine \"%s\" in state %d at step %d: %s",
						name, 1 + currentState, stepCount, (Boolean)result
						? String.format(
							"transitioned to ACCEPT after %d input items but " +
							"with input remaining", inputCount)
						: String.format(
							"could not proceed, looking at input %d: %s",
							inputCount, currentInput)));
					return false;
				}

				currentState = (Integer)result;
				if ( currentState > 0 )
					hasCurrent = false;
				else
					currentState *= -1;

				-- currentState;
			}

			for ( ;; )
			{
				++ stepCount;
				result = invoke(states[currentState], currentState, null);
				if ( result instanceof Boolean  &&  (Boolean)result )
					return true;
				else if ( result instanceof Integer  &&  0 > (Integer)result )
				{
					currentState = -1 - (Integer)result;
					continue;
				}
				break;
			}

			reporter.accept(String.format(
				"stateMachine \"%s\" in state %d at step %d: " +
				"does not accept at end of input after %d items",
				name, 1 + currentState, stepCount, inputCount));
			return false;
		}
	}

	/**
	 * Casts <em>o</em> to class <em>clazz</em>, testing it also for null.
	 *<p>
	 * This is meant as a shorthand in implementing states for
	 * {@link #stateMachine stateMachine}. If <em>o</em> either is null or
	 * is not castable to the desired type, a distinguished instance of
	 * {@code ClassCastException} will be thrown, which is treated specially
	 * if caught by {@code stateMachine} while evaluating a state.
	 */
	public static <T> T as(Class<T> clazz, Object o)
	{
		if ( clazz.isInstance(o) )
			return clazz.cast(o);
		throw failedAsException;
	}

	private static final ClassCastException failedAsException =
		new ClassCastException();

	/**
	 * Invokes the state handler <var>h</var>, passing it the current object
	 * <var>o</var> and, for special purposes, the state index (adjusted to
	 * be 1-based).
	 *<p>
	 * Conforming to the existing {@code InvocationHandler} interface, the
	 * state index is passed in boxed form as element zero of an {@code Object}
	 * array passed as the third argument.
	 */
	private static Object invoke(InvocationHandler h, int stateIdx, Object o)
	throws Exception
	{
		try
		{
			return h.invoke(o, null, new Object[] { 1 + stateIdx });
		}
		catch ( ClassCastException e )
		{
			if ( failedAsException == e )
				return false;
			throw e;
		}
		catch ( Exception e )
		{
			throw e;
		}
		catch ( Throwable t )
		{
			throw (Error)t;
		}
	}

	/*
	 * For parsing the postmaster.pid file, these have been the lines at least
	 * back to 9.1, except PM_STATUS appeared in 10. That's too bad; before 10
	 * it isn't possible to wait for a status of ready, which may necessitate
	 * just retrying the initial connection if the timing is unlucky.
	 * Cribbed from <utils/pidfile.h>.
	 */
	private static final int LOCK_FILE_LINE_PID         = 1;
	private static final int LOCK_FILE_LINE_DATA_DIR 	= 2;
	private static final int LOCK_FILE_LINE_START_TIME	= 3;
	private static final int LOCK_FILE_LINE_PORT 		= 4;
	private static final int LOCK_FILE_LINE_SOCKET_DIR	= 5;
	private static final int LOCK_FILE_LINE_LISTEN_ADDR = 6;
	private static final int LOCK_FILE_LINE_SHMEM_KEY	= 7;
	private static final int LOCK_FILE_LINE_PM_STATUS	= 8;
	private static final String PM_STATUS_READY = "ready   ";

	/**
	 * Waits for the {@code postmaster.pid} file to have the right contents
	 * (the right pid for process <em>p</em>, and ready status for PG 10+).
	 *<p>
	 * The {@code PostgreSQL:Test:Cluster} version of this is also used when
	 * shutting down, and waits for the file to go away; that could be
	 * implemented here, but not today.
	 */
	private void wait_for_pid_file(Process p, ProcessHandle.Info info)
	throws Exception
	{
		Path datadir = data_dir();
		Path pidfile = datadir.resolve("postmaster.pid");
		Path pidonly = pidfile.getFileName();

		/*
		 * If m_usePostgres is true, the p passed above is the actual postgres
		 * process, and we can compare its pid to what's in the pidfile.
		 * If pg_ctl was used, it's just the pid of the pg_ctl process, and
		 * instead of "checking" the pid in the pidfile, construct a process
		 * handle from it, to be saved as the handle of the server.
		 */
		Predicate<String[]> checkPid =
			m_usePostgres
			? (s -> Long.parseLong(s[LOCK_FILE_LINE_PID - 1]) == p.pid())
			: (s ->
				{
					long pid = Long.parseLong(s[LOCK_FILE_LINE_PID - 1]);
					m_serverHandle = ProcessHandle.of(pid).get();
					return true;
				}
			  );

		/*
		 * The isAlive check is a simple check on p in the m_usePostgres case.
		 * Otherwise, p is the pg_ctl process and probably has exited already;
		 * the handle assigned to m_serverHandle must be checked. If no handle
		 * has been assigned yet, just assume alive. The prospect of an
		 * unbounded wait (server process exiting before its pid could be
		 * collected from the pid file) should not be realizable, as long as
		 * pg_ctl itself waits long enough for the file to be present.
		 */
		BooleanSupplier isAlive =
			m_usePostgres
			? (() -> p.isAlive())
			: (() -> null != m_serverHandle ? m_serverHandle.isAlive() : true);

		StringBuilder tracepoints = new StringBuilder();
		Matcher dejavu = compile("(.+?)(?:\\1){16,}").matcher(tracepoints);
		Consumer<Character> trace = c ->
		{
			tracepoints.insert(0, c);
			if ( ! dejavu.reset().lookingAt() )
				return;
			tracepoints.reverse();
			String preamble =
				tracepoints.substring(0, tracepoints.length() - dejavu.end());
			String cycle =
				tracepoints.substring(tracepoints.length() - dejavu.end(1));
			throw new CancellationException(
				"Guru Meditation #" + preamble + "." + cycle);
		};

		trace.accept('A');
		if ( ! m_usePostgres )
			if ( 0 != p.waitFor() )
				throw new IllegalStateException(
					"pg_ctl exited with status " + p.exitValue());
		trace.accept('B');

		/*
		 * Initialize a watch service just in case the postmaster.pid file
		 * isn't there or has the wrong contents when we first look,
		 * and we need to wait for something to happen to it.
		 */
		try (WatchService watcher = datadir.getFileSystem().newWatchService())
		{
			WatchKey key =
				datadir.register(watcher, ENTRY_CREATE, ENTRY_MODIFY);

			for ( ;; )
			{
				trace.accept('C');
				try
				{
					if ( getLastModifiedTime(pidfile).toInstant().plusSeconds(1)
						.isBefore(info.startInstant().get()) )
						throw new NoSuchFileException("honest!");
						/*
						 * That was kind of a lie, but it's older than the
						 * process, so catching the exception below and waiting
						 * for it to change will be the right thing to do.
						 */

					trace.accept('D');
					String[] status;
					try ( Stream<String> lines = lines(pidfile) )
					{
						status = lines.toArray(String[]::new);
					}
					if ( (status.length == LOCK_FILE_LINE_PM_STATUS)
						&& checkPid.test(status)
						&& PM_STATUS_READY.equals(
							status[LOCK_FILE_LINE_PM_STATUS - 1]) )
						return;
					trace.accept('E');
					if (
						(
							status.length == LOCK_FILE_LINE_SHMEM_KEY
							|| s_isWindows
							&& status.length == LOCK_FILE_LINE_LISTEN_ADDR
						)
						&& checkPid.test(status)
						&& waitPrePG10() )
						return;
					trace.accept('F');
				}
				catch (NoSuchFileException e)
				{
					trace.accept('G');
				}

				/*
				 * The file isn't there yet, or isn't fully written or "ready"
				 */
				for ( ;; )
				{
					if ( ! isAlive.getAsBoolean() )
						throw new IllegalStateException(
							"Server process exited while awaiting \"ready\"" +
							(
								m_usePostgres
								? " with status " + p.exitValue()
								: ""
							)
						);
					trace.accept('H');
					WatchKey k = watcher.poll(250, MILLISECONDS);
					trace.accept('I');
					if ( interrupted() )
						throw new InterruptedException();
					trace.accept('J');
					if ( null == k )
						break; // timed out; check again just in case
					trace.accept('K');
					assert key.equals(k); // it's the only one we registered
					boolean recheck = k.pollEvents().stream()
						.anyMatch(e ->
							{
								WatchEvent.Kind<?> kind = e.kind();
								if ( OVERFLOW == kind )
									return true;
								if ( ENTRY_CREATE == kind &&
									pidonly.equals(
										ENTRY_CREATE.type().cast(e.context())) )
									return true;
								if ( ENTRY_MODIFY == kind &&
									pidonly.equals(
										ENTRY_MODIFY.type().cast(e.context())) )
									return true;
								return false;
							}
						);
					trace.accept('L');
					if ( recheck )
						break;
					trace.accept('M');
					k.reset();
				}
			}
		}
		catch ( final Throwable t )
		{
			/*
			 * In the ! m_usePostgres case, m_serverHandle gets unconditionally
			 * set in checkPid; don't let that escape if completing abruptly.
			 */
			m_serverHandle = null;
			throw t;
		}
	}

	/**
	 * Checks whether the server being started is earlier than PG 10 and, if so,
	 * sleeps for a period expected to be adequate for it to become ready to
	 * accept connections, then returns true.
	 *<p>
	 * This is called from the generic {@code wait_for_pid_file}, only if the
	 * file has already appeared and has all entries but {@code PM_STATUS}. That
	 * could mean it is a pre-PG10 server that will not write {@code PM_STATUS},
	 * or a PG 10 or later server that was caught in mid-write to the file.
	 *<p>
	 * Return false if it is PG 10 or later, in which case the caller should
	 * continue waiting for {@code PM_STATUS_READY} to appear.
	 *<p>
	 * The fixed wait in the pre-PG10 case should not need to be terribly long,
	 * because this method isn't called until the PID file has already appeared,
	 * so that much of server startup has already occurred.
	 */
	private boolean waitPrePG10() throws Exception
	{
		if ( lines(data_dir().resolve("PG_VERSION")).limit(1).noneMatch(
			s -> s.contains(".")) )
			return false;
		Thread.sleep(2000); // and hope
		return true;
	}

	/*
	 * Workarounds for ProcessBuilder command argument preservation problems
	 * in various circumstances. Each of the functions below acts on a
	 * ProcessBuilder by possibly modifying its 'command' argument vector into
	 * such a form that the intended target will be correctly invoked with the
	 * original arguments.
	 *
	 * - Java's Windows implementation faces a near-impossible task because of
	 *   the variety of parsing rules that could be applied by some arbitrary
	 *   invoked program. Here, with the simplifying assumption that the program
	 *   will be one of initdb, postgres, or pg_ctl, all C programs using the C
	 *   run-time code to parse command lines, and checking to exclude a few
	 *   cases that can't be reliably handled, the simpler problem is tractable.
	 *
	 * - pg_ctl itself is surprisingly problem-ridden. Here the starting point
	 *   is an argument list intended for invoking postgres directly, which will
	 *   be transformed into one to start postgres via pg_ctl. The only options
	 *   handled here are the ones start() might supply: -D for the datadir and
	 *   -c for options, which will be rewritten as -o values for pg_ctl.
	 */

	/*
	 * The same method is duplicated in pljava-pgxs/PGXSUtils.java . While making
	 * changes to this method, review the other occurrence also and replicate the
	 * changes there if desirable.
	 */
	/**
	 * Adjusts the command arguments of a {@code ProcessBuilder} so that they
	 * will be recovered correctly on Windows by a target C/C++ program using
	 * the argument parsing algorithm of the usual C run-time code, when it is
	 * known that the command will not be handled first by {@code cmd}.
	 *<p>
	 * This transformation must account for the way the C runtime will
	 * ultimately parse the parameters apart, and also for the behavior of
	 * Java's runtime in assembling the command line that the invoked process
	 * will receive.
	 * @param pb a ProcessBuilder whose command has been set to an executable
	 * that parses parameters using the C runtime rules, and arguments as they
	 * should result from parsing.
	 * @return The same ProcessBuilder, with the argument list rewritten as
	 * necessary to produce the original list as a result of Windows C runtime
	 * parsing,
	 * @throws IllegalArgumentException if the ProcessBuilder does not have at
	 * least the first command element (the executable to run)
	 * @throws UnsupportedOperationException if the arguments passed, or system
	 * properties in effect, produce a case this transformation cannot handle
	 */
	public static ProcessBuilder forWindowsCRuntime(ProcessBuilder pb)
	{
		ListIterator<String> args = pb.command().listIterator();
		if ( ! args.hasNext() )
			throw new IllegalArgumentException(
				"ProcessBuilder command must not be empty");

		/*
		 * The transformation implemented here must reflect the parsing rules
		 * of the C run-time code, and the rules are taken from:
		 * http://www.daviddeley.com/autohotkey/parameters/parameters.htm#WINARGV
		 *
		 * It must also take careful account of what the Java runtime does to
		 * the arguments before the target process is launched, and line numbers
		 * in comments below refer to this version of the source:
		 * http://hg.openjdk.java.net/jdk9/jdk9/jdk/file/65464a307408/src/java.base/windows/classes/java/lang/ProcessImpl.java
		 *
		 * 1. Throw Unsupported if the jdk.lang.Process.allowAmbiguousCommands
		 *    system property is in force.
		 *
		 *    Why?
		 *      a. It is never allowed under a SecurityManager, so to allow it
		 *         at all would allow code's behavior to change depending on
		 *         whether a SecurityManager is in place.
		 *      b. It results in a different approach to preparing the arguments
		 *         (line 364) that would have to be separately analyzed.
		 *
		 * Do not test this property with Boolean.getBoolean: that returns true
		 * only if the value equalsIgnoreCase("true"), which does not match the
		 * test in the Java runtime (line 362).
		 */
		String propVal = getProperty("jdk.lang.Process.allowAmbiguousCommands");
		if ( null != propVal && ! "false".equalsIgnoreCase(propVal) )
			throw new UnsupportedOperationException(
				"forWindowsCRuntime transformation does not support operation" +
				" with jdk.lang.Process.allowAmbiguousCommands in effect");

		/*
		 * 2. Throw Unsupported if the executable path name contains a "
		 *
		 *    Why? Because getExecutablePath passes true, unconditionally, to
		 *    isQuoted (line 303), so it will throw IllegalArgumentException if
		 *    there is any " in the executable path. The catch block for that
		 *    exception (line 383) will make a highly non-correctness-preserving
		 *    attempt to join and reparse the arguments, using
		 *    getTokensFromCommand (line 198), which uses a regexp (line 188)
		 *    that does not even remotely resemble the C runtime parsing rules.
		 *
		 *    Possible future work: this case could be handled by rewriting the
		 *    entire command as an invocation via CMD or another shell.
		 */
		String executable = args.next();
		if ( executable.contains("\"") )
			throw new UnsupportedOperationException(
				"forWindowsCRuntime does not support invoking an executable" +
				" whose name contains a \" character");

		/*
		 * 3. Throw Unsupported if the executable path ends in .cmd or .bat
		 *    (case-insensitively).
		 *
		 *    Why? For those extensions, the Java runtime will select different
		 *    rules (line 414).
		 *    a. Those rules would need to be separately analyzed.
		 *    b. They will reject (line 286) any argument that contains a "
		 *
		 *    Possible future work: this case could be handled by rewriting the
		 *    entire command as an invocation via CMD or another shell (which is
		 *    exactly the suggestion in the exception message that would be
		 *    produced if an argument contains a ").
		 */
		if ( executable.matches(".*\\.(?i:cmd|bat)$") )
			throw new UnsupportedOperationException(
				"forWindowsCRuntime does not support invoking a command" +
				" whose name ends in .cmd or .bat");

		/*
		 * 4. There is a worrisome condition in the Java needsEscaping check
		 *    (line 277), where it would conclude that escaping is NOT needed
		 *    if an argument both starts and ends with a " character. In other
		 *    words, it would treat that case (and just that case) not as
		 *    characters that are part of the content and need to be escaped,
		 *    but as a sign that its job has somehow already been done.
		 *
		 *    However, that will not affect this transformation, because our
		 *    rule 5 below will ensure that any leading " has a \ added before,
		 *    and therefore the questionable Java code will never see from us
		 *    an arg that both starts and ends with a ".
		 *
		 *    There is one edge case where this behavior of the Java runtime
		 *    will be relied on (see rule 7 below).
		 */

		while ( args.hasNext() )
		{
			String arg = args.next();

			/*
			 * 5. While the Java runtime code will add " at both ends of the
			 *    argument IF the argument contains space, tab, <, or >, it does
			 *    so with zero attention to any existing " characters in the
			 *    content of the argument. Those must, of course, be escaped so
			 *    the C runtime parser will not see them as ending the quoted
			 *    region. By those rules, a " is escaped by a \ and a \ is only
			 *    special if it is followed by a " (or in a sequence of \
			 *    ultimately leading to a "). The needed transformation is to
			 *    find any instance of n backslashes (n may be zero) followed
			 *    by a ", and replace that match with 2n+1 \ followed by the ".
			 *
			 *    This transformation is needed whether or not the Java runtime
			 *    will be adding " at start and end. If it does not, the same
			 *    \ escaping is needed so the C runtime will not see a " as
			 *    beginning a quoted region.
			 */
			String transformed = arg.replaceAll("(\\\\*+)(\")", "$1$1\\\\$2");

			/*
			 * 6. Only if the Java runtime will be adding " at start and end
			 *    (i.e., only if the arg contains space, tab, <, or >), there is
			 *    one more case where \ can be special: at the very end of the
			 *    arg (where it will end up followed by a " when the Java
			 *    runtime has done its thing). The Java runtime is semi-aware of
			 *    this case (line 244): it will add a single \ if it sees that
			 *    the arg ends with a \. However, that isn't the needed action,
			 *    which is to double ALL consecutive \ characters ending the
			 *    arg.
			 *
			 *    So the action needed here is to double all-but-one of any
			 *    consecutive \ characters at the end of the arg, leaving one
			 *    that will be doubled by the Java code.
			 */
			if ( transformed.matches("(?s:[^ \\t<>]*+.++)") )
				transformed = transformed.replaceFirst(
					"(\\\\)(\\\\*+)$", "$1$2$2");

			/*
			 * 7. If the argument is the empty string, it must be represented
			 *    as "" or it will simply disappear. The Java runtime will not
			 *    do that for us (after all, the empty string does not contain
			 *    space, tab, <, or >), so it has to be done here, replacing the
			 *    arg with exactly "".
			 *
			 *    This is the one case where we produce a value that both starts
			 *    and ends with a " character, thereby triggering the Java
			 *    runtime behavior described in (4) above, so the Java runtime
			 *    will avoid trying to further "protect" the string we have
			 *    produced here. For this one case, that 'worrisome' behavior is
			 *    just what we want.
			 */
			if ( transformed.isEmpty() )
				transformed = "\"\"";

			if ( ! transformed.equals(arg) )
				args.set(transformed);
		}

		return pb;
	}

	/**
	 * Adjusts the command arguments of a {@code ProcessBuilder} that would
	 * directly invoke {@code postgres} to start a server, so that it will
	 * instead start {@code postgres} via {@code pg_ctl}.
	 *<p>
	 * {@code pg_ctl} constructs a command line for {@code cmd.exe} (on Windows)
	 * or {@code /bin/sh} (elsewhere), which in turn will launch
	 * {@code postgres}. The way {@code pg_ctl} handles options ({@code -o})
	 * requires this transformation to be platform-aware and quote them
	 * correctly for {@code sh} or {@code cmd} as appropriate.
	 *<p>
	 * The result of this transformation still has to be received intact by
	 * {@code pg_ctl} itself, which requires (on Windows) a subsequent
	 * application of {@code forWindowsCRuntime} as well.
	 * @param pb a ProcessBuilder whose command has been set to an executable
	 * path for {@code postgres}, with only {@code -D} and {@code -c} options.
	 * @return The same ProcessBuilder, with the argument list rewritten to
	 * invoke {@code pg_ctl start} with the same {@code -D} and any other
	 * options supplied by {@code -o}.
	 * @throws IllegalArgumentException if the ProcessBuilder does not have at
	 * least the first command element (the executable to run)
	 * @throws UnsupportedOperationException if the arguments passed
	 * produce a case this transformation cannot handle
	 */
	public static ProcessBuilder asPgCtlInvocation(ProcessBuilder pb)
	{
		ListIterator<String> args = pb.command().listIterator();
		if ( ! args.hasNext() )
			throw new IllegalArgumentException(
				"ProcessBuilder command must not be empty");

		Matcher datadirDisallow =
			compile(s_isWindows ? "[\"^%]" : "[\"\\\\$]").matcher("");

		Path executable = Paths.get(args.next());
		if ( ! executable.endsWith("postgres") )
			throw new UnsupportedOperationException(
				"expected executable path to end with postgres");
		executable = executable.getParent().resolve("pg_ctl");
		args.set(executable.toString());
		args.add("start");

		while ( args.hasNext() )
		{
			String arg = args.next();
			switch ( arg )
			{
			case "-D":
				if ( datadirDisallow.reset(args.next()).find() )
					throw new UnsupportedOperationException(
						"datadir with \", "
						+ (s_isWindows ? "^, or %" : "\\, or $") +
						" character is likely to be messed up by pg_ctl");
				break;

			case "-c":
				args.set("-o");
				String setting = args.next();
				if ( s_isWindows )
				{
					/*
					 * The result of this transformation will be what pg_ctl
					 * passes to cmd. Because it will be (a) passed to cmd, and
					 * then (b) passed to postgres (a C program), it can use
					 * exactly the simplified "putting it together" rules from
					 * http://www.daviddeley.com/autohotkey/parameters/parameters.htm#CPP
					 *
					 * Because this is only about the handoff from pg_ctl to cmd
					 * to postgres, it does not need to handle the tricks of
					 * getting safely through the Java runtime. Getting what
					 * this transformation produces safely from Java to pg_ctl
					 * (another C program) is the job of forWindowsCRuntime.
					 */
					setting = setting.replaceAll("(\\\\++)(\"|$)","$1$1\\\\$2");
					setting = setting.replaceAll("([<>|&()^])", "^$1");
					setting = "^\"" + setting + "^\"";
				}
				else
				{
					/*
					 * The simple Bourne-shell rule for safely quoting an
					 * argument is like a glass of cool water on a hot day.
					 */
					setting = "'" + setting.replace("'", "'\\''") + "'";
				}
				args.set("-c " + setting);
				break;

			default:
				throw new UnsupportedOperationException(
					"asPgCtlInvocation does not handle postgres option \"" +
					arg + "\"");
			}
		}

		return pb;
	}
}
