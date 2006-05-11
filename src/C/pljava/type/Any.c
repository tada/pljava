/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <utils/memutils.h>
#include <utils/numeric.h>

#include "pljava/type/Type_priv.h"

static Type _Any_getRealType(Type self, Oid realId, jobject typeMap)
{
	Type real = Type_fromOid(realId, typeMap);
	if(Type_isPrimitive(real) && Type_getElementType(real) == 0)
		real = Type_getObjectType(real);
	return real;
}

/* Make this datatype available to the postgres system.
 */
extern void Any_initialize(void);
void Any_initialize(void)
{
	TypeClass cls = TypeClass_alloc("type.any");
	cls->JNISignature = "Ljava/lang/Object;";
	cls->javaTypeName = "java.lang.Object";
	cls->dynamic      = true;
	cls->getRealType  = _Any_getRealType;
	Type_registerType("java.lang.Object", TypeClass_allocInstance(cls, ANYELEMENTOID));
}
