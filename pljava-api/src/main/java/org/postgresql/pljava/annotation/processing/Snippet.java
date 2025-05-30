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
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import java.util.Set;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * A code snippet. May contain zero, one, or more complete SQL commands for
 * each of deploying and undeploying. The commands contained in one Snippet
 * will always be emitted in a fixed order. A collection of Snippets will be
 * output in an order constrained by their provides and requires methods.
 */
interface Snippet
{
	/**
	 * An {@code <implementor name>} that will be used to wrap each command
	 * from this Snippet as an {@code <implementor block>}. If null, the
	 * commands will be emitted as plain {@code <SQL statement>}s.
	 */
	public Identifier.Simple implementorName();
	/**
	 * A {@code DependTag} to represent this snippet's dependence on whatever
	 * determines whether the implementor name is to be recognized.
	 *<p>
	 * Represented for now as a {@code DependTag.Explicit} even though the
	 * dependency is implicitly created; an {@code SQLAction} snippet may have
	 * an explicit {@code provides=} that has to be matched.
	 */
	default DependTag implementorTag()
	{
		return new DependTag.Explicit(implementorName().nonFolded());
	}
	/**
	 * Return an array of SQL commands (one complete command to a string) to
	 * be executed in order during deployment.
	 */
	public String[] deployStrings();
	/**
	 * Return an array of SQL commands (one complete command to a string) to
	 * be executed in order during undeployment.
	 */
	public String[] undeployStrings();
	/**
	 * Return an array of arbitrary labels considered "provided" by this
	 * Snippet. In generating the final order of the deployment descriptor file,
	 * this Snippet will come before any whose requires method returns any of
	 * the same labels.
	 */
	public Set<DependTag> provideTags();
	/**
	 * Return an array of arbitrary labels considered "required" by this
	 * Snippet. In generating the final order of the deployment descriptor file,
	 * this Snippet will come after those whose provides method returns any of
	 * the same labels.
	 */
	public Set<DependTag> requireTags();
	/**
	 * Method to be called after all annotations'
	 * element/value pairs have been filled in, to compute any additional
	 * information derived from those values before deployStrings() or
	 * undeployStrings() can be called. May also check for and report semantic
	 * errors that are not easily checked earlier while populating the
	 * element/value pairs.
	 * @return A set of snippets that are now prepared and should be added to
	 * the graph to be scheduled and emitted according to provides/requires.
	 * Typically Set.of(this) if all went well, or Set.of() in case of an error
	 * or when the snippet will be emitted by something else. In some cases a
	 * characterize method can return additional snippets that are ready to be
	 * scheduled.
	 */
	public Set<Snippet> characterize();

	/**
	 * If it is possible to break an ordering cycle at this snippet, return a
	 * vertex wrapping a snippet (possibly this one, or another) that can be
	 * considered ready, otherwise return null.
	 *<p>
	 * The default implementation returns null unconditionally.
	 * @param v Vertex that wraps this Snippet
	 * @param deploy true when generating an ordering for the deploy strings
	 * @return a Vertex wrapping a Snippet that can be considered ready
	 */
	default Vertex<Snippet> breakCycle(Vertex<Snippet> v, boolean deploy)
	{
		return null;
	}

	/**
	 * Called when undeploy ordering breaks a cycle by using
	 * {@code DROP ... CASCADE} or equivalent on another object, with effects
	 * that would duplicate or interfere with this snippet's undeploy actions.
	 *<p>
	 * A snippet for which this can matter should note that this method has been
	 * called, and later generate its undeploy strings with any necessary
	 * adjustments.
	 *<p>
	 * The default implementation does nothing.
	 */
	default void subsume()
	{
	}
}
