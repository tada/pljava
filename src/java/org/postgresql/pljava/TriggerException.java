/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import java.sql.SQLException;

import org.postgresql.pljava.internal.TriggerData;

/**
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class TriggerException extends SQLException
{
	private static boolean s_recursionLock = false;

	public static final String TRIGGER_ACTION_EXCEPTION = "09000";

	private static final String makeMessage(TriggerData td, String message)
	{
		StringBuffer bld = new StringBuffer();
		bld.append("In Trigger ");
		if(!s_recursionLock)
		{
			s_recursionLock = true;
			try
			{
				bld.append(td.getName());
				bld.append(" on relation ");
				bld.append(td.getRelation().getName());
			}
			catch(SQLException e)
			{
				bld.append("(exception while generating exception message)");
			}
			finally
			{
				s_recursionLock = false;
			}
		}
		if(message != null)
		{
			bld.append(": ");
			bld.append(message);
		}
		return bld.toString();
	}

	public TriggerException(TriggerData td)
	{
		super(makeMessage(td, null), TRIGGER_ACTION_EXCEPTION);
	}

	public TriggerException(TriggerData td, String reason)
	{
		super(makeMessage(td, reason), TRIGGER_ACTION_EXCEPTION);
	}
}
