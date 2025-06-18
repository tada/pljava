/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.internal;

import java.sql.SQLException;


/**
 * A Java exception constructed from a PostgreSQL SPI result code.
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
