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
package org.postgresql.pljava.sqlj;

import java.io.IOException;

import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/**
 * Provider for an {@code sqlj:jarname} URL stream handler.
 *<p>
 * This is only used to allow the security policy to grant permissions to jars
 * by name. The handler is otherwise nonfunctional; its {@code openConnection}
 * method throws an exception.
 */
public class Handler extends URLStreamHandlerProvider
{
	private static final Handler INSTANCE = new Handler();

	public static URLStreamHandlerProvider provider()
	{
		return INSTANCE;
	}

	@Override
	public URLStreamHandler createURLStreamHandler(String protocol)
	{
		switch ( protocol )
		{
		case "sqlj":
			return SQLJ.INSTANCE;
		default:
			return null;
		}
	}

	static class SQLJ extends URLStreamHandler
	{
		static final SQLJ INSTANCE = new SQLJ();

		@Override
		protected URLConnection openConnection(URL u) throws IOException
		{
			throw new IOException(
				"URL of sqlj: protocol can't really be opened");
		}

		@Override
		protected void parseURL(URL u, String spec, int start, int limit)
		{
			if ( spec.length() > limit )
				throw new IllegalArgumentException(
					"sqlj: URL should not contain #");
			if ( spec.length() == start )
				throw new IllegalArgumentException(
					"sqlj: URL should not have empty path part");
			setURL(u, u.getProtocol(), null, -1, null, null,
				spec.substring(start), null, null);
		}
	}
}
