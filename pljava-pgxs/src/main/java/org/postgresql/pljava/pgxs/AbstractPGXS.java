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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Class to act as a blueprint for platform specific build configurations in
 * pljava-so/pom.xml
 */
public abstract class AbstractPGXS
{

	/**
	 * Add instructions for compiling the pljava-so C files on your platform
	 * by implementing this method in your configuration block.
	 */
	public abstract int compile(String compiler, List<String> files, Path targetPath,
								 List<String> includes, Map<String, String> defines,
								 List<String> flags);

	/**
	 * Add instructions for linking and producing the pljava-so shared library
	 * artifact on your platform by implementing this method in your
	 * configuration block.
	 */
	public abstract int link(String linker, List<String> flags, List<String> files, Path targetPath);

	/**
	 * Returns a list with all items prefixed with correct include flag symbol.
	 *
	 * This is the default implementation for formatting the list of includes,
	 * and prefixes the includes with -I. For compilers like MSVC that require
	 * different symbols, override this method in your configuration block.
	 */
	public List<String> formatIncludes(List<String> includesList)
	{
		return includesList.stream().map(s -> "-I" + s)
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list with all defines prefixed correctly.
	 *
	 * This is the default implementation for formatting the list of defines.
	 * Each item is prefixed with -D. If the define is associated with a value,
	 * adds equal symbol also followed by the value. If your linker expects a
	 * different format, override the method in your configuration block.
	 */
	public List<String> formatDefines(Map<String, String> definesMap)
	{
		return definesMap.entrySet().stream()
			.map(entry -> {
				String define = "-D" + entry.getKey();
				if (entry.getValue() != null)
					define += "=" + entry.getValue();
				return define;
			})
			.collect(Collectors.toList());
	}

	/**
	 * Returns the input pg_config property as a list of individual flags split
	 * at whitespace, except when quoted, and the quotes removed.
	 */
	public List<String> getPgConfigPropertyAsList(String properties) {
		List<String> propertyList = new ArrayList<>();
		StringBuilder builder = new StringBuilder();
		boolean isInsideQuotes = false;

		for (char x : properties.toCharArray())
		{
			if (x == '\'')
				isInsideQuotes = !isInsideQuotes;
			else if (!isInsideQuotes && x == ' ')
			{
				propertyList.add(builder.toString());
				builder.setLength(0);
			}
			else
				builder.append(x);
		}

		if (builder.length() != 0)
			propertyList.add(builder.toString());
		return propertyList;
	}
}
