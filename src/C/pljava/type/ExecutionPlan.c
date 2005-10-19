/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_ExecutionPlan.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/Function.h"
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

static Type ExecutionPlan_obtain(Oid typeId)
{
	return s_ExecutionPlan;
}

/* Make this datatype available to the postgres system.
 */
extern void ExecutionPlan_initialize(void);
void ExecutionPlan_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_cursorOpen",
		"(JLjava/lang/String;[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Portal;",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen
		},
		{
		"_isCursorPlan",
		"()Z",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1isCursorPlan
		},
		{
		"_execute",
		"(J[Ljava/lang/Object;I)I",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1execute
		},
		{
		"_prepare",
		"(Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)V",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare
		},
		{
		"_invalidate",
		"()V",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1invalidate
		},
		{ 0, 0, 0 }};

	s_ExecutionPlan_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/ExecutionPlan"));
	PgObject_registerNatives2(s_ExecutionPlan_class, methods);

	s_ExecutionPlanClass = JavaHandleClass_alloc("type.ExecutionPlan");
	s_ExecutionPlanClass->JNISignature   = "Lorg/postgresql/pljava/internal/ExecutionPlan;";
	s_ExecutionPlanClass->javaTypeName   = "org.postgresql.pljava.internal.ExecutionPlan";
	s_ExecutionPlan = TypeClass_allocInstance(s_ExecutionPlanClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.ExecutionPlan", ExecutionPlan_obtain);
}

static bool coerceObjects(void* ePlan, jobjectArray jvalues, Datum** valuesPtr, char** nullsPtr)
{
	char*  nulls = 0;
	Datum* values = 0;

	int count = SPI_getargcount(ePlan);
	if((jvalues == 0 && count != 0)
	|| (jvalues != 0 && count != JNI_getArrayLength(jvalues)))
	{
		Exception_throw(ERRCODE_PARAMETER_COUNT_MISMATCH,
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
			jobject value = JNI_getObjectArrayElement(jvalues, idx);
			if(value != 0)
			{
				values[idx] = Type_coerceObject(type, value);
				JNI_deleteLocalRef(value);
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
 * Signature: (JLjava/lang/String;[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Portal;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen(JNIEnv* env, jobject _this, jlong threadId, jstring cursorName, jobjectArray jvalues)
{
	jobject jportal = 0;

	BEGIN_NATIVE
	void* ePlan = JavaHandle_getStruct(_this);
	if(ePlan != 0)
	{
		STACK_BASE_VARS
		STACK_BASE_PUSH(threadId)
		PG_TRY();
		{
			Datum*  values  = 0;
			char*   nulls   = 0;
			if(coerceObjects(ePlan, jvalues, &values, &nulls))
			{
				Portal portal;
				char* name = 0;
				if(cursorName != 0)
					name = String_createNTS(cursorName);
	
				Backend_assertConnect();
				portal = SPI_cursor_open(
					name, ePlan, values, nulls, Function_isCurrentReadOnly());
				if(name != 0)
					pfree(name);
				if(values != 0)
					pfree(values);
				if(nulls != 0)
					pfree(nulls);
			
				jportal = Portal_create(portal);
			}
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_open");
		}
		PG_END_TRY();
		STACK_BASE_POP()
	}
	END_NATIVE
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
	jboolean result = JNI_FALSE;
	BEGIN_NATIVE

	void* ePlan = JavaHandle_getStruct(_this);
	if(ePlan != 0)
	{
		PG_TRY();
		{
			Backend_assertConnect();
			result = (jboolean)SPI_is_cursor_plan(ePlan);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_is_cursor_plan");
		}
		PG_END_TRY();
	}
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _execute
 * Signature: (J[Ljava/lang/Object;I)V
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1execute(JNIEnv* env, jobject _this, jlong threadId, jobjectArray jvalues, jint count)
{
	void* ePlan;
	jint result = 0;

	BEGIN_NATIVE

	ePlan = JavaHandle_getStruct(_this);
	if(ePlan != 0)
	{
		STACK_BASE_VARS
		STACK_BASE_PUSH(threadId)
		Backend_pushJavaFrame();
		PG_TRY();
		{
			Datum* values = 0;
			char*  nulls  = 0;
			if(coerceObjects(ePlan, jvalues, &values, &nulls))
			{
				Backend_assertConnect();
				result = (jint)SPI_execute_plan(
					ePlan, values, nulls, Function_isCurrentReadOnly(), (int)count);
				if(result < 0)
					Exception_throwSPI("execute_plan", result);
	
				if(values != 0)
					pfree(values);
				if(nulls != 0)
					pfree(nulls);
			}
			Backend_popJavaFrame();
		}
		PG_CATCH();
		{
			Backend_popJavaFrame();
			Exception_throw_ERROR("SPI_execute_plan");
		}
		PG_END_TRY();
		STACK_BASE_POP()
	}
	END_NATIVE
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _prepare
 * Signature: ()V;
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare(JNIEnv* env, jobject _this, jstring jcmd, jobjectArray paramTypes)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		char* cmd;
		void* ePlan;
		int paramCount = 0;
		Oid* paramOids = 0;

		if(paramTypes != 0)
		{
			paramCount = JNI_getArrayLength(paramTypes);
			if(paramCount > 0)
			{
				int idx;
				paramOids = (Oid*)palloc(paramCount * sizeof(Oid));
				for(idx = 0; idx < paramCount; ++idx)
				{
					jobject joid = JNI_getObjectArrayElement(paramTypes, idx);
					paramOids[idx] = Oid_getOid(joid);
					JNI_deleteLocalRef(joid);
				}
			}
		}

		cmd   = String_createNTS(jcmd);
		Backend_assertConnect();
		ePlan = SPI_prepare(cmd, paramCount, paramOids);
		pfree(cmd);

		if(ePlan == 0)
			Exception_throwSPI("prepare", SPI_result);
		else
		{
			JavaHandle_setPointer(_this, SPI_saveplan(ePlan));
			SPI_freeplan(ePlan);	/* Get rid of the original, nobody can see it anymore */
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_prepare");
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _invalidate
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1invalidate(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE

	/* The plan is not cached as a normal JavaHandle since its made
	 * persistent.
	 */
	void* ePlan = JavaHandle_getStruct(_this);
	if(ePlan != 0)
	{
		PG_TRY();
		{
			JavaHandle_setPointer(_this, 0);
			SPI_freeplan(ePlan);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_freeplan");
		}
		PG_END_TRY();
	}
	END_NATIVE
}
