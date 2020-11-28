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
package org.postgresql.pljava.policy;

import java.lang.reflect.ReflectPermission;

import java.net.URI;

import java.security.CodeSource;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.security.SecurityPermission;
import java.security.URIParameter;

import java.util.ArrayList;
import java.util.Arrays;
import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.enumeration;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import static org.postgresql.pljava.elog.ELogHandler.LOG_LOG;
import static org.postgresql.pljava.internal.Backend.log;
import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
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

	public TrialPolicy(String limitURI) throws NoSuchAlgorithmException
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
	public boolean implies(
		ProtectionDomain domain, java.security.Permission permission)
	{
		if ( realPolicy.implies(domain, permission) )
			return true;

		if ( ! limitPolicy.implies(domain, permission) )
		{
			/*
			 * The TrialPolicy.Permission below is an unusual one: like Java's
			 * own AllPermission, its implies() can be true for permissions of
			 * other classes than its own. Java's AllPermission is handled
			 * magically, and this one must be also, because deep down, the
			 * built-in Policy implementation keeps its PermissionCollections
			 * segregated by permission class. It would not notice on its own
			 * that 'permission' might be implied by a permission that is held
			 * but is of some other class.
			 */
			if ( ! limitPolicy.implies(domain, Permission.INSTANCE)
				|| ! Permission.INSTANCE.implies(permission) )
			return false;
		}

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
		StringBuilder sb = new StringBuilder(
			"POLICY DENIES/TRIAL POLICY ALLOWS: " + permission + '\n');
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

		/*
		 * This is not the best way to avoid blocking on log(); in some flavors
		 * of pljava.java_thread_pg_entry, threadMayEnterPG can return false
		 * simply because it's not /known/ that PG could be entered right now,
		 * and this could send the message off to System.err at times even if
		 * log() would have completed with no blocking. But the always accurate
		 * "could I enter PG right now without blocking?" method isn't provided
		 * yet.
		 */
		if ( threadMayEnterPG() )
			log(LOG_LOG, sb.toString());
		else
			System.err.println(sb);

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

	/**
	 * A permission like {@code java.security.AllPermission}, but without
	 * any {@code FilePermission} (the real policy's sandboxed/unsandboxed
	 * grants should handle those), nor a couple dozen varieties of
	 * {@code RuntimePermission}, {@code SecurityPermission}, and
	 * {@code ReflectPermission} that would typically not be granted without
	 * clear intent.
	 *<p>
	 * This permission can be granted in a {@code TrialPolicy} while identifying
	 * any straggling permissions needed by some existing code, without quite
	 * the excitement of granting {@code AllPermission}. Any of the permissions
	 * excluded from this one can also be granted in the {@code TrialPolicy},
	 * of course, if there is reason to believe the code might need them.
	 *<p>
	 * The proper spelling in a policy file is
	 * {@code org.postgresql.pljava.policy.TrialPolicy$Permission}.
	 *<p>
	 * This permission will probably only work right in a {@code TrialPolicy}.
	 * Any permission whose {@code implies} method can return true for
	 * permissions of other classes than its own may be ineffective in a stock
	 * Java policy, where permission collections are kept segregated by the
	 * class of the permission to be checked. Java's {@code AllPermission} gets
	 * special-case treatment in the stock implementation, and this permission
	 * likewise has to be treated specially in {@code TrialPolicy}. The only
	 * kind of custom permission that can genuinely drop in and work is one
	 * whose {@code implies} method only imposes semantics on the names/actions
	 * of different instances of that permission class.
	 *<p>
	 * A permission that does not live on the boot classpath is initially read
	 * from a policy file as an instance of {@code UnresolvedPermission}, and
	 * only gets resolved when a permission check is made, checking for an
	 * instance of its actual class. That is another complication when
	 * implementing a permission that may imply permissions of other classes.
	 *<p>
	 * A permission implemented in a different named module must be in a package
	 * that is exported to {@code java.base}.
	 */
	public static final class Permission extends java.security.Permission
	{
		private static final long serialVersionUID = 6401893677037633706L;

		/**
		 * An instance of this permission (not a singleton, merely one among
		 * possible others).
		 */
		static final Permission INSTANCE = new Permission();

		public Permission()
		{
			super("");
		}

		public Permission(String name, String actions)
		{
			super("");
		}

		@Override
		public boolean equals(Object other)
		{
			return other instanceof Permission;
		}

		@Override
		public int hashCode()
		{
			return 131113;
		}

		@Override
		public String getActions()
		{
			return null;
		}

		@Override
		public PermissionCollection newPermissionCollection()
		{
			return new Collection();
		}

		@Override
		public boolean implies(java.security.Permission p)
		{
			if ( p instanceof Permission )
				return true;

			if ( p instanceof java.io.FilePermission )
				return false;

			if ( Holder.EXCLUDERHS.stream().anyMatch(r -> p.implies(r)) )
				return false;

			if ( Holder.EXCLUDELHS.stream().anyMatch(l -> l.implies(p)) )
				return false;

			return true;
		}

		static class Collection extends PermissionCollection
		{
			private static final long serialVersionUID = 917249873714843122L;

			Permission the_permission = null;

			@Override
			public void add(java.security.Permission p)
			{
				if ( isReadOnly() )
					throw new SecurityException(
						"attempt to add a Permission to a readonly " +
						"PermissionCollection");

				if ( ! (p instanceof Permission) )
					throw new IllegalArgumentException(
						"invalid in homogeneous PermissionCollection: " + p);

				if ( null == the_permission )
					the_permission = (Permission) p;
			}

			@Override
			public boolean implies(java.security.Permission p)
			{
				if ( null == the_permission )
					return false;
				return the_permission.implies(p);
			}

			@Override
			public Enumeration<java.security.Permission> elements()
			{
				if ( null == the_permission )
					return emptyEnumeration();
				return enumeration(List.of(the_permission));
			}
		}

		static class Holder
		{
			static final List<java.security.Permission> EXCLUDERHS = List.of(
				new RuntimePermission("createClassLoader"),
				new RuntimePermission("getClassLoader"),
				new RuntimePermission("setContextClassLoader"),
				new RuntimePermission("enableContextClassLoaderOverride"),
				new RuntimePermission("setSecurityManager"),
				new RuntimePermission("createSecurityManager"),
				new RuntimePermission("shutdownHooks"),
				new RuntimePermission("exitVM"),
				new RuntimePermission("setFactory"),
				new RuntimePermission("setIO"),
				new RuntimePermission("getStackWalkerWithClassReference"),
				new RuntimePermission("setDefaultUncaughtExceptionHandler"),
				new RuntimePermission("manageProcess"),
				new ReflectPermission("suppressAccessChecks"),
				new SecurityPermission("createAccessControlContext"),
				new SecurityPermission("setPolicy"),
				new SecurityPermission("createPolicy.JavaPolicy")
			);

			static final List<java.security.Permission> EXCLUDELHS = List.of(
				new RuntimePermission("exitVM.*"),
				new RuntimePermission("defineClassInPackage.*"),
				new ReflectPermission("newProxyInPackage.*"),
				new SecurityPermission("setProperty.*")
			);
		}
	}
}
