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

import org.apache.maven.reporting.MavenReportException;

import java.util.Locale;

/**
 * Provides reasonable defaults and other required methods for
 * using JavaScript to during {@code Site} lifecycle phase to configure a
 * {@code MavenReport}.
 */
public interface ReportScript
{
	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @return whether the report is an external report
	 * @see ReportScriptingMojo#isExternalReport()
	 */
	default boolean isExternalReport(ReportScriptingMojo report)
	{
		return report.isExternalReportDefault();
	}

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @return category name of the report
	 * @see ReportScriptingMojo#getCategoryName()
	 */
	default String getCategoryName(ReportScriptingMojo report)
	{
		return report.getCategoryNameDefault();
	}

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @return whether the report can be generated
	 * @see ReportScriptingMojo#canGenerateReport()
	 */
	default boolean canGenerateReport(ReportScriptingMojo report)
	{
		return report.canGenerateReportDefault();
	}

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @return path of the report relative to target site directory
	 * @see ReportScriptingMojo#getCategoryName()
	 */
	String getOutputName (ReportScriptingMojo report);

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @param locale preferred locale for the name
	 * @return name of the report
	 * @see ReportScriptingMojo#getName(Locale)
	 */
	String getName (ReportScriptingMojo report, Locale locale);

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @param locale preferred locale for the description
	 * @return description of the report
	 * @see ReportScriptingMojo#getDescription(Locale)
	 */
	String getDescription (ReportScriptingMojo report, Locale locale);

	/**
	 * @param report instance of {@link ReportScriptingMojo}
	 * @param locale Locale to use for any locale-sensitive content in
	 * the report
	 * @return null if execution completed successfully, Exception that occurred
	 * during execution otherwise
	 * @see ReportScriptingMojo#executeReport(Locale)
	 */
	MavenReportException executeReport(ReportScriptingMojo report, Locale locale);
}
