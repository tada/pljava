/*
 * Copyright (c) 2020-2024 Tada AB and other contributors, as listed below.
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
import javax.script.ScriptEngine;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

import static javax.script.ScriptContext.ENGINE_SCOPE;

/**
 * Maven plugin goal to use JavaScript (or another JSR 223 script engine)
 * during any of build lifecycle phases.
 * <p>
 * The Mojo provides a limited subset of the functionality of the Maven AntRun
 * Plugin. This is intentional to simplify usage, as this Maven plugin is
 * specifically targeted at building PL/Java native code.
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
	 * Executes the script code inside the {@code script} tag in the plugin
	 * configuration.
	 *<p>
	 * Uses {@link PGXSUtils#getScriptEngine PGXSUtils.getScriptEngine}
	 * to instantiate the engine, and then makes these items available in
	 * the engine's scope (in addition to those placed there by
	 * {@link PGXSUtils#getScriptEngine getScriptEngine} itself):
	 *<dl>
	 * <dt>session<dd>The Maven session object
	 * <dt>plugin<dd>This object
	 *</dl>
	 */
	@Override
	public void execute () throws MojoExecutionException, MojoFailureException
	{
		try
		{
			utils = new PGXSUtils(project, getLog());
			String scriptText = script.getValue();
			ScriptEngine engine = utils.getScriptEngine(script);

			engine.getContext().setAttribute("session", session, ENGINE_SCOPE);
			engine.getContext().setAttribute("plugin", this, ENGINE_SCOPE);

			engine.eval(scriptText);

			GoalScript goal =
				((Invocable) engine).getInterface(GoalScript.class);

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
	 * Wraps the input object in an {@link AbstractMojoExecutionException}.
	 *
	 * The returned exception is constructed as follows:
	 *<ul>
	 * <li>If {@code object} is null, then {@link MojoExecutionException} is
	 * used to wrap and the message indicates that a null value was thrown
	 * by the script.
	 * <li>If {@code object} is already a {@link MojoExecutionException}, it is
	 * returned as is.
	 * <li>If {@code object} is already a {@link MojoFailureException}, it is
	 * returned as is.
	 * <li>For the steps below, the wrapping exception is chosen according to
	 * the value of the {@code scriptFailure} parameter.
	 * <li>If {@code object} is any other {@link Throwable}, set it as
	 * the wrapping exception's cause.
	 * <li>If {@code object} is a {@link String}, set it as the wrapping
	 * exception's message.
	 * <li>For any other object, the message of the exception is set in
	 * this format: Class name of object: String representation of object.
	 *</ul>
	 *
	 * @param object an object to wrap in an AbstractMojoExecutionException
	 * @param scriptFailure if true, use a MojoExecutionException for wrapping,
	 *                      otherwise use MojoFailureException. This parameter
	 *                      is ignored if the object is null or an instance of
	 *                      MojoExecutionException or MojoFailureException
	 * @return object wrapped inside an {@link AbstractMojoExecutionException}
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
