/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
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
	public TransactionEvent(Object eventSource)
	{
		super(eventSource);
	}
}
