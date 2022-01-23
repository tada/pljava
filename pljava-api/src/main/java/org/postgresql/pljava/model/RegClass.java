/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.model;

import java.util.List;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of PostgreSQL relations/"classes"/tables.
 *<p>
 * Instances of {@code RegClass} also serve as the "class ID" values for
 * objects within the catalog (including for {@code RegClass} objects, which
 * are no different from others in being defined by rows that appear in a
 * catalog table; there is a row in {@code pg_class} for {@code pg_class}).
 */
public interface RegClass
{
}
