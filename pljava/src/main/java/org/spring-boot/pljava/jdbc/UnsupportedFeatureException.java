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
package org.postgresql.pljava.jdbc;

import java.sql.SQLFeatureNotSupportedException;

/**
 * An {@code SQLException} specific to the case of attempted use of
 * an unsupported feature.
 * @author <a href="mailto:thomas.hallgren@ironjug.com">Thomas Hallgren</a>
 */
public class UnsupportedFeatureException extends SQLFeatureNotSupportedException
{
	private static final long serialVersionUID = 7956037664745636982L;

	public static final String FEATURE_NOT_SUPPORTED_EXCEPTION = "0A000";

	public UnsupportedFeatureException(String feature)
	{
		super("Feature not supported: " + feature, FEATURE_NOT_SUPPORTED_EXCEPTION);
	}
}
