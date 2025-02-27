/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.lang.module.ModuleDescriptor;

import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Iterator;
import java.util.Objects;

import java.util.stream.Stream;

import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.annotation.Function;
import static org.postgresql.pljava.annotation.Function.Effects.STABLE;

/**
 * Example code to support querying for the modules in Java's boot layer.
 */
public class Modules implements ResultSetProvider.Large {
	/**
	 * Returns information on the named modules in Java's boot module layer.
	 */
	@Function(
		effects = STABLE,
		out = {
			                   "name  pg_catalog.text",
			"any_unqualified_exports  boolean",
			  "any_unqualified_opens  boolean"
		}
	)
	public static ResultSetProvider java_modules()
	{
		return new Modules(
			ModuleLayer.boot().modules().stream().map(Module::getDescriptor)
				.filter(Objects::nonNull));
	}

	private final Iterator<ModuleDescriptor> iterator;
	private final Runnable closer;

	private Modules(Stream<ModuleDescriptor> s)
	{
		iterator = s.iterator();
		closer = s::close;
	}

	@Override
	public boolean assignRowValues(ResultSet receiver, long currentRow)
	throws SQLException
	{
		if ( ! iterator.hasNext() )
			return false;

		ModuleDescriptor md = iterator.next();

		receiver.updateString(1, md.name());

		receiver.updateBoolean(2,
			md.exports().stream().anyMatch(e -> ! e.isQualified()));

		receiver.updateBoolean(3,
			md.isOpen() ||
			md.opens().stream().anyMatch(o -> ! o.isQualified()));

		return true;
	}

	@Override
	public void close()
	{
		closer.run();
	}
}
