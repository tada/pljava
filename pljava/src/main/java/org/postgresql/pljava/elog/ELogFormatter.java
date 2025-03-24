/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.elog;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.MessageFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * A default formatter for the ELogHandler.
 *
 * @author Thomas Hallgren
 */
public class ELogFormatter extends Formatter
{
	private final static MessageFormat s_tsFormatter = new MessageFormat(
			"{0,date,dd MMM yy} {0,time,HH:mm:ss} {1} {2}");

	private final Date m_timestamp = new Date();
	private final Object m_args[] = new Object[] { m_timestamp, null, null };
	private final StringBuffer m_buffer = new StringBuffer();

	/**
	 * Format the given LogRecord.
	 * @param record the log record to be formatted.
	 * @return a formatted log record
	 */
	public synchronized String format(LogRecord record)
	{
		StringBuffer sb = m_buffer;
		sb.setLength(0);

		m_timestamp.setTime(record.getMillis());
		String tmp = record.getSourceClassName();
		m_args[1] = (tmp == null) ? record.getLoggerName() : tmp;
		m_args[2] = this.formatMessage(record);
		s_tsFormatter.format(m_args, sb, null);

		Throwable thrown = record.getThrown();
		if(thrown != null)
		{
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println(); /* line.separator safely cached in JVM initPhase1 */
			record.getThrown().printStackTrace(pw);
			pw.close();
			sb.append(sw.toString());
		}
		return sb.toString();
	}
}
