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
 */
package org.postgresql.pljava.packaging;

import org.gjt.cuspy.JarX;

import java.io.InputStream;

import static java.lang.System.getProperty;
import static java.lang.System.setProperty;

import java.util.Scanner;

import java.util.regex.Matcher;
import static java.util.regex.Pattern.compile;

/**
 * Subclass the JarX extraction tool to provide a {@code resolve} method that
 * replaces prefixes {@code pljava/foo/} in path names stored in the archive
 * with the result of {@code pg_config --foo}.
 */
public class Extractor extends JarX {

	private Matcher m_prefix;
	private int m_fsepLength;

	public static void main(String[] args) throws Exception
	{
		if ( args.length > 0 )
		{
			System.err.println("usage: java -jar filename.jar");
			System.exit(1);
		}

		new Extractor().extract();
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
				pb.redirectErrorStream(true);
				Process proc = pb.start();
				InputStream instream = proc.getInputStream();
				Scanner scanner = new Scanner(instream).useDelimiter("\\A");
				String output = scanner.next();
				int status = proc.waitFor();
				if ( 0 != status ) {
					System.err.println(
						"ERROR: pg_config status is "+status+":\n"+output);
					System.exit(1);
				}
				replacement = output.trim();
				setProperty(propkey, replacement);
			}
			int plen = m_fsepLength - 1; /* original separator had length 1 */
			plen += prefixLength;
			return replacement + platformPath.substring(plen);
		}

		System.err.println("WARNING: extraneous jar entry not extracted: "
			+ storedPath);
		return null;
	}
}
