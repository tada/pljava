/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 */
package org.postgresql.pljava.pgxs;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.ScriptContext;
import static javax.script.ScriptContext.GLOBAL_SCOPE;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import javax.tools.Diagnostic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	 *
	 * @param script the script block element in the configuration block of
	 *                  the plugin in the project
	 * @param log the logger associated with the plugin
	 * @return ScriptEngine based on the engine and mime type set by the user
	 * in the script block
	 */
	static ScriptEngine getScriptEngine(PlexusConfiguration script, Log log)
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
	 * @param s string to be escaped
	 * @return s wrapped in double quotes and with internal characters
	 * escaped where appropriate using the C conventions
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
	 * @param bytes byte array to be decoded
	 * @return string decoded from input bytes using default platform charset
	 * @throws CharacterCodingException if unable to decode bytes using
	 *                                  default platform charset
	 */
	public static String defaultCharsetDecodeStrict (byte[] bytes)
		throws CharacterCodingException
	{
		return Charset.defaultCharset().newDecoder()
			       .decode(ByteBuffer.wrap(bytes)).toString();
	}

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
}
