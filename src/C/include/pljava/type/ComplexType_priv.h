/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_ComplexType_priv_h
#define __pljava_type_ComplexType_priv_h

#include "pljava/type/Type_priv.h"
#include "pljava/type/ComplexType.h"

#ifdef __cplusplus
extern "C" {
#endif

struct ComplexType_
{
	/*
	 * The String "class" extends Type so the first
	 * entry must be the Type_ structure. This enables us
	 * to cast the ComplexType to a Type.
	 */
	struct Type_ Type_extension;

	/*
	 * The TupleDesc associated with the SETOF function.
	 */
	TupleDesc m_tupleDesc;
};

#ifdef __cplusplus
}
#endif
#endif
