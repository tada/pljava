/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 */
package org.postgresql.pljava.pgxs;

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import java.util.Locale;

@Mojo(name = "scripting-report")
@Execute(phase = LifecyclePhase.NONE)
public class ReportScriptingMojo extends AbstractMavenReport
{
	@Parameter
	public PlexusConfiguration script;

	public ReportScript reportScript = new ReportScript() {};

	public ReportScript getReportScript ()
	{
		return reportScript;
	}

	public void setReportScript (ReportScript reportScript)
	{
		this.reportScript = reportScript;
	}

	@Override
	public String getOutputName ()
	{
		return reportScript.getOutputName();
	}

	@Override
	public boolean isExternalReport ()
	{
		return reportScript.isExternalReport();
	}

	@Override
	public String getName (Locale locale)
	{
		return reportScript.getName(locale);
	}

	@Override
	public String getDescription (Locale locale)
	{
		return reportScript.getDescription(locale);
	}

	@Override
	public String getCategoryName ()
	{
		return reportScript.getCategoryName();
	}

	@Override
	public boolean canGenerateReport ()
	{
		return reportScript.canGenerateReport();
	}

	@Override
	protected void executeReport (Locale locale)
	{
		reportScript.executeReport(this);
	}

	@Override
	public MavenProject getProject ()
	{
		return super.getProject();
	}

	public String getInputEncoding ()
	{
		return super.getInputEncoding();
	}

	public String getOutputEncoding ()
	{
		return super.getOutputEncoding();
	}
}
