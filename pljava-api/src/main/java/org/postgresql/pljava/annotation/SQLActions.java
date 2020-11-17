/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for multiple {@link SQLAction} annotations (in case it is
 * convenient to hang more than one on a given program element).
 *<p>
 * This container annotation is documented for historical reasons (it existed
 * in PL/Java versions targeting earlier Java versions than 8). In new code, it
 * would be more natural to simply hang more than one {@code SQLAction}
 * annotation directly on a program element.
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - updated to Java6,
 * added SQLActions
 */
@Documented
@Target({ElementType.PACKAGE,ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface SQLActions
{
	/**
	 * The group of SQLAction annotations.
	 */
	SQLAction[] value();
}
