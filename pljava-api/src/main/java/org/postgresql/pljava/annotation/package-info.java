/*
 * Copyright (c) 2015 Tada AB and other contributors, as listed below.
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
/**
 * Annotations for use in Java code to generate the SQLJ Deployment Descriptor
 * automatically.
 * <p>
 * To define functions or types in PL/Java requires more than one step. The
 * Java code must be written, compiled to a jar, and made available to the
 * PostgreSQL server. Before the server can use the objects in the jar, the
 * corresponding PostgreSQL declarations of functions/types/triggers/operators,
 * and so on, must be made in SQL. This often lengthy SQL script (and the
 * version that undoes it when uninstalling the jar) can be written in a
 * prescribed form and stored inside the jar itself as an "SQLJ Deployment
 * Descriptor", and processed automatically when the jar is installed in or
 * removed from the backend.
 * <p>
 * To write the deployment descriptor by hand can be tedious and error-prone,
 * as it must largely duplicate the method and type declarations in the
 * Java code, but using SQL's syntax and types in place of Java's. Instead,
 * when the annotations in this package are used in the Java code, the Java
 * compiler itself will generate a deployment descriptor file, ready to include
 * with the compiled classes to make a complete SQLJ jar.
 * <p>
 * Automatic descriptor generation requires attention to a few things.
 * <ul>
 * <li>A Java 6 or later Java compiler is required, and at least the
 * <code>pljava-api</code> jar must be on its class path. (The full
 * <code>pljava.jar</code> would also work, but only <code>pljava-api</code>
 * is required.) The jar must be on the class path in any case in order to
 * compile PL/Java code.
 * <li>When recompiling after changing only a few sources, it is possible the
 * Java compiler will only process a subset of the source files containing
 * annotations. If so, it may generate an incomplete deployment descriptor,
 * and a clean build may be required to ensure the complete descriptor is
 * written.
 * <li>Additional options are available when invoking the Java compiler, and
 * can be specified with <code>-Aoption=value</code> on the command line:
 * <dl>
 * <dt><code>ddr.output</code>
 * <dd>The file name to be used for the generated deployment descriptor.
 * If not specified, the file will be named <code>pljava.ddr</code> and found
 * in the top directory of the tree where the compiled class files are written.
 * <dt><code>ddr.name.trusted</code>
 * <dd>The language name that will be used to declare methods that are
 * annotated to have {@link Function.Trust#RESTRICTED} behavior. If not
 * specified, the name <code>java</code> will be used. It must match the name
 * used for the "trusted" language declaration when PL/Java was installed.
 * <dt><code>ddr.name.untrusted</code>
 * <dd>The language name that will be used to declare methods that are
 * annotated to have {@link Function.Trust#UNRESTRICTED} behavior. If not
 * specified, the name <code>javaU</code> will be used. It must match the name
 * used for the "untrusted" language declaration when PL/Java was installed.
 * </dl>
 * <li>The deployment descriptor may contain statements that cannot succeed if
 * placed in the wrong order, and to keep a manually-edited script in a workable
 * order while adding and modifying code can be difficult. Most of the
 * annotations in this package accept arbitrary <code>requires</code> and
 * <code>provides</code> strings, which can be used to control the order of
 * statements in the generated descriptor. The strings given for
 * <code>requires</code> and <code>provides</code> have no meaning to the
 * compiler, except that it will make sure not to write anything that
 * <code>requires</code> some string <em>X</em> into the generated script
 * before whatever <code>provides</code> it.
 * </ul>
 */
package org.postgresql.pljava.annotation;
