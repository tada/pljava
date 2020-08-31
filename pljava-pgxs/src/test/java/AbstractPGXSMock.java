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
import org.postgresql.pljava.pgxs.AbstractPGXS;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class AbstractPGXSMock extends AbstractPGXS
{
	@Override
	public int compile(String compiler, List<String> files, Path targetPath,
					   List<String> includes, Map<String, String> defines,
					   List<String> flags)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public int link(String linker, List<String> flags, List<String> files,
					Path targetPath)
	{
		throw new UnsupportedOperationException();
	}
}
