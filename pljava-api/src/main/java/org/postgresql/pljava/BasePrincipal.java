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

import static java.lang.reflect.Modifier.isFinal;

import static java.util.Objects.requireNonNull;

import java.security.Principal;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Abstract base class for {@link Principal}s named by SQL simple identifiers.
 *<p>
 * Subclasses are expected to be either {@code abstract} or {@code final}.
 */
abstract class BasePrincipal implements Principal, Serializable
{
	private static final long serialVersionUID = -3577164744804938351L;

	BasePrincipal(String name)
	{
		this(Simple.fromJava(name));
	}

	BasePrincipal(Simple name)
	{
		m_name = requireNonNull(name);
		assert isFinal(getClass().getModifiers())
				: "instantiating a non-final BasePrincipal subclass";
	}

	private void readObject(ObjectInputStream in)
	throws IOException, ClassNotFoundException
	{
		in.defaultReadObject();
		if ( null == m_name )
			throw new InvalidObjectException(
				"deserializing a BasePrincipal with null name");
	}

	private Simple m_name;

	@Override
	public boolean equals(Object other)
	{
		if ( getClass().isInstance(other) )
			return m_name.equals(((BasePrincipal)other).m_name);
		return false;
	}

	@Override
	public String toString()
	{
		Class<? extends BasePrincipal> c = getClass();
		return c.getCanonicalName()
			.substring(1+c.getPackageName().length()) + ": " + getName();
	}

	@Override
	public int hashCode()
	{
		return m_name.hashCode();
	}

	@Override
	public String getName()
	{
		return m_name.toString();
	}
}
