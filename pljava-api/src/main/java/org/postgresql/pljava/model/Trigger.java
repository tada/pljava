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

import java.sql.SQLXML;

import java.util.List;
import java.util.Set;

import org.postgresql.pljava.TargetList.Projection;

import org.postgresql.pljava.annotation.Trigger.Called;
import org.postgresql.pljava.annotation.Trigger.Event;
import org.postgresql.pljava.annotation.Trigger.Scope;

import org.postgresql.pljava.model.CatalogObject.*;
import org.postgresql.pljava.model.RegProcedure.Call.Context.TriggerData;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.model.RegProcedure.Memo.Why;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a trigger entry in the PostgreSQL catalogs.
 *<p>
 * This catalog object, at least at first, will have an unusual limitation:
 * its accessor methods (other than those of {@link Addressed}) may only work
 * when called by a trigger function or its language handler within the scope
 * of the function's specialization and execution. Some may be unimplemented
 * even then, as noted in the documentation of the methods themselves.
 */
public interface Trigger
extends
	Addressed<Trigger>, Named<Simple>
{
	RegClass.Known<Trigger> CLASSID =
		formClassId(TriggerRelationId, Trigger.class);

	enum ReplicationRole { ON_ORIGIN, ALWAYS, ON_REPLICA, DISABLED };

	interface ForTrigger extends Why<ForTrigger> { }

	/**
	 * Name of this trigger.
	 *<p>
	 * The table on which the trigger is declared serves as a namespace,
	 * within which trigger names on the same table must be unique.
	 */
	@Override
	Simple name();

	/**
	 * The table on which this trigger is declared.
	 *<p>
	 * May throw {@code UnsupportedOperationException}. Within a trigger
	 * function or its handler, {@link TriggerData} can supply the same
	 * information.
	 */
	RegClass relation();

	/**
	 * Parent trigger this trigger is cloned from (applies to partitioned
	 * tables), null if not a clone.
	 *<p>
	 * May throw {@code UnsupportedOperationException}.
	 * @see #isClone
	 */
	Trigger parent();

	/**
	 * The function to be called.
	 *<p>
	 * May throw {@code UnsupportedOperationException}. Within a trigger
	 * function or its handler, this is just the function being called.
	 */
	RegProcedure<ForTrigger> function();

	/**
	 * When this trigger is to fire (before, after, or instead of the
	 * triggering event).
	 */
	Called called();

	/**
	 * The event(s) for which the trigger can fire.
	 */
	Set<Event> events();

	/**
	 * The scope (per-statement or per-row) of this trigger.
	 */
	Scope scope();

	/**
	 * For which {@code session_replication_role} modes the trigger fires.
	 */
	ReplicationRole enabled();

	/**
	 * True if the trigger is internally generated (usually to enforce the
	 * constraint identified by {@link #constraint}).
	 */
	boolean internal();

	/**
	 * The referenced table if this trigger pertains to a referential integrity
	 * constraint, otherwise null.
	 */
	RegClass constraintRelation();

	/**
	 * The index supporting a unique, primary key, referential integrity, or
	 * exclusion constraint, null if this trigger is not for such a constraint.
	 */
	RegClass constraintIndex();

	/**
	 * The constraint associated with the trigger, null if none.
	 * @return null, no {@code Constraint} catalog object is implemented yet
	 */
	Constraint constraint();

	/**
	 * True for a constraint trigger that is deferrable.
	 */
	boolean deferrable();

	/**
	 * True for a constraint trigger initially deferred.
	 */
	boolean initiallyDeferred();

	/**
	 * The columns of interest (as a {@link Projection} of {@link #relation}'s
	 * columns) if the trigger is column-specific, otherwise null.
	 */
	Projection columns();

	/**
	 * Any additional {@code String} arguments to pass to the trigger function.
	 */
	List<String> arguments();

	/**
	 * A {@code pg_node_tree} representation of a boolean expression restricting
	 * when this trigger can fire, or null if none.
	 */
	SQLXML when();

	/**
	 * Name by which an ephemeral table showing prior row values can be queried
	 * via SPI by the function for an {@code AFTER} trigger whose
	 * {@link #events events} include {@code UPDATE} or {@code DELETE}.
	 */
	Simple tableOld();

	/**
	 * Name by which an ephemeral table showing new row values can be queried
	 * via SPI by the function for an {@code AFTER} trigger whose
	 * {@link #events events} include {@code UPDATE} or {@code INSERT}.
	 */
	Simple tableNew();

	/**
	 * True if this trigger is a clone.
	 *<p>
	 * This information will be available to a trigger function or its handler,
	 * even if the actual {@link #parent parent} trigger is not.
	 */
	boolean isClone();
}
