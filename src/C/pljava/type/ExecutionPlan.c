/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Oid.h"
#include "pljava/type/Portal.h"
#include "pljava/type/String.h"
#include "pljava/type/ExecutionPlan.h"
#include "pljava/type/ExecutionPlan_JNI.h"

#include <executor/spi_priv.h> /* Needed to get to the argtypes of the plan */

/*
 * Returns the Oid of the type for argument at argIndex. First
 * parameter is at index zero.
 */
Oid SPI_getargtypeid(void* plan, int argIndex)
{
	if (plan == NULL || argIndex < 0 || argIndex >= ((_SPI_plan*)plan)->nargs)
	{
		SPI_result = SPI_ERROR_ARGUMENT;
		return InvalidOid;
	}
	return ((_SPI_plan*)plan)->argtypes[argIndex];
}

/*
 * Returns the number of arguments for the prepared plan.
 */
int SPI_getargcount(void* plan)
{
	if (plan == NULL)
	{
		SPI_result = SPI_ERROR_ARGUMENT;
		return -1;
	}
	return ((_SPI_plan*)plan)->nargs;
}

static Type      s_ExecutionPlan;
static TypeClass s_ExecutionPlanClass;
static jclass    s_ExecutionPlan_class;
static jmethodID s_ExecutionPlan_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject ExecutionPlan_create(JNIEnv* env, void* ep)
{
	if(ep == 0)
		return 0;

	jobject jep = NativeStruct_obtain(env, ep);
	if(jep == 0)
	{
		jep = (*env)->NewObject(env, s_ExecutionPlan_class, s_ExecutionPlan_init);
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
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_ExecutionPlan_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/ExecutionPlan"));

	s_ExecutionPlan_init = PgObject_getJavaMethod(
				env, s_ExecutionPlan_class, "<init>", "()V");

	s_ExecutionPlanClass = NativeStructClass_alloc("type.Tuple");
	s_ExecutionPlanClass->JNISignature   = "Lorg/postgresql/pljava/internal/ExecutionPlan;";
	s_ExecutionPlanClass->javaTypeName   = "org.postgresql.pljava.internal.ExecutionPlan";
	s_ExecutionPlanClass->coerceDatum    = _ExecutionPlan_coerceDatum;
	s_ExecutionPlan = TypeClass_allocInstance(s_ExecutionPlanClass);

	Type_registerJavaType("org.postgresql.pljava.internal.ExecutionPlan", ExecutionPlan_obtain);
	PG_RETURN_VOID();
}

static bool coerceObjects(JNIEnv* env, void* ePlan, jobjectArray jvalues, Datum** valuesPtr, char** nullsPtr)
{
	int count = SPI_getargcount(ePlan);
	if((jvalues == 0 && count != 0)
	|| (jvalues != 0 && count != (*env)->GetArrayLength(env, jvalues)))
		{
		Exception_throw(env, ERRCODE_PARAMETER_COUNT_MISMATCH,
			"Number of values does not match number of arguments for prepared plan");
		return false;
		}

	char*  nulls = 0;
	Datum* values = 0;
	if(count > 0)
	{
		values = (Datum*)palloc(count * sizeof(Datum));
		int idx;
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
					memset(nulls, count, ' ');	/* all values non-null initially */
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
 * Method:    cursorOpen
 * Signature: (Ljava/lang/String;[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Portal;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan_cursorOpen(JNIEnv* env, jobject _this, jstring cursorName, jobjectArray jvalues)
{
	void* ePlan = NativeStruct_getStruct(env, _this);
	if(ePlan == 0)
		return 0;

	Datum* values = 0;
	char*  nulls  = 0;
	if(!coerceObjects(env, ePlan, jvalues, &values, &nulls))
		return 0;

	char* name = 0;
	if(cursorName != 0)
		name = String_createNTS(env, cursorName);

	Portal portal = SPI_cursor_open(name, ePlan, values, nulls);
	if(name != 0)
		pfree(name);
	if(values != 0)
		pfree(values);
	if(nulls != 0)
		pfree(nulls);

	return Portal_create(env, portal);
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    execp
 * Signature: ([Ljava/lang/Object;I)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan_execp(JNIEnv* env, jobject _this, jobjectArray jvalues, jint count)
{
	void* ePlan = NativeStruct_getStruct(env, _this);
	if(ePlan == 0)
		return 0;

	Datum* values = 0;
	char*  nulls  = 0;
	if(!coerceObjects(env, ePlan, jvalues, &values, &nulls))
		return 0;

	jint result = (jint)SPI_execp(ePlan, values, nulls, (int)count);
	if(values != 0)
		pfree(values);
	if(nulls != 0)
		pfree(nulls);

	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    prepare
 * Signature: (Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan_prepare(JNIEnv* env, jclass cls, jstring jcmd, jobjectArray paramTypes)
{
 	int paramCount = 0;
	Oid* paramOids = 0;

	if(paramTypes != 0)
	{
		paramCount = (*env)->GetArrayLength(env, paramTypes);
		if(paramCount > 0)
		{
			paramOids = (Oid*)palloc(paramCount * sizeof(Oid));
			int idx;
			for(idx = 0; idx < paramCount; ++idx)
			{
				jobject joid = (*env)->GetObjectArrayElement(env, paramTypes, idx);
				paramOids[idx] = Oid_getOid(env, joid);
				(*env)->DeleteLocalRef(env, joid);
			}
		}
	}
	char* cmd = String_createNTS(env, jcmd);
	void* ePlan = SPI_prepare(cmd, paramCount, paramOids);
	pfree(cmd);
	return ExecutionPlan_create(env, ePlan);
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    invalidate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan_invalidate(JNIEnv* env, jobject _this)
{
	void* ePlan = NativeStruct_releasePointer(env, _this);
	if(ePlan != 0)
		SPI_freeplan(ePlan);
}
