/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
