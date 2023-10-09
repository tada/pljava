/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
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
#include <executor/tuptable.h>
#include <utils/guc.h>

#include "org_postgresql_pljava_internal_ExecutionPlan.h"
#include "pljava/DualState.h"
#include "pljava/Invocation.h"
#include "pljava/Exception.h"
#include "pljava/Function.h"
#include "pljava/SPI.h"
#include "pljava/type/Oid.h"
#include "pljava/type/Portal.h"
#include "pljava/type/String.h"

#if defined(NEED_MISCADMIN_FOR_STACK_BASE)
#include <miscadmin.h>
#endif

/* Class 07 - Dynamic SQL Error */
#define ERRCODE_PARAMETER_COUNT_MISMATCH	MAKE_SQLSTATE('0','7', '0','0','1')

#define SPI_READONLY_DEFAULT \
		org_postgresql_pljava_internal_ExecutionPlan_SPI_READONLY_FORCED
#define SPI_READONLY_FORCED  \
		org_postgresql_pljava_internal_ExecutionPlan_SPI_READONLY_FORCED
#define SPI_READONLY_CLEARED \
		org_postgresql_pljava_internal_ExecutionPlan_SPI_READONLY_CLEARED

static jclass s_ExecutionPlan_class;
static jmethodID s_ExecutionPlan_init;

/* Make this datatype available to the postgres system.
 */
extern void pljava_ExecutionPlan_initialize(void);
void pljava_ExecutionPlan_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_cursorOpen",
		"(JLjava/lang/String;[Ljava/lang/Object;S)Lorg/postgresql/pljava/internal/Portal;",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen
		},
		{
		"_isCursorPlan",
		"(J)Z",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1isCursorPlan
		},
		{
		"_execute",
		"(J[Ljava/lang/Object;SI)I",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1execute
		},
		{
		"_prepare",
		"(Ljava/lang/Object;Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;",
		Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare
		},
		{ 0, 0, 0 }
	};
	PgObject_registerNatives("org/postgresql/pljava/internal/ExecutionPlan", methods);

	s_ExecutionPlan_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/ExecutionPlan"));
	s_ExecutionPlan_init = PgObject_getJavaMethod(s_ExecutionPlan_class,
		"<init>",
		"(Ljava/lang/Object;J)V");
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
		jobject typeMap = Function_currentTypeMap();
		values = (Datum*)palloc(count * sizeof(Datum));
		for(idx = 0; idx < count; ++idx)
		{
			Oid typeId = SPI_getargtypeid(ePlan, idx);
			Type type = Type_fromOid(typeId, typeMap);
			jobject value = JNI_getObjectArrayElement(jvalues, idx);
			if(value != 0)
			{
				values[idx] = Type_coerceObjectBridged(type, value);
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
 * Signature: (JLjava/lang/String;[Ljava/lang/Object;S)Lorg/postgresql/pljava/internal/Portal;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1cursorOpen(JNIEnv* env, jobject jplan, jlong _this, jstring cursorName, jobjectArray jvalues, jshort readonly_spec)
{
	jobject jportal = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		STACK_BASE_VARS
		STACK_BASE_PUSH(env)
		PG_TRY();
		{
			Ptr2Long p2l;
			Datum*  values  = 0;
			char*   nulls   = 0;
			p2l.longVal = _this;
			if(coerceObjects(p2l.ptrVal, jvalues, &values, &nulls))
			{
				Portal portal;
				char* name = 0;
				bool read_only;
				if(cursorName != 0)
					name = String_createNTS(cursorName);

				Invocation_assertConnect();
				if ( SPI_READONLY_DEFAULT == readonly_spec )
					read_only = Function_isCurrentReadOnly();
				else
					read_only = (SPI_READONLY_FORCED == readonly_spec);
				portal = SPI_cursor_open(
					name, p2l.ptrVal, values, nulls, read_only);
				if(name != 0)
					pfree(name);
				if(values != 0)
					pfree(values);
				if(nulls != 0)
					pfree(nulls);
			
				jportal = pljava_Portal_create(portal, jplan);
			}
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_open");
		}
		PG_END_TRY();
		STACK_BASE_POP()
		END_NATIVE
	}
	return jportal;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _isCursorPlan
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1isCursorPlan(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;

	if(_this != 0)
	{
		BEGIN_NATIVE
		PG_TRY();
		{
			Ptr2Long p2l;
			p2l.longVal = _this;
			Invocation_assertConnect();
			result = (jboolean)SPI_is_cursor_plan(p2l.ptrVal);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_is_cursor_plan");
		}
		PG_END_TRY();
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _execute
 * Signature: (J[Ljava/lang/Object;SI)V
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1execute(JNIEnv* env, jclass clazz, jlong _this, jobjectArray jvalues, jshort readonly_spec, jint count)
{
	jint result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		STACK_BASE_VARS
		STACK_BASE_PUSH(env)
		PG_TRY();
		{
			Ptr2Long p2l;
			Datum* values = 0;
			char*  nulls  = 0;
			p2l.longVal = _this;
			if(coerceObjects(p2l.ptrVal, jvalues, &values, &nulls))
			{
				bool read_only;
				Invocation_assertConnect();
				if ( SPI_READONLY_DEFAULT == readonly_spec )
					read_only = Function_isCurrentReadOnly();
				else
					read_only = (SPI_READONLY_FORCED == readonly_spec);
				result = (jint)SPI_execute_plan(
					p2l.ptrVal, values, nulls, read_only, (int)count);
				if(result < 0)
					Exception_throwSPI("execute_plan", result);

				if(values != 0)
					pfree(values);
				if(nulls != 0)
					pfree(nulls);
			}
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_execute_plan");
		}
		PG_END_TRY();
		STACK_BASE_POP()
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_ExecutionPlan
 * Method:    _prepare
 * Signature: (Ljava/lang/Object;Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_ExecutionPlan__1prepare(JNIEnv* env, jclass clazz, jobject key, jstring jcmd, jobjectArray paramTypes)
{
	jobject result = 0;
	int spi_ret;
	BEGIN_NATIVE
	STACK_BASE_VARS
	STACK_BASE_PUSH(env)
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
		Invocation_assertConnect();
		ePlan = SPI_prepare(cmd, paramCount, paramOids);
		pfree(cmd);

		if(ePlan == 0)
			Exception_throwSPI("prepare", SPI_result);
		else
		{
			Ptr2Long p2l;
			
			/* Make the plan durable
			 */
			p2l.longVal = 0L; /* ensure that the rest is zeroed out */
			spi_ret = SPI_keepplan(ePlan);
			if ( 0 == spi_ret )
				p2l.ptrVal = ePlan;
			else
				Exception_throwSPI("keepplan", spi_ret);

			result = JNI_newObjectLocked(
				s_ExecutionPlan_class, s_ExecutionPlan_init,
				key, p2l.longVal);
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_prepare");
	}
	PG_END_TRY();
	STACK_BASE_POP()
	END_NATIVE
	return result;
}
