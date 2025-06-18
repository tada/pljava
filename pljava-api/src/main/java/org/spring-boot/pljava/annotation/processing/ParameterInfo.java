/*
 * Copyright (c) 2020-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import javax.lang.model.element.VariableElement;

import javax.lang.model.type.TypeMirror;

import org.postgresql.pljava.annotation.SQLType;

/**
 * Tiny 'record' used in factoring duplicative operations on function parameter
 * lists into operations on streams of these.
 */
class ParameterInfo
{
	final TypeMirror tm;
	final VariableElement ve;
	final SQLType st;
	final DBType dt;

	String name()
	{
		String name = null == st ? null : st.name();
		if ( null == name )
			name = ve.getSimpleName().toString();
		return name;
	}

	ParameterInfo(TypeMirror m, VariableElement e, SQLType t, DBType d)
	{
		tm = m;
		ve = e;
		st = t;
		dt = d;
	}
}
