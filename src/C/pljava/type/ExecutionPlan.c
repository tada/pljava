/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_ExecutionPlan.h"
#include "pljava/Exception.h"
#include "pljava/MemoryContext.h"
#include "pljava/SPI.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Oid.h"
#include "pljava/type/Portal.h"
#include "pljava/type/String.h"
#include "pljava/type/ExecutionPlan.h"

#include <utils/guc.h>

static Type      s_ExecutionPlan;
static TypeClass s_ExecutionPlanClass;
static jclass    s_ExecutionPlan_class;
static jmethodID s_ExecutionPlan_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject ExecutionPlan_create(JNIEnv* env, void* ep)
{
	jobject jep;
	if(ep == 0)
		return 0;

	jep = NativeStruct_obtain(env, ep);
	if(jep == 0)
	{
		jep = PgObject_newJavaObject(env, s_ExecutionPlan_class, s_ExecutionPlan_init);
		NativeStruct_init(env, jep, ep);
	}
	return jep;
}

static jvalue _ExecutionPlan_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = ExecutionPlan_create(env, DatumGetPointer(arg));
	return result;
}

static Type ExecutionPlan_obtain(Oid typeId)
{
	return s_ExecutionPlan;
}

/* Make this datatype available to the postgres system.
 */
extern Datum ExecutionPlan_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(ExecutionPlan_initialize);
Datum ExecutionPlan_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_savePlan",
	  	"()V",
	  	Java_org_postgresql_pljava_internal_ExecutionPlan__1savePlan
		},
		{
		"_cursorOpen",
		"(Ljava/lang/String;[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Portal;",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen
		},
		{
		"_isCursorPlan",
		"()Z",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1isCursorPlan
		},
		{
		"_execp",
		"([Ljava/lang/Object;I)I",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1execp
		},
		{
		"_prepare",
		"(Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare
		},
		{
		"_invalidate",
		"()V",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1invalidate
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_ExecutionPlan_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/ExecutionPlan"));

	PgObject_registerNatives2(env, s_ExecutionPlan_class, methods);

	s_ExecutionPlan_init = PgObject_getJavaMethod(
				env, s_ExecutionPlan_class, "<init>", "()V");

	s_ExecutionPlanClass = NativeStructClass_alloc("type.ExecutionPlan");
	s_ExecutionPlanClass->JNISignature   = "Lorg/postgresql/pljava/internal/ExecutionPlan;";
	s_ExecutionPlanClass->javaTypeName   = "org.postgresql.pljava.internal.ExecutionPlan";
	s_ExecutionPlanClass->coerceDatum    = _ExecutionPlan_coerceDatum;
	s_ExecutionPlan = TypeClass_allocInstance(s_ExecutionPlanClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.ExecutionPlan", ExecutionPlan_obtain);
	PG_RETURN_VOID();
}

static bool coerceObjects(JNIEnv* env, void* ePlan, jobjectArray jvalues, Datum** valuesPtr, char** nullsPtr)
{
	char*  nulls = 0;
	Datum* values = 0;

	int count = SPI_getargcount(ePlan);
	if((jvalues == 0 && count != 0)
	|| (jvalues != 0 && count != (*env)->GetArrayLength(env, jvalues)))
		{
		Exception_throw(env, ERRCODE_PARAMETER_COUNT_MISMATCH,
			"Number of values does not match number of arguments for prepared plan");
		return false;
		}

	if(count > 0)
	{
		int idx;
		values = (Datum*)palloc(count * sizeof(Datum));
		for(idx = 0; idx < count; ++idx)
		{
			Oid typeId = SPI_getargtypeid(ePlan, idx);
			Type type = Type_fromOid(typeId);
			jobject value = (*env)->GetObjectArrayElement(env, jvalues, idx);
			if(value != 0)
			{
				values[idx] = Type_coerceObject(type, env, value);
				(*env)->DeleteLocalRef(env, value);
			}
			else
			{
				values[idx] = 0;
				if(nulls == 0)
				{
					nulls = (char*)palloc(count+1);
					memset(nulls, ' ', count);	/* all values non-null initially */
					nulls[count] = 0;
					*nullsPtr = nulls;
				}
				nulls[idx] = 'n';
			}
		}
	}
	*valuesPtr = values;
	*nullsPtr = nulls;
	return true;
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _cursorOpen
 * Signature: (Ljava/lang/String;[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Portal;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen(JNIEnv* env, jobject _this, jstring cursorName, jobjectArray jvalues)
{
	void* ePlan;
	jobject jportal = 0;
	PLJAVA_ENTRY_FENCE(0)

	ePlan = NativeStruct_getStruct(env, _this);
	if(ePlan == 0)
		return 0;

	PG_TRY();
	{
		Datum*  values  = 0;
		char*   nulls   = 0;
		if(coerceObjects(env, ePlan, jvalues, &values, &nulls))
		{
			Portal portal;
			char* name = 0;
			if(cursorName != 0)
				name = String_createNTS(env, cursorName);
		
			portal = SPI_cursor_open(name, ePlan, values, nulls);
			if(name != 0)
				pfree(name);
			if(values != 0)
				pfree(values);
			if(nulls != 0)
				pfree(nulls);
		
			jportal = Portal_create(env, portal);
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_cursor_open");
	}
	PG_END_TRY();
	return jportal;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _isCursorPlan
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1isCursorPlan(JNIEnv* env, jobject _this)
{
	void* ePlan;
	bool result = false;
	PLJAVA_ENTRY_FENCE(false)

	ePlan = NativeStruct_getStruct(env, _this);
	if(ePlan == 0)
		return 0;

	PG_TRY();
	{
		result = SPI_is_cursor_plan(ePlan);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_is_cursor_plan");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _execp
 * Signature: ([Ljava/lang/Object;I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1execp(JNIEnv* env, jobject _this, jobjectArray jvalues, jint count)
{
	void* ePlan;
	jint result = 0;
	PLJAVA_ENTRY_FENCE(0)
	
	ePlan = NativeStruct_getStruct(env, _this);
	if(ePlan == 0)
		return 0;

	MemoryContext_pushJavaFrame(env);
	PG_TRY();
	{
		Datum* values = 0;
		char*  nulls  = 0;
		if(coerceObjects(env, ePlan, jvalues, &values, &nulls))
		{
			result = (jint)SPI_execp(ePlan, values, nulls, (int)count);
			if(values != 0)
				pfree(values);
			if(nulls != 0)
				pfree(nulls);
		}
		MemoryContext_popJavaFrame(env);
	}
	PG_CATCH();
	{
		MemoryContext_popJavaFrame(env);
		Exception_throw_ERROR(env, "SPI_execp");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _prepare
 * Signature: (Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare(JNIEnv* env, jclass cls, jstring jcmd, jobjectArray paramTypes)
{
	jobject jePlan = 0;
	PLJAVA_ENTRY_FENCE(0)
	PG_TRY();
	{
		char* cmd;
		void* ePlan;
		int paramCount = 0;
		Oid* paramOids = 0;

		if(paramTypes != 0)
		{
			paramCount = (*env)->GetArrayLength(env, paramTypes);
			if(paramCount > 0)
			{
				int idx;
				paramOids = (Oid*)palloc(paramCount * sizeof(Oid));
				for(idx = 0; idx < paramCount; ++idx)
				{
					jobject joid = (*env)->GetObjectArrayElement(env, paramTypes, idx);
					paramOids[idx] = Oid_getOid(env, joid);
					(*env)->DeleteLocalRef(env, joid);
				}
			}
		}

		cmd   = String_createNTS(env, jcmd);
		ePlan = SPI_prepare(cmd, paramCount, paramOids);
		pfree(cmd);

		if(ePlan == 0)
			Exception_throwSPI(env, "prepare");
		else
			jePlan = ExecutionPlan_create(env, ePlan);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_prepare");
	}
	PG_END_TRY();

	return jePlan;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _savePlan
 * Signature: ()V;
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1savePlan(JNIEnv* env, jobject _this)
{
	void* ePlan;
	PLJAVA_ENTRY_FENCE_VOID
	ePlan = NativeStruct_releasePointer(env, _this);
	if(ePlan == 0)
		return;

	PG_TRY();
	{
		NativeStruct_setPointer(env, _this, SPI_saveplan(ePlan));
		SPI_freeplan(ePlan);	/* Get rid of the original, nobody can see it anymore */
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_saveplan");
	}
	PG_END_TRY();
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _invalidate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1invalidate(JNIEnv* env, jobject _this)
{
	void* ePlan;
	PLJAVA_ENTRY_FENCE_VOID
	ePlan = NativeStruct_releasePointer(env, _this);
	if(ePlan == 0)
		return;

	PG_TRY();
	{
		SPI_freeplan(ePlan);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "SPI_freeplan");
	}
	PG_END_TRY();
}

