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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Mojo(name = "scripting", defaultPhase = LifecyclePhase.INITIALIZE,
      requiresDependencyResolution = ResolutionScope.TEST)
public class ScriptingMojo extends AbstractMojo
{
	/**
	 * A {@code ClassLoader} with (effectively) two parents, the inherited one
	 * and Java's platform class loader.
	 *<p>
	 * This loader will be given to the {@code ScriptEngineManager}. The
	 * inherited loader supplied by Maven does not have Java's platform
	 * class loader as its parent (or ancestor), which leaves Java's
	 * {@code ServiceLoader} mechanism unable to find Nashorn's script engine.
	 * Therefore, this loader will declare the Java platform class loader
	 * as its actual parent, and search the Maven-supplied class loader for
	 * whatever the platform class loader does not find.
	 *<p>
	 * This could pose a risk of class version conflicts if the Maven-supplied
	 * loader has defined classes that are also known to the platform loader.
	 * It would be safer to delegate to Maven's loader first and the parent as
	 * fallback. That would require overriding more of {@code ClassLoader}'s
	 * default functionality, though. With any luck, the targeted use of this
	 * loader only with the {@code ScriptEngineManager} will minimize the risk,
	 * already low because it would be odd to override classes of the Java
	 * platform itself.
	 */
	static class ScriptEngineLoader extends ClassLoader
	{
		private final ClassLoader mavenLoader;

		private ScriptEngineLoader(ClassLoader mavenLoader)
		{
			super("pgxsScriptLoader", ClassLoader.getPlatformClassLoader());
			this.mavenLoader = mavenLoader;
		}

		/**
		 * Delegate to the Maven-supplied loader.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code loadClass} only after the parent loader has drawn a blank,
		 * so there is nothing left to do but see if the Maven-supplied loader
		 * has the class.
		 */
		@Override
		protected Class<?> findClass(String name) throws ClassNotFoundException
		{
			Class<?> rslt = mavenLoader.loadClass(name);
			return rslt;
		}

		/**
		 * Delegate to the Maven-supplied loader for finding a resource.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code getResource} only after the parent loader has drawn a blank,
		 * so there is nothing left to do but see if the Maven-supplied loader
		 * has the resource.
		 */
		@Override
		protected URL findResource(String name)
		{
			URL rslt = mavenLoader.getResource(name);
			return rslt;
		}

		/**
		 * Delegate to the Maven-supplied loader for finding a resource.
		 *<p>
		 * This is called by the {@code super} implementation of
		 * {@code getResources} after enumerating the resources available from
		 * the parent loader. This method needs only to return the resources
		 * available from the Maven-supplied loader; the caller will combine the
		 * two enumerations.
		 */
		@Override
		protected Enumeration<URL> findResources(String name) throws IOException
		{
			Enumeration<URL> rslt = mavenLoader.getResources(name);
			return rslt;
		}
	}

	private ScriptEngine engine;

	@Component
	private MavenProject project;

	@Parameter
	private PlexusConfiguration script;

	@Override
	public void execute ()
	{
		try
		{
			String engineName = script.getAttribute("engine");
			String mimeType = script.getAttribute("mimetype");
			String scriptText = script.getValue();

			if (engineName == null && mimeType == null)
				throw new IllegalArgumentException("Neither script engine nor" +
					                                   " mimetype defined.");
			else
			{
				ScriptEngineManager manager = new ScriptEngineManager(
					new ScriptEngineLoader(ScriptingMojo.class.getClassLoader()));

				if (engineName != null)
					engine = manager.getEngineByName(engineName);

				if (mimeType != null)
					if (engine != null)
					{
						if (engine.getFactory().getMimeTypes().contains(
							mimeType))
							getLog().warn("Specified engine does " +
								              "not have given mime type : " + mimeType);
					}
					else
						engine = manager.getEngineByMimeType(mimeType);

				if (engine == null)
					throw new IllegalArgumentException("No suitable engine "
						                                   + "found for specified engine name or mime type");
			}

			getLog().debug(engine.toString());
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
