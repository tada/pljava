/*
 * Copyright (c) 2015- Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on a class to make it a PL/Java scalar User Defined Type.
 */
@Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS)
public @interface UDT
{
	enum Alignment { CHAR, INT2, INT4, DOUBLE }

	enum Storage { PLAIN, EXTERNAL, EXTENDED, MAIN }

	String name() default "";
	String schema() default "";
	String[] provides() default {};
	String[] requires() default {};
	String implementor() default "";
	String typeModifierInput() default "";
	String typeModifierOutput() default "";
	String analyze() default "";
	int internalLength() default -1;
	boolean passedByValue() default false;
	Alignment alignment() default Alignment.INT4;
	Storage storage() default Storage.PLAIN;
	String like() default "";
	char category() default 'U';
	boolean preferred() default false;
	String defaultValue() default "";
	String element() default "";
	char delimiter() default ',';
	boolean collatable() default false;
}
