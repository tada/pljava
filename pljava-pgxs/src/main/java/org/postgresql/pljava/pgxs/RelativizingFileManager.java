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

package org.postgresql.pljava.pgxs;

import java.io.File;
import java.io.IOException;

import java.nio.file.Path;

import java.util.Collection;
import java.util.Iterator;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

public class RelativizingFileManager
extends ForwardingJavaFileManager<StandardJavaFileManager>
implements StandardJavaFileManager
{
	public RelativizingFileManager(StandardJavaFileManager fileManager)
	{
		super(fileManager);
	}

	/**
	 * Call {@link #handleOption handleOption} on as many of the first supplied
	 * options as the file manager recognizes.
	 *<p>
	 * Returns when {@link #handleOption handleOption} first returns false,
	 * indicating an option the file manager does not recognize.
	 *<p>
	 * As the options recognized by the standard file manager are generally
	 * those among the "Standard Options" that javadoc inherits from javac
	 * (including the various location-setting options such as
	 * {@code -classpath}, as well as {@code -encoding}), with a little care to
	 * place those first in the argument list to be passed to the tool itself,
	 * the same list can be passed to this method to configure the file manager,
	 * without any more complicated option recognition needed here.
	 */
	public void handleFirstOptions(Iterable<String> firstOptions)
	{
		Iterator<String> it = firstOptions.iterator();

		while ( it.hasNext() )
			if ( ! handleOption(it.next(), it) )
				break;
	}

	/*
	 * The boilerplate StandardJavaFileManager forwards follow.
	 */

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjectsFromFiles(Iterable<? extends File> files)
	{
		return fileManager.getJavaFileObjectsFromFiles(files);
	}

	// @Override only when support horizon advances to >= Java 13
	public Iterable<? extends JavaFileObject>
	getJavaFileObjectsFromPaths(Collection<? extends Path> paths)
	{
		return fileManager.getJavaFileObjectsFromPaths(paths);
	}

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjectsFromPaths(Iterable<? extends Path> paths)
	{
		return fileManager.getJavaFileObjectsFromPaths(paths);
	}

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjects(File... files)
	{
		return fileManager.getJavaFileObjects(files);
	}

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjects(Path... paths)
	{
		return fileManager.getJavaFileObjects(paths);
	}

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjectsFromStrings(Iterable<String> names)
	{
		return fileManager.getJavaFileObjectsFromStrings(names);
	}

	@Override
	public Iterable<? extends JavaFileObject>
	getJavaFileObjects(String... names)
	{
		return fileManager.getJavaFileObjects(names);
	}

	@Override
	public void setLocation(Location location, Iterable<? extends File> files)
	throws IOException
	{
		fileManager.setLocation(location, files);
	}

	@Override
	public void setLocationFromPaths(
		Location location,
		Collection<? extends Path> paths)
	throws IOException
	{
		fileManager.setLocationFromPaths(location, paths);
	}

	@Override
	public void setLocationForModule(
		Location location,
        String moduleName,
        Collection<? extends Path> paths)
	throws IOException
	{
		fileManager.setLocationForModule(location, moduleName, paths);
	}

	@Override
	public Iterable<? extends File> getLocation(Location location)
	{
		return fileManager.getLocation(location);
	}

	@Override
	public Iterable<? extends Path> getLocationAsPaths(Location location)
	{
		return fileManager.getLocationAsPaths(location);
	}

	@Override
	public Path asPath(FileObject file)
	{
		return fileManager.asPath(file);
	}

	@Override
	public void setPathFactory(PathFactory f)
	{
		fileManager.setPathFactory(f);
	}
}
