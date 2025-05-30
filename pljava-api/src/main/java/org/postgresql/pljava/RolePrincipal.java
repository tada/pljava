/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
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

import java.nio.file.attribute.GroupPrincipal;

import java.util.function.Function;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Pseudo;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

public abstract class RolePrincipal extends BasePrincipal
{
	private static final long serialVersionUID = 5650953533699613976L;

	RolePrincipal(String name)
	{
		super(name);
		constrain(IllegalArgumentException::new);
	}

	RolePrincipal(Simple name)
	{
		super(name);
		constrain(IllegalArgumentException::new);
		/*
		 * Ensure the subclasses' PUBLIC singletons really are, by rejecting the
		 * Pseudo.PUBLIC identifier in this constructor. The subclasses use
		 * private constructors that call the specialized one below when
		 * initializing their singletons.
		 */
		if ( s_public == name )
			throw new IllegalArgumentException(
				"attempt to create non-singleton PUBLIC RolePrincipal");
	}

	RolePrincipal(Pseudo name)
	{
		super(name);
		constrain(IllegalArgumentException::new);
	}

	private final <E extends Exception> void constrain(Function<String,E> exc)
	throws E
	{
		Class<?> c = getClass();
		if ( c != Authenticated.class && c != Session.class
			&& c != Outer.class && c != Current.class )
			throw exc.apply(
				"forbidden to create unknown RolePrincipal subclass: "
				+ c.getName());

		/*
		 * Unlike many cases where a delimited identifier can be used whose
		 * regular-identifier form is a reserved word, PostgreSQL in fact
		 * forbids giving any role a name that the regular identifier public
		 * would match, even if the name is quoted.
		 */
		if ( ( "public".equals(m_name.nonFolded())
			|| "public".equals(m_name.pgFolded()) ) && m_name != s_public )
			throw exc.apply(
				"forbidden to create a RolePrincipal with name " +
				"that matches \"public\" by PostgreSQL rules");
	}

	private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		constrain(InvalidObjectException::new);
	}

	static final Pseudo s_public = Pseudo.PUBLIC;

	/**
	 * Compare two {@code RolePrincipal}s for equality, with special treatment
	 * for the {@code PUBLIC} ones.
	 *<p>
	 * Each concrete subclass of {@code RolePrincipal} has a singleton
	 * {@code PUBLIC} instance, which will only compare equal to itself (this
	 * method is not the place to say everything matches {@code PUBLIC}, because
	 * {@code equals} should be symmetric, and security checks should not be).
	 * Otherwise, the result is that of
	 * {@link Identifier#equals(Object) Identifier.equals}.
	 *<p>
	 * Note that these {@code PUBLIC} instances are distinct from the wild-card
	 * principal names that can appear in the Java policy file: those are
	 * handled without ever instantiating the class, and simply match any
	 * principal with the identically-spelled class name.
	 */
	@Override
	public final boolean equals(Object other)
	{
		if ( this == other )
			return true;
		/*
		 * Because the pseudo "PUBLIC" instances are restricted to being
		 * singletons (one per RolePrincipal subclass), the above test will have
		 * already handled the matching case for those. Below, if either one is
		 * a PUBLIC instance, its m_name won't match anything else, which is ok
		 * because of the PostgreSQL rule that no role can have a potentially
		 * matching name anyway.
		 */
		if ( ! getClass().isInstance(other) )
			return false;
		RolePrincipal o = (RolePrincipal)other;
		return m_name.equals(o.m_name);
	}

	public static final class Authenticated extends RolePrincipal
	{
		private static final long serialVersionUID = -4558155344619605758L;

		public static final Authenticated PUBLIC = new Authenticated(s_public);

		public Authenticated(String name)
		{
			super(name);
		}

		public Authenticated(Simple name)
		{
			super(name);
		}

		private Authenticated(Pseudo name)
		{
			super(name);
		}

		private Object readResolve() throws ObjectStreamException
		{
			return m_name == s_public ? PUBLIC : this;
		}
	}

	public static final class Session extends RolePrincipal
	{
		private static final long serialVersionUID = -598305505864518470L;

		public static final Session PUBLIC = new Session(s_public);

		public Session(String name)
		{
			super(name);
		}

		public Session(Simple name)
		{
			super(name);
		}

		private Session(Pseudo name)
		{
			super(name);
		}

		private Object readResolve() throws ObjectStreamException
		{
			return m_name == s_public ? PUBLIC : this;
		}
	}

	public static final class Outer extends RolePrincipal
	{
		private static final long serialVersionUID = 2177159367185354785L;

		public static final Outer PUBLIC = new Outer(s_public);

		public Outer(String name)
		{
			super(name);
		}

		public Outer(Simple name)
		{
			super(name);
		}

		private Outer(Pseudo name)
		{
			super(name);
		}

		private Object readResolve() throws ObjectStreamException
		{
			return m_name == s_public ? PUBLIC : this;
		}
	}

	public static final class Current extends RolePrincipal
	implements GroupPrincipal
	{
		private static final long serialVersionUID = 2816051825662188997L;

		public static final Current PUBLIC = new Current(s_public);

		public Current(String name)
		{
			super(name);
		}

		public Current(Simple name)
		{
			super(name);
		}

		private Current(Pseudo name)
		{
			super(name);
		}

		private Object readResolve() throws ObjectStreamException
		{
			return m_name == s_public ? PUBLIC : this;
		}
	}
}
