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

import java.net.URI;

import java.security.CodeSource;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.URIParameter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.postgresql.pljava.elog.ELogHandler.LOG_LOG;
import static org.postgresql.pljava.internal.Privilege.doPrivileged;

/**
 * An implementation of {@link Policy} intended for temporary use while
 * identifying needed permission grants for existing code.
 *<p>
 * This policy is meant to operate as a fallback in conjunction with the normal
 * PL/Java policy specified with the {@code pljava.policy_urls} configuration
 * setting. This policy is activated by specifying an additional policy file
 * URL with {@code -Dorg.postgresql.pljava.policy.trial=}<em>url</em> in the
 * {@code pljava.vmoptions} setting.
 *<p>
 * Permission checks that are allowed by the normal policy in
 * {@code pljava.policy_urls} are allowed with no further checking. Permissions
 * denied by that policy are checked in this one. If denied in this policy, that
 * is the end of the matter. A permission check that is denied by the normal
 * policy but allowed by this one is allowed, with a message to the server log.
 *<p>
 * The log message begins with {@code POLICY DENIES/TRIAL POLICY ALLOWS:}
 * and the requested permission, followed by an abbreviated stack trace.
 * To minimize log volume, the stack trace includes a frame above and below
 * each crossing of a module or protection domain boundary; a single {@code ...}
 * replaces intermediate frames within the same module and domain.
 * At the position in the trace of the protection domain that failed the policy
 * check, a line is inserted with the domain's code source and principals,
 * such as {@code >> sqlj:examples [PLPrincipal.Sandboxed: java] <<}. This
 * abbreviated trace should be well suited to the purpose of determining where
 * any additional permission grants ought to be made.
 *<p>
 * Because each check that is logged is then allowed, it can be possible to see
 * multiple log entries for the same permission check, one for each domain in
 * the call stack that is not granted the permission in the normal policy.
 *<h2>About false positives</h2>
 * It is not uncommon to have software that checks in normal operation for
 * certain permissions, catches exceptions, and proceeds to function normally.
 * Use of this policy, if it is configured to grant the permissions being
 * checked, will produce log entries for those 'hidden' checks and may create
 * the appearance that permissions need to be granted when, in fact, the
 * software would show no functional impairment without them. It is difficult
 * to distinguish such false positives from other log entries for permissions
 * that do need to be granted for the software to properly function.
 *<p>
 * One approach would be to try to determine, from the log entries, which
 * functions of the software led to the permission checks that were logged, and
 * specifically test those functions in a database session that has been set up
 * with a different policy file that does not grant those permissions. If the
 * software then functions without incident, it may be concluded that those
 * log entries were false positives.
 */
public class TrialPolicy extends Policy
{
	private static final String TYPE = "JavaPolicy";
	private static final RuntimePermission GET_PROTECTION_DOMAIN =
		new RuntimePermission("getProtectionDomain");
	private final Policy realPolicy;
	private final Policy limitPolicy;
	private final StackWalker walker =
		StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

	TrialPolicy(String limitURI) throws NoSuchAlgorithmException
	{
		URIParameter lim = new URIParameter(URI.create(limitURI));
		realPolicy = Policy.getInstance(TYPE, null);
		limitPolicy = Policy.getInstance(TYPE, lim);
	}

	@Override
	public PermissionCollection getPermissions(CodeSource codesource)
	{
		return realPolicy.getPermissions(codesource);
	}

	@Override
	public PermissionCollection getPermissions(ProtectionDomain domain)
	{
		return realPolicy.getPermissions(domain);
	}

	@Override
	public boolean implies(ProtectionDomain domain, Permission permission)
	{
		if ( realPolicy.implies(domain, permission) )
			return true;

		if ( ! limitPolicy.implies(domain, permission) )
			return false;

		/*
		 * Construct a (with any luck, useful) abbreviated stack trace, using
		 * the first frame encountered at each change of protection domain while
		 * walking up the stack, saving the index of the first entry for the
		 * domain being checked.
		 */
		List<StackTraceElement> stack = new ArrayList<>();
		int matchingDomainIndex = doPrivileged(() -> walker.walk(s ->
		{
			ProtectionDomain lastDomain = null;
			StackWalker.StackFrame lastFrame = null;
			Module lastModule = null;
			Module thisModule = getClass().getModule();
			int matchIndex = -1;
			int walkIndex = 0;
			int newDomainIndex = 0; // walkIndex of first frame in a new domain
			for ( StackWalker.StackFrame f :
					(Iterable<StackWalker.StackFrame>)s.skip(5)::iterator )
			{
				++ walkIndex;
				Class<?> frameClass = f.getDeclaringClass();
				Module frameModule = frameClass.getModule();
				ProtectionDomain frameDomain = frameClass.getProtectionDomain();
				if ( ! equals(lastDomain, frameDomain)
					|| null != lastModule && ! lastModule.equals(frameModule) )
				{
					if ( null != lastFrame && walkIndex > 1 + newDomainIndex )
					{
						if ( walkIndex > 2 + newDomainIndex )
							stack.add(null); // will be rendered as ...
						stack.add(lastFrame.toStackTraceElement());
					}
					if ( -1 == matchIndex && equals(domain, frameDomain) )
						matchIndex = stack.size();
					stack.add(f.toStackTraceElement());
					lastModule = frameModule;
					lastDomain = frameDomain;
					newDomainIndex = walkIndex;
				}

				/*
				 * Exit the walk early, skip boring EntryPoints.
				 */
				if ( frameModule.equals(thisModule)
					&& "org.postgresql.pljava.internal.EntryPoints"
						.equals(frameClass.getName()) )
				{
					if ( newDomainIndex == walkIndex )
						stack.remove(stack.size() - 1);
					-- walkIndex;
					break;
				}

				lastFrame = f;
			}

			if ( null != lastFrame && walkIndex > 1 + newDomainIndex )
				stack.add(lastFrame.toStackTraceElement());

			if ( -1 == matchIndex )
				matchIndex = stack.size();
			return matchIndex;
		}), null, GET_PROTECTION_DOMAIN);

		/*
		 * Construct a string representation of the trace.
		 */
		StringBuilder sb = new StringBuilder();
		Iterator<StackTraceElement> it = stack.iterator();
		int i = 0;
		for ( ;; )
		{
			if ( matchingDomainIndex == i ++ )
				sb.append(">> ")
				.append(domain.getCodeSource().getLocation())
				.append(' ')
				.append(Arrays.toString(domain.getPrincipals()))
				.append(" <<\n");
			if ( ! it.hasNext() )
				break;
			StackTraceElement e = it.next();
			sb.append(null == e ? "..." : e.toString());
			if ( it.hasNext()  ||  matchingDomainIndex == i )
				sb.append('\n');
		}

		Backend.log(LOG_LOG,
			"POLICY DENIES/TRIAL POLICY ALLOWS: " + permission + '\n' + sb);

		return true;
	}

	@Override
	public void refresh()
	{
		realPolicy.refresh();
		limitPolicy.refresh();
	}

	/*
	 * Compare two protection domains, only by their code source for now.
	 * It appears that StackWalker doesn't invoke domain combiners, so the
	 * frames seen in the walk won't match the principals of the argument
	 * to implies().
	 */
	private boolean equals(ProtectionDomain a, ProtectionDomain b)
	{
		if ( null == a  ||  null == b)
			return a == b;

		CodeSource csa = a.getCodeSource();
		CodeSource csb = b.getCodeSource();

		if ( null == csa  ||  null == csb )
			return csa == csb;

		return csa.equals(csb);
	}
}
