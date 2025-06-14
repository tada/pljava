/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.SQLException;
import java.util.IdentityHashMap;

import org.postgresql.pljava.ObjectPool;
import org.postgresql.pljava.PooledObject;

class ObjectPoolImpl<T extends PooledObject> implements ObjectPool<T>
{
	/**
	 * An InstanceHandle is a link in a single linked list that
	 * holds on to a ResultSetProvider.
	 */
	private static class PooledObjectHandle<T extends PooledObject>
	{
		private T m_instance;
		private PooledObjectHandle<T> m_next;
	}

	private static Class[] s_ctorSignature = { ObjectPool.class };
	private static PooledObjectHandle s_handlePool;
	private static final IdentityHashMap<Class<?>,ObjectPoolImpl<?>>
		s_poolCache = new IdentityHashMap<>();

	private final Constructor<T> m_ctor;
	private PooledObjectHandle<T> m_providerPool;

	private ObjectPoolImpl(Class<T> c)
	{
		if(!PooledObject.class.isAssignableFrom(c))
			throw new IllegalArgumentException("Class " + c + " does not implement the " +
				PooledObject.class + " interface");

		try
		{
			m_ctor = c.getConstructor(s_ctorSignature);
		}
		catch(SecurityException e)
		{
			throw new RuntimeException(e);
		}
		catch(NoSuchMethodException e)
		{
			throw new IllegalArgumentException("Unable to locate constructor " + c + "(ObjectPool)");
		}
	}

	/**
	 * Obtain a pool for the given class.
	 */
	@SuppressWarnings("unchecked")
	public static <T extends PooledObject> ObjectPoolImpl<T>
	getObjectPool(Class<T> cls)
	{
		ObjectPoolImpl<T> pool = (ObjectPoolImpl<T>)s_poolCache.get(cls);
		if(pool == null)
		{
			pool = new ObjectPoolImpl(cls);
			s_poolCache.put(cls, pool);
		}
		return pool;
	}

	@SuppressWarnings("unchecked")
	public T activateInstance()
	throws SQLException
	{
		T instance;
		PooledObjectHandle<T> handle = m_providerPool;
		if(handle != null)
		{
			m_providerPool = handle.m_next;
			instance = handle.m_instance;

			// Return the handle to the unused handle pool.
			//
			handle.m_instance = null;
			handle.m_next = s_handlePool;
			s_handlePool = handle;
		}
		else
		{
			try
			{
				instance = m_ctor.newInstance(new Object[] { this });
			}
			catch(InvocationTargetException e)
			{
				Throwable t = e.getTargetException();
				if(t instanceof SQLException)
					throw (SQLException)t;
				if(t instanceof RuntimeException)
					throw (RuntimeException)t;
				if(t instanceof Error)
					throw (Error)t;
				throw new SQLException(e.getMessage());
			}
			catch(RuntimeException e)
			{
				throw e;
			}
			catch(Exception e)
			{
				throw new SQLException("Failed to create an instance of: " +
					m_ctor.getDeclaringClass() + " :" + e.getMessage());
			}
		}
		try
		{
			instance.activate();
		}
		catch(SQLException e)
		{
			instance.remove();
			throw e;
		}
		return instance;
	}

	public void passivateInstance(T instance)
	throws SQLException
	{
		try
		{
			instance.passivate();
		}
		catch(SQLException e)
		{
			instance.remove();
			throw e;
		}

		// Obtain a handle from the pool of handles so that
		// we have something to wrap the instance in.
		//
		@SuppressWarnings("unchecked")
		PooledObjectHandle<T> handle = (PooledObjectHandle<T>)s_handlePool;
		if(handle != null)
			s_handlePool = handle.m_next;
		else
			handle = new PooledObjectHandle<>();

		handle.m_instance = instance;
		handle.m_next = m_providerPool;
		m_providerPool = handle;
	}

	@SuppressWarnings("unchecked")
	public void removeInstance(T instance) throws SQLException
	{
		PooledObjectHandle prev = null;
		for(PooledObjectHandle handle = m_providerPool;
			handle != null; handle = handle.m_next)
		{
			if(handle.m_instance == instance)
			{
				if(prev == null)
					m_providerPool = handle.m_next;
				else
					prev.m_next = handle.m_next;

				handle.m_instance = null;
				handle.m_next = s_handlePool;
				s_handlePool = handle;
				break;
			}
		}
		instance.remove();
	}
}
