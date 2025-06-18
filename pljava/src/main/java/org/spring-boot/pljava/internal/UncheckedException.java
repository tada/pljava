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

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * An unchecked exception to efficiently wrap checked Throwables.
 *<p>
 * This exception does not carry a message or stack trace of its own; most of
 * its methods proxy through to those of its 'cause', so that it does not appear
 * as an extra layer of indirection in a typical stack trace. It has one
 * specific new method, {@link #unwrap unwrap}, to obtain the actual wrapped
 * throwable (as {@code getCause} is proxied to return the wrapped throwable's
 * cause).
 */
public final class UncheckedException extends RuntimeException
{
	/**
	 * Return the exception <em>e</em> as a {@code RuntimeException}.
	 *<p>
	 * Intended for use in a {@code throw unchecked(e);} construct.
	 * If <em>e</em> is already an unchecked exception, it is simply returned;
	 * otherwise, it is returned wrapped.
	 * @return the supplied exception, possibly wrapped
	 */
	public static RuntimeException unchecked(Exception e)
	{
		if ( e instanceof RuntimeException )
			return (RuntimeException)e;
		return new UncheckedException(e);
	}

	/**
	 * Return the throwable <em>t</em> as a {@code RuntimeException}.
	 *<p>
	 * Intended for use in a {@code throw unchecked(t);} construct.
	 * If <em>t</em> is already a {@code RuntimeException}, it is simply
	 * returned; if it is an {@code Error}, it is thrown from this method;
	 * otherwise, it is returned wrapped.
	 * @return the supplied exception, possibly wrapped
	 * @throws Error or a subclass, if that's what t is
	 */
	public static RuntimeException unchecked(Throwable t)
	{
		if ( t instanceof Error )
			throw (Error)t;
		if ( t instanceof RuntimeException )
			return (RuntimeException)t;
		return new UncheckedException(t);
	}

	private UncheckedException(Throwable t)
	{
		super(null, null != t ? t : new NullPointerException(
				"null 'cause' passed to UncheckedException constructor"),
				true, false);
	}

	/**
	 * Return the {@code Throwable} that this {@code UncheckedException} wraps.
	 *<p>
	 * The familiar inherited methods proxy through to the wrapped throwable
	 * (so {@code getCause} will return <em>its</em> cause, and so on); this
	 * distinct method is provided to undo the wrapping.
	 * @return the wrapped Throwable
	 */
	public Throwable unwrap()
	{
		return super.getCause();
	}

	@Override
	public Throwable fillInStackTrace()
	{
		super.getCause().fillInStackTrace();
		return this;
	}

	@Override
	public Throwable getCause()
	{
		return super.getCause().getCause();
	}

	@Override
	public String getLocalizedMessage()
	{
		return super.getCause().getLocalizedMessage();
	}

	@Override
	public String getMessage()
	{
		return super.getCause().getMessage();
	}

	@Override
	public StackTraceElement[] getStackTrace()
	{
		return super.getCause().getStackTrace();
	}

	@Override
	public Throwable initCause(Throwable cause)
	{
		super.getCause().initCause(cause);
		return this;
	}

	@Override
	public void printStackTrace()
	{
		super.getCause().printStackTrace();
	}

	@Override
	public void printStackTrace(PrintStream s)
	{
		super.getCause().printStackTrace(s);
	}

	@Override
	public void printStackTrace(PrintWriter s)
	{
		super.getCause().printStackTrace(s);
	}

	@Override
	public void setStackTrace(StackTraceElement[] stackTrace)
	{
		super.getCause().setStackTrace(stackTrace);
	}

	@Override
	public String toString()
	{
		return "unchecked:" + super.getCause().toString();
	}
}
