/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.annotation.processing;

import org.postgresql.pljava.annotation.Trigger;

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
		switch ( t.called() )
		{
			case     BEFORE: bld.append( 'b'); break;
			case      AFTER: bld.append( 'a'); break;
			case INSTEAD_OF: bld.append( 'i'); break;
		}
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
		return bld.toString();
	}
}
