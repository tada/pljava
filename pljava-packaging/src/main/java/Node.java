/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
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
import static java.lang.Thread.interrupted;

import static java.net.InetAddress.getLoopbackAddress;
import static java.net.URLEncoder.encode;
import java.net.ServerSocket;

import static java.nio.charset.StandardCharsets.US_ASCII;

import static java.nio.file.Files.createTempFile;
import static java.nio.file.Files.createTempDirectory;
import static java.nio.file.Files.deleteIfExists;
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

import java.nio.file.NoSuchFileException;

import java.sql.Connection;
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
import java.util.List;
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

import java.util.concurrent.Callable; // like a Supplier but allows exceptions!
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import java.util.jar.JarFile;

import java.util.stream.Stream;
import static java.util.stream.StreamSupport.stream;

/**
 * Subclass the JarX extraction tool to provide a {@code resolve} method that
 * replaces prefixes {@code pljava/foo/} in path names stored in the archive
 * with the result of {@code pg_config --foo}.
 *<p>
 * As this represents a second extra {@code .class} file that has to be added
 * to the installer jar anyway, it will also contain some methods intended to be
 * useful for tasks related to installation and testing. The idea is not to go
 * overboard, but supply a few methods largely modeled on the most basic ones of
 * PostgreSQL's {@code PostgresNode.pm}, with the idea that they can be invoked
 * from {@code jshell} if its classpath includes the installer jar (and
 * pgjdbc-ng).
 *<p>
 * Unlike the many capabilities of {@code PostgresNode.pm}, this only deals in
 * TCP sockets bound to {@code localhost} (Java doesn't have Unix sockets out of
 * the box yet) and only a few of the most basic operations.
 *<p>
 * As in JarX itself, some liberties with coding style may be taken here to keep
 * this one extra {@code .class} file from proliferating into a bunch of them.
 */
public class Node extends JarX {

	private Matcher m_prefix;
	private int m_fsepLength;
	private String m_lineSep;
	private boolean m_dryrun = false;

