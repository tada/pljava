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

/**
 * Java {@code Principal} representing a PostgreSQL {@code PROCEDURAL LANGUAGE},
 * which has a name (a simple identifier, not schema-qualified) and is either
 * {@code Sandboxed} (declared with SQL {@code CREATE TRUSTED LANGUAGE} or
 * {@code Unsandboxed}.
 *<p>
 * Only the subclasses, {@code Sandboxed} or {@code Unsandboxed} can be
 * instantiated, or granted permissions in policy.
 */
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

	/**
	 * Returns either {@link Trust#SANDBOXED SANDBOXED} or
	 * {@link Trust#UNSANDBOXED UNSANDBOXED}
	 * according to PostgreSQL's catalog entry for the language.
	 */
	public abstract Trust trust();

	/**
	 * Java {@code Principal} representing a PostgreSQL
	 * {@code PROCEDURAL LANGUAGE} that was declared with the {@code TRUSTED}
	 * keyword and can be used to declare new functions by any role that has
	 * been granted {@code USAGE} permission on it.
	 *<p>
	 * A Java security policy can grant permissions to this {@code Principal}
	 * by class and wildcard name, or by class and the specific name given in
	 * SQL to the language.
	 */
	public static final class Sandboxed extends PLPrincipal
	{
		private static final long serialVersionUID = 55704990613451177L;

		/**
		 * Construct an instance given its name in {@code String} form.
		 *<p>
		 * The name will be parsed as described for
		 * {@link Simple#fromJava Identifier.Simple.fromJava}.
		 */
		public Sandboxed(String name)
		{
			super(name);
		}

		/**
		 * Construct an instance given its name already as an
		 * {@code Identifier.Simple}.
		 */
		public Sandboxed(Simple name)
		{
			super(name);
		}

		/**
		 * Returns {@code SANDBOXED}.
		 */
		@Override
		public Trust trust()
		{
			return Trust.SANDBOXED;
		}
	}

	/**
	 * Java {@code Principal} representing a PostgreSQL
	 * {@code PROCEDURAL LANGUAGE} that was declared <em>without</em> the
	 * {@code TRUSTED} keyword, and can be used to declare new functions only
	 * by a PostgreSQL superuser.
	 *<p>
	 * A Java security policy can grant permissions to this {@code Principal}
	 * by class and wildcard name, or by class and the specific name given in
	 * SQL to the language.
	 */
	public static final class Unsandboxed extends PLPrincipal
	{
		private static final long serialVersionUID = 7487230786813048525L;

		/**
		 * Construct an instance given its name in {@code String} form.
		 *<p>
		 * The name will be parsed as described for
		 * {@link Simple#fromJava Identifier.Simple.fromJava}.
		 */
		public Unsandboxed(String name)
		{
			super(name);
		}

		/**
		 * Construct an instance given its name already as an
		 * {@code Identifier.Simple}.
		 */
		public Unsandboxed(Simple name)
		{
			super(name);
		}

		/**
		 * Returns {@code UNSANDBOXED}.
		 */
		@Override
		public Trust trust()
		{
			return Trust.UNSANDBOXED;
		}
	}
}
