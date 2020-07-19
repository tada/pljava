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
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;

@Mojo(name = "scripting", defaultPhase = LifecyclePhase.INITIALIZE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	/**
	 * A {@code ClassLoader} with (effectively) two parents, the inherited one
	 * and Java's application class loader.
	 *<p>
	 * This loader will be given to the {@code ScriptEngineManager}. The
	 * inherited loader supplied by Maven omits the application class loader,
	 * to insulate Maven builds from possible differences in the application
	 * class path; a reasonable idea, but the Nashorn script engine was moved to
	 * the application class path as of Java 9, so without checking there, we
	 * will not find it. This loader (like most) delegates first to its parent,
	 * which should be the Maven-supplied loader; only what is not found there
	 * will be sought from the application loader.
	 */
	static class ScriptEngineLoader extends ClassLoader
	{
		private ScriptEngineLoader(ClassLoader parent)
		{
			super("pgxsScriptLoader", parent);
		}

		/**
		 * Delegate to the application loader.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code loadClass} only after the parent loader has drawn a blank,
		 * so there is nothing left to do but see if the application loader
		 * has the class.
		 */
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException
		{
			return findSystemClass(name);
		}

		/**
		 * Delegate to the application loader for finding a resource.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code getResource} only after the parent loader has drawn a blank,
		 * so there is nothing left to do but see if the application loader
		 * has the resource.
		 */
		@Override
		protected URL findResource(String name)
		{
			return getSystemResource(name);
		}

		/**
		 * Delegate to the application loader for finding a resource.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code getResources} after enumerating the resources available from
		 * the parent loader. This method needs only to return the resources
		 * available from the application loader; the caller will combine the
		 * two enumerations.
		 */
		@Override
		protected Enumeration<URL> findResources(String name) throws IOException
		{
			return getSystemResources(name);
		}
	}

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
			new ScriptEngineLoader(ScriptingMojo.class.getClassLoader()));
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
