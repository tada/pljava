/*
 * Copyright (c) 2020-2024 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 *   Kartik Ohri
 */
package org.postgresql.pljava.pgxs;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.tools.Diagnostic;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.System.getProperty;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Stream.iterate;
import static javax.script.ScriptContext.ENGINE_SCOPE;

/**
 * Utility methods to simplify and hide the bland implementation details
 * for writing JavaScript snippets.
 */
public final class PGXSUtils
{
	/**
	 * maven project for which plugin is executed
	 */
	private final MavenProject project;

	/**
	 * maven plugin logger for diagnostics
	 */
	private final Log log;

	private static final Pattern mustBeQuotedForC = Pattern.compile(
		"([\"\\\\]|(?<=\\?)\\?(?=[=(/)'<!>-]))|" +  // (just insert backslash)
		"([\\a\b\\f\\n\\r\\t\\x0B])|" +             // (use specific escapes)
		"(\\p{Cc}((?=\\p{XDigit}))?)" // use hex, note whether an XDigit follows
	);

	public PGXSUtils (MavenProject project, Log log)
	{
		this.project = project;
		this.log = log;
	}

	/**
	 * Returns a ScriptEngine with some useful engine-scoped bindings
	 * supplied for the convenience of the script.
	 *<p>
	 * These bindings are placed in the engine's scope:
	 *<dl>
	 * <dt>project<dd>The Maven project instance
	 * <dt>utils<dd>This object
	 * <dt>error, warn, info, debug<dd>Consumers of {@link CharSequence} that
	 * will log a message through Maven with the corresponding severity
	 * <dt>diag<dd>A BiConsumer of {@link Diagnostic.Kind} and a
	 * {@link CharSequence}, to log a message through Maven with its severity
	 * determined by the {@code Diagnostic.Kind}.
	 * <dt>runCommand<dd>A function from {@link ProcessBuilder} to {@code int}
	 * that will run the specified command and return its exit status. The
	 * command and arguments are first logged through Maven at {@code debug}
	 * level.
	 * <dt>runWindowsCRuntimeCommand<dd>A function from {@link ProcessBuilder}
	 * to {@code int} that will apply the
	 * {@link #forWindowsCRuntime forWindowsCRuntime} transformation to the
	 * arguments and then run the specified command and return its exit
	 * status. The command and arguments are first logged through Maven at
	 * {@code debug} level, and before the transformation is applied.
	 * <dt>buildPaths<dd>Separates a list of pathnames into those that belong
	 * on a class path and those that belong on a module path.
	 * <dt>getPgConfigProperty<dd>Returns the output of {@code pg_config} when
	 * run with the given single argument.
	 * <dt>isProfileActive<dd>Predicate indicating whether a named Maven profile
	 * is active.
	 * <dt>quoteStringForC<dd>Transforms a {@code String} into a C string
	 * literal representing it.
	 * <dt>resolve<dd>A direct reference to the {code Path.resolve} overload
	 * with {@code Path} parameter types, to work around some versions of
	 * graaljs being unable to determine which overload a script intends.
	 * <dt>setProjectProperty<dd>Sets a property of the Maven project to a
	 * supplied value.
	 *</dl>
	 *
	 * @param script the script block element in the configuration block of the
	 * plugin in the project object model. Its {@code mimetype} or
	 * {@code engine} attribute will be used to find a suitable engine
	 * @return ScriptEngine based on the engine and/or MIME type provided in the
	 * script block
	 */
	ScriptEngine getScriptEngine(PlexusConfiguration script)
	{
		/*
		 * Set the polyglot.js.nashorn-compat system property to true if it is
		 * unset and this is Java >= 15. It would be preferable to set this in
		 * a pom profile rather than hardcoding it here; properties-maven-plugin
		 * can do it, but that doesn't happen in the 'site' lifecycle, and we
		 * use scripting in reports too. In Java >= 15, the Nashorn JavaScript
		 * engine isn't available, and a profile will have arranged for Graal's
		 * JavaScript engine to be on the classpath, but it doesn't behave
		 * compatibly with Nashorn unless this property is set.
		 */
		if ( 0 <= Runtime.version().compareTo(Runtime.Version.parse("15-ea")) )
			System.getProperties()
				.putIfAbsent("polyglot.js.nashorn-compat", "true");

		ScriptEngine engine = null;
		try
		{
			String engineName = script.getAttribute("engine");
			String mimeType = script.getAttribute("mimetype");

			if (engineName == null && mimeType == null)
				throw new IllegalArgumentException("Neither script engine nor" +
					" mimetype defined.");
			else
			{
				ScriptEngineManager manager =
					new ScriptEngineManager(new ScriptEngineLoader(
						ScriptingMojo.class.getClassLoader()));

				if (engineName != null)
					engine = manager.getEngineByName(engineName);

				if (mimeType != null)
					if (engine != null)
					{
						if ( ! engine.getFactory().getMimeTypes()
							.contains(mimeType) )
							log.warn("Specified engine does " +
								"not have given mime type : " + mimeType);
					}
					else
						engine = manager.getEngineByMimeType(mimeType);

				if (engine == null)
					throw new IllegalArgumentException("No suitable engine "
						+ "found for specified engine name or mime type");
			}
			log.debug("Loaded script engine " + engine);
		} catch (Exception e) {
			log.error(e);
		}

		ScriptContext context = engine.getContext();

		/*
		 * Give the script convenient access to the Maven project and this
		 * object.
		 */
		context.setAttribute("project", project, ENGINE_SCOPE);
		context.setAttribute("utils", this, ENGINE_SCOPE);

		/*
		 * Give the script some convenient methods for logging to the Maven log.
		 * Only supply the versions with one CharSequence parameter, in case of
		 * a script engine that might not handle overloads well. The script may
		 * have another way to get access to the Log instance and use its other
		 * methods; these are just for convenience.
		 */
		context.setAttribute("error",
			(Consumer<CharSequence>) log::error, ENGINE_SCOPE);
		context.setAttribute("warn",
			(Consumer<CharSequence>) log::warn, ENGINE_SCOPE);
		context.setAttribute("info",
			(Consumer<CharSequence>) log::info, ENGINE_SCOPE);
		context.setAttribute("debug",
			(Consumer<CharSequence>) log::debug, ENGINE_SCOPE);

		/*
		 * Also provide a specialized method useful for a script that may
		 * handle diagnostics from Java tools.
		 */
		context.setAttribute("diag",
			(BiConsumer<Diagnostic.Kind,CharSequence>)((kind,content) ->
			{
				switch ( kind )
				{
				case ERROR:
					log.error(content);
					break;
				case MANDATORY_WARNING:
				case WARNING:
					log.warn(content);
					break;
				case NOTE:
					log.info(content);
					break;
				case OTHER:
					log.debug(content);
					break;
				}
			}
			), ENGINE_SCOPE);

		/*
		 * Supply a runCommand function to which the script can supply
		 * a ProcessBuilder after configuring it as needed, and an alias
		 * runWindowsCRuntimeCommand that does the same, but applies the
		 * forWindowsCRuntime transformation to the ProcessBuilder's arguments
		 * first. Two aliases are used so that the command arguments can be
		 * logged (at debug level) in either case, and before the transformation
		 * is applied, in the Windows case.
		 */
		context.setAttribute("runCommand",
			(ToIntFunction<ProcessBuilder>) b ->
			{
				log.debug("To run: " + b.command());
				return runCommand(b);
			}, ENGINE_SCOPE);

		context.setAttribute("runWindowsCRuntimeCommand",
			(ToIntFunction<ProcessBuilder>) b ->
			{
				log.debug("To run (needs WindowsCRuntime transformation): " +
					b.command());
				return runCommand(forWindowsCRuntime(b));
			}, ENGINE_SCOPE);

		/*
		 * Convenient access to some other methods provided here.
		 */
		context.setAttribute("buildPaths",
			(Function<List<String>, Map<String, String>>) this::buildPaths,
			ENGINE_SCOPE);

		context.setAttribute("getPgConfigProperty",
			(Function<String, String>) p ->
			{
				try
				{
					return getPgConfigProperty(p);
				}
				catch ( Exception e )
				{
					log.error(e);
					return null;
				}
			}, ENGINE_SCOPE);

		context.setAttribute("isProfileActive",
			(Function<String, Boolean>) this::isProfileActive,
			ENGINE_SCOPE);

		context.setAttribute("quoteStringForC",
			(Function<String, String>) this::quoteStringForC, ENGINE_SCOPE);

		context.setAttribute("setProjectProperty",
			(BiConsumer<String, String>)this::setProjectProperty, ENGINE_SCOPE);

		/*
		 * A graaljs bug (graalvm/graaljs#254) means that when you are passing
		 * a Path object to Path.resolve (which has overloads taking a Path or
		 * a String), graaljs can't decide which one you mean. Provide a resolve
		 * (Path,Path) function to make it a little more blindingly obvious.
		 */
		context.setAttribute("resolve", (BinaryOperator<Path>)Path::resolve,
			ENGINE_SCOPE);

		return engine;
	}

