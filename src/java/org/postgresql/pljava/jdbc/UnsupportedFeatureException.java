/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.sql.SQLException;

/**
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class UnsupportedFeatureException extends SQLException
{
	public static final String FEATURE_NOT_SUPPORTED_EXCEPTION = "0A000";

	public UnsupportedFeatureException(String feature)
	{
		super("Feature not supported: " + feature, FEATURE_NOT_SUPPORTED_EXCEPTION);
	}
}
