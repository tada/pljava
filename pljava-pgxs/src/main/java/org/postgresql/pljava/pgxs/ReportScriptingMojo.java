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

import javax.script.ScriptEngine;
import java.io.File;
import java.util.Locale;

@Mojo(name = "scripting-report")
@Execute(phase = LifecyclePhase.NONE)
public class ReportScriptingMojo extends AbstractMavenReport
{
	@Parameter
	private PlexusConfiguration script;

	@Override
	public String getOutputName ()
	{
		return "apidocs" + File.separator + "index";
	}

	@Override
	public boolean isExternalReport ()
	{
		return true;
	}

	@Override
	public String getName (Locale locale)
	{
		return String.format(locale, "%s", "Documentation Report");
	}

	@Override
	public String getDescription (Locale locale)
	{
		return "Javadoc Generation Goal";
	}

	@Override
	protected void executeReport (Locale locale)
	{
		try
		{
			ScriptEngine engine = PGXSUtils.getScriptEngine(script, getLog());
			engine.put("report", this);
			String scriptText = script.getValue();
			getLog().debug(scriptText);
			engine.eval(scriptText);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
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
