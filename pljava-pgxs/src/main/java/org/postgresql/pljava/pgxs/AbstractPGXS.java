package org.postgresql.pljava.pgxs;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractPGXS
{
	public abstract int compile(String compiler, List<String> files, Path targetPath,
								 List<String> includes, Map<String, String> defines,
								 List<String> flags);

	public abstract int link(String linker, List<String> flags, List<String> files, Path targetPath);

	public List<String> formatIncludes(List<String> includesList)
	{
		return includesList.stream().map(s -> "-I" + s)
			.collect(Collectors.toList());
	}

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

	public List<String> getPgConfigPropertyAsList(String properties) {
		Pattern pattern = Pattern.compile("[^\\s']+|'([^']*)'");
		Matcher matcher = pattern.matcher(properties);
		return matcher.results().map(MatchResult::group).collect(Collectors.toList());
	}
}
