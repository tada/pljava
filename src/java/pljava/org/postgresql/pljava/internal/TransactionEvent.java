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
	private static final long serialVersionUID = -4654877678815379385L;

	/**
	 * @param source
	 */
	public TransactionEvent(Object source)
	{
		super(source);
	}
}
