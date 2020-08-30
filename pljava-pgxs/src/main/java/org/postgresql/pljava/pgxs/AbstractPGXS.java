package org.postgresql.pljava.pgxs;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
