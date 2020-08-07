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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Mojo(name = "scripting", defaultPhase = LifecyclePhase.INITIALIZE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	@Component
	private MavenProject project;

	@Parameter
	private PlexusConfiguration script;

	@Override
	public void execute ()
	{
		try
		{
			String scriptText = script.getValue();
			ScriptEngine engine = PGXSUtils.getScriptEngine(script, getLog());
			getLog().debug(scriptText);

			engine.getContext().setAttribute("plugin", this,
				ScriptContext.GLOBAL_SCOPE);
			engine.put("quoteStringForC",
				(Function<String, String>) PGXSUtils::quoteStringForC);
			engine.put("setProjectProperty",
				(BiConsumer<String, String>) this::setProjectProperty);
			engine.put("getPgConfigProperty",
				(Function<String, String>) this::getPgConfigProperty);
			engine.eval(scriptText);
		}
		catch (ScriptException e)
		{
			e.printStackTrace();
		}
	}

	public void setProjectProperty (String property, String value)
	{
		project.getProperties().setProperty(property, value);
	}

	public String getPgConfigProperty (String property)
	{
		try
		{
			String pgConfigCommand = project.getProperties()
				                         .getProperty("pgsql.pgconfig");
			return PGXSUtils.getPgConfigProperty(pgConfigCommand, property);
		}
		catch (Exception e)
		{
			e.printStackTrace();
			return null;
		}
	}
}