	/**
	 * Returns the input wrapped in double quotes and with internal characters
	 * escaped where appropriate using the C conventions.
	 *
	 * @param s string to be escaped
	 * @return a C string literal representing <var>s</var>
	 */
	public String quoteStringForC (String s)
	{
		Matcher m = mustBeQuotedForC.matcher(s);
		StringBuffer b = new StringBuffer();
		while (m.find())
		{
			if (-1 != m.start(1)) // things that just need a backslash
				m.appendReplacement(b, "\\\\$1");
			else if (-1 != m.start(2)) // things with specific escapes
			{
				char ec = 0;
				switch (m.group(2)) // switch/case uses ===
				{
					case "\u0007":
						ec = 'a';
						break;
					case "\b":
						ec = 'b';
						break;
					case "\f":
						ec = 'f';
						break;
					case "\n":
						ec = 'n';
						break;
					case "\r":
						ec = 'r';
						break;
					case "\t":
						ec = 't';
						break;
					case "\u000B":
						ec = 'v';
						break;
				}
				m.appendReplacement(b, "\\\\" + ec);
			}
			else // it's group 3, use hex escaping
			{
				m.appendReplacement(b,
					"\\\\x" + Integer.toHexString(
						m.group(3).codePointAt(0)) +
						(-1 == m.start(4) ? "" : "\"\"")); // XDigit follows?
			}
		}
		return "\"" + m.appendTail(b) + "\"";
	}

