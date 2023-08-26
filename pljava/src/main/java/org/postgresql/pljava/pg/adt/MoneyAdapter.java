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

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Money;
import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.RegNamespace.PG_CATALOG;
import org.postgresql.pljava.model.RegType;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Adapter for the {@code MONEY} type to the functional interface {@link Money}.
 */
public abstract class MoneyAdapter<T> extends Adapter.As<T,Void>
{
	private static final Simple s_name_MONEY = Simple.fromJava("money");
	private static RegType s_moneyType;
	private final Money<T> m_ctor;

	@SuppressWarnings("removal") // JEP 411
	private static final Configuration s_config =
		AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(MoneyAdapter.class, Via.INT64SX));

	public MoneyAdapter(Money<T> ctor)
	{
		super(ctor, null, s_config);
		m_ctor = ctor;
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		/*
		 * There has to be some kind of rule for which data types deserve
		 * their own RegType constants. The date/time/timestamp ones all do
		 * because JDBC mentions them, but it doesn't mention interval.
		 * So just compare it by name here, unless the decision is made
		 * to have a RegType constant for it too.
		 */
		RegType moneyType = s_moneyType;
		if ( null != moneyType ) // did we match the type and cache it?
			return moneyType == pgType;

		if ( ! s_name_MONEY.equals(pgType.name())
			|| PG_CATALOG != pgType.namespace() )
			return false;

		/*
		 * Hang onto this matching RegType for faster future checks.
		 * Because RegTypes are singletons, and reference writes can't
		 * be torn, this isn't evil as data races go.
		 */
		s_moneyType = pgType;
		return true;
	}

	public T fetch(Attribute a, long scaledToInteger)
	throws IOException, SQLException
	{
		return m_ctor.construct(scaledToInteger);
	}
}
