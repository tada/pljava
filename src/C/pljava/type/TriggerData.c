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
#include "pljava/Exception.h"

static Type      s_TriggerData;
static TypeClass s_TriggerDataClass;
static jclass    s_TriggerData_class;
static jmethodID s_TriggerData_init;
static jmethodID s_TriggerData_getTriggerReturnTuple;

/*
 * org.postgresql.pljava.TriggerData type.
 */
jobject TriggerData_create(JNIEnv* env, TriggerData* td)
{
	if(td == 0)
		return 0;

	jobject jtd = NativeStruct_obtain(env, td);
	if(jtd == 0)
	{
		jtd = PgObject_newJavaObject(env, s_TriggerData_class, s_TriggerData_init);
		NativeStruct_init(env, jtd, td);
	}
	return jtd;
}

Datum TriggerData_getTriggerReturnTuple(JNIEnv* env, jobject jtd, bool* wasNull)
{
	Datum ret = 0;

	bool saveicj = isCallingJava;
	isCallingJava = true;
	jobject jtuple = (*env)->CallObjectMethod(env, jtd, s_TriggerData_getTriggerReturnTuple);
	isCallingJava = saveicj;

	if(jtuple != 0)
	{
		ret = PointerGetDatum(NativeStruct_getStruct(env, jtuple));
		(*env)->DeleteLocalRef(env, jtuple);
	}
	else
		*wasNull = true;
	return ret;
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
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/TriggerData"));

	s_TriggerData_init = PgObject_getJavaMethod(
				env, s_TriggerData_class, "<init>", "()V");

	s_TriggerData_getTriggerReturnTuple = PgObject_getJavaMethod(
				env, s_TriggerData_class, "getTriggerReturnTuple", "()Lorg/postgresql/pljava/internal/Tuple;");

	s_TriggerDataClass = NativeStructClass_alloc("type.TriggerData");
	
	// Use interface name for signatures.
	//
	s_TriggerDataClass->JNISignature   = "Lorg/postgresql/pljava/TriggerData;";
	s_TriggerDataClass->javaTypeName   = "org.postgresql.pljava.TriggerData";
	s_TriggerDataClass->coerceDatum    = _TriggerData_coerceDatum;
	s_TriggerData = TypeClass_allocInstance(s_TriggerDataClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.TriggerData", TriggerData_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getRelation
 * Signature: ()Lorg/postgresql/pljava/internal/Relation;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getRelation(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0;
	return Relation_create(env, td->tg_relation);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getTriggerTuple
 * Signature: ()Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getTriggerTuple(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0L;
	return Tuple_create(env, td->tg_trigtuple);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getNewTuple
 * Signature: ()Lorg/postgresql/pljava/internal/Tuple;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getNewTuple(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, _this);
	if(td == 0)
		return 0L;
	return Tuple_create(env, td->tg_newtuple);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _getArguments
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getArguments(JNIEnv* env, jobject _this)
{
	PLJAVA_ENTRY_FENCE(0)
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
 * Method:    _getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1getName(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(0)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return 0;
	return String_createJavaStringFromNTS(env, td->tg_trigger->tgname);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredAfter
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredAfter(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_AFTER(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredBefore
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredBefore(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BEFORE(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredForEachRow
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredForEachRow(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_FOR_ROW(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredForStatement
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredForStatement(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_FOR_STATEMENT(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByDelete
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByDelete(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_DELETE(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByInsert
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByInsert(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_INSERT(td->tg_event);
}

/*
 * Class:     org_postgresql_pljava_TriggerData
 * Method:    _isFiredByUpdate
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_TriggerData__1isFiredByUpdate(JNIEnv* env, jobject triggerData)
{
	PLJAVA_ENTRY_FENCE(false)
	TriggerData* td = (TriggerData*)NativeStruct_getStruct(env, triggerData);
	if(td == 0)
		return false;
	return (jboolean)TRIGGER_FIRED_BY_UPDATE(td->tg_event);
}
