/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Filip Hrbek
 */
#include "org_postgresql_pljava_internal_TypeMap.h"
#include "pljava/TypeMap.h"
#include "pljava/type/Type_priv.h" 
#include "pljava/Exception.h"
#include "pljava/type/String.h"

Datum TypeMap_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	JNINativeMethod methods[] = {
		{
		"_getClassNameFromPgOid",
		"(I)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_TypeMap__1getClassNameFromPgOid
		},
		{ 0, 0, 0 }};

	PgObject_registerNatives(env, "org/postgresql/pljava/internal/TypeMap", methods);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_TypeMap
 * Method:    _getClassNameFromPgOid
 * Signature: (I)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TypeMap__1getClassNameFromPgOid(JNIEnv* env, jclass cls, jint oid)
{
	Type type = NULL;

	PLJAVA_ENTRY_FENCE(0)
	PG_TRY();
	{
		if(!OidIsValid(oid))
		{
			Exception_throw(env,
				ERRCODE_DATA_EXCEPTION,
				"Invalid OID \"%d\"", (int)oid);
		}
		else
		{
			type = Type_fromOid(oid);
			if(Type_isPrimitive(type))
				/*
				 * This is a primitive type
				 */
				type = type->m_class->objectType;
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "TypeMap_getClassNameFromPgOid");
	}
	PG_END_TRY();

	return String_createJavaStringFromNTS(env, Type_getJavaTypeName(type));
}
