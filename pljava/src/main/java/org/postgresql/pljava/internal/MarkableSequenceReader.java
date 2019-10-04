/*
 * Copyright (c) 2019 Tada AB and other contributors, as listed below.
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

import java.io.Reader;
import java.io.IOException;

import java.lang.reflect.UndeclaredThrowableException;

import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Like a {@link MarkableSequenceInputStream} but for characters.
 *<p>
 * This class implements {@code mark} and {@code reset} by calling the
 * corresponding methods on the underlying readers; it does not add buffering
 * or have any means of providing {@code mark} and {@code reset} support if
 * the underlying readers do not.
 *<p>
 * As with {@code SequenceInputStream}, each underlying reader, when completely
 * read and no longer needed, is closed to free resources. This instance itself
 * will remain in "open at EOF" condition until explicitly closed, but does not
 * prevent reclamation of the underlying readers.
 *<p>
 * Unlike {@code SequenceInputStream}, this class can keep underlying readers
 * open, after fully reading them, if a {@code mark} has been set, so that
 * {@code reset} will be possible. When a mark is no longer needed, it can be
 * canceled (by calling {@code mark} with a {@code readlimit} of 0) to again
 * allow the underlying readers to be reclaimed as soon as possible.
 */
public class MarkableSequenceReader extends Reader
{
	private boolean m_closed = false;
	private Reader m_markedStream = null;

	private ListIterator<Reader> m_streams;
	private int m_readlimit_orig;
	private int m_readlimit_curr;
	private boolean m_markSupported;
	private boolean m_markSupported_determined;

	/**
	 * Create a {@code MarkableSequenceReader} from one or more existing
	 * readers.
	 * @param streams Sequence of {@code Reader}s that will be read from
	 * in order.
	 * @throws NullPointerException if {@code streams} is {@code null}, or
	 * contains {@code null} for any stream.
	 */
	public MarkableSequenceReader(Reader... streams)
	{
		if ( null == streams )
			throw new NullPointerException("MarkableSequenceReader(null)");
		LinkedList<Reader> isl = new LinkedList<>();
		for ( Reader s : streams )
		{
			if ( null == s )
				throw new NullPointerException(
					"MarkableSequenceReader(..., null, ...)");
			isl.add(s);
		}
		m_streams =  isl.listIterator();
	}

	/*
	 * This method depends on an invariant: the iterator's next() method, when
	 * called here, will return the current, active stream. Each consumer method
	 * is responsible for preserving that invariant by calling previous() once
	 * after obtaining, but not hitting EOF on, a stream from next().
	 */
	private Reader currentStream() throws IOException
	{
		if ( m_closed )
			throw new IOException("I/O on closed Reader");
		if ( m_streams.hasNext() )
			return m_streams.next();
		return null;
	}

	/*
	 * The invariant here is that a "current" stream was recently obtained, and
	 * can be re-obtained with previous(). This should not be called unless
	 * there is nothing left to read from that stream.
	 */
	private Reader nextStream() throws IOException
	{
		if ( null == m_markedStream )
		{
			m_streams.previous().close();
			assert ! m_streams.hasPrevious();
			m_streams.remove();
			if ( m_streams.hasNext() )
				return m_streams.next();
		}
		else if ( m_streams.hasNext() )
		{
			Reader is = m_streams.next();
			is.mark(m_readlimit_curr);
			return is;
		}
		return null;
	}

	private void decrementLimit(long bytes) throws IOException
	{
		assert 0 < bytes;
		if ( null == m_markedStream )
			return;
		if ( bytes < m_readlimit_curr )
		{
			m_readlimit_curr -= bytes;
			return;
		}
		mark(0); /* undo markage of underlying streams */
	}

	@Override
	public int read() throws IOException
	{
		synchronized ( this )
		{
			for ( Reader s = currentStream(); null != s; s = nextStream() )
			{
				int c = s.read();
				if ( -1 != c )
				{
					decrementLimit(1);
					m_streams.previous(); /* maintain "current" invariant */
					return c;
				}
			}
			return -1;
		}
	}

	@Override
	public int read(char[] b, int off, int len) throws IOException
	{
		synchronized ( this )
		{
			for ( Reader s = currentStream(); null != s; s = nextStream() )
			{
				int rslt = s.read(b, off, len);
				if ( -1 != rslt )
				{
					decrementLimit(rslt);
					m_streams.previous(); /* maintain "current" invariant */
					return rslt;
				}
			}
			return -1;
		}
	}

	@Override
	public long skip(long n) throws IOException
	{
		synchronized ( this )
		{
			long skipped;
			long totalSkipped = 0;
			Reader s = currentStream();
			while ( null != s )
			{
				skipped = s.skip(n);
				n -= skipped;
				decrementLimit(skipped);
				totalSkipped += skipped;
				if ( 0 >= n )
				{
					m_streams.previous(); /* maintain "current" invariant */
					break;
				}
				/*
				 * A short count from skip doesn't have to mean EOF was reached.
				 * A read() will settle that question, though.
				 */
				if ( -1 != s.read() )
				{
					n -= 1;
					decrementLimit(1);
					totalSkipped += 1;
					continue;
				}
				/*
				 * Ok, it was EOF on that underlying stream.
				 */
				s = nextStream();
			}
			return totalSkipped;
		}
	}

