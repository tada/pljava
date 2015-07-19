/*
 * Copyright (c) 2004-2015 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava;

import java.sql.SQLException;

/**
 * A pool of objects of a single class.
 * Obtain an <code>ObjectPool</code> from the {@link Session} by calling
 * {@link Session#getObjectPool getObjectPool} with a {@link Class} object
 * for the class to be pooled, which must implement {@link PooledObject}.
 * @author Thomas Hallgren
 */
public interface ObjectPool
{
	/**
	 * Obtain a pooled object, calling its {@link PooledObject#activate()}
	 * method. A new instance is created if needed. The pooled
	 * object is removed from the pool and activated.
	 * 
	 * @return A new object or an object found in the pool.
	 */
	PooledObject activateInstance()
	throws SQLException;

	/**
	 * Call the {@link PooledObject#passivate()} method and return the object
	 * to the pool.
	 * @param instance The instance to passivate.
	 */
	void passivateInstance(PooledObject instance)
	throws SQLException;

	/**
	 * Call the {@link PooledObject#remove()} method and evict the object
	 * from the pool.
	 */
	void removeInstance(PooledObject instance)
	throws SQLException;
}
