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
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
#include <postgres.h>
#include <commands/portalcmds.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_Portal.h"
#include "pljava/Backend.h"
#include "pljava/DualState.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/HashMap.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Portal.h"
#include "pljava/type/String.h"

#if defined(NEED_MISCADMIN_FOR_STACK_BASE)
#include <miscadmin.h>
#endif

/**
 * \addtogroup JNI
 * @{
 */

static jclass    s_Portal_class;
static jmethodID s_Portal_init;

/*
 * org.postgresql.pljava.type.Portal type.
 */
jobject pljava_Portal_create(Portal portal, jobject jplan)
{
	jobject jportal;
	Ptr2Long p2l;
	Ptr2Long p2lro;
	if(portal == 0)
		return NULL;

	p2l.longVal = 0L; /* ensure that the rest is zeroed out */
	p2l.ptrVal = portal;

	p2lro.longVal = 0L;
	p2lro.ptrVal = portal->resowner;

	jportal = JNI_newObjectLocked(s_Portal_class, s_Portal_init,
		pljava_DualState_key(), p2lro.longVal, p2l.longVal, jplan);

	return jportal;
}

/* Make this datatype available to the postgres system.
 */
void pljava_Portal_initialize(void)
{
	JNINativeMethod methods[] =
	{
		{
		"_getName",
		"(J)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Portal__1getName
		},
		{
		"_getPortalPos",
		"(J)J",
	  	Java_org_postgresql_pljava_internal_Portal__1getPortalPos
		},
		{
		"_getTupleDesc",
		"(J)Lorg/postgresql/pljava/internal/TupleDesc;",
		Java_org_postgresql_pljava_internal_Portal__1getTupleDesc
		},
		{
		"_fetch",
		"(JZJ)J",
	  	Java_org_postgresql_pljava_internal_Portal__1fetch
		},
		{
		"_isAtEnd",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isAtEnd
		},
		{
		"_isAtStart",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isAtStart
		},
		{
		"_move",
		"(JZJ)J",
	  	Java_org_postgresql_pljava_internal_Portal__1move
		},
		{ 0, 0, 0 }
	};

	s_Portal_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Portal"));
	PgObject_registerNatives2(s_Portal_class, methods);
	s_Portal_init = PgObject_getJavaMethod(s_Portal_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;JJLorg/postgresql/pljava/internal/ExecutionPlan;)V");
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getPortalPos
 * Signature: (J)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_Portal__1getPortalPos(JNIEnv* env, jclass clazz, jlong _this)
{
	jlong result = 0;
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jlong)((Portal)p2l.ptrVal)->portalPos;
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _fetch
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_Portal__1fetch(JNIEnv* env, jclass clazz, jlong _this, jboolean forward, jlong count)
{
	jlong result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		STACK_BASE_VARS
		STACK_BASE_PUSH(env)

		/*
		 * One call to cleanEnqueued... is made in Invocation_popInvocation,
		 * when any PL/Java function returns to PostgreSQL. But what of a
		 * PL/Java function that loops through a lot of data before returning?
		 * It could be important to call cleanEnqueued... from some other
		 * strategically-chosen places, and this seems a good one. We get here
		 * every fetchSize (default 1000? See SPIStatement) rows retrieved.
		 */
		pljava_DualState_cleanEnqueuedInstances();

		p2l.longVal = _this;
		PG_TRY();
		{
			Invocation_assertConnect();
			SPI_cursor_fetch((Portal)p2l.ptrVal, forward == JNI_TRUE,
				(long)count);
			result = (jlong)SPI_processed;
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_fetch");
		}
		PG_END_TRY();
		STACK_BASE_POP()
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getName
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_org_postgresql_pljava_internal_Portal__1getName(JNIEnv* env, jclass clazz, jlong _this)
{
	jstring result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = String_createJavaStringFromNTS(((Portal)p2l.ptrVal)->name);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getTupleDesc
 * Signature: (J)Lorg/postgresql/pljava/internal/TupleDesc;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_Portal__1getTupleDesc(JNIEnv* env, jclass clazz, jlong _this)
{
	jobject result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = pljava_TupleDesc_create(((Portal)p2l.ptrVal)->tupDesc);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _isAtStart
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isAtStart(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)((Portal)p2l.ptrVal)->atStart;
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _isAtEnd
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isAtEnd(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)((Portal)p2l.ptrVal)->atEnd;
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _move
 * Signature: (JZJ)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_Portal__1move(JNIEnv* env, jclass clazz, jlong _this, jboolean forward, jlong count)
{
	jlong result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		STACK_BASE_VARS
		STACK_BASE_PUSH(env)

		p2l.longVal = _this;
		PG_TRY();
		{
			Invocation_assertConnect();
			SPI_cursor_move((Portal)p2l.ptrVal, forward == JNI_TRUE, (long)count);
			result = (jlong)SPI_processed;
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_move");
		}
		PG_END_TRY();
		STACK_BASE_POP()
		END_NATIVE
	}
	return result;
}
/** @} */
