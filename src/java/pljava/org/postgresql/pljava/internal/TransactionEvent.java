/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.util.EventObject;

/**
 * @author Thomas Hallgren
 */
public class TransactionEvent extends EventObject
{
	/**
	 * @param source
	 */
	public TransactionEvent(Object source)
	{
		super(source);
		// TODO Auto-generated constructor stub
	}
}
