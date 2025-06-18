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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import java.nio.file.Path;

import java.util.Collection;
import java.util.Iterator;

import java.util.regex.Pattern;

import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.stream.StreamSupport.stream;

import javax.tools.DocumentationTool; // mentioned in javadoc
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import static javax.tools.JavaFileObject.Kind.HTML;
import javax.tools.StandardJavaFileManager;

/**
 * A {@link ForwardingJavaFileManager} that interposes when asked for an output
 * file of type {@code HTML}, and rewrites {@code href} URLs that contain
 * {@code RELDOTS} as a component.
 *<h2>Purpose</h2>
 *<p>
 * This file manager is intended for use with the {@link DocumentationTool}
 * when {@code -linkoffline} is used to generate links between subprojects
 * (for example, {@code pljava-examples} to {@code pljava-api}). Maven's
 * {@code site:stage} will copy the generated per-subproject documentation trees
 * into a single {@code staging} directory, which can be relocated, deployed to
 * web servers, etc. Therefore, it is both reasonable and desirable for the
 * subproject API docs to refer to each other by relative links. However, the
 * documentation for {@code -linkoffline} states that the relative links should
 * be given as if from the output destination ({@code -d}) directory. That
 * implies that the tool will add the right number of {@code ../} components,
 * when generating links in a file some levels below the {@code -d} directory,
 * so that the resulting relative URL will be correct. And it doesn't. The tool
 * simply doesn't.
 *<p>
 * As a workaround, the {@code -linkoffline} option can be told to produce URLs
 * that contain {@code RELDOTS}, for example,
 * {@code ../../RELDOTS/pljava-api/apidocs}, and this file manager can be used
 * when running the tool. As the HTML files are written, any {@code href} URL
 * that begins with zero or more {@code ../} followed by {@code RELDOTS} will
 * have the {@code RELDOTS} replaced with the right number of {@code ../} to
 * ascend from that file's containing directory to the output destination
 * directory, resulting in relative URLs that are correct in files at any depth
 * in the API docs tree.
 *<p>
 * An alert reader will notice that {@code RELDOTS} is expanded to exactly what
 * {@code {@docRoot}} is supposed to expand to. But experiment showed that
 * {@code {@docRoot}} does not get expanded in a {@code -linkoffline} URL.
 *<h2>Limitations</h2>
 * The postprocessing is done blindly to any rules of HTML syntax. It will
 * simply replace {@code RELDOTS} in any substring of the content resembling
 * <code>href=&quot;../RELDOTS/</code> (with any number, zero or more, of
 * {@code ../} before the {@code RELDOTS}). The example in the preceding
 * sentence was written carefully to avoid being rewritten in this comment.
 *<p>
 * Only the form with a double quote is recognized, as the javadoc tool does not
 * appear to generate the single-quoted form.
 */
public class RelativizingFileManager
extends ForwardingJavaFileManager<StandardJavaFileManager>
implements StandardJavaFileManager
{
	private final Charset outputEncoding;

	/**
	 * Construct a {@code RelativizingFileManager}, given the underlying file
	 * manager from {@link DocumentationTool#getStandardFileManager}, and the
	 * output encoding to be used.
	 *<p>
	 * The javadoc tool requests {@link OutputStream}s for its output files, and
	 * supplies content already encoded, so the encoding is needed in order to
	 * decode them here for simple processing (as {@code java.util.regex} does
	 * not offer byte-domain flavors of patterns and matchers), then re-encode
	 * the result.
	 *<p>
	 * The file manager constructed here must still be configured by passing
	 * the necessary subset of the desired javadoc options to
	 * {@link #handleFirstOptions handleFirstOptions}.
	 * @param fileManager the original file manager to be wrapped by this one
	 * @param outputEncoding the encoding that the caller will be using when
	 * writing bytes to an output file from this manager
	 */
	public RelativizingFileManager(
		StandardJavaFileManager fileManager,
		Charset outputEncoding)
	{
		super(fileManager);
		this.outputEncoding = outputEncoding;
	}

	static final Pattern toReplace = Pattern.compile(
		"(\\shref=\"(?:\\.\\./)*+)RELDOTS/");

	/**
	 * Overridden to return the superclass result unchanged unless the requested
	 * file is of kind {@code HTML}, and in that case to return a file object
	 * that will interpose on the {@code OutputStream} and apply the rewriting.
	 */
	@Override
	public FileObject getFileForOutput(
		Location location /* location */,
		String packageName,
		String relativePath,
		FileObject sibling)
	throws IOException
	{
		FileObject fo = fileManager.getFileForOutput(
			location, packageName, relativePath, sibling);
		if ( ! (fo instanceof JavaFileObject) )
			return fo;
		JavaFileObject jfo = (JavaFileObject)fo;
		if ( ! (HTML == jfo.getKind()) )
			return fo;

		Path fp = asPath(fo);
		Path r =
			stream(getLocationAsPaths(location).spliterator(), false)
			.filter(p -> fp.startsWith(p)).findAny().get();

		int depth = r.relativize(fp).getNameCount() - 1; // -1 for file name

		if ( location.isModuleOrientedLocation() )
			++ depth;

		final String dots = Stream.generate(() -> "../").limit(depth)
			.collect(Collectors.joining());

		return new ForwardingJavaFileObject<>(jfo)
		{
			@Override
			public OutputStream openOutputStream() throws IOException
			{
				final OutputStream os = fileObject.openOutputStream();

				return new ByteArrayOutputStream()
				{
					private boolean closed = false;

					@Override
					public void close() throws IOException
					{
						if ( closed )
							return;
						closed = true;
						super.close();

						try (os; Writer w =
							new OutputStreamWriter(os,
								outputEncoding.newEncoder()))
						{
							ByteBuffer bb = ByteBuffer.wrap(buf, 0, count);
							CharBuffer cb =
								outputEncoding.newDecoder().decode(bb);
							String fixed = toReplace.matcher(cb).replaceAll(
								"$1" + dots);
							w.append(fixed);
						}
					}
				};
			}
		};
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
	 * @param firstOptions an Iterable of options, where those recognized by a
	 * file manager must be first
	 */
	public void handleFirstOptions(Iterable<String> firstOptions)
	{
		Iterator<String> it = firstOptions.iterator();

		while ( it.hasNext() )
			if ( ! handleOption(it.next(), it) )
				break;
	}

	/*
	 * The file manager supplied by the tool is an instance of
	 * StandardJavaFileManager. There is no forwarding version of that, so we
	 * must extend ForwardingJavaFileManager and then supply forwarding versions
	 * of all methods added in StandardJavaFileManager. Those boilerplate
	 * forwarding methods follow.
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
