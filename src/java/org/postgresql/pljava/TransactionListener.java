/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2004 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava;

import java.util.EventListener;

/**
 * @author Thomas Hallgren
 */
public interface TransactionListener extends EventListener
{
	void afterAbort(TransactionEvent e);

	void afterCommit(TransactionEvent e);
}
