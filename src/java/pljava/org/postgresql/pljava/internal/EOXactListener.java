/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;


/**
 * Interface implemented by internal classes that wishes to listen to the
 * PostgreSQL EOXact event.
 *
 * @author Thomas Hallgren
 */
public interface EOXactListener
{
	/**
	 * Callback received from the backend when a transaction has ended.
	 * @param isCommit Set to <code>true</code> if the commit was a success
	 * and <code>false</code> if the transaction aborted.
	 */
	public void onEOXact(boolean isCommit);
}
