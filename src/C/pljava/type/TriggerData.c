/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TriggerData.h"
#include "pljava/type/TriggerData_JNI.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Relation.h"

static Type      s_TriggerData;
static TypeClass s_TriggerDataClass;
static jclass    s_TriggerData_class;
static jmethodID s_TriggerData_init;

/*
 * org.postgresql.pljava.TriggerData type.
 */
jobject TriggerData_create(JNIEnv* env, TriggerData* td)
{
	jobject jtd = NativeStruct_obtain(env, td);
	if(jtd == 0)
	{
		jtd = (*env)->NewObject(env, s_TriggerData_class, s_TriggerData_init);
		NativeStruct_init(env, jtd, td);
	}
	return jtd;
}

static jvalue _TriggerData_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = TriggerData_create(env, (TriggerData*)DatumGetPointer(arg));
	return result;
}

static Type TriggerData_obtain(Oid typeId)
{
	return s_TriggerData;
}

/* Make this datatype available to the postgres system.
 */
extern Datum TriggerData_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(TriggerData_initialize);
Datum TriggerData_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_TriggerData_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/TriggerData"));

	s_TriggerData_init = PgObject_getJavaMethod(
				env, s_TriggerData_class, "<init>", "()V");

	s_TriggerDataClass = NativeStructClass_alloc("type.TriggerData");
	s_TriggerDataClass->JNISignature   = "Lorg/postgresql/pljava/TriggerData;";
	s_TriggerDataClass->javaTypeName   = "org.postgresql.pljava.TriggerData";
	s_TriggerDataClass->coerceDatum    = _TriggerData_coerceDatum;
	s_TriggerData = TypeClass_allocInstance(s_TriggerDataClass);

	Type_registerJavaType("org.postgresql.pljava.TriggerData", TriggerData_obtain);
	PG_RETURN_VOID();
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    getRelation
 * Signature: ()Lorg/postgresql/pljava/Relation;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_TriggerData_getRelation(JNIEnv* env, jobject _this)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0;
	return Relation_create(env, td->tg_relation);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    getTriggerTuple
 * Signature: ()Lorg/postgresql/pljava/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_TriggerData_getTriggerTuple(JNIEnv* env, jobject _this)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0L;
	jobject tdsc = TupleDesc_create(env, td->tg_relation->rd_att);
	jobject tpl  = Tuple_create(env, td->tg_trigtuple, tdsc);
	(*env)->DeleteLocalRef(env, tdsc);
	return tpl;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    getNewTuple
 * Signature: ()Lorg/postgresql/pljava/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_TriggerData_getNewTuple(JNIEnv* env, jobject _this)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0L;
	jobject tdsc = TupleDesc_create(env, td->tg_relation->rd_att);
	jobject tpl  = Tuple_create(env, td->tg_newtuple, tdsc);
	(*env)->DeleteLocalRef(env, tdsc);
	return tpl;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    getArguments
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
Java_org_postgresql_pljava_TriggerData_getArguments(JNIEnv* env, jobject _this)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0;

	Trigger* tg = td->tg_trigger;
	jint nargs = (jint)tg->tgnargs;
	jobjectArray oa = (*env)->NewObjectArray(env, nargs, s_String_class, 0);
	char** cpp = tg->tgargs;
	jint idx;
	for(idx = 0; idx < nargs; ++idx)
	{
		jstring js = String_createJavaStringFromNTS(env, cpp[idx]);
		(*env)->SetObjectArrayElement(env, oa, idx, js);
		(*env)->DeleteLocalRef(env, js);
	}
	return oa;
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_TriggerData_getName(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return 0;
	return String_createJavaStringFromNTS(env, td->tg_trigger->tgname);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredAfter
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredAfter(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_AFTER(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredBefore
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredBefore(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BEFORE(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredForEachRow
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredForEachRow(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_FOR_ROW(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredForStatement
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredForStatement(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_FOR_STATEMENT(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredByDelete
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredByDelete(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_DELETE(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredByInsert
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredByInsert(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_INSERT(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    isFiredByUpdate
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_TriggerData_isFiredByUpdate(JNIEnv* env, jobject triggerData)
{
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_UPDATE(td->tg_event);
}