	/**
	 * Returns the string decoded from input bytes using default platform
	 * charset.
	 *
	 * @param bytes byte array to be decoded
	 * @return string decoded from input bytes
	 * @throws CharacterCodingException if unable to decode bytes using
	 *                                  default platform charset
	 */
	public String defaultCharsetDecodeStrict (byte[] bytes)
		throws CharacterCodingException
	{
		return Charset.defaultCharset().newDecoder()
			       .decode(ByteBuffer.wrap(bytes)).toString();
	}

	/**
	 * Returns the output, decoded using default platform charset, of the
	 * {@code pg_config} command executed with the single supplied argument.
	 * <p>
	 * If multiple versions of {@code pg_config} are available or
	 * {@code pg_config} is not present on the path, the system property
	 * {@code pgsql.pgconfig} should be set as an absolute path to the desired
	 * executable.
	 *
	 * @param pgConfigArgument argument to be passed to the command
	 * @return output of the input command executed with the input argument
	 * @throws IOException if unable to read output of the command
	 * @throws InterruptedException if command does not complete successfully
	 */
	public String getPgConfigProperty (String pgConfigArgument)
		throws IOException, InterruptedException
	{
		String pgConfigCommand =
			System.getProperty("pgsql.pgconfig", "pg_config");

		ProcessBuilder processBuilder =
			new ProcessBuilder(pgConfigCommand, pgConfigArgument);
		processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
		Process process = processBuilder.start();
		process.getOutputStream().close();
		byte[] bytes = process.getInputStream().readAllBytes();

		int exitCode = process.waitFor();
		if (exitCode != 0)
			throw new InterruptedException("pg_config process failed and " +
				                               "exited with " + exitCode);
		String pgConfigOutput = defaultCharsetDecodeStrict(bytes);
		return pgConfigOutput.substring(0,
			pgConfigOutput.length() - System.lineSeparator().length());
	}

