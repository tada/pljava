/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 *   Kartik Ohri
 */
package org.postgresql.pljava.pgxs;

import java.util.Locale;

public interface ReportScript
{
	default boolean isExternalReport(ReportScriptingMojo report) {
		return report.isExternalReportDefault();
	}

	default String getCategoryName(ReportScriptingMojo report) {
		return report.getCategoryNameDefault();
	}

	default boolean canGenerateReport(ReportScriptingMojo report) {
		return report.canGenerateReportDefault();
	}

	String getOutputName (ReportScriptingMojo report);

	String getName (ReportScriptingMojo report, Locale locale);

	String getDescription (ReportScriptingMojo report, Locale locale);

	void executeReport(ReportScriptingMojo report, Locale locale);
}
