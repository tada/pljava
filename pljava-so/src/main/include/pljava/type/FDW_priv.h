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
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_UDT_priv_h
#define __pljava_type_UDT_priv_h

#include "pljava/type/Type_priv.h"
#include "pljava/type/UDT.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * @author Thomas Hallgren
 **************************************************************************/

struct UDT_
{
	/*
	 * The UDT "class" extends Type so the first
	 * entry must be the Type_ structure. This enables us
	 * to cast the String to a Type.
	 */
	struct Type_ Type_extension;

	jstring   sqlTypeName;
	bool      hasTupleDesc;
	jobject parse;
	jobject readSQL;

	/*
	 * At first glance, one might not retain writeSQL and toString handles
	 * per-UDT, as they are both inherited methods common to all UDTs and so
	 * do not depend on the class of the receiver. What these jobjects hold,
	 * though, is an Invocable, which carries an AccessControlContext, which is
	 * chosen at resolution time per-UDT or per-function, so they must be here.
	 */
	jobject writeSQL;
	jobject toString;
};

extern Datum _UDT_coerceObject(Type self, jobject jstr);

extern jvalue _UDT_coerceDatum(Type self, Datum value);

#ifdef __cplusplus
}
#endif
#endif
