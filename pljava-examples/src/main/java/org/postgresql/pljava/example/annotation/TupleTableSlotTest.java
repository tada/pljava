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
package org.postgresql.pljava.example.annotation;

import java.sql.Connection;
import static java.sql.DriverManager.getConnection;
import java.sql.SQLException;

import java.util.List;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsBoolean;

import org.postgresql.pljava.annotation.Function;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.SlotTester;
import org.postgresql.pljava.model.TupleTableSlot;

/**
 * A temporary test jig during TupleTableSlot development; intended
 * to be used from a debugger.
 */
public class TupleTableSlotTest
{
	/**
	 * A temporary test jig during TupleTableSlot development; intended
	 * to be used from a debugger.
	 */
	@Function(schema="javatest")
	public static void tupleTableSlotTest(
		String query, String adpClass, String adpInstance)
	throws SQLException, ReflectiveOperationException
	{
		new TupleTableSlotTest().testWith(query, adpClass, adpInstance);
	}

	As<?,?>      adpL;
	AsLong<?>    adpJ;
	AsDouble<?>  adpD;
	AsInt<?>     adpI;
	AsFloat<?>   adpF;
	AsShort<?>   adpS;
	AsChar<?>    adpC;
	AsByte<?>    adpB;
	AsBoolean<?> adpZ;

	void testWith(String query, String adpClass, String adpInstance)
	throws SQLException, ReflectiveOperationException
	{
		Connection c = getConnection("jdbc:default:connection");
		SlotTester t = c.unwrap(SlotTester.class);

		List<TupleTableSlot> tups = t.test(query);

		int ntups = tups.size();

		boolean firstTime = true;

		int form = 8; // set with debugger, 8 selects reference-typed adpL

		boolean go; // true until set false by debugger each time through loop

		/*
		 * Results from adapters of assorted types.
		 */
		long    jj = 0;
		double  dd = 0;
		int     ii = 0;
		float   ff = 0;
		short   ss = 0;
		char    cc = 0;
		byte    bb = 0;
		boolean zz = false;
		Object  ll = null;

		for ( TupleTableSlot tts : tups )
		{
			if ( firstTime )
			{
				firstTime = false;
				Adapter a = tts.adapterPlease(adpClass, adpInstance);
				if ( a instanceof As )
					adpL = (As<?,?>)a;
				else if ( a instanceof AsLong )
					adpJ = (AsLong<?>)a;
				else if ( a instanceof AsDouble )
					adpD = (AsDouble<?>)a;
				else if ( a instanceof AsInt )
					adpI = (AsInt<?>)a;
				else if ( a instanceof AsFloat )
					adpF = (AsFloat<?>)a;
				else if ( a instanceof AsShort )
					adpS = (AsShort<?>)a;
				else if ( a instanceof AsChar )
					adpC = (AsChar<?>)a;
				else if ( a instanceof AsByte )
					adpB = (AsByte<?>)a;
				else if ( a instanceof AsBoolean )
					adpZ = (AsBoolean<?>)a;
			}

			for ( Attribute att : tts.descriptor().attributes() )
			{
				go = true;
				while ( go )
				{
					go = false;
					try
					{
						switch ( form )
						{
						case 0: jj = tts.get(att, adpJ); break;
						case 1: dd = tts.get(att, adpD); break;
						case 2: ii = tts.get(att, adpI); break;
						case 3: ff = tts.get(att, adpF); break;
						case 4: ss = tts.get(att, adpS); break;
						case 5: cc = tts.get(att, adpC); break;
						case 6: bb = tts.get(att, adpB); break;
						case 7: zz = tts.get(att, adpZ); break;
						case 8: ll = tts.get(att, adpL); break;
						}
					}
					catch ( SQLException e )
					{
						System.out.println(e);
					}
				}
			}
		}
	}
}
