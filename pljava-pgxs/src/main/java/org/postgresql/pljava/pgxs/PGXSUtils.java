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
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.script.ScriptContext.GLOBAL_SCOPE;

/**
 * Utility methods to simplify and hide the bland implementation details
 * for writing JavaScript snippets.
 */
public final class PGXSUtils
{
	static final Pattern mustBeQuotedForC = Pattern.compile(
		"([\"\\\\]|(?<=\\?)\\?(?=[=(/)'<!>-]))|" +  // (just insert backslash)
		"([\\a\b\\f\\n\\r\\t\\x0B])|" +             // (use specific escapes)
		"(\\p{Cc}((?=\\p{XDigit}))?)" // use hex, note whether an XDigit follows
	);

	private PGXSUtils ()
	{
	}

	/**
	 * Returns a ScriptEngine with some basic utilities for scripting.
	 *
	 * @param script the script block element in the configuration block of
	 *                  the plugin in the project
	 * @param log the logger associated with the plugin
	 * @param project the maven project requesting the ScriptEngine
	 *
	 * @return ScriptEngine based on the engine and mime type provided in the
	 * script block
	 */
	static ScriptEngine getScriptEngine(PlexusConfiguration script, Log log,
	                                    MavenProject project)
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
				ScriptEngineManager manager = new ScriptEngineManager(
					new ScriptEngineLoader(ScriptingMojo.class.getClassLoader()));

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

		/*
		 * Give the script some convenient methods for logging to the Maven log.
		 * Only supply the versions with one CharSequence parameter, in case of
		 * a script engine that might not handle overloads well. The script may
		 * have another way to get access to the Log instance and use its other
		 * methods; these are just for convenience.
		 */
		ScriptContext context = engine.getContext();
		context.setAttribute("debug",
			(Consumer<CharSequence>) log::debug, GLOBAL_SCOPE);
		context.setAttribute("error",
			(Consumer<CharSequence>) log::error, GLOBAL_SCOPE);
		context.setAttribute("warn",
			(Consumer<CharSequence>) log::warn, GLOBAL_SCOPE);
		context.setAttribute("info",
			(Consumer<CharSequence>) log::info, GLOBAL_SCOPE);
		context.setAttribute("isProfileActive",
			(Function<String, Boolean>) id -> isProfileActive(log, project, id),
			GLOBAL_SCOPE);
		context.setAttribute("buildPaths",
			(Function<List<String>, Map<String, String>>) elements -> buildPaths(log, elements),
			GLOBAL_SCOPE);