	/**
	 * Reports the detailed {@code PG_VERSION_STR} for the PostgreSQL version
	 * found to build against.
	 *<p>
	 * This should be found as a C string literal after
	 * {@code #define PG_VERSION_STR} in
	 * <var>includedir_server</var>/{@code pg_config.h}.
	 *<p>
	 * If the value can be found, it is logged at {@code info} level. Otherwise,
	 * the exception(s) responsible will be logged at {@code debug} level.
	 * @param includedir_server pass the result of a previous
	 * {@code getPgConfigProperty(..., "--includedir_server")}
	 */
	public void reportPostgreSQLVersion(String includedir_server)
	{
		Path pg_config_h = Paths.get(includedir_server, "pg_config.h");
		try
		{
			log.info(
				defaultCharsetDecodeStrict(Files.readAllBytes(pg_config_h))
				.replaceFirst(
					"(?ms).*^#define\\s++PG_VERSION_STR\\s++(?-s:(.++))$.*+",
					"Found $1")
			);
		}
		catch ( IOException | IndexOutOfBoundsException e )
		{
			log.debug(
				"in reportPostgreSQLVersion: " +
				iterate(e, Objects::nonNull, Throwable::getCause)
				.map(Object::toString).collect(joining("\nCaused by: "))
			);
		}
	}

	/**
	 * Sets the value of a property for the current project.
	 *
	 * @param property key to use for property
	 * @param value the value of property to set
	 */
	public void setProjectProperty (String property, String value)
	{
		project.getProperties().setProperty(property, value);
	}

	/**
	 * Returns a ProcessBuilder with suitable defaults and arguments added
	 * by the supplied <var>consumer</var>.
	 *
	 * @param consumer function which adds arguments to the ProcessBuilder
	 * @return ProcessBuilder with input arguments and suitable defaults
	 */
	public ProcessBuilder processBuilder(Consumer<List<String>> consumer)
	{
		ProcessBuilder processBuilder = new ProcessBuilder();
		consumer.accept(processBuilder.command());
		processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		processBuilder.directory(new File(project.getBuild().getDirectory(),
			"pljava-pgxs"));
		return processBuilder;
	}

	/**
	 * Executes a ProcessBuilder and returns the exit code of the process.
	 *
	 * @param processBuilder to execute
	 * @return exit code of the executed process or -1 if an exception occurs
	 * during execution
	 */
	public int runCommand(ProcessBuilder processBuilder)
	{
		Path outputDirectoryPath = processBuilder.directory().toPath();
		try
		{
			if (!Files.exists(outputDirectoryPath))
				Files.createDirectories(outputDirectoryPath);
			Process process = processBuilder.start();
			return process.waitFor();
		} catch (Exception e) {
			log.error(e);
		}
		return -1;
	}

	/**
	 * Returns true if the profile with given name exists and is active, false
	 * otherwise.
	 * <p>
	 * A warning is logged if no profile with the input name exists in the
	 * current project.
	 *
	 * @param profileName name of profile to check
	 * @return true if profile exists and is active, false otherwise
	 */
	public boolean isProfileActive(String profileName)
	{
		boolean isValidProfile =
			project.getModel().getProfiles().stream()
				.anyMatch(profile -> profile.getId().equals(profileName));

		if (!isValidProfile)
		{
			log.warn(profileName + " does not exist in " + project.getName());
			return false;
		}

		return project.getActiveProfiles().stream()
			       .anyMatch(profile -> profile.getId().equals(profileName));
	}

