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

import java.io.IOException;

import java.nio.ByteBuffer;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.adt.Datetime;
import org.postgresql.pljava.adt.Timespan;
import org.postgresql.pljava.adt.spi.Datum;
import org.postgresql.pljava.model.Attribute;
import static org.postgresql.pljava.model.RegNamespace.PG_CATALOG;
import org.postgresql.pljava.model.RegType;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * PostgreSQL date, time, timestamp, and interval types, available in various
 * representations by implementing the corresponding functional interfaces
 * to construct them.
 */
public abstract class DateTimeAdapter extends Adapter.Container
{
	private DateTimeAdapter() // no instances
	{
	}

	private static final Configuration[] s_configs;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration[] configs = AccessController.doPrivileged(
			(PrivilegedAction<Configuration[]>)() -> new Configuration[]
			{
				configure(       Date.class, Via.INT32SX),
				configure(       Time.class, Via.INT64SX),
				configure(     TimeTZ.class, Via.DATUM  ),
				configure(  Timestamp.class, Via.INT64SX),
				configure(TimestampTZ.class, Via.INT64SX),
				configure(   Interval.class, Via.DATUM  )
			});

		s_configs = configs;
	}

	/**
	 * Instances of the date/time/timestamp adapters using the JSR310
	 * {@code java.time} types.
	 *<p>
	 * A holder interface so these won't be instantiated unless wanted.
	 */
	public interface JSR310
	{
		Date<LocalDate>             DATE_INSTANCE =
			new Date<>(Datetime.Date.AsLocalDate.INSTANCE);

		Time<LocalTime>             TIME_INSTANCE =
			new Time<>(Datetime.Time.AsLocalTime.INSTANCE);

		TimeTZ<OffsetTime>          TIMETZ_INSTANCE =
			new TimeTZ<>(Datetime.TimeTZ.AsOffsetTime.INSTANCE);

		Timestamp<LocalDateTime>    TIMESTAMP_INSTANCE =
			new Timestamp<>(Datetime.Timestamp.AsLocalDateTime.INSTANCE);

		TimestampTZ<OffsetDateTime> TIMESTAMPTZ_INSTANCE =
			new TimestampTZ<>(Datetime.TimestampTZ.AsOffsetDateTime.INSTANCE);

		/*
		 * See org.postgresql.pljava.adt.Timespan.Interval for why a reference
		 * implementation for that type is missing here.
		 */
	}

	/**
	 * Adapter for the {@code DATE} type to the functional interface
	 * {@link Datetime.Date Datetime.Date}.
	 */
	public static class Date<T> extends Adapter.As<T,Void>
	{
		private Datetime.Date<T> m_ctor;
		public Date(Datetime.Date<T> ctor)
		{
			super(ctor, null, s_configs[0]);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.DATE == pgType;
		}

		public T fetch(Attribute a, int in)
		{
			return m_ctor.construct(in);
		}
	}

	/**
	 * Adapter for the {@code TIME} type to the functional interface
	 * {@link Datetime.Time Datetime.Time}.
	 */
	public static class Time<T> extends Adapter.As<T,Void>
	{
		private Datetime.Time<T> m_ctor;
		public Time(Datetime.Time<T> ctor)
		{
			super(ctor, null, s_configs[1]);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.TIME == pgType;
		}

		public T fetch(Attribute a, long in)
		{
			return m_ctor.construct(in);
		}
	}

	/**
	 * Adapter for the {@code TIME WITH TIME ZONE} type to the functional
	 * interface {@link Datetime.TimeTZ Datetime.TimeTZ}.
	 */
	public static class TimeTZ<T> extends Adapter.As<T,Void>
	{
		private Datetime.TimeTZ<T> m_ctor;
		public TimeTZ(Datetime.TimeTZ<T> ctor)
		{
			super(ctor, null, s_configs[2]);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.TIMETZ == pgType;
		}

		public T fetch(Attribute a, Datum.Input in)
		throws IOException, SQLException
		{
			try
			{
				in.pin();
				ByteBuffer bb = in.buffer();
				long microsecondsSincePostgresEpoch = bb.getLong();
				int  secondsWestOfPrimeMeridian     = bb.getInt();
				return m_ctor.construct(
					microsecondsSincePostgresEpoch, secondsWestOfPrimeMeridian);
			}
			finally
			{
				in.unpin();
				in.close();
			}
		}
	}

	/**
	 * Adapter for the {@code TIMESTAMP} type to the functional
	 * interface {@link Datetime.Timestamp Datetime.Timestamp}.
	 */
	public static class Timestamp<T> extends Adapter.As<T,Void>
	{
		private Datetime.Timestamp<T> m_ctor;
		public Timestamp(Datetime.Timestamp<T> ctor)
		{
			super(ctor, null, s_configs[3]);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.TIMESTAMP == pgType;
		}

		public T fetch(Attribute a, long in)
		{
			return m_ctor.construct(in);
		}
	}

	/**
	 * Adapter for the {@code TIMESTAMP WITH TIME ZONE} type to the functional
	 * interface {@link Datetime.TimestampTZ Datetime.TimestampTZ}.
	 */
	public static class TimestampTZ<T> extends Adapter.As<T,Void>
	{
		private Datetime.TimestampTZ<T> m_ctor;
		public TimestampTZ(Datetime.TimestampTZ<T> ctor)
		{
			super(ctor, null, s_configs[4]);
			m_ctor = ctor;
		}

		@Override
		public boolean canFetch(RegType pgType)
		{
			return RegType.TIMESTAMPTZ == pgType;
		}

		public T fetch(Attribute a, long in)
		{
			return m_ctor.construct(in);
		}
	}

	/**
	 * Adapter for the {@code INTERVAL} type to the functional
	 * interface {@link Timespan.Interval Timespan.Interval}.
	 */
	public static class Interval<T> extends Adapter.As<T,Void>
	{
		private static final Simple
			s_name_INTERVAL = Simple.fromJava("interval");

		private static RegType s_intervalType;

		private Timespan.Interval<T> m_ctor;
		public Interval(Timespan.Interval<T> ctor)
		{
			super(ctor, null, s_configs[5]);
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
			RegType intervalType = s_intervalType;
			if ( null != intervalType ) // did we match the type and cache it?
				return intervalType == pgType;

			if ( ! s_name_INTERVAL.equals(pgType.name())
				|| PG_CATALOG != pgType.namespace() )
				return false;

			/*
			 * Hang onto this matching RegType for faster future checks.
			 * Because RegTypes are singletons, and reference writes can't
			 * be torn, this isn't evil as data races go.
			 */
			s_intervalType = pgType;
			return true;
		}

		public T fetch(Attribute a, Datum.Input in)
		throws IOException, SQLException
		{
			try
			{
				in.pin();
				ByteBuffer bb = in.buffer();
				long microseconds = bb.getLong();
				int          days = bb.getInt();
				int        months = bb.getInt();
				return m_ctor.construct(microseconds, days, months);
			}
			finally
			{
				in.unpin();
				in.close();
			}
		}
	}
}
