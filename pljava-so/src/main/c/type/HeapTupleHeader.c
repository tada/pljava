/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Copyright (c) 2012 PostgreSQL Global Development Group
 *
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://wiki.tada.se/index.php?title=PLJava_License
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/HeapTupleHeader.h"

#include <executor/executor.h>
#include <executor/spi.h>
#include <utils/typcache.h>

#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/type/TupleDesc.h"

jobject HeapTupleHeader_getTupleDesc(HeapTupleHeader ht)
{
	jobject result;
	TupleDesc tupleDesc =
	  lookup_rowtype_tupdesc(HeapTupleHeaderGetTypeId(ht),
				 HeapTupleHeaderGetTypMod(ht));
	result = TupleDesc_create(tupleDesc);
	/*
	 * TupleDesc_create() creates a copy of the tuple descriptor, so
	 * can release this now
	 */
	ReleaseTupleDesc(tupleDesc);
	return result;
}

jobject HeapTupleHeader_getObject(JNIEnv* env, jlong hth, jlong jtd, jint attrNo)
{
	jobject result = 0;
	HeapTupleHeader self = (HeapTupleHeader)Invocation_getWrappedPointer(hth);
	if(self != 0 && jtd != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = jtd;
		BEGIN_NATIVE
		PG_TRY();
		{
			Oid typeId = SPI_gettypeid((TupleDesc)p2l.ptrVal, (int)attrNo);
			if(!OidIsValid(typeId))
			{
				Exception_throw(ERRCODE_INVALID_DESCRIPTOR_INDEX,
					"Invalid attribute number \"%d\"", (int)attrNo);
			}
			else
			{
				Datum binVal;
				bool wasNull = false;
				Type type = Type_fromOid(typeId, Invocation_getTypeMap());
				if(Type_isPrimitive(type))
					/*
					 * This is a primitive type
					 */
					type = Type_getObjectType(type);
	
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

void HeapTupleHeader_free(JNIEnv* env, jlong hth)
{
	BEGIN_NATIVE_NO_ERRCHECK
	Invocation_freeLocalWrapper(hth);
	END_NATIVE
}
