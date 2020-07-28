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

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.tools.DocumentationTool;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PGXSUtils
{
	static Pattern mustBeQuotedForC = Pattern.compile(
		"([\"\\\\]|(?<=\\?)\\?(?=[=(/)'<!>-]))|" +  // (just insert backslash)
			"([\\a\b\\f\\n\\r\\t\\x0B])|" +     // (use specific escapes)
			"(\\p{Cc}((?=\\p{XDigit}))?)"
		// use hex, note whether an XDigit follows
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
						if (engine.getFactory().getMimeTypes().contains(mimeType))
							log.warn("Specified engine does " +
								"not have given mime type : " + mimeType);
					}
					else
						engine = manager.getEngineByMimeType(mimeType);

				if (engine == null)
					throw new IllegalArgumentException("No suitable engine "
						+ "found for specified engine name or mime type");
			}
			log.debug(engine.toString());
		} catch (Exception e) {
			log.error(e);
		}
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

	/**
	 *
	 * @param project maven project for the property key-value pair is to be set
	 * @param property key for the property to set
	 * @param propertyValue value of the property
	 */
	public static void setPgConfigProperty (MavenProject project,
	                                        String property,
	                                        String propertyValue)
	{
		project.getProperties().setProperty(property, propertyValue);
	}

	/**
	 *
	 * @param javadocArguments arguments to be passed to the documentation tool
	 */
	public static void executeDocumentationTool(List<String> javadocArguments)
	{
		DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
		DocumentationTool.DocumentationTask task = tool.getTask(null,
		null, null, null, javadocArguments, null);
		task.call();
	}

}
