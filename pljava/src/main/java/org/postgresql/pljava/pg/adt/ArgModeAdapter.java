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

import java.util.List;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Array.AsFlatList;
import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegProcedure;
import org.postgresql.pljava.model.RegProcedure.ArgMode;
import org.postgresql.pljava.model.RegType;

/**
 * {@link ArgMode ArgMode} from a {@code "char"} column.
 *<p>
 * This adapter is arguably too specialized to deserve to exist, but it does,
 * to support the {@link RegProcedure#argModes RegProcedure.argModes} array
 * property. Once array adapter contracts have been generalized to support
 * a primitive-typed element adapter (which has to happen anyway), an
 * array-of-{@code ArgMode} type will be trivially achievable with a contract,
 * and this adapter can go away.
 */
public class ArgModeAdapter extends Adapter.As<ArgMode,Void>
{
	public static final ArgModeAdapter INSTANCE;

	public static final ArrayAdapter<List<ArgMode>,?> LIST_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(ArgModeAdapter.class, Via.BYTE));

		INSTANCE = new ArgModeAdapter(config);

		LIST_INSTANCE = new ArrayAdapter<>(
			AsFlatList.of(AsFlatList::nullsIncludedCopy), INSTANCE);
	}

	private ArgModeAdapter(Configuration c)
	{
		super(c, null, null);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		return RegType.CHAR == pgType;
	}

	public ArgMode fetch(Attribute a, byte in)
	{
		switch ( in )
		{
		case (byte)'i':
			return ArgMode.IN;
		case (byte)'o':
			return ArgMode.OUT;
		case (byte)'b':
			return ArgMode.INOUT;
		case (byte)'v':
			return ArgMode.VARIADIC;
		case (byte)'t':
			return ArgMode.TABLE;
		default:
			throw new UnsupportedOperationException(String.format(
				"Unrecognized procedure/function argument mode value %#x", in));
		}
	}
}
