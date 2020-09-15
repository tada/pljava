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
package org.postgresql.pljava.internal;

import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import java.util.stream.Stream;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import static org.postgresql.pljava.internal.Checked.closing;
import static org.postgresql.pljava.internal.Checked.OptionalBase.ofNullable;

public class CheckedTest
{
	public void compilability()
	{
		try
		{
			Checked.Consumer
			.use((String n) -> { Class.forName(n); })
			.in(l -> { Stream.of("Foo").forEach(l); });
		}
		catch ( ClassNotFoundException e )
		{
		}

		try
		{
			Checked.DoubleConsumer
			.use(v -> {throw new IllegalAccessException();})
			.in(l -> { DoubleStream.of(4.2).forEach(l); });

			Checked.IntConsumer
			.use(v -> {throw new IllegalAccessException();})
			.in(l -> { IntStream.of(42).forEach(l); });

			Checked.LongConsumer
			.use(v -> {throw new IllegalAccessException();})
			.in(l -> { LongStream.of(4).forEach(l); });

			Checked.Runnable
			.use(() -> {throw new IllegalAccessException();})
			.in(r -> { Stream.of("").forEach(o -> {r.run();}); });
		}
		catch ( IllegalAccessException e )
		{
		}

		Boolean zl =
			Checked.Supplier
			.use(() -> Boolean.TRUE)
			.inReturning(s -> s.get());

		boolean z =
			Checked.BooleanSupplier
			.use(() -> true)
			.inBooleanReturning(zs -> zs.getAsBoolean());

		double d =
			Checked.DoubleSupplier
			.use(() -> 4.2)
			.inDoubleReturning(ds -> ds.getAsDouble());

		int i =
			Checked.IntSupplier
			.use(() -> 4)
			.inIntReturning(is -> is.getAsInt());

		long j =
			Checked.LongSupplier
			.use(() -> 4)
			.inLongReturning(ls -> ls.getAsLong());

		byte b =
			Checked.ByteSupplier
			.use(() -> 4)
			.inByteReturning(bs -> bs.getAsByte());

		short s =
			Checked.ShortSupplier
			.use(() -> 4)
			.inShortReturning(ss -> ss.getAsShort());

		char c =
			Checked.CharSupplier
			.use(() -> 4)
			.inCharReturning(cs -> cs.getAsChar());

		float f =
			Checked.FloatSupplier
			.use(() -> 2.4f)
			.inFloatReturning(fs -> fs.getAsFloat());

		try (Checked.AutoCloseable<IllegalAccessException> ac = // Java 10: var
			closing(() -> {throw new IllegalAccessException();}))
		{
		}
		catch ( IllegalAccessException e )
		{
		}

		OptionalDouble opd = ofNullable(4.2);
		OptionalInt opi = ofNullable(4);
		OptionalLong opj = ofNullable(4L);
		Checked.OptionalBoolean opz = ofNullable(true);
		Checked.OptionalByte opb = ofNullable((byte)2);
		Checked.OptionalShort ops = ofNullable((short)2);
		Checked.OptionalChar opc = ofNullable('2');
		Checked.OptionalFloat opf = ofNullable(2f);
	}
}
