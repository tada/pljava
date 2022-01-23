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

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a PostgreSQL extension that has been installed for the current
 * database.
 */
public interface Extension
extends Addressed<Extension>, Named<Simple>, Owned
{
	RegClass.Known<Extension> CLASSID =
		formClassId(ExtensionRelationId, Extension.class);

	/**
	 * Namespace in which most (or all, for a relocatable extension) of the
	 * namespace-qualified objects belonging to the extension are installed.
	 *<p>
	 * Not a namespace qualifying the extension's name; extensions are not
	 * namespace-qualified.
	 */
	RegNamespace namespace();
	boolean relocatable();
	String version();
	List<RegClass> config();
	List<String> condition();
}
