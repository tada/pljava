/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
