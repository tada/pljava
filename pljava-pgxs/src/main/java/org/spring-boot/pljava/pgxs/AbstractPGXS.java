/*
 * Copyright (c) 2020-2024 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 *   Chapman Flack
 */
package org.postgresql.pljava.pgxs;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class to act as a blueprint for platform-specific build configurations in a
 * {@code pom.xml}.
 *<p>
 * A {@code scripted-goal} configuration in the POM should contain a script
 * that somehow selects and supplies a concrete implementation of this abstract
 * class.
 *<p>
 * In {@code pljava-so/pom.xml}, a block of {@code application/javascript} is
 * supplied that contains a {@code configuration} array of JS objects, each of
 * which has a {@code name} entry, a {@code probe} function returning true on
 * some supported platform, and the necessary functions to serve as an
 * implementation of this class. The script selects one whose probe succeeds
 * and, using JSR 223 magic, makes an instance of this class from it.
 *<p>
 * The script can make use of convenience methods implemented here, and also
 * a number of items (such as a {@code runCommand} function) presupplied in the
 * script engine's binding scope by
 * {@link PGXSUtils#getScriptEngine PGXSUtils.getScriptEngine} and by
 * {@link ScriptingMojo#execute ScriptingMojo.execute}).
 */
public abstract class AbstractPGXS
{
	/**
	 * Performs platform-specific compilation of a set of {@code .c} files with
	 * the specified compiler, target path, includes, defines, and flags.
	 *<p>
	 * An implementation should make any needed adjustments to the includes,
	 * defines, and flags, format everything appropriately for the compiler
	 * in question, execute it, and return an exit status (zero on success).
	 */
	public abstract int compile(
		String compiler, List<String> files, Path targetPath,
		List<String> includes, Map<String, String> defines, List<String> flags);

	/**
	 * Performs platform-specific linking of a set of object files with
	 * the specified linker and flags, to produce the shared object at the
	 * specified target path.
	 *<p>
	 * An implementation should make any needed adjustments to the flags, format
	 * everything appropriately for the linker in question, execute it, and
	 * return an exit status (zero on success).
	 */
	public abstract int link(
		String linker, List<String> flags, List<String> files, Path targetPath);

	/**
	 * Returns a list with all items prefixed with correct include flag symbol.
	 *
	 * This is the default implementation for formatting the list of includes,
	 * and prefixes the includes with {@code -I}. For compilers like MSVC that
	 * require different formatting, the script should supply an overriding
	 * implementation of this method.
	 */
	public List<String> formatIncludes(List<String> includesList)
	{
		return includesList.stream().map(s -> "-I" + s)
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list with all defines represented correctly.
	 *
	 * This is the default implementation for formatting the map of defines.
	 * Each item is prefixed with {@code -D}. If the name is mapped to a
	 * non-null value, an {@code =} is appended, followed by the value. For
	 * compilers like MSVC that require different formatting, the script should
	 * supply an overriding implementation of this method.
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
	 * Returns the requested {@code pg_config} property as a list of individual
	 * flags split at whitespace, except when quoted, and the quotes removed.
	 *<p>
	 * The assumed quoting convention is single straight quotes around regions
	 * to be protected, which do not have to be an entire argument. This method
	 * doesn't handle a value that <em>contains</em> a single quote as content;
	 * the intended convention for that case doesn't seem to be documented, and
	 * PostgreSQL's own build breaks in such a case, so there is little need,
	 * for now, to support it here. We don't know, for now, whether the
	 * convention implemented here is also right on Windows.
	 */
	public List<String> getPgConfigPropertyAsList(String properties) {
		Pattern pattern = Pattern.compile("(?:[^\\s']++|'(?:[^']*+)')++");
		Matcher matcher = pattern.matcher(properties);
		return matcher.results()
			.map(MatchResult::group)
			.map(s -> s.replace("'", ""))
			.collect(Collectors.toList());
	}
}
