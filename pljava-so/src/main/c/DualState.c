/*
 * Copyright (c) 2018-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */

#include "org_postgresql_pljava_internal_DualState_SinglePfree.h"
#include "org_postgresql_pljava_internal_DualState_SingleMemContextDelete.h"
#include "org_postgresql_pljava_internal_DualState_SingleFreeTupleDesc.h"
#include "org_postgresql_pljava_internal_DualState_SingleHeapFreeTuple.h"
#include "org_postgresql_pljava_internal_DualState_SingleFreeErrorData.h"
#include "org_postgresql_pljava_internal_DualState_SingleSPIfreeplan.h"
#include "org_postgresql_pljava_internal_DualState_SingleSPIfreetuptable.h"
#include "org_postgresql_pljava_internal_DualState_SingleSPIcursorClose.h"
#include "org_postgresql_pljava_internal_DualState_BBHeapFreeTuple.h"
#include "org_postgresql_pljava_internal_DualState_SingleDeleteGlobalRefP.h"
#include "pljava/DualState.h"

#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"
#include "pljava/SPI.h"

/*
 * Includes for objects dependent on DualState, so they can be initialized here.
 * (If there's a .c file that has no corresponding .h file because there would
 * be only an ..._initialize method in it and nothing else at all, just declare
 * its init method here.)
 */
#include "pljava/type/ErrorData.h"
extern void pljava_ExecutionPlan_initialize(void);
#include "pljava/type/Portal.h"
#include "pljava/type/Relation.h"
#include "pljava/type/SingleRowReader.h"
#include "pljava/type/TriggerData.h"
#include "pljava/type/Tuple.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/SQLInputFromTuple.h"
#include "pljava/VarlenaWrapper.h"

static jclass s_DualState_class;

static jmethodID s_DualState_cleanEnqueuedInstances;

/*
 * Rather than using finalizers (deprecated in recent Java anyway), which can
 * increase the number of threads needing to interact with PG, DualState objects
 * will be enqueued on a ReferenceQueue when their referents become unreachable,
 * and this function should be called from strategically-chosen points in native
 * code so the thread already interacting with PG will clean the enqueued items.
 */
void pljava_DualState_cleanEnqueuedInstances(void)
{
	JNI_callStaticVoidMethodLocked(s_DualState_class,
								   s_DualState_cleanEnqueuedInstances);
}

