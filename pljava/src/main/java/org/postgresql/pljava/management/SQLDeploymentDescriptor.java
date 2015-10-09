/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.management;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * This class deals with parsing and executing the deployment descriptor as
 * defined in ISO/IEC 9075-13:2003. It has the following format:<pre><code>
 * &lt;descriptor file&gt; ::=
 * SQLActions &lt;left bracket&gt; &lt;right bracket&gt; &lt;equal sign&gt;
 * { [ &lt;double quote&gt; &lt;action group&gt; &lt;double quote&gt;
 *   [ &lt;comma&gt; &lt;double quote&gt; &lt;action group&gt; &lt;double quote&gt; ] ] }
 *
 * &lt;action group&gt; ::=
 *     &lt;install actions&gt;
 *   | &lt;remove actions&gt;
 * 
 * &lt;install actions&gt; ::=
 *   BEGIN INSTALL [ &lt;command&gt; &lt;semicolon&gt; ]... END INSTALL
 *
 * &lt;remove actions&gt; ::=
 *   BEGIN REMOVE [ &lt;command&gt; &lt;semicolon&gt; ]... END REMOVE
 *
 * &lt;command&gt; ::=
 *     &lt;SQL statement&gt;
 *   | &lt;implementor block&gt;
 *
 * &lt;SQL statement&gt; ::= &lt;SQL token&gt;...
 * 
 * &lt;implementor block&gt; ::=
 *   BEGIN &lt;implementor name&gt; &lt;SQL token&gt;... END &lt;implementor name&gt;
 *
 * &lt;implementor name&gt; ::= &lt;identifier&gt;
 *
 * &lt;SQL token&gt; ::= an SQL lexical unit specified by the term &quot;&lt;token&gt;&quot; in
 * Subclause 5.2, &quot;&lt;token&gt;&quot; and &quot;&lt;separator&gt;&quot;, in ISO/IEC 9075-2.</code></pre>
 *
 * <p><strong>Note:</strong> this parser departs from the specification for
 * {@code <descriptor file>} in the following ways:</p>
 * <ul>
 *  <li>Per ISO/IEC 9075-13, an {@code <SQL statement>} (not wrapped as an
 *  {@code <implementor block>}), may be one of only a few types of statement:
 *  declaring a procedure, function, or type; granting {@code USAGE} on a
 *  type, or {@code EXECUTE} on a procedure or function; declaring the ordering
 *  of a type. Any SQL that is not one of those, or does not use the exact
 *  ISO/IEC 9075 syntax, is allowed only within an {@code <implementor block>}.
 *  This parser does not enforce that restriction. This behavior is strictly
 *  more lax than the spec, and will not reject any standards-conformant
 *  descriptor file.</li>
 *  <li>Officially, an {@code <implementor name>} is an SQL {@code identifier}
 *  and may have any of the forms defined in 9075-2 subclause 5.2. This parser
 *  (a) only recognizes the {@code <regular identifier>} form (that is,
 *  non-double-quoted, no Unicode escape, matched case-insensitively), and (b)
 *  replaces the SQL allowable-character rules {@code <identifier start>} and
 *  {@code <identifier extend>} with the similar but nonidentical Java rules
 *  {@link Character#isJavaIdentifierStart(char)} and
 *  {@link Character#isJavaIdentifierPart(char)} (which do not work for
 *  characters in the {@linkplain Character#isSupplementaryCodePoint(int)
 *  supplementary character} range). In unlikely cases this
 *  could lead to rejecting a deployment descriptor that in fact conforms to the
 *  standard.</li>
 *  <li>Through PL/Java 1.4.3, this parser has not recognized {@code --} as the
 *  start of an SQL comment (which it is), and <em>has</em> recognized
 *  {@code //}, which isn't.</li>
 *  <li>Also through PL/Java 1.4.3, all whitespace (outside of quoted literals
 *  and identifiers) has been collapsed to a single {@code SPACE}, which would
 *  run afoul of the SQL rules for quoted literal/identifier continuation, if
 *  a deployment descriptor were ever to use that.</li>
 * </ul>
 * <p>The most conservative way to generate a deployment descriptor for
 * PL/Java's consumption is to wrap <em>all</em> commands as
 * {@code <implementor block>} and ensure that any {@code <implementor name>}
 * is both a valid Java identifier and a valid SQL {@code <regular identifier>}
 * containing nothing from the supplementary character range.
 * </p>
 * @author Thomas Hallgren
 * @author Chapman Flack
 */
public class SQLDeploymentDescriptor
{
	private final ArrayList<Command> m_installCommands =
		new ArrayList<Command>();
	private final ArrayList<Command> m_removeCommands =
		new ArrayList<Command>();
	
	private final StringBuffer m_buffer = new StringBuffer();
	private final char[] m_image;
	private final Logger m_logger;

	private int m_position = 0;

	/**
	 * Parses the deployment descriptor <code>descImage</code> into a series of
	 * {@code Command} objects each having an SQL command and, if present, an
	 * {@code <implementor name>}. The install and remove blocks are remembered
	 * for later execution
	 * with calls to {@link #install install()} and {@link #remove remove()}.
	 * @param descImage The image to parse
	 * @throws ParseException If a parse error is encountered
	 */
	public SQLDeploymentDescriptor(String descImage)
	throws ParseException
	{
		m_image = descImage.toCharArray();
		m_logger = Logger.getAnonymousLogger();
		this.readDescriptor();
	}

	/**
	 * Executes the <code>INSTALL</code> actions.
	 * @param conn The connection to use for the execution.
	 * @throws SQLException
	 */
	public void install(Connection conn)
	throws SQLException
	{
		this.executeArray(m_installCommands, conn);
	}

	/**
	 * Executes the <code>REMOVE</code> actions.
	 * @param conn The connection to use for the execution.
	 * @throws SQLException
	 */
	public void remove(Connection conn)
	throws SQLException
	{
		this.executeArray(m_removeCommands, conn);
	}

	/**
	 * Returns the original image.
	 */
	public String toString()
	{
		return new String(m_image);
	}

	private void executeArray(ArrayList<Command> array, Connection conn)
	throws SQLException
	{
		m_logger.entering("org.postgresql.pljava.management.SQLDeploymentDescriptor", "executeArray");
		for( Command c : array )
			c.execute( conn);
		m_logger.exiting("org.postgresql.pljava.management.SQLDeploymentDescriptor", "executeArray");
	}

	private ParseException parseError(String msg)
	{
		return new ParseException(msg, m_position);
	}

	private void readDescriptor()
	throws ParseException
	{
		m_logger.entering("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readDescriptor");
		if(!"SQLACTIONS".equals(this.readIdentifier()))
			throw this.parseError("Excpected keyword 'SQLActions'");

		this.readToken('[');
		this.readToken(']');
		this.readToken('=');
		this.readToken('{');
		for(;;)
		{
			readActionGroup();
			if(readToken("},") == '}')
			{
				// Only whitespace allowed now
				//
				int c = this.skipWhite();
				if(c >= 0)
					throw this.parseError(
						"Extraneous characters at end of descriptor");
				m_logger.exiting("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readDescriptor");
				return;
			}
		}
	}

	private void readActionGroup()
	throws ParseException
	{
		m_logger.entering("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readActionGroup");
		this.readToken('"');
		if(!"BEGIN".equals(this.readIdentifier()))
			throw this.parseError("Expected keyword 'BEGIN'");

		ArrayList<Command> commands;
		String actionType = this.readIdentifier();
		if("INSTALL".equals(actionType))
			commands = m_installCommands;
		else if("REMOVE".equals(actionType))
			commands = m_removeCommands;
		else
			throw this.parseError("Expected keyword 'INSTALL' or 'REMOVE'");

		for(;;)
		{
			String cmd = this.readCommand();					

			// Check if the cmd is in the form:
			//
			// <implementor block> ::=
			//	BEGIN <implementor name> <SQL token>... END <implementor name>
			//
			// If it is, and if the implementor name corresponds to the one
			// defined for this deployment, then extract the SQL token stream.
			//
			String implementorName;
			int top = cmd.length();
			if(top >= 15
			&& "BEGIN ".equalsIgnoreCase(cmd.substring(0, 6))
			&& Character.isJavaIdentifierStart(cmd.charAt(6)))
			{
				int pos;
				for(pos = 7; pos < top; ++pos)
					if(!Character.isJavaIdentifierPart(cmd.charAt(pos)))
						break;

				if(cmd.charAt(pos) != ' ')
					throw this.parseError(
						"Expected whitespace after <implementor name>");

				implementorName = cmd.substring(6, pos);
				int iLen = implementorName.length();

				int endNamePos = top - iLen;
				int endPos = endNamePos - 4;
				if(!implementorName.equalsIgnoreCase(cmd.substring(endNamePos))
				|| !"END ".equalsIgnoreCase(cmd.substring(endPos, endNamePos)))
					throw this.parseError(
						"Implementor block must end with END <implementor name>");

				cmd = cmd.substring(pos+1, endPos);
			}
			else
				implementorName = null;

			commands.add(new Command(cmd.trim(), implementorName));

			// Check if we have END INSTALL or END REMOVE
			//
			int savePos = m_position;
			try
			{
				String tmp = this.readIdentifier();
				if("END".equals(tmp))
				{
					tmp = this.readIdentifier();
					if(actionType.equals(tmp))
						break;
				}
				m_position = savePos;
			}
			catch(ParseException e)
			{
				m_position = savePos;
			}
		}
		this.readToken('"');
		m_logger.exiting("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readActionGroup");
	}

	private String readCommand()
	throws ParseException
	{
		m_logger.entering("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readCommand");
		int startQuotePos = -1;
		int inQuote = 0;
		int c = this.skipWhite();
		m_buffer.setLength(0);
		while(c != -1)
		{
			switch(c)
			{
			case '\\':
				m_buffer.append((char)c);
				c = this.read();
				if(c != -1)
				{
					m_buffer.append((char)c);
					c = this.read();
				}
				break;

			case '"':
			case '\'':
				if(inQuote == 0)
				{
					startQuotePos = m_position;
					inQuote = c;
				}
				else if(inQuote == c)
				{
					startQuotePos = -1;
					inQuote = 0;
				}
				m_buffer.append((char)c);
				c = this.read();
				break;

			case ';':
				if(inQuote == 0)
				{
					String cmd = m_buffer.toString();
					m_logger.exiting("org.postgresql.pljava.management.SQLDeploymentDescriptor", "readCommand", cmd);
					return cmd;
				}

				m_buffer.append((char)c);
				c = this.read();
				break;

			default:
				if(inQuote == 0 && Character.isWhitespace((char)c))
				{
					// Change multiple whitespace into one singe space.
					//
					m_buffer.append(' ');
					c = this.skipWhite();
				}
				else
				{	
					m_buffer.append((char)c);
					c = this.read();
				}
			}
		}
		if(inQuote != 0)
			throw this.parseError("Untermintated " + (char)inQuote +
					" starting at position " + startQuotePos);

		throw this.parseError("Unexpected EOF. Expecting ';' to end command");
	}

	private int skipWhite()
	throws ParseException
	{
		int c;
		for(;;)
		{
			c = this.read();
			if(c >= 0 && Character.isWhitespace((char)c))
				continue;
			
			if(c == '/')
			{
				switch(this.peek())
				{
				// "//" starts a line comment. Skip until end of line.
				//
				case '/':
					this.skip();
					for(;;)
					{
						c = this.read();
						switch(c)
						{
						case '\n':
						case '\r':
						case -1:
							break;
						default:
							continue;
						}
						break;
					}
					continue;

				// "/*" starts a line comment. Skip until "*/"
				//
				case '*':
					this.skip();
					for(;;)
					{
						c = this.read();
						switch(c)
						{
						case -1:
							throw this.parseError(
								"Unexpected EOF when expecting end of multi line comment");
						
						case '*':
							if(this.peek() == '/')
							{
								this.skip();
								break;
							}
							continue;

						default:
							continue;
						}
						break;
					}
					continue;
				}
			}
			break;
		}
		return c;
	}

	private String readIdentifier()
	throws ParseException
	{
		int c = this.skipWhite();
		if(c < 0)
			throw this.parseError("Unexpected EOF when expecting start of identifier");

		char ch = (char)c;
		if(!Character.isJavaIdentifierStart(ch))
			throw this.parseError(
					"Syntax error at '" + ch +
					"', expected identifier");

		m_buffer.setLength(0);
		m_buffer.append(ch);
		for(;;)
		{
			c = this.peek();
			if(c < 0)
				break;

			ch = (char)c;
			if(Character.isJavaIdentifierPart(ch))
			{	
				m_buffer.append(ch);
				this.skip();
				continue;
			}
			break;
		}
		return m_buffer.toString().toUpperCase();
	}

	private char readToken(String tokens)
	throws ParseException
	{
		int c = this.skipWhite();
		if(c < 0)
			throw this.parseError("Unexpected EOF when expecting one of \"" + tokens + '"');

		char ch = (char)c;
		if(tokens.indexOf(ch) < 0)
			throw this.parseError(
				"Syntax error at '" + ch +
				"', expected one of '" + tokens + "'");
		return ch;
	}

	private char readToken(char token)
	throws ParseException
	{
		int c = this.skipWhite();
		if(c < 0)
			throw this.parseError("Unexpected EOF when expecting token '" + token + '\'');

		char ch = (char)c;
		if(ch != token)
			throw this.parseError(
				"Syntax error at '" + ch +
				"', expected '" + token + "'");
		return ch;
	}

	private int peek()
	{
		return (m_position >= m_image.length) ? -1 : m_image[m_position];
	}

	private void skip()
	{
		m_position++;
	}

	private int read()
	{
		int pos = m_position++;
		return (pos >= m_image.length) ? -1 : m_image[pos];
	}
}

/**
 * A {@code <command>} in the deployment descriptor grammar.
 * If {@link #tag} is {@code null}, this is an {@code <SQL statement>}
 * (that is, to be run unconditionally, though no attempt has been made to
 * restrict it to the five types of standard-conforming statements allowed
 * by the spec). If {@code tag} is not null, this is an
 * {@code <implementor block>}, to be executed conditionally based on the
 * value of {@code tag}.
 * <p>
 * It could seem tempting to subclass this and assign specific behaviors, but
 * really it is created too early for that. At the time of creation only the
 * tag name (or absence) is known. Which tags are to be honored with what sort
 * of behavior will not be known for all tags, and may change as commands are
 * executed.
 */
class Command
{
	/** The sql to execute (if this command is not suppressed). Never null.
	 */
	final String sql;
	private  final String tag;

	/**
	 * Execute this {@code Command} using a {@code DDRExecutor} chosen
	 * according to its {@code <implementor name>}.
	 */
	void execute( Connection conn) throws SQLException
	{
		DDRExecutor ddre = DDRExecutor.forImplementor( tag);
		ddre.execute( sql, conn);
	}

	Command(String sql, String tag)
	{
		this.sql = sql.trim();
		this.tag = tag;
	}

	public String toString()
	{
		if ( null == tag )
			return "/*<SQL statement>*/ " + sql;
		return "/*<implementor block> " + tag + "*/ " + sql;
	}
}