	@Override
	public void close() throws IOException
	{
		synchronized ( this )
		{
			if ( m_closed )
				return;
			while ( m_streams.hasPrevious() )
				m_streams.previous();
			while ( m_streams.hasNext() )
				m_streams.next().close();
			m_streams = null;
			m_closed = true;
		}
	}

	/**
	 * Marks the current position in this reader. In this implementation,
	 * it is possible to 'cancel' a mark, by passing this method a
	 * {@code readlimit} of zero, returning the reader immediately to the state
	 * of having no mark.
	 */
	@Override
	public void mark(int readlimit) throws IOException
	{
		synchronized ( this )
		{
			if ( m_closed )
				return;

			Reader activeStream = null;
			if ( m_streams.hasNext() )
			{
				m_streams.next();
				activeStream = m_streams.previous();
			}

			if ( null != m_markedStream )
			{
				for ( Reader is = activeStream; is != m_markedStream; )
					is = m_streams.previous();
				/*
				 * Whether the above loop executed zero or more times, the last
				 * event on m_streams was a previous(), and returned the marked
				 * stream, and the next next() will also.
				 */
				m_markedStream = null; // so nextStream() will close things
				/*
				 * It is safe to start off this loop with next(), because it
				 * will return the formerly marked stream, known to exist.
				 */
				for ( Reader is = m_streams.next(); is != activeStream; )
				{
					try
					{
						is = nextStream(); // will close stream and return next
					}
					catch ( IOException e )
					{
						throw new UndeclaredThrowableException(e);
					}
				}
				/*
				 * Leave the invariant the same whether this if block was taken
				 * or not.
				 */
				if ( null != activeStream )
					m_streams.previous();
			}

			if ( 0 >= readlimit )			/* setting instantly-invalid mark */
			{
				m_readlimit_curr = m_readlimit_orig = 0;
				return;
			}
			m_readlimit_curr = m_readlimit_orig = readlimit;

			if ( null == activeStream )  /* setting mark at EOF */
				return;

			m_markedStream = activeStream;
			activeStream.mark(readlimit);
		}
	}

	@Override
	public void reset() throws IOException
	{
		synchronized ( this )
		{
			if ( m_closed )
				throw new IOException("reset on closed Reader");

			if ( null == m_markedStream )
			{
				if ( 0 < m_readlimit_orig )
					return; // the mark-at-EOF case; reset allowed, no effect
				throw new IOException("reset without mark");
			}

			Reader is = currentStream();
			/*
			 * 'is' right now is the active stream, or null if we are at EOF;
			 * either way the first call to previous() coming up below will
			 * return an existing stream, the one we need (in reverse order)
			 * to reset first.
			 */

			while ( true )
			{
				is = m_streams.previous();
				is.reset();
				if ( is == m_markedStream )
					break;
				is.mark(0); // release possible resources
			}
			m_readlimit_curr = m_readlimit_orig;
			/*
			 * The invariant (that the next next() will return the stream we
			 * just touched) is already satisfied, as we obtained it with
			 * previous() above.
			 */
		}
	}

	/**
	 * Tests if this reader supports the mark and reset methods.
	 *<p>
	 * For any instance of this class, the
	 * result is determined by the first call to this method, and does not
	 * change thereafter. At the first call, the result is determined only by
	 * the underlying readers remaining to be read (or, if a mark has been
	 * set, which is possible before checking this method, then by the
	 * underlying readers including and following the one that was current
	 * when the mark was set). The result will be {@code true} unless any of
	 * those underlying readers reports it as {@code false}.
	 */
	@Override
	public boolean markSupported()
	{
		synchronized ( this )
		{
			if ( m_markSupported_determined )
				return m_markSupported;
			if ( m_closed )
				return false;

			Reader activeStream = null;
			if ( m_streams.hasNext() )
			{
				m_streams.next();
				activeStream = m_streams.previous();
			}

			if ( null != m_markedStream )
			{
				for ( Reader is = activeStream; is != m_markedStream; )
					is = m_streams.previous();
			}

			/*
			 * The next next() returns the marked stream (if there is one), or
			 * the active stream (if there is one).
			 */
			m_markSupported = true;
			while ( m_streams.hasNext() )
				if ( ! m_streams.next().markSupported() )
					m_markSupported = false;
			/*
			 * We've run to the end of the streams list. Back up to the active
			 * one.
			 */
			for ( Reader is = null; is != activeStream; )
				is = m_streams.previous();

			/*
			 * The "current" invariant is satisfied.
			 */
			m_markSupported_determined = true;
			return m_markSupported;
		}
	}
}
