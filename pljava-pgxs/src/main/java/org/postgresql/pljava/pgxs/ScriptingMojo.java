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

/**
 * Maven plugin goal to use JavaScript during any of build lifecycle phases.
 * <p>
 * The Mojo provides a limited subset of the functionality provided Maven AntRun
 * Plugin. This is intentional to simplify usage as this maven plugin is
 * specifically targeted at building Pl/Java native code.
 */
@Mojo(name = "scripted-goal", defaultPhase = LifecyclePhase.INITIALIZE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter
	private PlexusConfiguration script;

	/**
	 * Executes the javascript code inside {@code script} tag inside plugin
	 * configuration.
	 */
	@Override
	public void execute ()
	{
		try
		{
			String scriptText = script.getValue();
			ScriptEngine engine =
				PGXSUtils.getScriptEngine(script, getLog(), project);
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

	/**
	 * Sets the value of a property for the current project.
	 *
	 * @param property key to use for property
	 * @param value the value of property to set
	 */
	public void setProjectProperty (String property, String value)
	{
		project.getProperties().setProperty(property, value);
	}

	/**
	 * Returns the value of a pg_config property.
	 *
	 * @param property property whose value is to be retrieved from pg_config
	 * @return output of pg_config executed with the input property as argument
	 */
	public String getPgConfigProperty (String property)
	{
		try
		{
			String pgConfigCommand = System.getProperty("pgsql.pgconfig");
			return PGXSUtils.getPgConfigProperty(pgConfigCommand, property);
		}
		catch (Exception e)
		{
			getLog().error(e);
			return null;
		}
	}
}
