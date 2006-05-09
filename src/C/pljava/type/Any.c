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

static TypeClass s_anyClass;
static Type s_any;

static Type _Any_obtain(Oid typeId)
{
	return s_any;
}

static Type _Any_getRealType(Type self, Oid realId, jobject typeMap)
{
	return Type_objectTypeFromOid(realId, typeMap);
}

/* Make this datatype available to the postgres system.
 */
extern void Any_initialize(void);
void Any_initialize(void)
{
	s_anyClass = TypeClass_alloc("type.any");
	s_anyClass->JNISignature = "Ljava/lang/Object;";
	s_anyClass->javaTypeName = "java.lang.Object";
	s_anyClass->dynamic      = true;
	s_anyClass->getRealType  = _Any_getRealType;
	s_any = TypeClass_allocInstance(s_anyClass, ANYELEMENTOID);

	Type_registerType(ANYELEMENTOID, "java.lang.Object", _Any_obtain);
}
