/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_TriggerData.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/String.h"
#include "pljava/type/TriggerData.h"
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
jobject TriggerData_create(TriggerData* td)
{
	jobject jtd;
	if(td == 0)
		return 0;

	jtd = MemoryContext_lookupNative(td);
	if(jtd == 0)
	{
		jtd = JNI_newObject(s_TriggerData_class, s_TriggerData_init);
		JavaHandle_init(jtd, td);
	}
	return jtd;
}

Datum TriggerData_getTriggerReturnTuple(jobject jtd, bool* wasNull)
{
	Datum ret = 0;
	jobject jtuple = JNI_callObjectMethod(jtd, s_TriggerData_getTriggerReturnTuple);

	if(jtuple != 0)
	{
		ret = PointerGetDatum(JavaHandle_getStruct(jtuple));
		JNI_deleteLocalRef(jtuple);
	}
	else
		*wasNull = true;
	return ret;
}

static jvalue _TriggerData_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = TriggerData_create((TriggerData*)DatumGetPointer(arg));
	return result;
}

static Type TriggerData_obtain(Oid typeId)
{
	return s_TriggerData;
}

/* Make this datatype available to the postgres system.
 */
extern void TriggerData_initialize(void);
void TriggerData_initialize(void)
{
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

	s_TriggerData_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/TriggerData"));
	PgObject_registerNatives2(s_TriggerData_class, methods);

	s_TriggerData_init = PgObject_getJavaMethod(s_TriggerData_class, "<init>", "()V");
	s_TriggerData_getTriggerReturnTuple = PgObject_getJavaMethod(s_TriggerData_class, "getTriggerReturnTuple", "()Lorg/postgresql/pljava/internal/Tuple;");
	s_TriggerDataClass = JavaHandleClass_alloc("type.TriggerData");

	/* Use interface name for signatures.
	 */
	s_TriggerDataClass->JNISignature   = "Lorg/postgresql/pljava/TriggerData;";
	s_TriggerDataClass->javaTypeName   = "org.postgresql.pljava.TriggerData";
	s_TriggerDataClass->coerceDatum    = _TriggerData_coerceDatum;
	s_TriggerData = TypeClass_allocInstance(s_TriggerDataClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.TriggerData", TriggerData_obtain);
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
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = Relation_create(((TriggerData*)p2l.ptrVal)->tg_relation);
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
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = Tuple_create(((TriggerData*)p2l.ptrVal)->tg_trigtuple);
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
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = Tuple_create(((TriggerData*)p2l.ptrVal)->tg_newtuple);
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
	if(_this != 0)
	{
		char** cpp;
		jint idx;
		Ptr2Long p2l;
		p2l.longVal = _this;

		BEGIN_NATIVE
		TriggerData* td = (TriggerData*)p2l.ptrVal;
		Trigger* tg = td->tg_trigger;
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
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = String_createJavaStringFromNTS(((TriggerData*)p2l.ptrVal)->tg_trigger->tgname);
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_AFTER(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_BEFORE(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_FOR_ROW(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_FOR_STATEMENT(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_BY_DELETE(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_BY_INSERT(((TriggerData*)p2l.ptrVal)->tg_event);
	}
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
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)TRIGGER_FIRED_BY_UPDATE(((TriggerData*)p2l.ptrVal)->tg_event);
	}
	return result;
}
