/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;


/**
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class SPIException extends SQLException
{
	private static final long serialVersionUID = -834098440757881189L;

	public SPIException(int resultCode)
	{
		super("SPI exception. Result = " + SPI.getResultText(resultCode));
	}
}
