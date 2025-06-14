/*
 * Copyright (c) 2020-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import java.util.Arrays;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;

import javax.annotation.processing.Messager;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * Abstraction of a dependency tag, encompassing {@code Explicit} ones declared
 * in annotations and distinguished by {@code String}s, and others added
 * implicitly such as {@code Type}s known by {@code Identifier.Qualified}.
 */
abstract class DependTag<T>
{
	protected final T m_value;

	protected DependTag(T value)
	{
		m_value = value;
	}

	@Override
	public int hashCode()
	{
		return hash(getClass(), m_value);
	}

	@Override
	public final boolean equals(Object o)
	{
		return equals(o, null);
	}

	public boolean equals(Object o, Messager msgr)
	{
		if ( this == o )
			return true;
		if ( null == o )
			return false;
		return
			getClass() == o.getClass()
				&&  m_value.equals(((DependTag<?>)o).m_value);
	}

	@Override
	public String toString()
	{
		return '(' + getClass().getSimpleName() + ')' + m_value.toString();
	}

	static final class Explicit extends DependTag<String>
	{
		Explicit(String value)
		{
			super(requireNonNull(value));
		}
	}

	static abstract class Named<T extends Identifier> extends DependTag<T>
	{
		Named(T value)
		{
			super(value);
		}

		@Override
		public boolean equals(Object o, Messager msgr)
		{
			if ( this == o )
				return true;
			if ( null == o )
				return false;
			return
				getClass() == o.getClass()
					&&  m_value.equals(((DependTag<?>)o).m_value, msgr);
		}
	}

	static final class Type
	extends Named<Identifier.Qualified<Identifier.Simple>>
	{
		Type(Identifier.Qualified<Identifier.Simple> value)
		{
			super(requireNonNull(value));
		}
	}

	static final class Function
	extends Named<Identifier.Qualified<Identifier.Simple>>
	{
		private DBType[] m_signature;

		Function(
			Identifier.Qualified<Identifier.Simple> value, DBType[] signature)
		{
			super(requireNonNull(value));
			m_signature = signature.clone();
		}

		@Override
		public boolean equals(Object o, Messager msgr)
		{
			if ( ! super.equals(o, msgr) )
				return false;
			Function f = (Function)o;
			if ( m_signature.length != f.m_signature.length )
				return false;
			for ( int i = 0; i < m_signature.length; ++ i )
			{
				if ( null == m_signature[i]  ||  null == f.m_signature[i] )
				{
					if ( m_signature[i] != f.m_signature[i] )
						return false;
					continue;
				}
				if ( ! m_signature[i].equals(f.m_signature[i], msgr) )
					return false;
			}
			return true;
		}

		@Override
		public String toString()
		{
			return super.toString() + Arrays.toString(m_signature);
		}
	}

	static final class Operator
	extends Named<Identifier.Qualified<Identifier.Operator>>
	{
		private DBType[] m_signature;

		Operator(
			Identifier.Qualified<Identifier.Operator> value, DBType[] signature)
		{
			super(requireNonNull(value));
			assert 2 == signature.length : "invalid Operator signature length";
			m_signature = signature.clone();
		}

		@Override
		public boolean equals(Object o, Messager msgr)
		{
			if ( ! super.equals(o, msgr) )
				return false;
			Operator op = (Operator)o;
			if ( m_signature.length != op.m_signature.length )
				return false;
			for ( int i = 0; i < m_signature.length; ++ i )
			{
				if ( null == m_signature[i]  ||  null == op.m_signature[i] )
				{
					if ( m_signature[i] != op.m_signature[i] )
						return false;
					continue;
				}
				if ( ! m_signature[i].equals(op.m_signature[i], msgr) )
					return false;
			}
			return true;
		}

		@Override
		public String toString()
		{
			return super.toString() + Arrays.toString(m_signature);
		}
	}
}