		context.setAttribute("processBuilder",
			(Function<Consumer<List<String>>, ProcessBuilder>)
				consumer -> processBuilder(project, consumer),
			GLOBAL_SCOPE);

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
			), GLOBAL_SCOPE);

		return engine;
	}

	/**
	 * Returns the input wrapped in double quotes and with internal characters
	 * escaped where appropriate using the C conventions.
	 *
	 * @param s string to be escaped
	 * @return a C compatible String enclosed in double quotes
	 */
	public static String quoteStringForC (String s)
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
	 * Returns the string decoded from input bytes using default platform charset.
	 *
	 * @param bytes byte array to be decoded
	 * @return string decoded from input bytes
	 * @throws CharacterCodingException if unable to decode bytes using
	 *                                  default platform charset
	 */
	public static String defaultCharsetDecodeStrict (byte[] bytes)
		throws CharacterCodingException
	{
		return Charset.defaultCharset().newDecoder()
			       .decode(ByteBuffer.wrap(bytes)).toString();
	}

	/**
	 * Returns the output, decoded using default platform charset, of the input
	 * command executed with the input argument.
	 * <p>
	 * If the input parameter {@code pgConfigCommand} is empty or null,
	 * {@code pg_config} is used as the default value. If multiple version of
	 * {@code pg_config} are available or {@code pg_config} is not present on
	 * the path, consider passing an absolute path to {@code pg_config}. It is
	 * also recommended that only a single property be passed at a time.
	 *
	 * @param pgConfigCommand pg_config command to execute
	 * @param pgConfigArgument argument to be passed to the command
	 * @return output of the input command executed with the input argument
	 * @throws IOException if unable to read output of the command
	 * @throws InterruptedException if command does not complete successfully
	 */
	public static String getPgConfigProperty (String pgConfigCommand,
	                                          String pgConfigArgument)
		throws IOException, InterruptedException
	{
		if (pgConfigCommand == null || pgConfigCommand.isEmpty())
			pgConfigCommand = "pg_config";

		ProcessBuilder processBuilder = new ProcessBuilder(pgConfigCommand,
			pgConfigArgument);
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
	 * @param project maven project invoking the method
	 * @param consumer function which adds arguments to the ProcessBuilder
	 *
	 * @return ProcessBuilder with input arguments and suitable defaults
	 */
	public static ProcessBuilder processBuilder(MavenProject project,
	                                            Consumer<List<String>> consumer)
	{
		ProcessBuilder processBuilder = new ProcessBuilder();
		consumer.accept(processBuilder.command());
		processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
		processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
		File outputDirectory = new File(project.getBuild().getDirectory(), "pljava-pgxs");
		try
		{
			Files.createDirectories(outputDirectory.toPath());
			processBuilder.directory(outputDirectory);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return processBuilder;
	}

	/**
	 * Returns true if the profile with given name exists and is active, false
	 * otherwise.
	 * <p>
	 * A warning is logged if the no profile with the input name exists in the
	 * current project.
	 *
	 * @param logger plugin logger instance to log warnings
	 * @param project maven project in which to check profiles
	 * @param profileName name of profile to check
	 * @return true if profile exists and is active, false otherwise
	 */
	public static boolean isProfileActive(Log logger,
	                                      MavenProject project,
	                                      String profileName)
	{
		boolean isValidProfile =
			project.getModel().getProfiles().stream()
				.anyMatch(profile -> profile.getId().equals(profileName));

		if (!isValidProfile)
		{
			logger.warn(profileName + " does not exist in " + project.getName());
			return false;
		}

		return project.getActiveProfiles().stream()
			       .anyMatch(profile -> profile.getId().equals(profileName));
	}

	/**
	 * Returns a map with two elements with {@code classpath} and {@code modulepath}
	 * as keys and their joined string paths as the respective values.
	 *
	 * @param logger Maven Log instance for diagnostics
	 * @param elements list of elements to build classpath and modulepath from
	 * @return a map containing the {@code classpath} and {@code modulepath}
	 * as separate elements
	 */
	public static Map<String, String> buildPaths(Log logger,
	                                             List<String> elements)
	{
		List<String> modulepathElements = new ArrayList<>();
		List<String> classpathElements = new ArrayList<>();
		String pathSeparator = System.getProperty("path.separator");
		try
		{
			for (String element : elements)
			{
				if (element.contains(pathSeparator))
					logger.warn(String.format("cannot add %s to path because " +
						"it contains path separator %s", element, pathSeparator));
				else if (shouldPlaceOnModulepath(element))
					modulepathElements.add(element);
				else
					classpathElements.add(element);
			}
		}
		catch (Exception e)
		{
			logger.error(e);
		}
		String modulepath = String.join(pathSeparator, modulepathElements);
		String classpath = String.join(pathSeparator, classpathElements);
		return Map.of("classpath", classpath, "modulepath", modulepath);
	}

	/**
	 * Returns true if the element should be placed on the module path.
	 * <p>
	 * An file path element should be placed on the module path if it points to
	 * 1) a directory with a top level {@code module-info.class} file
	 * 2) a {@code JAR} file having a {@code module-info.class} entry or the
	 * {@code Automatic-Module-Name} as a manifest attribute
	 *
	 * @param filePath the filepath to check whether is a module
	 * @return true if input path should go on modulepath, false otherwise
	 * @throws IOException any thrown by the underlying file operations
	 */
	public static boolean shouldPlaceOnModulepath(String filePath)
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
}
