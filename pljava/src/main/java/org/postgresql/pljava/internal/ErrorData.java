/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
		 * As long as this value is used in instance methods on ErrorData
		 * (or subclasses, or on something that holds a reference to this
		 * ErrorData) and only while they hold Backend.THREADLOCK, it isn't
		 * necessary to also hold the monitor on this State object. The state
		 * can't go java-unreachable while an instance method's on the stack,
		 * and as long as we're on the thread that's in PG, the Invocation that
		 * state is scoped to can't be popped before we return.
		 */
		private long getErrorDataPtr() throws SQLException
		{
			return getPointer();
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
		synchronized(Backend.THREADLOCK)
		{
			return _getErrorLevel(this.getNativePointer());
		}
	}

	/**
	 * Returns true if the error will be reported to the server log
	 */
	public boolean isOutputToServer()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isOutputToServer(this.getNativePointer());
		}
	}

	/**
	 * Returns true if the error will be reported to the client
	 */
	public boolean isOutputToClient()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isOutputToClient(this.getNativePointer());
		}
	}

	/**
	 * Returns true if funcname inclusion is set
	 */
	public boolean isShowFuncname()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _isShowFuncname(this.getNativePointer());
		}
	}

	/**
	 * Returns The file where the error occured
	 */
	public String getFilename()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getFilename(this.getNativePointer());
		}
	}

	/**
	 * Returns The line where the error occured
	 */
	public int getLineno()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getLineno(this.getNativePointer());
		}
	}

	/**
	 * Returns the name of the function where the error occured
	 */
	public String getFuncname()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getFuncname(this.getNativePointer());
		}
	}

	/**
	 * Returns the unencoded ERRSTATE
	 */
	public String getSqlState()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getSqlState(this.getNativePointer());
		}
	}

	/**
	 * Returns the primary error message
	 */
	public String getMessage()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getMessage(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the detailed error message
	 */
	public String getDetail()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getDetail(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the hint message
	 */
	public String getHint()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getHint(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the context message
	 */
	public String getContextMessage()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getContextMessage(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the cursor index into the query string
	 */
	public int getCursorPos()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getCursorPos(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the cursor index into internal query
	 */
	public int getInternalPos()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getInternalPos(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the internally-generated query
	 */
	public String getInternalQuery()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getInternalQuery(this.getNativePointer());
		}
	}
	
	/**
	 * Returns the errno at entry
	 */
	public int getSavedErrno()
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getSavedErrno(this.getNativePointer());
		}
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
	protected native void _free(long pointer);
}
