/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.sqlgen;

import org.postgresql.pljava.annotation.Trigger;

import static org.postgresql.pljava.annotation.Trigger.Event.DELETE;
import static org.postgresql.pljava.annotation.Trigger.Event.INSERT;
import static org.postgresql.pljava.annotation.Trigger.Event.TRUNCATE;

/**
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - update to Java6
 */

class TriggerNamer
{
	static String synthesizeName( Trigger t)
	{
		StringBuilder bld = new StringBuilder();
		bld.append("trg_");
		bld.append((t.when() == Trigger.When.BEFORE) ? 'b' : 'a');
		bld.append((t.scope() == Trigger.Scope.ROW) ? 'r' : 's');

		// Fixed order regardless of order in list.
		//
		boolean atDelete = false;
		boolean atInsert = false;
		boolean atUpdate = false;
		boolean atTruncate = false;
		for( Trigger.Event e : t.events() )
		{
			switch( e )
			{
				case DELETE:
					atDelete = true;
					break;
				case INSERT:
					atInsert = true;
					break;
				case TRUNCATE:
					atTruncate = true;
					break;
				default:
					atUpdate = true;
			}
		}
		bld.append('_');
		if(atDelete)
			bld.append('d');
		if(atInsert)
			bld.append('i');
		if(atUpdate)
			bld.append('u');
		if(atTruncate)
			bld.append('t');
		bld.append('_');
		bld.append(t.table());
		return bld.toString();
	}
}
