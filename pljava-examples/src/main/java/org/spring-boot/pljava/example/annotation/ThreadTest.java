/*
 * Copyright (c) 2015 Tada AB and other contributors, as listed below.
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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import java.lang.reflect.UndeclaredThrowableException;

import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.Function;

/**
 * Test control of access to 1-thread backend by n-thread JVM.
 *
 * The "select strictlyNestedTest()" is marked "notFromDDR" because it actually
 * <em>does</em> deadlock when invoked from within install_jar, though it
 * succeeds when invoked directly. The explanation may lie in the JNI spec's
 * caveat that JNI MonitorEnter/MonitorExit functions must be paired with each
 * other and not arbitrarily mixed with JVM monitorenter/monitorexit bytecodes.
 * In the present design, that can happen (install_jar uses a synchronized
 * block to call into the backend when executing DDR commands; DDR command
 * calling strictlyNestedTest leads to JNI MonitorExit in BEGIN_CALL; perhaps
 * that does not effectively release the lock taken by the synchronized block).
 */
@SQLAction(implementor="notFromDDR",
	requires="strictlyNestedTest fn",
	install="select strictlyNestedTest()"
)
public class ThreadTest implements Runnable {
	/**
	 * Test that another thread can enter SPI while the calling thread is out.
	 *
	 * Create a thread that uses SPI to perform a query and set the value of
	 * {@link #result}. Start and wait for that thread (so this one is clearly
	 * out of the backend the whole time), then return the result.
	 */
	@Function(provides="strictlyNestedTest fn")
	public static String strictlyNestedTest()
	throws SQLException {
		ThreadTest tt = new ThreadTest();
		Thread t = new Thread( tt);
		t.start();
		while ( true )
		{
			try
			{
				t.join();
			}
			catch ( InterruptedException ie )
			{
				continue;
			}
			break;
		}
		return tt.result;
	}

	String result;

	public void run()
	{
		try
		{
			Connection c = DriverManager.getConnection(
				"jdbc:default:connection");
			Statement s = c.createStatement();
			ResultSet rs = s.executeQuery( "select version() as version");
			rs.next();
			result = rs.getString( "version");
		}
		catch ( Exception e )
		{
			if ( e instanceof RuntimeException )
				throw (RuntimeException)e;
			throw new UndeclaredThrowableException( e);
		}
	}
}
