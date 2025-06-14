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
 * Maven plugin goal to use JavaScript (or another JSR 223 script engine)
 * for configuring
 * {@link org.apache.maven.reporting.MavenReport} during the
 * {@link LifecyclePhase#SITE}.
 * <p>
 * This plugin goal intends to allow the use of scripting in the {@code SITE}
 * lifecycle phase with the help of {@link ReportScript}. The motivation behind
 * this is the inability to use Maven AntRun in the {@code SITE} phase.
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
			engine.eval(scriptText);
			reportScript = ((Invocable)engine).getInterface(ReportScript.class);
		}
		catch (Exception e)
		{
			getLog().error(e);
		}
	}

	/**
	 * Queries the script for the report output path relative to the target site
	 * directory.
	 * <p>
	 * This value will be used by {@code Maven} to provide a link to the report
	 * from {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code getOutputName(report)} defined by the script snippet
	 * associated with the report. No default implementation is provided; the
	 * script must implement this method.
	 */
	@Override
	public String getOutputName ()
	{
		setReportScript();
		return reportScript.getOutputName(this);
	}

	/**
	 * Queries the script to return false if this report will produce output
	 * through a supplied {@link Sink}, or true if it is 'external', producing
	 * its output some other way.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code isExternalReport(report)} if defined in the script
	 * snippet associated with the report. Otherwise, the implementation
	 * inherited by this class is effectively invoked.
	 */
	@Override
	public boolean isExternalReport ()
	{
		setReportScript();
		return reportScript.isExternalReport(this);
	}

	/**
	 * Queries the script for the name of this report to be used
	 * by {@code Maven} for display in {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code getName(report, locale)} defined by the script
	 * snippet associated with the report. No default implementation is
	 * provided; the script must implement this method.
	 */
	@Override
	public String getName (Locale locale)
	{
		setReportScript();
		return reportScript.getName(this, locale);
	}

	/**
	 * Queries the script for the description of this report, to be used
	 * by {@code Maven} for display in {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code getDescription(report, locale)} defined in the script
	 * snippet associated with the report. No default implementation is
	 * provided; the script must implement this method.
	 */
	@Override
	public String getDescription (Locale locale)
	{
		setReportScript();
		return reportScript.getDescription(this, locale);
	}

	/**
	 * Queries the script for the category name of this report, used
	 * by {@code Maven} to place the report under the correct heading
	 * in {@code index.html}.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code getCategoryName(report)} if defined by the script
	 * snippet associated with the report. Otherwise, the implementation
	 * inherited by this class is effectively invoked.
	 */
	@Override
	public String getCategoryName ()
	{
		setReportScript();
		return reportScript.getCategoryName(this);
	}

	/**
	 * Queries the script as to whether this report can be generated.
	 * <p>
	 * Calls {@code setReportScript} to ensure that the instance of
	 * {@link ReportScript} is available. Invokes
	 * {@code canGenerateReport(report)} if defined by the script
	 * snippet. Otherwise, the implementation inherited by this class is
	 * effectively invoked.
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
	 * {@link ReportScript} is available. Invokes its
	 * {@code executeReport(report, locale)}, passing this instance and
	 * the supplied locale.
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
	 * {@code isExternalReport(report)} is not defined in the script
	 * snippet associated with the report.
	 */
	boolean isExternalReportDefault ()
	{
		return super.isExternalReport();
	}

	/**
	 * Default implementation of
	 * {@link ReportScript#getCategoryName(ReportScriptingMojo)}. Invoked if
	 * {@code getCategoryName(report)} is not defined in the script
	 * snippet associated with the report.
	 */
	String getCategoryNameDefault ()
	{
		return super.getCategoryName();
	}

	/**
	 * Default implementation of
	 * {@link ReportScript#canGenerateReport(ReportScriptingMojo)}. Invoked if
	 * {@code canGenerateReport(report)} is not defined in the script
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
	 *<ul>
	 * <li>If {@code object} is null, the exception message indicates the same.
	 * <li>If {@code object} is already a {@link MavenReportException}, it is
	 * returned as is.
	 * <li>If {@code object} is any other {@link Throwable}, it is used as
	 * the wrapping exception's cause.
	 * <li>If {@code object} is a {@link String}, it is used as
	 * the wrapping exception's message.
	 * <li>If it is any other object, the wrapping exception's message is set in
	 * this format: Class mame of object: String representation of object.
	 *</ul>
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
			MavenReportException exception =
				new MavenReportException(t.getMessage());
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
