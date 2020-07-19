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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.List;

@Mojo(name = "scripting", defaultPhase = LifecyclePhase.INITIALIZE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	ScriptEngine engine;

	@Parameter(defaultValue = "${project}", required = true, readonly = true)
	MavenProject project;

	@Parameter(property = "script")
	private String script;

	@Parameter(property = "plugin.artifacts", required = true, readonly = true)
	private List<Artifact> pluginArtifacts;

	@Override
	public void execute ()
	{
		ScriptEngineManager manager = new ScriptEngineManager(
			ClassLoader.getSystemClassLoader());
		engine = manager.getEngineByName("JavaScript");

		getLog().debug(engine.toString());
		getLog().debug(script);

		try
		{
			engine.getContext().setAttribute("plugin", this,
				ScriptContext.GLOBAL_SCOPE);
			engine.eval(
				"function quoteStringForC(text)" +
					"{" +
					"return Packages.PGXSUtils.quoteStringForC(text);" +
					"}");

			engine.eval(
				"function setProjectProperty(key, value)" +
					"{" +
					"plugin.setProjectProperty(key, value);" +
					"}");

			engine.eval(
				"function getPgConfigProperty(key)" +
					"{" +
					"return plugin.getPgConfigProperty(key);" +
					"}");
			engine.eval(script);
		}
		catch (ScriptException e)
		{
			e.printStackTrace();
		}
	}

	public void setProjectProperty (String property, String value)
	{
		PGXSUtils.setPgConfigProperty(project, property, value);
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
