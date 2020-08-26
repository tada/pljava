package org.postgresql.pljava.pgxs;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractPGXS
{
	public abstract void probe();
	public abstract void compile(String compiler, List<String> files, Path targetPath,
								 List<String> includes, List<String> defines,
								 List<String> flags);

	public abstract void link(String linker, List<String> flags,List<String> files, Path targetPath);

	public List<String> formatIncludes(List<String> includesList)
	{
		return includesList.stream().map(s -> s.startsWith("-I") ? s : "-I" + s)
			.collect(Collectors.toList());
	}

	public List<String> formatDefines(List<String> definesList)
	{
		return definesList.stream().map(s -> s.startsWith("-D") ? s : "-D" + s)
			.collect(Collectors.toList());
	}

	public List<String> getPgConfigPropertyAsList(String properties) {
		Pattern pattern = Pattern.compile("[^\\s']+|'([^']*)'");
		Matcher matcher = pattern.matcher(properties);
		return matcher.results().map(MatchResult::group).collect(Collectors.toList());
	}
}
