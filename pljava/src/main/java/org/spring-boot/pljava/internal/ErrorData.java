/*
 * Copyright (c) 2004-2021 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.internal;

import static org.postgresql.pljava.internal.Backend.doInPG;

import java.lang.reflect.UndeclaredThrowableException;
import java.sql.SQLException;

/**
 * The <code>ErrorData</code> correspons to the ErrorData obtained
 * using an internal PostgreSQL <code>CopyErrorData</code> call.
 *
 * @author Thomas Hallgren
 */
public class ErrorData
{
	private final State m_state;

	ErrorData(DualState.Key cookie, long resourceOwner, long pointer)
	{
		m_state = new State(cookie, this, resourceOwner, pointer);
	}

	private static class State
	extends DualState.SingleFreeErrorData<ErrorData>
	{
		private State(
			DualState.Key cookie, ErrorData ed, long ro, long ht)
		{
			super(cookie, ed, ro, ht);
		}

		/**
		 * Return the ErrorData pointer.
		 *<p>
		 * This is a transitional implementation: ideally, each method requiring
		 * the native state would be moved to this class, and hold the pin for
		 * as long as the state is being manipulated. Simply returning the
		 * guarded value out from under the pin, as here, is not great practice,
		 * but as long as the value is only used in instance methods of
		 * ErrorData, or subclasses, or something with a strong reference to
		 * this ErrorData, and only on a thread for which
		 * {@code Backend.threadMayEnterPG()} is true, disaster will not strike.
		 * It can't go Java-unreachable while an instance method's on the call
		 * stack, and the {@code Invocation} marking this state's native scope
		 * can't be popped before return of any method using the value.
		 */
		private long getErrorDataPtr() throws SQLException
		{
			pin();
			try
			{
				return guardedLong();
			}
			finally
			{
				unpin();
			}
		}
	}

	/**
	 * Return pointer to native ErrorData structure as a long; use only while
	 * a reference to this class is live and the THREADLOCK is held.
	 */
	private final long getNativePointer()
	{
		try
		{
			return m_state.getErrorDataPtr();
		}
		catch ( SQLException e )
		{
			throw new UndeclaredThrowableException(e, e.getMessage());
		}
	}

	/**
	 * Returns The error level
	 */
	public int getErrorLevel()
	{
		return doInPG(() -> _getErrorLevel(this.getNativePointer()));
	}

	/**
	 * Returns true if the error will be reported to the server log
	 */
	public boolean isOutputToServer()
	{
		return doInPG(() -> _isOutputToServer(this.getNativePointer()));
	}

	/**
	 * Returns true if the error will be reported to the client
	 */
	public boolean isOutputToClient()
	{
		return doInPG(() -> _isOutputToClient(this.getNativePointer()));
	}

	/**
	 * Returns true if funcname inclusion is set
	 * @deprecated The property queried by this method was only used
	 * in PostgreSQL when communicating with old clients over the v2
	 * frontend/backend protocol, superseded in PostgreSQL 7.4. In PG 14
	 * and later, there is no such property, and this method will always
	 * return false.
	 */
	@Deprecated
	public boolean isShowFuncname()
	{
		return doInPG(() -> _isShowFuncname(this.getNativePointer()));
	}

	/**
	 * Returns The file where the error occurred
	 */
	public String getFilename()
	{
		return doInPG(() -> _getFilename(this.getNativePointer()));
	}

	/**
	 * Returns The line where the error occurred
	 */
	public int getLineno()
	{
		return doInPG(() -> _getLineno(this.getNativePointer()));
	}

	/**
	 * Returns the name of the function where the error occurred
	 */
	public String getFuncname()
	{
		return doInPG(() -> _getFuncname(this.getNativePointer()));
	}

	/**
	 * Returns the unencoded ERRSTATE
	 */
	public String getSqlState()
	{
		return doInPG(() -> _getSqlState(this.getNativePointer()));
	}

	/**
	 * Returns the primary error message
	 */
	public String getMessage()
	{
		return doInPG(() -> _getMessage(this.getNativePointer()));
	}
	
	/**
	 * Returns the detailed error message
	 */
	public String getDetail()
	{
		return doInPG(() -> _getDetail(this.getNativePointer()));
	}
	
	/**
	 * Returns the hint message
	 */
	public String getHint()
	{
		return doInPG(() -> _getHint(this.getNativePointer()));
	}
	
	/**
	 * Returns the context message
	 */
	public String getContextMessage()
	{
		return doInPG(() -> _getContextMessage(this.getNativePointer()));
	}
	
	/**
	 * Returns the cursor index into the query string
	 */
	public int getCursorPos()
	{
		return doInPG(() -> _getCursorPos(this.getNativePointer()));
	}
	
	/**
	 * Returns the cursor index into internal query
	 */
	public int getInternalPos()
	{
		return doInPG(() -> _getInternalPos(this.getNativePointer()));
	}
	
	/**
	 * Returns the internally-generated query
	 */
	public String getInternalQuery()
	{
		return doInPG(() -> _getInternalQuery(this.getNativePointer()));
	}
	
	/**
	 * Returns the errno at entry
	 */
	public int getSavedErrno()
	{
		return doInPG(() -> _getSavedErrno(this.getNativePointer()));
	}

	private static native int _getErrorLevel(long pointer);
	private static native boolean _isOutputToServer(long pointer);
	private static native boolean _isOutputToClient(long pointer);
	private static native boolean _isShowFuncname(long pointer);
	private static native String _getFilename(long pointer);
	private static native int _getLineno(long pointer);
	private static native String _getFuncname(long pointer);
	private static native String _getSqlState(long pointer);
	private static native String _getMessage(long pointer);
	private static native String _getDetail(long pointer);
	private static native String _getHint(long pointer);
	private static native String _getContextMessage(long pointer);
	private static native int _getCursorPos(long pointer);
	private static native int _getInternalPos(long pointer);
	private static native String _getInternalQuery(long pointer);
	private static native int _getSavedErrno(long pointer);	/* errno at entry */
}
