/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;

/**
 * The <code>TypeMap</code> maps PostgreSQL oid to java classes.
 *
 * @author Filip Hrbek
 */
public class TypeMap extends NativeStruct
{
	/**
	 * Retrieves java class name frm PostgreSQL OID.
     * If no corresponding type is found, java.lang.String is returned.
	 * @param oid Type OID
	 * @return Java class name.
	 * @throws SQLException If any error occurs.
	 */
	public static String getClassNameFromPgOid(int oid)
	throws SQLException
	{
		synchronized(Backend.THREADLOCK)
		{
			return _getClassNameFromPgOid(oid);
		}
	}

	private native static String _getClassNameFromPgOid(int oid)
	throws SQLException;
}

