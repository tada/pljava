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

import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Locale;

@Mojo(name = "scripting-report")
@Execute(phase = LifecyclePhase.NONE)
public class ReportScriptingMojo extends AbstractMavenReport
{
	@Parameter
	public PlexusConfiguration script;

	private ReportScript reportScript;

	private void setReportScript()
	{
		if ( null != reportScript )
			return;

		try
		{
			ScriptEngine engine = PGXSUtils.getScriptEngine(script, getLog());
			String scriptText = script.getValue();
			getLog().debug(scriptText);
			engine.eval(scriptText);
			reportScript = ((Invocable)engine).getInterface(ReportScript.class);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public String getOutputName ()
	{
		setReportScript();
		return reportScript.getOutputName(this);
	}

	@Override
	public boolean isExternalReport ()
	{
		setReportScript();
		return reportScript.isExternalReport(this);
	}

	@Override
	public String getName (Locale locale)
	{
		setReportScript();
		return reportScript.getName(this, locale);
	}

	@Override
	public String getDescription (Locale locale)
	{
		setReportScript();
		return reportScript.getDescription(this, locale);
	}

	@Override
	public String getCategoryName ()
	{
		setReportScript();
		return reportScript.getCategoryName(this);
	}

	@Override
	public boolean canGenerateReport ()
	{
		setReportScript();
		return reportScript.canGenerateReport(this);
	}

	@Override
	protected void executeReport (Locale locale)
	{
		setReportScript();
		reportScript.executeReport(this, locale);
	}

	@Override
	public MavenProject getProject ()
	{
		return super.getProject();
	}

	@Override
	public String getInputEncoding ()
	{
		return super.getInputEncoding();
	}

	@Override
	public String getOutputEncoding ()
	{
		return super.getOutputEncoding();
	}

	boolean isExternalReportDefault ()
	{
		return super.isExternalReport();
	}

	String getCategoryNameDefault ()
	{
		return super.getCategoryName();
	}

	boolean canGenerateReportDefault ()
	{
		return super.canGenerateReport();
	}
}
