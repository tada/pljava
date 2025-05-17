/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
#include <postgres.h>
#include <access/heapam.h>
#include "org_postgresql_pljava_internal_TriggerData.h"
#include "pljava/Invocation.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TriggerData.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Relation.h"

/**
 * \addtogroup JNI
 * @{
 */

static jclass    s_TriggerData_class;
static jmethodID s_TriggerData_init;
static jmethodID s_TriggerData_getTriggerReturnTuple;

jobject pljava_TriggerData_create(TriggerData* triggerData)
{
	Ptr2Long p2ltd;
	Ptr2Long p2lro;

	if ( NULL == triggerData )
		return NULL;

	p2ltd.longVal = 0L;
	p2ltd.ptrVal = triggerData;

	p2lro.longVal = 0L;
	p2lro.ptrVal = currentInvocation;

	return JNI_newObjectLocked(
			s_TriggerData_class,
			s_TriggerData_init,
			pljava_DualState_key(),
			p2lro.longVal,
			p2ltd.longVal);
}

HeapTuple pljava_TriggerData_getTriggerReturnTuple(jobject jtd, bool* wasNull)
{
	Ptr2Long p2l;
	HeapTuple ret = 0;
	p2l.longVal = JNI_callLongMethod(jtd, s_TriggerData_getTriggerReturnTuple);
	if(p2l.longVal != 0)
		ret = heap_copytuple((HeapTuple)p2l.ptrVal); /* unconditional copy?? */
	else
		*wasNull = true;
	return ret;
}

/* Make this datatype available to the postgres system.
 */
void pljava_TriggerData_initialize(void)
{
	TypeClass cls;
	jclass jcls;
	JNINativeMethod methods[] =
	{
		{
		"_getRelation",
	  	"(J)Lorg/postgresql/pljava/internal/Relation;",
	  	Java_org_postgresql_pljava_internal_TriggerData__1getRelation
		},
		{
		"_getTriggerTuple",
		"(J)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_TriggerData__1getTriggerTuple
		},
		{
		"_getNewTuple",
		"(J)Lorg/postgresql/pljava/internal/Tuple;",
		Java_org_postgresql_pljava_internal_TriggerData__1getNewTuple
		},
		{
		"_getArguments",
	  	"(J)[Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_TriggerData__1getArguments
		},
		{
		"_getName",
	  	"(J)Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_TriggerData__1getName
		},
		{
		"_isFiredAfter",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredAfter
		},
		{
		"_isFiredBefore",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredBefore
		},
		{
		"_isFiredForEachRow",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredForEachRow
		},
		{
		"_isFiredForStatement",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredForStatement
		},
		{
		"_isFiredByDelete",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredByDelete
		},
		{
		"_isFiredByInsert",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredByInsert
		},
		{
		"_isFiredByUpdate",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_TriggerData__1isFiredByUpdate
		},
		{ 0, 0, 0 }
	};

	jcls = PgObject_getJavaClass("org/postgresql/pljava/internal/TriggerData");
	PgObject_registerNatives2(jcls, methods);

	s_TriggerData_init = PgObject_getJavaMethod(jcls, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJ)V");
	s_TriggerData_getTriggerReturnTuple = PgObject_getJavaMethod(
		jcls, "getTriggerReturnTuple", "()J");
	s_TriggerData_class = JNI_newGlobalRef(jcls);
	JNI_deleteLocalRef(jcls);

	/* Use interface name for signatures.
	 */
	cls = TypeClass_alloc("type.TriggerData");
	cls->JNISignature   = "Lorg/postgresql/pljava/TriggerData;";
	cls->javaTypeName   = "org.postgresql.pljava.TriggerData";
	Type_registerType("org.postgresql.pljava.TriggerData", TypeClass_allocInstance(cls, InvalidOid));
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getRelation
 * Signature: (J)Lorg/postgresql/pljava/internal/Relation;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getRelation(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
	{
		BEGIN_NATIVE
		result = pljava_Relation_create(self->tg_relation);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getTriggerTuple
 * Signature: (J)Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getTriggerTuple(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
	{
		BEGIN_NATIVE
		result = pljava_Tuple_create(self->tg_trigtuple);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getNewTuple
 * Signature: (J)Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getNewTuple(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
	{
		BEGIN_NATIVE
		result = pljava_Tuple_create(self->tg_newtuple);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getArguments
 * Signature: (J)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getArguments(JNIEnv* env, jclass clazz, jlong _this)
{
	jobjectArray result = 0;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
	{
		char** cpp;
		jint idx;

		BEGIN_NATIVE
		Trigger* tg = self->tg_trigger;
		jint nargs = (jint)tg->tgnargs;
		result = JNI_newObjectArray(nargs, s_String_class, 0);
		cpp = tg->tgargs;
		for(idx = 0; idx < nargs; ++idx)
		{
			jstring js = String_createJavaStringFromNTS(cpp[idx]);
			JNI_setObjectArrayElement(result, idx, js);
			JNI_deleteLocalRef(js);
		}
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getName(JNIEnv* env, jclass clazz, jlong _this)
{
	jstring result = 0;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
	{
		BEGIN_NATIVE
		result = String_createJavaStringFromNTS(self->tg_trigger->tgname);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredAfter
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredAfter(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_AFTER(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredBefore
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredBefore(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_BEFORE(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredForEachRow
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredForEachRow(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_FOR_ROW(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredForStatement
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredForStatement(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_FOR_STATEMENT(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByDelete
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByDelete(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_BY_DELETE(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByInsert
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByInsert(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_BY_INSERT(self->tg_event);
	return result;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByUpdate
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByUpdate(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	TriggerData* self;
	Ptr2Long p2l;
	p2l.longVal = _this;
	self = (TriggerData*)p2l.ptrVal;
	if(self != 0)
		result = (jboolean)TRIGGER_FIRED_BY_UPDATE(self->tg_event);
	return result;
}
/** @} */
