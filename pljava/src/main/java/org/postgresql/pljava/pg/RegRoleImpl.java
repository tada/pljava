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
package org.postgresql.pljava.pg;

import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.UserPrincipal;

import java.util.List;

import org.postgresql.pljava.RolePrincipal;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Implementation of the {@link RegRole RegRole} interface.
 *<p>
 * That this class can in fact be cast to {@link RegRole.Grantee Grantee} is an
 * unadvertised implementation detail.
 */
class RegRoleImpl extends Addressed<RegRole>
implements
	Shared<RegRole>, Named<Simple>,
	AccessControlled<CatalogObject.Grant.OnRole>, RegRole.Grantee
{
	/* API methods */

	@Override
	public List<RegRole> memberOf()
	{
		throw notyet();
	}

	@Override
	public boolean superuser()
	{
		throw notyet();
	}

	@Override
	public boolean inherit()
	{
		throw notyet();
	}

	@Override
	public boolean createRole()
	{
		throw notyet();
	}

	@Override
	public boolean createDB()
	{
		throw notyet();
	}

	@Override
	public boolean canLogIn()
	{
		throw notyet();
	}

	@Override
	public boolean replication()
	{
		throw notyet();
	}

	@Override
	public boolean bypassRLS()
	{
		throw notyet();
	}

	@Override
	public int connectionLimit()
	{
		throw notyet();
	}

	/* Implementation of RegRole.Grantee */

	/*
	 * As it turns out, PostgreSQL doesn't use a notion like Identifier.Pseudo
	 * for the name of the public grantee. It uses the ordinary, folding name
	 * "public" and reserves it, forbidding that any actual role have any name
	 * that matches it according to the usual folding rules. So, construct that
	 * name here.
	 */
	private static final Simple s_public_name = Simple.fromCatalog("public");

	@Override
	public Simple nameAsGrantee()
	{
		return isPublic() ? s_public_name : name();
	}
}