	/**
	 * Returns a two-element map with with {@code classpath} and
	 * {@code modulepath} as keys and their joined string paths as the
	 * respective values.
	 *<p>
	 * For each supplied element,
	 * {@link #shouldPlaceOnModulepath shouldPlaceOnModulepath} is used to
	 * determine which path the element is added to.
	 *
	 * @param elements list of elements to build classpath and modulepath from
	 * @return a map containing the {@code classpath} and {@code modulepath}
	 * as separate elements
	 */
	public Map<String, String> buildPaths(List<String> elements)
	{
		List<String> modulepathElements = new ArrayList<>();
		List<String> classpathElements = new ArrayList<>();
		String pathSeparator = System.getProperty("path.separator");
		try
		{
			for (String element : elements)
			{
				if (element.contains(pathSeparator))
					log.warn(String.format("cannot add %s to path because " +
						"it contains path separator %s",
						element, pathSeparator));
				else if (shouldPlaceOnModulepath(element))
					modulepathElements.add(element);
				else
					classpathElements.add(element);
			}
		}
		catch (Exception e)
		{
			log.error(e);
		}
		String modulepath = String.join(pathSeparator, modulepathElements);
		String classpath = String.join(pathSeparator, classpathElements);
		return Map.of("classpath", classpath, "modulepath", modulepath);
	}

	/**
	 * Returns true if the element should be placed on the module path.
	 * <p>
	 * An file path element should be placed on the module path if it points to
	 * <ol>
	 * <li>a directory with a top level {@code module-info.class} file
	 * <li>a {@code JAR} file having a {@code module-info.class} entry or the
	 * {@code Automatic-Module-Name} as a manifest attribute
	 * </ol>
	 *
	 * @param filePath the filepath to check
	 * @return true if input path should go on modulepath, false otherwise
	 * @throws IOException any thrown by the underlying file operations
	 */
	public boolean shouldPlaceOnModulepath(String filePath)
	throws IOException
	{
		Path path = Paths.get(filePath);
		if (Files.isDirectory(path))
		{
			Path moduleInfoFile = path.resolve("module-info.class");
			return Files.exists(moduleInfoFile);
		}

		if (path.getFileName().toString().endsWith(".jar"))
		{
			try(JarFile jarFile = new JarFile(path.toFile()))
			{
				if (jarFile.getEntry("module-info.class") != null)
					return true;
				Manifest manifest = jarFile.getManifest();
				if (manifest == null)
					return false;
				return manifest.getMainAttributes()
					       .containsKey("Automatic-Module-Name");
			}
		}
		return false;
	}

	/**
	 * Returns a list of files with given extension in and below
	 * the input directory.
	 *
	 * @param sourceDirectory root of the tree of files to list
	 * @param extension to filter files to be selected
	 * @return list of strings of absolute paths of files
	 */
	public List<String> getFilesWithExtension(Path sourceDirectory,
	                                          String extension)
	{
		try
		{
			return Files
				       .walk(sourceDirectory)
				       .filter(Files::isRegularFile)
				       .map(Path::toAbsolutePath)
				       .map(Path::toString)
				       .filter(path -> path.endsWith(extension))
				       .collect(java.util.stream.Collectors.toList());
		} catch (Exception e) {
			log.error(e);
		}
		return null;
	}

	/*
	 * This method is duplicated in pljava-packaging/Node.java. If making
	 * changes to this method, review the other occurrence also and replicate
	 * the changes there if desirable.
	 */
	/**
	 * Adjust the command arguments of a {@code ProcessBuilder} so that they
	 * will be recovered correctly on Windows by a target C/C++ program using
	 * the argument parsing algorithm of the usual C run-time code, when it is
	 * known that the command will not be handled first by {@code cmd}.
	 *<p>
	 * This transformation must account for the way the Windows C runtime will
	 * ultimately parse the parameters apart, and also for the behavior of
	 * Java's runtime in assembling the command line that the invoked process
	 * will receive.
	 * @param pb a ProcessBuilder whose command has been set to an executable
	 * that parses parameters using the C runtime rules, and arguments as they
	 * should result from parsing.
	 * @return The same ProcessBuilder, with the argument list rewritten as
	 * necessary to produce the original list as a result of Windows C runtime
	 * parsing.
	 * @throws IllegalArgumentException if the ProcessBuilder does not have at
	 * least the first command element (the executable to run)
	 * @throws UnsupportedOperationException if the arguments passed, or system
	 * properties in effect, produce a case this transformation cannot handle
	 */
	public ProcessBuilder forWindowsCRuntime(ProcessBuilder pb)
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

}
