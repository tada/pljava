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
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Mojo(name = "generate-javadoc")
@Execute(phase = LifecyclePhase.NONE)
public class DocumentationMojo extends AbstractMavenReport
{
	@Parameter
	private PlexusConfiguration script;

	private final List<String> javadocArguments = new ArrayList<>();

	@Override
	public String getOutputName ()
	{
		return "Documentation Report";
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
	protected void executeReport (Locale locale) throws MavenReportException
	{
		try
		{
			ScriptEngine engine = PGXSUtils.getScriptEngine(script, getLog());
			String scriptText = script.getValue();
			getLog().debug(scriptText);

			engine.getContext().setAttribute("report", this,
			                                 ScriptContext.GLOBAL_SCOPE);
			engine.put("addJavadocArgument",
			           (Consumer<String>) this::addJavadocArgument);
			engine.eval(scriptText);

			PGXSUtils.executeDocumentationTool(javadocArguments);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void addJavadocArgument(String argument)
	{
		javadocArguments.add(argument);
	}

}
