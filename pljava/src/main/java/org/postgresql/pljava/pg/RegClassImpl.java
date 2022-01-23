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

import java.util.List;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.pg.CatalogObjectImpl.*;
import static org.postgresql.pljava.pg.ModelConstants.RELOID; // syscache

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/*
 * Can get lots of information, including Form_pg_class rd_rel and
 * TupleDesc rd_att, from the relcache. See CacheRegisterRelcacheCallback().
 * However, the relcache copy of the class tuple is cut off at CLASS_TUPLE_SIZE.
 */

class RegClassImpl extends Addressed<RegClass>
implements
	Nonshared<RegClass>, Namespaced<Simple>, Owned,
	AccessControlled<CatalogObject.Grant.OnClass>, RegClass
{
	static class Known<T extends CatalogObject.Addressed<T>>
	extends RegClassImpl implements RegClass.Known<T>
	{
	}

	@Override
	int cacheId()
	{
		return RELOID;
	}

	RegClassImpl()
	{
	}

	TupleDescriptor.Interned[] m_tupDescHolder;

	@Override
	public TupleDescriptor.Interned tupleDescriptor()
	{
		throw notyet();
	}

	@Override
	public RegType type()
	{
		throw notyet();
	}

	@Override
	public RegType ofType()
	{
		throw notyet();
	}

	// am
	// filenode
	// tablespace

	/* Of limited interest ... estimates used by planner
	 *
	int pages();
	float tuples();
	int allVisible();
	 */

	@Override
	public RegClass toastRelation()
	{
		throw notyet();
	}

	@Override
	public boolean hasIndex()
	{
		throw notyet();
	}

	@Override
	public boolean isShared()
	{
		throw notyet();
	}

	// persistence
	// kind

	@Override
	public short nAttributes()
	{
		throw notyet();
	}

	@Override
	public short checks()
	{
		throw notyet();
	}

	@Override
	public boolean hasRules()
	{
		throw notyet();
	}

	@Override
	public boolean hasTriggers()
	{
		throw notyet();
	}

	@Override
	public boolean hasSubclass()
	{
		throw notyet();
	}

	@Override
	public boolean rowSecurity()
	{
		throw notyet();
	}

	@Override
	public boolean forceRowSecurity()
	{
		throw notyet();
	}

	@Override
	public boolean isPopulated()
	{
		throw notyet();
	}

	// replident

	@Override
	public boolean isPartition()
	{
		throw notyet();
	}

	// rewrite
	// frozenxid
	// minmxid

	@Override
	public List<String> options()
	{
		throw notyet();
	}

	// partbound
}
