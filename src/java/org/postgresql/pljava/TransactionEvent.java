/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

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
