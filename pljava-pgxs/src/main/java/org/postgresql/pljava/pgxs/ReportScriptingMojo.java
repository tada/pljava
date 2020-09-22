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

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.codehaus.plexus.configuration.PlexusConfiguration;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import java.util.Locale;

/**
 * Maven plugin goal to use JavaScript for configuring
 * {@link org.apache.maven.reporting.MavenReport} during the
 * {@link LifecyclePhase#SITE}.
 * <p>
 * This plugin goal intends to allow the use of JavaScript during {@code SITE}
 * lifecycle phase with the help of {@link ReportScript}. The motivation behind
 * this is the inability to use Maven AntRun during {@code SITE} phase.
 */
@Mojo(name = "scripted-report")
@Execute(phase = LifecyclePhase.NONE)
public class ReportScriptingMojo extends AbstractMavenReport
{
	/**
	 * The script to be used to produce the report, in the scripting language
	 * identified by its {@code mimetype} or {@code engine} attribute.
	 *<p>
	 * The scripting language must be supported by an engine that implements
	 * {@link Invocable}, and the script, when evaluated, must define functions
	 * that correspond to all of the abstract methods of {@link ReportScript},
	 * and any of the default methods that it wishes to override.
	 */
	@Parameter
	public PlexusConfiguration script;

	private ReportScript reportScript;

	private PGXSUtils utils;

	/**
	 * Creates an instance of {@link ReportScript} using methods defined in
	 * the JavaScript snippet in configuration of the report in {@code pom.xml}.
	 * Does nothing if the instance is already initialized.
	 */
	private void setReportScript()
	{
		if ( null != reportScript )
			return;

		try
		{
			utils = new PGXSUtils(project, getLog());
			ScriptEngine engine = utils.getScriptEngine(script);
			String scriptText = script.getValue();
			getLog().debug(scriptText);
			engine.eval(scriptText);
			reportScript = ((Invocable)engine).getInterface(ReportScript.class);
		}
		catch (Exception e)
		{
			getLog().error(e);
		}
	}

	/**
	 * Returns the path relative to the target site directory of the this report.
	 * This value will be used by {@code Maven} to provide a link to the report
	 * from {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun getOutputName(report)} defined in the JavaScript snippet
	 * associated with the report. No default implementation is provided. User
	 * must implement the method in JavaScript.
	 */
	@Override
	public String getOutputName ()
	{
		setReportScript();
		return reportScript.getOutputName(this);
	}

	/**
	 * Returns false if this report will produce output through a
	 * supplied {@link Sink}, true if it is 'external', producing its output
	 * some other way.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun isExternalReport(report)} if defined in the javascript
	 * snippet associated with the report. Otherwise, the {@code super}
	 * implementation is invoked effectively.
	 */
	@Override
	public boolean isExternalReport ()
	{
		setReportScript();
		return reportScript.isExternalReport(this);
	}

	/**
	 * Returns the name of this report used by {@code Maven} for displaying in
	 * {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun getName(report, locale)} defined in the javascript
	 * snippet associated with the report. No default implementation is provided
	 * . User must implement the method in javascript.
	 */
	@Override
	public String getName (Locale locale)
	{
		setReportScript();
		return reportScript.getName(this, locale);
	}

	/**
	 * Returns the description of this report, used by {@code Maven} to display
	 * report description in {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun getDescription(report, locale)} defined in the javascript
	 * snippet associated with the report. No default implementation is provided
	 * . User must implement the method in javascript.
	 */
	@Override
	public String getDescription (Locale locale)
	{
		setReportScript();
		return reportScript.getDescription(this, locale);
	}

	/**
	 * Returns the category name of this report, used by {@code Maven} to display
	 * the report under the correct in {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun getCategoryName(report)} if defined in the javascript
	 * snippet associated with the report. Otherwise, the {@code super}
	 * implementation is invoked effectively.
	 */
	@Override
	public String getCategoryName ()
	{
		setReportScript();
		return reportScript.getCategoryName(this);
	}

	/**
	 * Returns true if a report can be generated, false otherwise.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code fun canGenerateReport(report)} if defined in the javascript
	 * snippet. Otherwise, the {@code super} implementation is invoked
	 * effectively.
	 */
	@Override
	public boolean canGenerateReport ()
	{
		setReportScript();
		return reportScript.canGenerateReport(this);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes the
	 * {@code fun executeReport(report, locale)} with the instance of the
	 * current report.
	 */
	@Override
	protected void executeReport (Locale locale) throws MavenReportException
	{
		setReportScript();
		MavenReportException exception = reportScript.executeReport(this, locale);
		if (exception != null)
			throw exception;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public MavenProject getProject ()
	{
		return super.getProject();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getInputEncoding ()
	{
		return super.getInputEncoding();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getOutputEncoding ()
	{
		return super.getOutputEncoding();
	}

	/**
	 * Default implementation for
	 * {@link ReportScript#isExternalReport(ReportScriptingMojo)}. Invoked if
	 * {@code fun isExternalReport(report)} is not defined in the javascript
	 * snippet associated with the report.
	 */
	boolean isExternalReportDefault ()
	{
		return super.isExternalReport();
	}

	/**
	 * Default implementation of
	 * {@link ReportScript#getCategoryName(ReportScriptingMojo)}. Invoked if
	 * {@code fun getCategoryName(report)} is not defined in the javascript
	 * snippet associated with the report.
	 */
	String getCategoryNameDefault ()
	{
		return super.getCategoryName();
	}

	/**
	 * Default implementation of
	 * {@link ReportScript#canGenerateReport(ReportScriptingMojo)}. Invoked if
	 * {@code fun canGenerateReport(report)} is not defined in the javascript
	 * snippet associated with the report.
	 */
	boolean canGenerateReportDefault ()
	{
		return super.canGenerateReport();
	}

	/**
	 * Wraps the input object in a {@link MavenReportException}.
	 *
	 * The exception returned is constructed as follows:
	 * 1) If {@code object} is null, the exception message indicates the same.
	 * 2) If {@code object} is already a {@link MavenReportException}, return it
	 * as is.
	 * 3) If {@code object} is any other {@link Throwable}, set it as the cause
	 * for the exception.
	 * {@link MavenReportException} with {@code object} as its cause.
	 * 4) If {@code object} is a {@link String}, set it as the message of the
	 * exception.
	 * 5) For all other case, the message of the exception is set in this format
	 * , Class Name of object: String representation of object.
	 *
	 * @param object to wrap in MavenReportException
	 * @return object wrapped inside a {@link MavenReportException}
	 */
	public MavenReportException exceptionWrap(Object object)
	{
		if (object == null)
			return new MavenReportException("Script threw a null value");
		else if (object instanceof MavenReportException)
			return (MavenReportException) object;
		else if (object instanceof Throwable)
		{
			Throwable t = (Throwable) object;
			MavenReportException exception = new MavenReportException(t.getMessage());
			exception.initCause(t);
			return exception;
		}
		else if (object instanceof String)
			return new MavenReportException((String) object);
		else
			return new MavenReportException(object.getClass().getCanonicalName()
				+ ": " + object.toString());
	}
}
