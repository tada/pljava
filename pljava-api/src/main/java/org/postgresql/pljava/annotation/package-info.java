/*
 * Copyright (c) 2015-2025 Tada AB and other contributors, as listed below.
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
 * <h2>Eliminating error-prone hand-maintained SQL scripts</h2>
 * <p>
 * To define functions or types in PL/Java requires more than one step. The
 * Java code must be written, compiled to a jar, and made available to the
 * PostgreSQL server. Before the server can use the objects in the jar, the
 * corresponding PostgreSQL declarations of functions/types/triggers/operators,
 * and so on, must be made in SQL. This often lengthy SQL script (and the
 * version that undoes it when uninstalling the jar) can be written in a
 * prescribed form and stored inside the jar itself as an "SQLJ Deployment
 * Descriptor", and processed automatically when the jar is installed in or
 * removed from the DBMS.
 * <p>
 * To write the deployment descriptor by hand can be tedious and error-prone,
 * as it must largely duplicate the method and type declarations in the
 * Java code, but using SQL's syntax and types in place of Java's. Instead,
 * when the annotations in this package are used in the Java code, the Java
 * compiler itself will generate a deployment descriptor (DDR) file, ready to
 * include with the compiled classes to make a complete SQLJ jar.
 * <p>
 * Automatic descriptor generation requires attention to a few things.
 * <ul>
 * <li>The {@code pljava-api} jar must be on the Java compiler's class path.
 * (All but the simplest PL/Java functions probably refer to some class in
 * PL/Java's API anyway, in which case the jar would already have to be on
 * the class path.)
 * <li>Java compilers older than Java 23 will automatically find and use
 * PL/Java's DDR processor as long as the {@code pljava-api} jar is on the class
 * path. Starting in Java 23, the compiler will not do so automatically, and a
 * {@code -processor org.postgresql.pljava.annotation.processing.DDRProcessor}
 * option is also needed on the {@code javac} command line. (Warnings about this
 * are issued starting in Java 21, though the processor is still used
 * automatically, with the warnings, until Java 23.)
 * <li>When recompiling after changing only a few sources, it is possible the
 * Java compiler will only process a subset of the source files containing
 * annotations. If so, it may generate an incomplete deployment descriptor,
 * and a clean build may be required to ensure the complete descriptor is
 * written.
 * </ul>
 * <h2>New compiler options when generating the deployment descriptor</h2>
 * <p>Additional options are available when invoking the Java compiler, and
 * can be specified with {@code -Aoption=value} on the command line:
 * <dl>
 * <dt>{@code ddr.output}
 * <dd>The file name to be used for the generated deployment descriptor.
 * If not specified, the file will be named <code>pljava.ddr</code> and found
 * in the top directory of the tree where the compiled class files are written.
 * <dt>{@code ddr.name.trusted}
 * <dd>The language name that will be used to declare methods that are
 * annotated to have {@link org.postgresql.pljava.annotation.Function.Trust#SANDBOXED} behavior. If not
 * specified, the name {@code java} will be used. It must match the name
 * used for the "trusted" language declaration when PL/Java was installed.
 * <dt>{@code ddr.name.untrusted}
 * <dd>The language name that will be used to declare methods that are
 * annotated to have {@link org.postgresql.pljava.annotation.Function.Trust#UNSANDBOXED} behavior. If not
 * specified, the name {@code javaU} will be used. It must match the name
 * used for the "untrusted" language declaration when PL/Java was installed.
 * <dt>{@code ddr.implementor}
 * <dd>The identifier (defaulting to {@code PostgreSQL} if not specified here)
 * that will be used in the {@code <implementor block>}s wrapping any SQL
 * generated from elements that do not specify their own. If this is set to a
 * single hyphen (-), elements that specify no implementor will produce plain
 * {@code <SQL statement>}s not wrapped in {@code <implementor block>}s.
 * <dt>{@code ddr.reproducible}
 * <dd>When {@code true} (the default), SQL statements are written to the
 * deployment descriptor in an order meant to be consistent across successive
 * compilations of the same sources. This option is further discussed below.
 * </dl>
 * <h2>Controlling order of statements in the deployment descriptor</h2>
 * <p>The deployment descriptor may contain statements that cannot succeed if
 * placed in the wrong order, and to keep a manually-edited script in a workable
 * order while adding and modifying code can be difficult. Most of the
 * annotations in this package accept arbitrary {@code requires} and
 * {@code provides} strings, which can be used to control the order of
 * statements in the generated descriptor. The strings given for
 * {@code requires} and {@code provides} have no meaning to the
 * compiler, except that it will make sure not to write anything that
 * {@code requires} some string <em>X</em> into the generated script
 * before whatever {@code provides} it.
 * <h3>Effect of {@code ddr.reproducible}</h3>
 * <p>There can be multiple ways to order the statements in the deployment
 * descriptor to satisfy the given {@code provides} and {@code requires}
 * relationships. While the compiler will always write the descriptor in an
 * order that satisfies those relationships, when the {@code ddr.reproducible}
 * option is {@code false}, the precise order may differ between successive
 * compilations of the same sources, which <em>should not</em> affect successful
 * loading and unloading of the jar with {@code install_jar} and
 * {@code remove_jar}. In testing, this can help to confirm that all of the
 * needed {@code provides} and {@code requires} relationships have been
 * declared. When the {@code ddr.reproducible} option is {@code true}, the order
 * of statements in the deployment descriptor will be one of the possible
 * orders, chosen arbitrarily but consistently between multiple compilations as
 * long as the sources are unchanged. This can be helpful in software
 * distribution when reproducible output is wanted.
 * <h2>Conditional execution in the deployment descriptor</h2>
 * <p>The deployment-descriptor syntax fixed by the ISO SQL/JRT standard has
 * a rudimentary conditional-inclusion feature based on
 * {@code <implementor block>}s.
 * SQL statements wrapped in {@code BEGIN}/{@code END} with an
 * {@code <implementor name>} are executed only if that name is recognized
 * by the DBMS when installing or removing the jar. Statements in the deployment
 * descriptor that are not wrapped in an {@code <implementor block>} are
 * executed unconditionally.
 * <p>PL/Java's descriptor generator normally emits statements
 * as {@code <implementor block>}s, using the name {@code PostgreSQL}
 * (or the value of the {@code ddr.implementor} option if present on
 * the compiler command line) by default, or a specific name supplied
 * with {@code implementor=} to one of the annotations in this package.
 * <p>When loading or unloading a jar file and processing its deployment
 * descriptor, PL/Java 'recognizes' any implementor name listed in the runtime
 * setting {@code pljava.implementors}, which contains only {@code PostgreSQL}
 * by default.
 * <p>The {@code pljava.implementors} setting can be changed, even by SQL
 * statements within a deployment descriptor, to affect which subsequent
 * statements will be executed. An SQL statement may test some condition and
 * set {@code pljava.implementors} accordingly. In PL/Java's supplied examples,
 * <a href=
 "https://github.com/tada/pljava/blob/REL1_6_STABLE/pljava-examples/src/main/java/org/postgresql/pljava/example/annotation/ConditionalDDR.java"
 >ConditionalDDR</a> illustrates this approach to conditional execution.
 * <p>Naturally, this scheme requires the SQL generator to emit the statement
 * that tests the condition earlier in the deployment descriptor than
 * the statements relying on the {@code <implementor name>} being set.
 * Building on the existing ability to control the order of statements
 * using {@code provides} and {@code requires} elements, an {@code implementor}
 * element specified in the annotation for a statement is treated also as
 * an implicit {@code requires} for that name, so the programmer only needs
 * to place an explicit {@code provides} element on whatever
 * {@link SQLAction SQLAction} tests the condition and determines if the name
 * will be recognized.
 * <p>The {@code provides}/{@code requires} relationship so created differs
 * in three ways from other {@code provides}/{@code requires} relationships:
 * <ul>
 * <li>It does not reverse for generating {@code remove} actions.
 * Normal dependencies must be reversed for that case, so dependent objects
 * are removed before those they depend on. By contrast, a condition determining
 * the setting of an implementor name must be evaluated before the name
 * is needed, whether the jar is being installed or removed.
 * <li>If it does not have an explicit {@code remove} action (the usual case),
 * its {@code install} action (the condition test and setting of the name)
 * is used both when installing and removing.
 * <li>It is weak. The SQL generator does not flag an error if the implicit
 * {@code requires} for an implementor name is not satisfied by any annotation's
 * {@code provides} in the visible Java sources. It is possible the name may be
 * set some other way in the DBMS environment where the jar is to be deployed.
 * Faced with statements that require such 'unprovided' implementor names,
 * the SQL generator just falls back to emitting them as late in the deployment
 * descriptor as possible, after all other statements that do not depend
 * on them.
 * </ul>
 * <h3>Matching {@code implementor} and {@code provides}</h3>
 * <p>Given the 'weak' nature of the {@code implementor}/{@code provides}
 * relationship, an error will not be reported if a spelling or upper/lower case
 * difference prevents identifying an {@code <implementor name>} with the
 * {@code provides} string of an annotated statement intended to match it.
 * The resulting deployment descriptor may have a workable order
 * as a result of the fallback ordering rules, or may have a mysteriously
 * unworkable order, particularly of the {@code remove} actions.
 * <p>According to the ISO SQL/JRT standard, an {@code <implementor name>} is
 * an SQL identifier, having a case-insensitive matching behavior unless quoted.
 * PL/Java, however, treats a {@code provides} value as an arbitrary Java string
 * that can only match exactly, and so PL/Java's SQL generator will successfully
 * match up {@code implementor} and {@code provides} strings <em>only when
 * they are identical in spelling and case</em>.
 */
package org.postgresql.pljava.annotation;