void pljava_DualState_initialize(void)
{
	jclass clazz;

	JNINativeMethod singlePfreeMethods[] =
	{
		{
		"_pfree",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SinglePfree__1pfree
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleMemContextDeleteMethods[] =
	{
		{
		"_memContextDelete",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleMemContextDelete__1memContextDelete
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleFreeTupleDescMethods[] =
	{
		{
		"_freeTupleDesc",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleFreeTupleDesc__1freeTupleDesc
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleHeapFreeTupleMethods[] =
	{
		{
		"_heapFreeTuple",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleHeapFreeTuple__1heapFreeTuple
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleFreeErrorDataMethods[] =
	{
		{
		"_freeErrorData",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleFreeErrorData__1freeErrorData
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleSPIfreeplanMethods[] =
	{
		{
		"_spiFreePlan",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleSPIfreeplan__1spiFreePlan
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleSPIfreetuptableMethods[] =
	{
		{
		"_spiFreeTupTable",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleSPIfreetuptable__1spiFreeTupTable
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod singleSPIcursorCloseMethods[] =
	{
		{
		"_spiCursorClose",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleSPIcursorClose__1spiCursorClose
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod bbHeapFreeTupleMethods[] =
	{
		{
		"_heapFreeTuple",
		"(Ljava/nio/ByteBuffer;)V",
		Java_org_postgresql_pljava_internal_DualState_00024BBHeapFreeTuple__1heapFreeTuple
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod deleteGlobalRefPMethods[] =
	{
		{
		"_deleteGlobalRefP",
		"(J)V",
		Java_org_postgresql_pljava_internal_DualState_00024SingleDeleteGlobalRefP__1deleteGlobalRefP
		},
		{ 0, 0, 0 }
	};

	s_DualState_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState"));
	s_DualState_cleanEnqueuedInstances = PgObject_getStaticJavaMethod(
		s_DualState_class, "cleanEnqueuedInstances", "()V");

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SinglePfree");
	PgObject_registerNatives2(clazz, singlePfreeMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleMemContextDelete");
	PgObject_registerNatives2(clazz, singleMemContextDeleteMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleFreeTupleDesc");
	PgObject_registerNatives2(clazz, singleFreeTupleDescMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleHeapFreeTuple");
	PgObject_registerNatives2(clazz, singleHeapFreeTupleMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleFreeErrorData");
	PgObject_registerNatives2(clazz, singleFreeErrorDataMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleSPIfreeplan");
	PgObject_registerNatives2(clazz, singleSPIfreeplanMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleSPIfreetuptable");
	PgObject_registerNatives2(clazz, singleSPIfreetuptableMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleSPIcursorClose");
	PgObject_registerNatives2(clazz, singleSPIcursorCloseMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$BBHeapFreeTuple");
	PgObject_registerNatives2(clazz, bbHeapFreeTupleMethods);
	JNI_deleteLocalRef(clazz);

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$SingleDeleteGlobalRefP");
	PgObject_registerNatives2(clazz, deleteGlobalRefPMethods);
	JNI_deleteLocalRef(clazz);

	/*
	 * Call initialize() methods of known classes built upon DualState.
	 */
	pljava_ErrorData_initialize();
	pljava_ExecutionPlan_initialize();
	pljava_Portal_initialize();
	pljava_Relation_initialize();
	pljava_SingleRowReader_initialize();
	pljava_SQLInputFromTuple_initialize();
	pljava_TriggerData_initialize();
	pljava_TupleDesc_initialize();
	pljava_Tuple_initialize();
	pljava_VarlenaWrapper_initialize();
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SinglePfree
 * Method:    _pfree
 * Signature: (J)V
 *
 * Cadged from JavaWrapper.c
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SinglePfree__1pfree(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	pfree(JLongGet(void *, pointer));
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleMemContextDelete
 * Method:    _memContextDelete
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleMemContextDelete__1memContextDelete(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	MemoryContextDelete(JLongGet(MemoryContext, pointer));
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleFreeTupleDesc
 * Method:    _freeTupleDesc
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleFreeTupleDesc__1freeTupleDesc(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	FreeTupleDesc(JLongGet(TupleDesc, pointer));
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleHeapFreeTuple
 * Method:    _heapFreeTuple
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleHeapFreeTuple__1heapFreeTuple(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	heap_freetuple(JLongGet(HeapTuple, pointer));
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleFreeErrorData
 * Method:    _freeErrorData
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleFreeErrorData__1freeErrorData(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	FreeErrorData(JLongGet(ErrorData *, pointer));
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleSPIfreeplan
 * Method:    _spiFreePlan
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleSPIfreeplan__1spiFreePlan(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		SPI_freeplan(JLongGet(SPIPlanPtr, pointer));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_freeplan");
	}
	PG_END_TRY();
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleSPIfreetuptable
 * Method:    _spiFreeTupTable
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleSPIfreetuptable__1spiFreeTupTable(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		SPI_freetuptable(JLongGet(SPITupleTable *, pointer));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_freetuptable");
	}
	PG_END_TRY();
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleSPIcursorClose
 * Method:    _spiCursorClose
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleSPIcursorClose__1spiCursorClose(
	JNIEnv* env, jobject _this, jlong pointer)
{
	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		/*
		 * This code copied from its former location in Portal.c, for reasons
		 * not really explained there, is different from most of the other
		 * javaStateReleased actions here, by virtue of being conditional; it
		 * does nothing if the current Invocation's errorOccurred flag is set,
		 * or during an end-of-expression-context callback from the executor.
		 */
		if ( HAS_INVOCATION && ! currentInvocation->errorOccurred
			&& ! currentInvocation->inExprContextCB )
			SPI_cursor_close(JLongGet(Portal, pointer));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("SPI_cursor_close");
	}
	PG_END_TRY();
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_BBHeapFreeTuple
 * Method:    _heapFreeTuple
 * Signature: (Ljava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024BBHeapFreeTuple__1heapFreeTuple(
	JNIEnv* env, jobject _this, jobject bb)
{
	HeapTuple tup = (*env)->GetDirectBufferAddress(env, bb);
	if ( NULL == tup )
		return;
	BEGIN_NATIVE_NO_ERRCHECK
	heap_freetuple(tup);
	END_NATIVE
}



/*
 * Class:     org_postgresql_pljava_internal_DualState_SingleDeleteGlobalRefP
 * Method:    _deleteGlobalRefP
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_DualState_00024SingleDeleteGlobalRefP__1deleteGlobalRefP(
	JNIEnv* env, jobject _this, jlong jrefp)
{
	jobject *refp = JLongGet(jobject *, jrefp);
	jobject ref = *refp;
	*refp = NULL;
	/* no call into PostgreSQL here, just one simple JNI operation */
	(*env)->DeleteGlobalRef(env, ref);
}
