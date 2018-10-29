/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   PostgreSQL Global Development Group
 *   Chapman Flack
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

jobject HeapTupleHeader_getObject(
	JNIEnv* env, jlong hth, jlong jtd, jint attrNo, jclass rqcls)
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
			Type type = TupleDesc_getColumnType(
				(TupleDesc) p2l.ptrVal, (int) attrNo);
			if (type != 0)
			{
				Datum binVal;
				bool wasNull = false;
				binVal = GetAttributeByNum(self, (AttrNumber)attrNo, &wasNull);
				if(!wasNull)
					result = Type_coerceDatumAs(type, binVal, rqcls).l;
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
