/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import java.lang.annotation.Annotation;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;

import javax.lang.model.SourceVersion;

import javax.lang.model.element.TypeElement;

/**
 * Annotation processor invoked by the annotations framework in javac for
 * annotations of type org.postgresql.pljava.annotation.*.
 *
 * Simply forwards to a DDRProcessorImpl instance that is not constructed
 * until the framework calls init (since there is nothing useful for the
 * constructor to do until then).
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - update to Java6,
 * add SQLType/SQLAction, polishing
 */
@SupportedAnnotationTypes({"org.postgresql.pljava.annotation.*"})
@SupportedOptions
({
  "ddr.reproducible",    // default true
  "ddr.name.trusted",    // default "java"
  "ddr.name.untrusted",  // default "javaU"
  "ddr.implementor",     // implementor when not annotated, default "PostgreSQL"
  "ddr.output"           // name of ddr file to write
})
public class DDRProcessor extends AbstractProcessor
{
	private DDRProcessorImpl impl;

	@Override
	public SourceVersion getSupportedSourceVersion()
	{
		/*
		 * Because this must compile on Java versions back to 9, it must not
		 * mention by name any SourceVersion constant later than RELEASE_9.
		 *
		 * Update latest_tested to be the latest Java release on which this
		 * annotation processor has been tested without problems.
		 */
		int latest_tested = 25;
		int ordinal_9 = SourceVersion.RELEASE_9.ordinal();
		int ordinal_latest = latest_tested - 9 + ordinal_9;

		SourceVersion latestSupported = SourceVersion.latestSupported();

		if ( latestSupported.ordinal() <= ordinal_latest )
			return latestSupported;

		return SourceVersion.values()[ordinal_latest];
	}
	
	@Override
	public void init( ProcessingEnvironment processingEnv)
	{
		super.init( processingEnv);
		impl = new DDRProcessorImpl( processingEnv);
	}
	
	@Override
	public boolean process( Set<? extends TypeElement> tes, RoundEnvironment re)
	{
		if ( null == impl )
			throw new IllegalStateException(
				"The annotation processing framework has called process() " +
				"before init()");
		return impl.process( tes, re);
	}
}