	private static Node s_jarxHelper = new Node(null, 0, null, null);
	private static boolean s_jarProcessed = false;
	private static String s_examplesJar;

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
	 * Extract the jar contents, just as done in the normal case of running
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
	 * Prepare the resolver, ignoring the passed string (ordinarily a script or
	 * rules); this resolver's rules are hardcoded.
	 */
	@Override
	public void prepareResolver(String v) throws Exception
	{
		m_prefix = compile("^pljava/([^/]+dir)(?![^/])").matcher("");
		m_fsepLength = getProperty("file.separator").length();
		m_lineSep = getProperty("line.separator");
	}

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
			if ( ! m_dryrun )
				return replacement;
			if ( -1 != storedPath.indexOf("/pljava-examples-") )
				s_examplesJar = replacement;
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
	 * Construct an instance; all nulls for the parameters are passed by the
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
	}

	/**
	 * Return a new {@code Node} that can be used to initialize and start a
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
	 * Return a TCP port on the loopback interface that is free at the moment
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
	 * Recursively remove the <em>basedir</em> and its descendants.
	 */
	public void clean_node() throws Exception
	{
		clean_node(false);
	}

	/**
	 * Recursively remove the <em>basedir</em> and its descendants.
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
				deleteIfExists(stk.pop());
			stk.push(p);
		}
		if ( keepRoot )
			stk.pollLast();
		for ( Path p : stk )
			deleteIfExists(p);
	}

	/**
	 * Process the jar without really extracting, to compute the path mappings.
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
	 * to {@code pg_config}, return the platform-specific path where it would
	 * be installed.
	 */
	private static String resolve(String archivePath) throws Exception
	{
		return s_jarxHelper.resolve(
			archivePath, Paths.get("", archivePath.split("/")).toString());
	}

	/**
	 * Return the directory name to be used as the PostgreSQL data directory
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
	public AutoCloseable initialized_cluster(Map<String,String> suppliedOptions)
	throws Exception
	{
		return initialized_cluster(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Like {@code init()} but returns an {@code AutoCloseable} that will
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
	 * Like {@code init()} but returns an {@code AutoCloseable} that will
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
	 * Invoke {@code initdb} for the node, passing default options appropriate
	 * for this setting, and optionally one or more tweaks to be applied to the
	 * {@code ProcessBuilder} before it is started.
	 */
	public void init(Map<String,String> suppliedOptions) throws Exception
	{
		init(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Invoke {@code initdb} for the node, passing default options appropriate
	 * for this setting, and tweaks to be applied to the
	 * {@code ProcessBuilder} before it is started.
	 */
	public void init(UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		init(Map.of(), tweaks);
	}

	/**
	 * Invoke {@code initdb} for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default.
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
	 * further configure it.
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
			tweaks.apply(pb);
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
	public AutoCloseable started_server(Map<String,String> suppliedOptions)
	throws Exception
	{
		return started_server(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Like {@code start()} but returns an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 */
	public AutoCloseable started_server(UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		return started_server(Map.of(), tweaks);
	}

	/**
	 * Like {@code start()} but returns an {@code AutoCloseable} that will
	 * stop the server on the exit of a calling try-with-resources scope.
	 */
	public AutoCloseable started_server(
		Map<String,String> suppliedOptions,
		UnaryOperator<ProcessBuilder> tweaks)
	throws Exception
	{
		start(suppliedOptions, tweaks);
		return () ->
		{
			stop();
		};
	}

	/**
	 * Start a PostgreSQL server for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default.
	 */
	public void start(Map<String,String> suppliedOptions) throws Exception
	{
		start(suppliedOptions, UnaryOperator.identity());
	}

	/**
	 * Start a PostgreSQL server for the node, passing default options
	 * appropriate for this setting, and tweaks to be
	 * applied to the {@code ProcessBuilder} before it is started.
	 */
	public void start(UnaryOperator<ProcessBuilder> tweaks) throws Exception
	{
		start(Map.of(), tweaks);
	}

	/**
	 * Start a PostgreSQL server for the node, with <em>suppliedOptions</em>
	 * overriding or supplementing the ones that would be passed by default.
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
	 * further configure it.
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

		Stream<String> cmd =
			m_usePostgres
			? Stream.of(resolve("pljava/bindir/postgres"))
			: Stream.of(resolve("pljava/bindir/pg_ctl"), "start");

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
					:
					m_usePostgres
					? Stream.of("-c", e.getKey() + "=" + e.getValue())
					: Stream.of("-o", "-c", "-o", e.getKey()+"="+e.getValue())
				)
			)
			.toArray(String[]::new);

		ProcessBuilder pb =
			new ProcessBuilder(args)
			.redirectOutput(INHERIT)
			.redirectError(INHERIT);
		tweaks.apply(pb);
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
	 * Stop the server instance associated with this Node.
	 *<p>
	 * No effect if it has not been started or has already been stopped, but
	 * a message to standard error is logged if the server had been started and
	 * the process is found to have exited unexpectedly.
	 */
	public void stop() throws Exception
	{
		if ( null == ( m_usePostgres ? m_server : m_serverHandle ) )
			return;
		if ( ! m_usePostgres )
		{
			stopViaPgCtl();
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

	private void stopViaPgCtl() throws Exception
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
		Process p = pb.start();
		p.getOutputStream().close();
		if ( 0 != p.waitFor() )
			throw new AssertionError(
				"Nonzero pg_ctl stop result: " + p.waitFor());
		m_serverHandle = null;
	}

	/**
	 * Indicate whether to use {@code pg_ctl} to start and stop the server
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
	 * Return a {@code Connection} to the server associated with this Node,
	 * using default properties appropriate for this setting.
	 */
	public Connection connect() throws Exception
	{
		return connect(new Properties());
	}

	/**
	 * Return a {@code Connection} to the server associated with this Node,
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
	 * Return a {@code Connection} to the server associated with this Node,
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
		String url = "jdbc:pgsql://localhost:" + m_port + '/';
		p = (Properties)p.clone();
		p.putIfAbsent("database.name", "postgres");
		p.putIfAbsent("user", "postgres");
		p.putIfAbsent("password", m_password);
		p.computeIfAbsent("application.name", o -> "Conn" + (m_connCount++));
		/*
		 * Contrary to its documentation, pgjdbc-ng does *not* accept a URL with
		 * the database name omitted. It is no use having it in the properties
		 * here; it must be appended to the URL.
		 */
		url += encode(p.getProperty("database.name"), "UTF-8");
		return getConnection(url, p);
	}

	/**
	 * Set a configuration variable on the server.
	 *<p>
	 * This deserves a convenience method because the most familiar PostgreSQL
	 * syntax for SET doesn't lend itself to parameterization.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
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
	 * Install a jar.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
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
	 * Remove a jar.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
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
	 * Set the class path for a schema.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
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
	 * Execute some arbitrary SQL
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
	 */
	public static Stream<Object> q(Connection c, String sql) throws Exception
	{
		Statement s = c.createStatement();
		return q(s, () -> s.execute(sql));
	}

	/**
	 * Execute some arbitrary SQL and pass
	 * the {@link #q(Statement,Callable) result stream}
	 * to {@link #qp(Stream<Object>)} for printing to standard output.
	 */
	public static void qp(Connection c, String sql) throws Exception
	{
		qp(q(c, sql));
	}

	/**
	 * Invoke some {@code execute} method on a {@code Statement} and pass
	 * the {@link #q(Statement,Callable) result stream}
	 * to {@link #qp(Stream<Object>)} for printing to standard output.
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
	 * Return true if the examples jar includes the
	 * {@code org.postgresql.pljava.example.saxon.S9} class (meaning the
	 * appropriate Saxon jar must be installed and on the classpath first before
	 * the examples jar can be deployed, unless {@code check_function_bodies}
	 * is {@code off} to skip dependency checking.
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
	 * Install the examples jar, under the name {@code examples}.
	 *<p>
	 * The jar is specified by a {@code file:} URI and the path is the one where
	 * this installer installed (or would have installed) it.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
	 */
	public static Stream<Object> installExamples(Connection c, boolean deploy)
	throws Exception
	{
		dryExtract();
		return installJar(c, "file:"+s_examplesJar, "examples", deploy);
	}

	/**
	 * Install the examples jar, under the name {@code examples}, and place it
	 * on the class path for schema {@code public}.
	 *<p>
	 * The return of a concatenated result stream from two consecutive
	 * statements might be likely to fail in cases where the first
	 * statement has any appreciable data to return, but pgjdbc-ng seems to
	 * handle it at least in this case where each statement just returns one
	 * row / one column of {@code void}. And it is convenient.
	 * @return a combined {@link #q(Statement,Callable) result stream} from
	 * executing the statements
	 */
	public static Stream<Object> installExamplesAndPath(
		Connection c, boolean deploy)
	throws Exception
	{
		Stream<Object> s1 = installExamples(c, deploy);
		Stream<Object> s2 = setClasspath(c, "public", "examples");
		return Stream.concat(s1, s2);
	}

	/**
	 * Install a Saxon jar under the name {@code saxon}, given the path to a
	 * local Maven repo and the needed version of Saxon, assuming the jar has
	 * been downloaded there already.
	 * @return a {@link #q(Statement,Callable) result stream} from executing
	 * the statement
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
	 * Install a Saxon jar under the name {@code saxon}, and place it on the
	 * class path for schema {@code public}.
	 * @return a combined {@link #q(Statement,Callable) result stream} from
	 * executing the statements
	 */
	public static Stream<Object> installSaxonAndPath(
		Connection c, String repo, String version)
	throws Exception
	{
		Stream<Object> s1 = installSaxon(c, repo, version);
		Stream<Object> s2 = setClasspath(c, "public", "saxon");
		return Stream.concat(s1, s2);
	}

	/**
	 * A four-fer: install Saxon, add it to the class path, then install the
	 * examples jar, and update the classpath to include both.
	 * @param repo the base directory of a local Maven repository into which the
	 * Saxon jar has been downloaded
	 * @param version the needed version of Saxon
	 * @param deploy whether to run the example jar's deployment code
	 * @return a combined {@link #q(Statement,Callable) result stream} from
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
	 * Produce a {@code Stream} of the (in JDBC, possibly multiple) results
	 * from some {@code execute} method on a {@code Statement}.
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
	 * A flat-mapping function to expand any {@code SQLException} or
	 * {@code SQLWarning} instance in a result stream into the stream of
	 * possibly multiple linked diagnostics and causes in the encounter order
	 * of the {@code SQLException} iterator.
	 *<p>
	 * Any other object is returned in a singleton stream.
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
	 * Print streamed results of a {@code Statement} in
	 * (somewhat) readable fashion.
	 *<p>
	 * Uses {@code writeXml} of {@code WebRowSet}, which is very verbose, but
	 * about the easiest way to readably dump a {@code ResultSet} in just a
	 * couple lines of code.
	 *<p>
	 * The supplied stream is flattened (see
	 * {@link #flattenDiagnostics flattenDiagnostics}) so that any chained
	 * {@code SQLException}s or {@code SQLWarning}s are printed in sequence.
	 */
	public static void qp(Stream<Object> s) throws Exception
	{
		try ( Stream<Object> flat = s.flatMap(Node::flattenDiagnostics) )
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
	 * Overload of {@code qp} for direct application to a {@code ResultSet}.
	 *<p>
	 * Sometimes one has a {@code ResultSet} that didn't come from executing
	 * a query, such as from a JDBC metadata method. This prints it the same way
	 * {@code qp} on a query result would. The {@code ResultSet} is not closed
	 * (but will have been read through the last row).
	 *<p>
	 * A result set with no columns of type other than {@code void} will be
	 * printed in an abbreviated form.
	 */
	public static void qp(ResultSet rs) throws Exception
	{
		ResultSetMetaData md = rs.getMetaData();
		int cols = md.getColumnCount();

		boolean allVoid = true;

		for ( int c = 1; c <= cols; ++c )
		{
			if ( Types.OTHER == md.getColumnType(c)
				&& "void".equals(md.getColumnTypeName(c)) )
				continue;
			allVoid = false;
			break;
		}

		if ( allVoid )
		{
			rs.last();
			System.out.println(
				"<void rows='" + rs.getRow() + "' cols='" + cols + "'/>");
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
		WebRowSet wrs = RowSetProvider.newFactory().createWebRowSet();
		try
		{
			RowSetMetaDataImpl mdi = new RowSetMetaDataImpl();
			mdi.setColumnCount(md.getParameterCount());
			for ( int i = 1; i <= md.getParameterCount(); ++ i )
			{
				mdi.setColumnType(i, md.getParameterType(i));
				mdi.setColumnTypeName(i, md.getParameterTypeName(i));
				mdi.setPrecision(i, md.getPrecision(i));
				mdi.setScale(i, md.getScale(i));
				mdi.setNullable(i, md.isNullable(i));
				mdi.setSigned(i, md.isSigned(i));
			}
			wrs.setMetaData(mdi);
			wrs.writeXml(System.out);
		}
		finally
		{
			wrs.close();
		}
	}

	/**
	 * Print a {@code Throwable} retrieved from a result stream, with
	 * special handling for {@code SQLException} and {@code SQLWarning}.
	 *<p>
	 * In keeping with the XMLish vibe established by
	 * {@link #qp(Stream<Object>) qp} for other items in a result
	 * stream, this will render a {@code Throwable} as an {@code error},
	 * {@code warning}, or {@code info} element (PostgreSQL's finer
	 * distinctions of severity are not exposed by pgjdbc-ng's API.)
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
	 * Return an array of three {@code String}s, element, sqlState, and message,
	 * as would be printed by {@link #qp(Throwable)}.
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
				if ( sqlState.startsWith("00") )
				{
					element = "info";
					if ( "00000".equals(sqlState) )
						sqlState = null;
				}
				else
					element = "warning";
			}
		}
		return new String[] { element, sqlState, msg };
	}

	/**
	 * Escape a string as an XML attribute.
	 *<p>
	 * Right on the borderline of trivial enough to implement here rather than
	 * forcing the beleaguered user to add yet one more --add-modules for
	 * {@code java.xml} just to run this in {@code jshell}.
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
	 * Wait for the {@code postmaster.pid} file to have the right contents
	 * (the right pid for process <em>p</em>, and ready status for PG 10+).
	 *<p>
	 * The {code PostgresNode.pm} version of this is also used when shutting
	 * down, and waits for the file to go away; that could be implemented here,
	 * but not today.
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
				try
				{
					if ( getLastModifiedTime(pidfile).toInstant()
						.isBefore(info.startInstant().get()) )
						throw new NoSuchFileException("honest!");
						/*
						 * That was kind of a lie, but it's older than the
						 * process, so catching the exception below and waiting
						 * for it to change will be the right thing to do.
						 */

					String[] status = lines(pidfile).toArray(String[]::new);
					if ( (status.length == LOCK_FILE_LINE_PM_STATUS)
						&& checkPid.test(status)
						&& PM_STATUS_READY.equals(
							status[LOCK_FILE_LINE_PM_STATUS - 1]) )
						return;
					if ( (status.length == LOCK_FILE_LINE_SHMEM_KEY)
						&& checkPid.test(status)
						&& waitPrePG10() )
						return;
				}
				catch (NoSuchFileException e)
				{
				}

				/*
				 * The file isn't there yet, or isn't fully written or "ready"
				 */
				for ( ;; )
				{
					if ( ! p.isAlive() )
						throw new IllegalStateException(
							"Server process exited while awaiting \"ready\" " +
							"with status " + p.exitValue());
					WatchKey k = watcher.poll(250, MILLISECONDS);
					if ( interrupted() )
						throw new InterruptedException();
					if ( null == k )
						break; // timed out; check again just in case
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
					if ( recheck )
						break;
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
	 * Check whether the server being started is earlier than PG 10 and, if so,
	 * sleep for a period expected to be adequate for it to become ready to
	 * accept connections, then return true.
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
}
