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
package org.postgresql.pljava;

import java.io.InvalidObjectException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

import org.postgresql.pljava.annotation.Function.Trust;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

public abstract class PLPrincipal extends BasePrincipal
{
	private static final long serialVersionUID = 4876111394761861189L;

	PLPrincipal(String name)
	{
		super(name);
	}

	PLPrincipal(Simple name)
	{
		super(name);
	}

	private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		Class<?> c = getClass();
		if ( c != Sandboxed.class && c != Unsandboxed.class )
			throw new InvalidObjectException(
				"deserializing unknown PLPrincipal subclass: "
				+ c.getName());
	}

	public abstract Trust trust();

	public static final class Sandboxed extends PLPrincipal
	{
		private static final long serialVersionUID = 55704990613451177L;

		public Sandboxed(String name)
		{
			super(name);
		}
		public Sandboxed(Simple name)
		{
			super(name);
		}

		@Override
		public Trust trust()
		{
			return Trust.SANDBOXED;
		}
	}

	public static final class Unsandboxed extends PLPrincipal
	{
		private static final long serialVersionUID = 7487230786813048525L;

		public Unsandboxed(String name)
		{
			super(name);
		}
		public Unsandboxed(Simple name)
		{
			super(name);
		}

		@Override
		public Trust trust()
		{
			return Trust.UNSANDBOXED;
		}
	}
}
