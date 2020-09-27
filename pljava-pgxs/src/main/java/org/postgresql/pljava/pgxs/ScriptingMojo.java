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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Maven plugin goal to use JavaScript during any of build lifecycle phases.
 * <p>
 * The Mojo provides a limited subset of the functionality provided Maven AntRun
 * Plugin. This is intentional to simplify usage as this maven plugin is
 * specifically targeted at building Pl/Java native code.
 */
@Mojo(name = "scripted-goal", defaultPhase = LifecyclePhase.COMPILE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	@Parameter(defaultValue = "${project}", readonly = true)
	private MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true)
	private MavenSession session;


	@Parameter
	private PlexusConfiguration script;

	private PGXSUtils utils;

	/**
	 * Executes the javascript code inside {@code script} tag inside plugin
	 * configuration.
	 */
	@Override
	public void execute () throws MojoExecutionException, MojoFailureException
	{
		try
		{
			utils = new PGXSUtils(project, getLog());
			String scriptText = script.getValue();
			ScriptEngine engine = utils.getScriptEngine(script);
			getLog().debug(scriptText);

			engine.getContext().setAttribute("session", session,
				ScriptContext.GLOBAL_SCOPE);
			engine.getContext().setAttribute("plugin", this,
				ScriptContext.GLOBAL_SCOPE);
			engine.put("quoteStringForC",
				(Function<String, String>) utils::quoteStringForC);
			engine.put("setProjectProperty",
				(BiConsumer<String, String>) this::setProjectProperty);
			engine.put("getPgConfigProperty",
				(Function<String, String>) this::getPgConfigProperty);
			engine.eval(scriptText);

			GoalScript goal = ((Invocable) engine).getInterface(GoalScript.class);
			AbstractMojoExecutionException exception = goal.execute();
			if (exception != null)
				throw exception;
		}
		catch (MojoFailureException | MojoExecutionException e)
		{
			throw e;
		}
		catch (Exception e)
		{
			throw (MojoExecutionException) exceptionWrap(e, true);
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
			return utils.getPgConfigProperty(pgConfigCommand, property);
		}
		catch (Exception e)
		{
			getLog().error(e);
			return null;
		}
	}

	/**
	 * Wraps the input object in a {@link AbstractMojoExecutionException}.
	 *
	 * The returned exception is constructed as follows:
	 * 1) If {@code object} is null, then {@link MojoExecutionException} is used
	 * to wrap and the message indicates that null value was thrown by the script.
	 * 2) If {@code object} is already a {@link MojoExecutionException}, return
	 * it as is.
	 * 3) If {@code object} is already a {@link MojoFailureException}, return it
	 * as is.
	 *
	 * For the steps, below the wrapping exception is chosen according to the
	 * the value of {@code scriptFailure} parameter.
	 *
	 * 4) If {@code object} is any other {@link Throwable}, set it as the cause
	 * for the exception.
	 * 5) If {@code object} is a {@link String}, set it as the message of the
	 * exception.
	 * 6) For all other case, the message of the exception is set in this format
	 * , Class Name of object: String representation of object.
	 *
	 * @param object to wrap in AbstractMojoExecutionException
	 * @param scriptFailure if true, use a MojoExecutionException for wrapping
	 *                      otherwise use MojoFailureException. this parameter
	 *                      is ignored, if the object is null or instance of
	 *                      MojoExecutionException or MojoFailureException
	 * @return object wrapped inside a {@link AbstractMojoExecutionException}
	 */
	public AbstractMojoExecutionException exceptionWrap(Object object,
														boolean scriptFailure)
	{
		BiFunction<String, Throwable, ? extends AbstractMojoExecutionException>
			createException = scriptFailure ? MojoExecutionException::new :
			MojoFailureException::new;

		AbstractMojoExecutionException exception;
		if (object == null)
			exception = new MojoExecutionException("Script threw a null value");
		else if (object instanceof MojoExecutionException)
			exception = (MojoExecutionException) object;
		else if (object instanceof MojoFailureException)
			exception = (MojoFailureException) object;
		else if (object instanceof Throwable)
		{
			Throwable t = (Throwable) object;
			exception = createException.apply(t.getMessage(), t);
		}
		else if (object instanceof String)
			exception = createException.apply((String) object, null);
		else
		{
			String message = object.getClass().getCanonicalName() + ": "
				+ object.toString();
			exception = createException.apply(message, null);
		}
		return exception;
	}

}
