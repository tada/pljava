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

/**
 * Subclass the JarX extraction tool to provide a {@code resolve} method that
 * replaces prefixes {@code pljava/foo/} in path names stored in the archive
 * with the result of {@code pg_config --foo}.
 */
public class Extractor extends JarX {
	public static void main(String[] args) throws Exception
	{
		if ( args.length > 0 )
		{
			System.err.println("usage: java -jar filename.jar");
			System.exit(1);
		}

		new Extractor().extract();
	}
}
