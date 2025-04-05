/*
 * Copyright (c) 2025 Tada AB and other contributors, as listed below.
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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

import org.postgresql.pljava.TargetList.Projection;

import java.sql.SQLXML;
import java.util.List;

/**
 * Model of the PostgreSQL {@code pg_constraing} system catalog.
 */
public interface Constraint
extends Addressed<Constraint>, Namespaced<Simple>
{
	RegClass.Known<Constraint> CLASSID =
		formClassId(ConstraintRelationId, Constraint.class);

	enum Type
	{
		CHECK, FOREIGN_KEY, NOT_NULL, PRIMARY_KEY, UNIQUE, CONSTRAINT_TRIGGER,
		EXCLUSION
	}

	enum ReferentialAction { NONE, RESTRICT, CASCADE, SET_NULL, SET_DEFAULT }

	enum MatchType { FULL, PARTIAL, SIMPLE }

	Type type();
	boolean deferrable();
	boolean deferred();
	boolean validated();
	RegClass onTable();
	RegType onDomain();
	RegClass index();
	Constraint parent();
	RegClass referencedTable();
	/**
	 * The action specified for update of a referenced column; null if not
	 * a foreign key constraint.
	 */
	ReferentialAction updateAction();
	/**
	 * The action specified for delete of a referenced row; null if not
	 * a foreign key constraint.
	 */
	ReferentialAction deleteAction();
	/**
	 * How foreign-key columns are to be matched; null if not
	 * a foreign key constraint.
	 */
	MatchType matchType();
	boolean isLocal();
	short inheritCount();
	boolean noInherit();
	Projection key();
	Projection fkey();
	List<RegOperator> pfEqOp();
	List<RegOperator> ppEqOp();
	List<RegOperator> ffEqOp();
	Projection fdelSetColumns();
	List<RegOperator> exclOp();
	SQLXML bin();
}
