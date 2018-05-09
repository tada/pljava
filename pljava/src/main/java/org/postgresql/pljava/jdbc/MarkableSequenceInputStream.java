package org.postgresql.pljava.jdbc;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.IOException;

/**
 * Version of {@link SequenceInputStream} that supports
 * {@code mark} and {@code reset}, to the extent its constituent input streams
 * do.
 *<p>
 * This class implements {@code mark} and {@code reset} by calling the
 * corresponding methods on the underlying streams; it does not add buffering
 * or have any means of providing {@code mark} and {@code reset} support if
 * the underlying streams do not.
 *<p>
 * As with {@code SequenceInputStream}, each underlying stream, when completely
 * read and no longer needed, is closed to free resources. This instance itself
 * will remain in "open at EOF" condition until explicitly closed, but does not
 * prevent reclamation of the underlying streams.
 *<p>
 * Unlike {@code SequenceInputStream}, this class can keep underlying streams
 * open, after fully reading them, if a {@code mark} has been set, so that
 * {@code reset} will be possible. When a mark is no longer needed, it can be
 * canceled (by calling {@code mark} with a {@code readlimit} of 0) to again
 * allow the underlying streams to be reclaimed as soon as possible.
 */
public class MarkableSequenceInputStream extends InputStream
{
	private InputStream[] m_streams;
	private int m_activeStream;
	private int m_markedStream;
	private int m_readlimit_orig;
	private int m_readlimit_curr;
	private boolean m_markSupported;
	private boolean m_markSupported_determined;

	/**
	 * Create a {@code MarkableSequenceInputStream} from one or more existing
	 * input streams.
	 * @param streams Sequence of {@code InputStream}s that will be read from
	 * in order.
	 * @throws NullPointerException if {@code streams} is {@code null}, or
	 * contains {@code null} for any stream.
	 */
	public MarkableSequenceInputStream(InputStream... streams)
	{
		if ( null == streams )
			throw new NullPointerException("MarkableSequenceInputStream(null)");
		for ( InputStream s : streams )
			if ( null == s )
				throw new NullPointerException(
					"MarkableSequenceInputStream(..., null, ...)");

		m_streams = streams;
		m_activeStream = 0;		/* -1 will mean closed */
		m_markedStream = -1;	/* -1 will mean no mark has been set */
	}

	private InputStream currentStream() throws IOException
	{
		if ( -1 == m_activeStream )
			throw new IOException("I/O on closed InputStream");
		if ( m_streams.length == m_activeStream )
			return null;
		return m_streams[m_activeStream];
	}

	private InputStream nextStream() throws IOException
	{
		assert m_streams.length > m_activeStream;
		assert -1 == m_streams[m_activeStream].read();

		if ( -1 == m_markedStream )
		{
			m_streams[m_activeStream].close();
			m_streams[m_activeStream++] = null;
		}
		else if ( m_streams.length > ++m_activeStream )
			m_streams[m_activeStream].mark(m_readlimit_curr);

		if ( m_streams.length == m_activeStream )
			return null;
		return m_streams[m_activeStream];
	}

	private void decrementLimit(long bytes)
	{
		assert 0 < bytes;
		if ( -1 == m_markedStream )
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
			for ( InputStream s = currentStream(); null != s; s = nextStream() )
			{
				int c = s.read();
				if ( -1 != c )
				{
					decrementLimit(1);
					return c;
				}
			}
			return -1;
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException
	{
		synchronized ( this )
		{
			for ( InputStream s = currentStream(); null != s; s = nextStream() )
			{
				int rslt = s.read(b, off, len);
				if ( -1 != rslt )
				{
					decrementLimit(rslt);
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
			InputStream s = currentStream();
			while ( null != s )
			{
				skipped = s.skip(n);
				n -= skipped;
				decrementLimit(skipped);
				totalSkipped += skipped;
				if ( 0 >= n )
					break;
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
	public int available() throws IOException
	{
		synchronized ( this )
		{
			if ( -1 == m_activeStream )
				return 0;
			InputStream s = currentStream();
			return null == s ? 0 : s.available();
		}
	}

	@Override
	public void close() throws IOException
	{
		synchronized ( this )
		{
			if ( -1 == m_activeStream )
				return;
			if ( -1 != m_markedStream )
				m_activeStream = m_markedStream;
			for ( ; m_streams.length > m_activeStream ; ++ m_activeStream )
				m_streams[m_activeStream].close();
			m_activeStream = -1;
			m_streams = null;
		}
	}

	/**
	 * Marks the current position in this input stream. In this implementation,
	 * it is possible to 'cancel' a mark, by passing this method a
	 * {@code readlimit} of zero, returning the stream immediately to the state
	 * of having no mark.
	 */
	@Override
	public void mark(int readlimit)
	{
		synchronized ( this )
		{
			if ( -1 == m_activeStream )
				return;

			if ( -1 == m_markedStream )
				m_markedStream = m_activeStream;
			else
			{
				for ( ; m_markedStream < m_activeStream ; ++ m_markedStream )
				{
					try
					{
						m_streams[m_markedStream].close();
					}
					catch ( IOException e )
					{
					}
					m_streams[m_markedStream] = null;
				}
			}

			if ( 0 >= readlimit )			/* setting instantly-invalid mark */
			{
				m_readlimit_curr = m_readlimit_orig = 0;
				m_markedStream = -1;
				return;
			}
			m_readlimit_curr = m_readlimit_orig = readlimit;

			if ( m_streams.length == m_markedStream )  /* setting mark at EOF */
				return;

			m_streams[m_markedStream].mark(readlimit);
		}
	}

	@Override
	public void reset() throws IOException
	{
		synchronized ( this )
		{
			if ( -1 == m_activeStream )
				throw new IOException("reset on closed InputStream");
			if ( -1 == m_markedStream )
				throw new IOException("reset without mark");
			while ( true )
			{
				if ( m_streams.length > m_activeStream )
					m_streams[m_activeStream].reset();
				if ( m_activeStream == m_markedStream )
					break;
				-- m_activeStream;
			}
			m_readlimit_curr = m_readlimit_orig;
		}
	}

	/**
	 * Tests if this input stream supports the mark and reset methods.
	 *<p>
	 * By the API spec, this method's return is "an invariant property of a
	 * particular input stream instance." For any instance of this class, the
	 * result is determined by the first call to this method, and does not
	 * change thereafter. At the first call, the result is determined only by
	 * the underlying input streams remaining to be read (or, if a mark has been
	 * set, which is possible before checking this method, then by the
	 * underlying input streams including and following the one that was current
	 * when the mark was set). The result will be {@code true} unless any of
	 * those underlying streams reports it as {@code false}.
	 */
	@Override
	public boolean markSupported()
	{
		synchronized ( this )
		{
			if ( m_markSupported_determined )
				return m_markSupported;
			int i = m_markedStream;
			if ( -1 == i )
				i = m_activeStream;
			if ( -1 == i )
				return false;
			m_markSupported = true;
			for ( ; m_streams.length > i ; ++ i )
			{
				if ( ! m_streams[i].markSupported() )
					m_markSupported = false;
			}
			m_markSupported_determined = true;
			return m_markSupported;
		}
	}
}
