/*
 * Copyright (c) 2015-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import org.postgresql.pljava.annotation.SQLAction;

/**
 * Test of a very simple form of conditional execution in the deployment
 * descriptor using only the {@code <implementor name>} specified for an
 * {@code <implementor block>}.
 * <p>
 * When a deployment descriptor is executed, the config setting
 * {@code pljava.implementors} determines which {@code <implementor block>}s
 * will be executed (in addition to all of the plain {@code <SQL statement>}s
 * that are not tagged with an implementor name). The default setting of
 * {@code pljava.implementors} is simply {@code postgresql}.
 * <p>
 * In this example, an SQLAction (with the default implementor name PostgreSQL
 * so it should always execute) tests some condition and, based on the result,
 * adds {@code LifeIsGood} to the list of recognized implementor names.
 * <p>
 * Later SQLActions with that implementor name should also be executed, while
 * those with a different, unrecognized implementor should not.
 * <p>
 * That is what happens at <em>deployment</em> (or undeployment) time, when the
 * jar has been loaded into the target database and the deployment descriptor is
 * being processed.
 * <p>
 * The {@code provides} and {@code requires} attributes matter at
 * <em>compile</em> time: they are hints to the DDR generator so it will be sure
 * to write the SQLAction that tests the condition ahead of the ones that
 * depend on the condition having been tested. The example illustrates that an
 * SQLAction's {@code implementor} is treated as an implicit {@code requires}.
 * Unlike an explicit one, it is weak: if there is nothing declared that
 * {@code provides} it, that's not an error; affected SQLActions will just be
 * placed as late in the generated DDR as other dependencies allow, in case
 * something in the preceding actions will be setting those implementor tags.
 * <p>
 * The implicit {@code requires} derived from an {@code implementor} is also
 * special in another way: it does not have its sense reversed when generating
 * the "undeploy" actions of the deployment descriptor. Ordinary requirements
 * do, so the dependent objects get dropped before the things they depend on.
 * But the code for setting a conditional implementor tag has to be placed
 * ahead of the uses of the tag, whether deploying or undeploying.
 * <p>
 * An {@code SQLAction} setting an implementor tag does not need to have any
 * {@code remove=} actions. If it does not (the usual case), its
 * {@code install=} actions will be used in both sections of the deployment
 * descriptor.
 * <p>
 * This example adds {@code LifeIsGood} ahead of the prior content of
 * {@code pljava.implementors}. Simply replacing the value would stop the
 * default implementor PostgreSQL being recognized, probably not what's wanted.
 * The final {@code true} argument to {@code set_config} makes the setting
 * local, so it is reverted when the transaction completes.
 * <p>
 * In addition to the goodness-of-life examples, this file also generates
 * several statements setting PostgreSQL-version-based implementor tags that
 * are relied on by various other examples in this directory.
 */
@SQLAction(provides={"LifeIsGood","LifeIsNotGood"}, install=
	"SELECT CASE 42 WHEN 42 THEN " +
	" set_config('pljava.implementors', 'LifeIsGood,' || " +
	"  current_setting('pljava.implementors'), true) " +
	"ELSE " +
	" set_config('pljava.implementors', 'LifeIsNotGood,' || " +
	"  current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(implementor="LifeIsGood", install=
	"SELECT javatest.logmessage('INFO', 'Looking good!')"
)

@SQLAction(implementor="LifeIsNotGood", install=
	"SELECT javatest.logmessage('WARNING', 'This should not be executed')"
)

@SQLAction(provides="postgresql_ge_80300", install=
	"SELECT CASE WHEN" +
	" 80300 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_80300,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(provides="postgresql_ge_80400", install=
	"SELECT CASE WHEN" +
	" 80400 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_80400,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(provides="postgresql_ge_90000", install=
	"SELECT CASE WHEN" +
	" 90000 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_90000,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(provides="postgresql_ge_90100", install=
	"SELECT CASE WHEN" +
	" 90100 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_90100,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(provides="postgresql_ge_90300", install=
	"SELECT CASE WHEN" +
	" 90300 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_90300,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)

@SQLAction(provides="postgresql_ge_100000", install=
	"SELECT CASE WHEN" +
	" 100000 <= CAST(current_setting('server_version_num') AS integer)" +
	" THEN set_config('pljava.implementors', 'postgresql_ge_100000,' || " +
	" current_setting('pljava.implementors'), true) " +
	"END"
)
public class ConditionalDDR { }
