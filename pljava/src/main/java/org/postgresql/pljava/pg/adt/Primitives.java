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
package org.postgresql.pljava.pg.adt;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;

/**
 * PostgreSQL primitive numeric and boolean, as the corresponding Java
 * primitive types.
 */
public abstract class Primitives extends Adapter.Container
{
	private Primitives() // no instances
	{
	}

	public static final Int8 INT8_INSTANCE;
	public static final Int4 INT4_INSTANCE;
	public static final Int2 INT2_INSTANCE;
	/**
	 * The PostgreSQL type {@code "char"} (with the quotes, to distinguish it
	 * from the different, standard SQL type), an 8-bit signed value with no
	 * associated character encoding (though often used in PostgreSQL catalogs
	 * with ASCII letters as values).
	 */
	public static final Int1 INT1_INSTANCE;
	public static final Float8 FLOAT8_INSTANCE;
	public static final Float4 FLOAT4_INSTANCE;
	public static final Boolean BOOLEAN_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure(   Int8.class, Via.INT64SX),
				configure(   Int4.class, Via.INT32SX),
				configure(   Int2.class, Via.SHORT),
				configure(   Int1.class, Via.BYTE),
				configure( Float8.class, Via.DOUBLE),
				configure( Float4.class, Via.FLOAT),
				configure(Boolean.class, Via.BOOLEAN)
			});

		INT8_INSTANCE    = new	  Int8(configs[0]);
		INT4_INSTANCE    = new	  Int4(configs[1]);
		INT2_INSTANCE    = new	  Int2(configs[2]);
		INT1_INSTANCE    = new	  Int1(configs[3]);
		FLOAT8_INSTANCE  = new	Float8(configs[4]);
		FLOAT4_INSTANCE  = new	Float4(configs[5]);
		BOOLEAN_INSTANCE = new Boolean(configs[6]);
	}

	/**
	 * Adapter for the {@code int8} type.
	 */
	public static class Int8 extends Adapter.AsLong.Signed<Void>
	{
		private Int8(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.INT8 == pgType;
		}

		public long fetch(Attribute a, long in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code int4} type.
	 */
	public static class Int4 extends Adapter.AsInt.Signed<Void>
	{
		private Int4(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.INT4 == pgType;
		}

		public int fetch(Attribute a, int in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code int2} type.
	 */
	public static class Int2 extends Adapter.AsShort.Signed<Void>
	{
		private Int2(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.INT2 == pgType;
		}

		public short fetch(Attribute a, short in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code "char"} type.
	 */
	public static class Int1 extends Adapter.AsByte.Signed<Void>
	{
		private Int1(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.CHAR == pgType;
		}

		public byte fetch(Attribute a, byte in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code float8} type.
	 */
	public static class Float8 extends Adapter.AsDouble<Void>
	{
		private Float8(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.FLOAT8 == pgType;
		}

		public double fetch(Attribute a, double in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code float4} type.
	 */
	public static class Float4 extends Adapter.AsFloat<Void>
	{
		private Float4(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.FLOAT4 == pgType;
		}

		public float fetch(Attribute a, float in)
		{
			return in;
		}
	}

	/**
	 * Adapter for the {@code boolean} type.
	 */
	public static class Boolean extends Adapter.AsBoolean<Void>
	{
		private Boolean(Configuration c)
		{
			super(c, null);
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.BOOL == pgType;
		}

		public boolean fetch(Attribute a, boolean in)
		{
			return in;
		}
	}
}
