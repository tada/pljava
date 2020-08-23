package org.postgresql.pljava.pgxs;

import java.nio.file.Path;
import java.util.List;

public abstract class AbstractPGXS
{
	public abstract void probe();
	public abstract void compile(Path sourcePath, Path targetPath,
	                             List<String> includes, List<String> defines,
	                             List<String> flags);
	public abstract void link(Path outputPath);
}
