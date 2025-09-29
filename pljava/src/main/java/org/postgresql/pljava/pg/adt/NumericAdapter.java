/*
 * Copyright (c) 2023 Tada AB and other contributors, as listed below.
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

import java.io.IOException;

import java.math.BigDecimal;

import java.nio.ShortBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Numeric;
import org.postgresql.pljava.adt.Numeric.Kind;
import org.postgresql.pljava.adt.Numeric.AsBigDecimal;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegType;

/**
 * Adapter for the {@code NUMERIC} type to the functional interface
 * {@link Numeric}.
 */
public class NumericAdapter<T> extends Adapter.As<T,Void>
{
	private final Numeric<T> m_ctor;

	@SuppressWarnings("removal") // JEP 411
	private static final Configuration s_config =
		AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(NumericAdapter.class, Via.DATUM));

	public static final NumericAdapter<BigDecimal> BIGDECIMAL_INSTANCE =
		new NumericAdapter<>(AsBigDecimal.INSTANCE);

	public NumericAdapter(Numeric<T> ctor)
	{
		super(ctor, null, s_config);
		m_ctor = ctor;
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.NUMERIC == pgType;
	}

	public T fetch(Attribute a, Datum.Input in) throws SQLException
	{
		in.pin();
		try
		{
			ShortBuffer b =
				in.buffer().order(nativeOrder()).asShortBuffer();

			/*
			 * Magic numbers used below are not exposed in .h files, but
			 * only found in PostgreSQL's utils/adt/numeric.c. Most are used
			 * naked here, rather than named, if they aren't needed in many
			 * places and the usage is clear in context. Regression tests
			 * are the only way to confirm they are right anyway.
			 */

			short header = b.get();

			boolean isShort = 0 != (header & 0x8000);

			Kind k;

			switch ( header & 0xF000 )
			{
			case 0xC000: k = Kind.NAN;         break;
			case 0xD000: k = Kind.POSINFINITY; break;
			case 0xF000: k = Kind.NEGINFINITY; break;
			default:
				int displayScale;
				int weight;

				if ( isShort )
				{
					k = 0 != (header & 0x2000) ? Kind.NEGATIVE : Kind.POSITIVE;
					displayScale = (header & 0x1F80) >>> 7;
					weight = ( (header & 0x007F) ^ 0x0040 ) - 0x0040;// sign ext
				}
				else
				{
					k = 0 != (header & 0x4000) ? Kind.NEGATIVE : Kind.POSITIVE;
					displayScale = header & 0x3FFF;
					weight = b.get();
				}

				short[] base10000Digits = new short [ b.remaining() ];
				b.get(base10000Digits);

				return m_ctor.construct(
					k, displayScale, weight, base10000Digits);
			}

			return m_ctor.construct(k, 0, 0, new short[0]);
		}
		finally
		{
			in.unpin();
		}
	}
}
