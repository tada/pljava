/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava.annotation.processing;

import java.io.IOException;
import java.io.Writer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.StandardLocation.CLASS_OUTPUT;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import static org.postgresql.pljava.sqlgen.Lexicals.ISO_PG_JAVA_IDENTIFIER;

/**
 * Class for writing an SQLJ deployment descriptor file in proper form, given
 * snippets of SQL code for deployment and undeployment that have already been
 * arranged into a workable order.
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - update to Java6, reorganize,
 * add lexable check
 */
public class DDRWriter
{
	/**
	 * Generate the deployment descriptor file.
	 *<p>
	 * Important: it is <em>assumed</em> that {@code fwdSnips} and
	 * {@code revSnips} contain exactly the same snippets and differ only in
	 * their ordering.
	 *
	 * @param fwdSnips Code snippets to include in the file, in a workable order
	 * for the install actions group.
	 * @param revSnips The same snippets in a workable order for the remove
	 * actions group. Not necessarily simply fwdSnips back to front, as the
	 * implied dependencies on implementor tags have the same sense for both
	 * install and remove: the tag conditions have to be evaluated before the
	 * snippets that depend on them.
	 * @param p Reference to the calling object, used to obtain the Filer
	 * object and desired output file name, and for diagnostic messages.
	 */
	static void emit( Snippet[] fwdSnips, Snippet[] revSnips,
		DDRProcessorImpl p) throws IOException
	{
		if ( ! ensureLexable( fwdSnips, p) ) // assume same members as revSnips!
			return;

		Writer w =
			p.filr.createResource( CLASS_OUTPUT, "", p.output).openWriter();

		w.write( "SQLActions[]={\n\"BEGIN INSTALL\n");
		
		for ( Snippet snip : fwdSnips )
			for ( String s : snip.deployStrings() )
				writeCommand( w, s, snip.implementorName());

		w.write( "END INSTALL\",\n\"BEGIN REMOVE\n");
		
		for ( Snippet snip : revSnips )
			for ( String s : snip.undeployStrings() )
				writeCommand( w, s, snip.implementorName());

		w.write( "END REMOVE\"\n}\n");
		
		w.close();
	}
	
	/**
	 * Write a single command into the current (install or remove) action group.
	 * Can emit either the implementor-specific or non-specific command form.
	 *
	 * @param w The Writer object on which the command should be written
	 * @param s The command to write. If implementor is null, the command will
	 * be written as is, with only a semicolon added. If implementor is not
	 * null, then s will be wrapped in a BEGIN implementor s END implementor;
	 * construct.
	 * @param implementor The case-insensitive string identifying specific
	 * SQL implementation targeted by the command, or null if the command
	 * is implementor-nonspecific. PostgreSQL is the string to use for
	 * PostgreSQL-specific commands.
	 */
	static void writeCommand( Writer w, String s, Identifier.Simple implementor)
	throws IOException
	{
		if ( null != implementor )
		{
			w.write( "BEGIN ");
			w.write( implementor.toString());
			w.write( '\n');
		}

		w.write( s);

		if ( null != implementor )
		{
			w.write( "\nEND ");
			w.write( implementor.toString());
		}

		w.write( ";\n");
	}

	/**
	 * Check that the snippets to be written into the deployment descriptor
	 * file will survive the code that reads them in. The code in
	 * SQLDeploymentDescriptor.java makes no attempt to recognize SQL, but
	 * uses simple rules about backslash escapes and balanced ' and " quote
	 * pairs to read in snippets intact and find the terminating ; (and not,
	 * for example, a quoted ; somewhere in the snippet). Happily, those rules
	 * can actually work: the SQL convention of escaping a ' or " by doubling
	 * it plays nicely with the simple balanced-quotes rule, and backslashes
	 * can be made to work as the rule assumes (e.g. as long as they are inside
	 * e'' strings or standard_conforming_strings is off).
	 *
	 * That means it is at least possible to create annotations that survive
	 * being written to the .ddr file and read in again, but not necessarily
	 * without thinking. When the snippets pass this check, that means they'll
	 * be succesfully recovered by SQLDeploymentDescriptor.java and passed along
	 * in that form to the backend; it does not guarantee the backend will make
	 * the intended sense of them. For that to happen, developers must be
	 * careful to write the strings in annotations in ways that both satisfy
	 * these rules and make sense to the backend. For example, it is best to use
	 * the e'' form for string constants, because the backend's interpretation
	 * of the '' form may or may not match these rules depending on the setting
	 * of standard_conforming_strings. Dollar-quoting is a great idea for some
	 * jobs but not for this one, because the deployment descriptor lexer
	 * doesn't grok it and will be foiled by any literal \ ' " in the content.
	 *
	 * Fortunately, generating a deployment descriptor is not the sort of task
	 * where the risk model includes deliberate injection attacks by an
	 * adversary, but just a developer who presumably wants his or her code to
	 * work right, and might simply have to be extra careful and need more than
	 * one try before it does.
	 *
	 * @param snips Array of snippets whose deployStrings and undeployStrings
	 * will be checked for unbalanced quoting
	 * @param p Reference to the DDRProcessorImpl to be used for error reporting
	 * @return true if the snippets satisfy the rules to be succesfully read
	 * back from the .ddr file.
	 */
	static boolean ensureLexable( Snippet[] snips, DDRProcessorImpl p)
	{
		boolean errorRaised = false;
		Matcher m = checker.matcher( "");
		/*
		 * Restricting this identifier to satisfy Java rules as well as SQL ones
		 * is unnecessarily restrictive (it isn't going to appear in Java code,
		 * after all), but at present those are the rules used to scan it in
		 * SQLDeploymentDescriptor, so it had better fit.
		 */
		Matcher i = ISO_PG_JAVA_IDENTIFIER.matcher( "");
		
		for ( Snippet snip : snips )
		{
			String implementor = snip.implementorName().nonFolded();
			if ( null != implementor )
			{
				i.reset( implementor);
				if ( ! i.matches() )
				{
					p.msg( ERROR,
						"non-SQL- or -Java-structured implementor-name: %s",
						implementor
					);
				}
			}
			for ( String s : snip.deployStrings() )
			{
				m.reset( s);
				if ( ! m.matches() )
				{
					p.msg( ERROR, checkMsg, "install", s);
					errorRaised = true;
				}
			}
			for ( String s : snip.undeployStrings() )
			{
				m.reset( s);
				if ( ! m.matches() )
				{
					p.msg( ERROR, checkMsg, "remove", s);
					errorRaised = true;
				}
			}
		}
		
		return ! errorRaised;
	}

	/**
	 * Return the e'...' form of quoting a string (the only one compatible with
	 * the lexer rules for re-reading the .ddr file, without needing to know
	 * how standard_conforming_strings is set on the backend).
	 *
	 * @param s Something to be quoted
	 * @return The e-quoted form of s
	 */
	public static String eQuote( CharSequence s)
	{
		Matcher m = equoter.matcher( s);
		return "e'" + m.replaceAll( "$0$0") + '\'';
	}

	static final Pattern checker = Pattern.compile(
		"(?s:\\\\.|[^;'\"]|'(?:\\\\.|[^'])*+'|\"(?:\\\\.|[^\"])*+\")*+");

	static final String checkMsg =
		"%s command contains unquoted ; or unbalanced '/\": %s";
	
	static final Pattern equoter = Pattern.compile(	"\\\\|'");
}
