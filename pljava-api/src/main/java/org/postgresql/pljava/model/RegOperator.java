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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.model.RegProcedure.Memo;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Operator;

/**
 * Model of a PostgreSQL operator as defined in the system catalogs, including
 * its kind (infix or prefix), operand and result types, and a number of
 * properties helpful in query planning.
 */
public interface RegOperator
extends Addressed<RegOperator>, Namespaced<Operator>, Owned
{
	RegClass.Known<RegOperator> CLASSID =
		formClassId(OperatorRelationId, RegOperator.class);

	enum Kind
	{
		/**
		 * An operator used between a left and a right operand.
		 */
		INFIX,

		/**
		 * An operator used to the left of a single right operand.
		 */
		PREFIX,

		/**
		 * An operator used to the right of a single left operand.
		 * @deprecated Postfix operators are deprecated since PG 13 and
		 * unsupported since PG 14.
		 */
		@Deprecated(since="PG 13")
		POSTFIX
	}

	interface Evaluator              extends Memo<Evaluator> { }
	interface RestrictionSelectivity extends Memo<RestrictionSelectivity> { }
	interface JoinSelectivity        extends Memo<JoinSelectivity> { }

	Kind kind();
	boolean canMerge();
	boolean canHash();
	RegType leftOperand();
	RegType rightOperand();
	RegType result();
	RegOperator commutator();
	RegOperator negator();
	RegProcedure<Evaluator> evaluator();
	RegProcedure<RestrictionSelectivity> restrictionEstimator();
	RegProcedure<JoinSelectivity> joinEstimator();
}
