/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava;

import java.sql.SQLException;

/**
 * @author Thomas Hallgren
 */
public class TriggerException extends SQLException
{
	private static final long serialVersionUID = 5543711707414329116L;

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
				bld.append(td.getTableName());
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
