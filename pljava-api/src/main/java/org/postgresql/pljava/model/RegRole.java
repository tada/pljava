/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.model;

import java.util.List;

import org.postgresql.pljava.RolePrincipal;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Pseudo;

/**
 * Model of a PostgreSQL role.
 *<p>
 * In addition to the methods returning the information in the {@code pg_authid}
 * system catalog, there are methods to return four different flavors of
 * {@link RolePrincipal RolePrincipal}, all representing this role.
 *<p>
 * The {@code ...Principal()} methods should not be confused with environment
 * accessors returning actual information about the execution context. Each of
 * the methods simply returns an instance of the corresponding class that would
 * be appropriate to find in the execution context if this role were,
 * respectively, the authenticated, session, outer, or current role.
 *<p>
 * {@link RolePrincipal.Current} implements the
 * {@code UserPrincipal/GroupPrincipal} interfaces of
 * {@code java.nio.file.attribute}, so
 * {@link #currentPrincipal() currentPrincipal()} can also be used to obtain
 * {@code Principal}s that will work in the Java NIO.2 filesystem API.
 *<p>
 * The {@code ...Principal} methods only succeed when {@code name()} does,
 * therefore not when {@code isValid} is false. The {@code RegRole.Grantee}
 * representing {@code PUBLIC} is, for all other purposes, not a valid role,
 * including for its {@code ...Principal} methods.
 */
public interface RegRole
extends Addressed<RegRole>, Named<Simple>, AccessControlled<Grant.OnRole>
{
	RegClass.Known<RegRole> CLASSID =
		formClassId(AuthIdRelationId, RegRole.class);

	/**
	 * A {@code RegRole.Grantee} representing {@code PUBLIC}; not a valid
	 * {@code RegRole} for other purposes.
	 */
	RegRole.Grantee PUBLIC = publicGrantee();

	/**
	 * Subinterface of {@code RegRole} returned by methods of
	 * {@link CatalogObject.AccessControlled CatalogObject.AccessControlled}
	 * identifying the role to which a privilege has been granted.
	 *<p>
	 * A {@code RegRole} appearing as a grantee can be {@link #PUBLIC PUBLIC},
	 * unlike a {@code RegRole} in any other context, so the
	 * {@link #isPublic isPublic()} method appears only on this subinterface,
	 * as well as the {@link #nameAsGrantee nameAsGrantee} method, which will
	 * return the correct name even in that case (the ordinary {@code name}
	 * method will not).
	 */
	interface Grantee extends RegRole
	{
		/**
		 * In the case of a {@code RegRole} obtained as the {@code grantee} of a
		 * {@link Grant}, indicate whether it is a grant to "public".
		 */
		default boolean isPublic()
		{
			return ! isValid();
		}

		/**
		 * Like {@code name()}, but also returns the expected name for a
		 * {@code Grantee} representing {@code PUBLIC}.
		 */
		Simple nameAsGrantee();
	}

	/**
	 * Return a {@code RolePrincipal} that would represent this role as a
	 * session's authenticated identity (which was established at connection
	 * time and will not change for the life of a session).
	 */
	default RolePrincipal.Authenticated authenticatedPrincipal()
	{
		return new RolePrincipal.Authenticated(name());
	}

	/**
	 * Return a {@code RolePrincipal} that would represent this role as a
	 * session's "session" identity (which can be changed during a session
	 * by {@code SET SESSION AUTHORIZATION}).
	 */
	default RolePrincipal.Session sessionPrincipal()
	{
		return new RolePrincipal.Session(name());
	}

	/**
	 * Return a {@code RolePrincipal} that would represent this role as the one
	 * last established by {@code SET ROLE}, and outside of any
	 * {@code SECURITY DEFINER} function.
	 */
	default RolePrincipal.Outer outerPrincipal()
	{
		return new RolePrincipal.Outer(name());
	}

	/**
	 * Return a {@code RolePrincipal} that would represent this role as the
	 * effective one for normal privilege checks, usually the same as the
	 * session or outer, but changed during {@code SECURITY DEFINER} functions.
	 *<p>
	 * This method can also be used to obtain a {@code Principal} that will work
	 * in the Java NIO.2 filesystem API.
	 */
	default RolePrincipal.Current currentPrincipal()
	{
		return new RolePrincipal.Current(name());
	}

	/**
	 * Roles of which this role is <em>directly</em> a member.
	 *<p>
	 * For the other direction, see {@link #grants() grants()}.
	 */
	List<RegRole> memberOf();

	boolean superuser();
	boolean inherit();
	boolean createRole();
	boolean createDB();
	boolean canLogIn();
	boolean replication();
	boolean bypassRLS();
	int connectionLimit();
}
