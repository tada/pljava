/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_type_String_priv_h
#define __pljava_type_String_priv_h

#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"

#ifdef __cplusplus
extern "C" {
#endif

struct String_
{
	/*
	 * The String "class" extends Type so the first
	 * entry must be the Type_ structure. This enables us
	 * to cast the String to a Type.
	 */
	struct Type_ Type_extension;

	/*
	 * Transforms text into a Datum.
	 */
	FmgrInfo textInput;
	
	/*
	 * Transforms datum into text.
	 */
	FmgrInfo textOutput;

	/*
	 * Oid of elment type (if any)
	 */
	Oid elementType;
};

extern Datum _String_coerceObject(Type self, JNIEnv* env, jobject jstr);

extern jvalue _String_coerceDatum(Type self, JNIEnv* env, Datum value);

#ifdef __cplusplus
}
#endif
#endif
