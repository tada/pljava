/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_HeapTupleHeader.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/HeapTupleHeader.h"

#include <executor/spi.h>
#include <utils/typcache.h>

#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/type/TupleDesc.h"

static jclass    s_HeapTupleHeader_class;
static jmethodID s_HeapTupleHeader_init;

jobject HeapTupleHeader_create(HeapTupleHeader ht)
{
	return (ht == 0) ? 0 : JNI_newObject(
		s_HeapTupleHeader_class,
		s_HeapTupleHeader_init,
		Invocation_createLocalWrapper(ht));
}

extern void HeapTupleHeader_initialize(void);
void HeapTupleHeader_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_getObject",
	  	"(JI)Ljava/lang/Object;",
	  	Java_org_postgresql_pljava_internal_HeapTupleHeader__1getObject
		},
		{
		"_getTupleDesc",
		"(J)Lorg/postgresql/pljava/internal/TupleDesc;",
		Java_org_postgresql_pljava_internal_HeapTupleHeader__1getTupleDesc
		},
		{
		"_free",
		"(J)V",
		Java_org_postgresql_pljava_internal_HeapTupleHeader__1free
		},
		{ 0, 0, 0 }
	};

	s_HeapTupleHeader_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/HeapTupleHeader"));
	PgObject_registerNatives2(s_HeapTupleHeader_class, methods);
	s_HeapTupleHeader_init = PgObject_getJavaMethod(s_HeapTupleHeader_class, "<init>", "(J)V");
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_HeapTupleHeader
 * Method:    _free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_HeapTupleHeader__1free(JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	Invocation_freeLocalWrapper(pointer);
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_HeapTupleHeader
 * Method:    _getObject
 * Signature: (JI)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_HeapTupleHeader__1getObject(JNIEnv* env, jclass clazz, jlong _this, jint attrNo)
{
	jobject result = 0;

	HeapTupleHeader self = (HeapTupleHeader)Invocation_getWrappedPointer(_this);
	if(self != 0)
	{
		BEGIN_NATIVE
		PG_TRY();
		{
			TupleDesc tupleDesc = lookup_rowtype_tupdesc(
						HeapTupleHeaderGetTypeId(self),
						HeapTupleHeaderGetTypMod(self));
	
			Oid typeId = SPI_gettypeid(tupleDesc, (int)attrNo);
			if(!OidIsValid(typeId))
			{
				Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
					"Invalid attribute number \"%d\"", (int)attrNo);
			}
			else
			{
				Datum binVal;
				bool wasNull = false;
				Type type = Type_fromOid(typeId);
				if(Type_isPrimitive(type))
					/*
					 * This is a primitive type
					 */
					type = type->m_class->objectType;
	
				binVal = GetAttributeByNum(self, (AttrNumber)attrNo, &wasNull);
				if(!wasNull)
					result = Type_coerceDatum(type, binVal).l;
			}
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("GetAttributeByNum");
		}
		PG_END_TRY();
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_HeapTupleHeader
 * Method:    _getTupleDesc
 * Signature: ()Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_HeapTupleHeader__1getTupleDesc(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	HeapTupleHeader self = (HeapTupleHeader)Invocation_getWrappedPointer(_this);
	if(self != 0)
	{
		BEGIN_NATIVE
		result = TupleDesc_create(lookup_rowtype_tupdesc(
						HeapTupleHeaderGetTypeId(self),
						HeapTupleHeaderGetTypMod(self)));
		END_NATIVE
	}
	return result;
}

