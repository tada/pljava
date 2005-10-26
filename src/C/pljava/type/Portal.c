/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <commands/portalcmds.h>
#include <executor/spi.h>
#include <executor/tuptable.h>

#include "org_postgresql_pljava_internal_Portal.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/type/Portal.h"
#include "pljava/type/String.h"

static Type      s_Portal;
static TypeClass s_PortalClass;
static jclass    s_Portal_class;
static jmethodID s_Portal_init;

typedef void (*PortalCleanupProc)(Portal portal);

static PortalCleanupProc s_originalCleanupProc = 0;

static void _pljavaPortalCleanup(Portal portal)
{
	MemoryContext currCtx = MemoryContextSwitchTo(TopTransactionContext);
	jobject jportal = MemoryContext_lookupNative(portal);
	if(jportal != 0)
		/*
		 * Remove this object from the cache and clear its
		 * handle.
		 */
		JavaHandle_releasePointer(jportal);

	MemoryContextSwitchTo(currCtx);

	portal->cleanup = s_originalCleanupProc;
	if(s_originalCleanupProc != 0)
	{
		(*s_originalCleanupProc)(portal);
	}
}

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject Portal_create(Portal portal)
{
	MemoryContext currCtx;
	jobject jportal;

	if(portal == 0)
		return 0;

	/* We must cache the native mapping in a context that is reachable
	 * from the _pljavaPortalCleanup callback.
	 */
	currCtx = MemoryContextSwitchTo(TopTransactionContext);
	jportal = MemoryContext_lookupNative(portal);
	if(jportal == 0)
	{
		jportal = JNI_newObject(s_Portal_class, s_Portal_init);
		JavaHandle_init(jportal, portal);

		/* We need to know when a portal is dropped so that we
		 * don't attempt to drop it twice.
		 */
		if(s_originalCleanupProc == 0)
			s_originalCleanupProc = portal->cleanup;

		if(portal->cleanup == s_originalCleanupProc)
			portal->cleanup = _pljavaPortalCleanup;
	}
	MemoryContextSwitchTo(currCtx);
	return jportal;
}

static jvalue _Portal_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = Portal_create((Portal)DatumGetPointer(arg));
	return result;
}

static Type Portal_obtain(Oid typeId)
{
	return s_Portal;
}

/* Make this datatype available to the postgres system.
 */
extern void Portal_initialize(void);
void Portal_initialize(void)
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
	  	"(J)I",
	  	Java_org_postgresql_pljava_internal_Portal__1getPortalPos
		},
		{
		"_getTupleDesc",
		"(J)Lorg/postgresql/pljava/internal/TupleDesc;",
		Java_org_postgresql_pljava_internal_Portal__1getTupleDesc
		},
		{
		"_fetch",
	  	"(JZI)I",
	  	Java_org_postgresql_pljava_internal_Portal__1fetch
		},
		{
		"_invalidate",
	  	"(J)V",
	  	Java_org_postgresql_pljava_internal_Portal__1invalidate
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
		"_isPosOverflow",
	  	"(J)Z",
	  	Java_org_postgresql_pljava_internal_Portal__1isPosOverflow
		},
		{
		"_move",
	  	"(JZI)I",
	  	Java_org_postgresql_pljava_internal_Portal__1move
		},
		{ 0, 0, 0 }
	};

	s_Portal_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/Portal"));
	PgObject_registerNatives2(s_Portal_class, methods);
	s_Portal_init = PgObject_getJavaMethod(s_Portal_class, "<init>", "()V");

	s_PortalClass = JavaHandleClass_alloc("type.Tuple");
	s_PortalClass->JNISignature   = "Lorg/postgresql/pljava/internal/Portal;";
	s_PortalClass->javaTypeName   = "org.postgresql.pljava.internal.Portal";
	s_PortalClass->coerceDatum    = _Portal_coerceDatum;
	s_Portal = TypeClass_allocInstance(s_PortalClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.Portal", Portal_obtain);
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _getPortalPos
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1getPortalPos(JNIEnv* env, jclass clazz, jlong _this)
{
	jint result = 0;
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jint)((Portal)p2l.ptrVal)->portalPos;
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _fetch
 * Signature: (JZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1fetch(JNIEnv* env, jclass clazz, jlong _this, jboolean forward, jint count)
{
	jint result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		PG_TRY();
		{
			SPI_cursor_fetch((Portal)p2l.ptrVal, forward == JNI_TRUE, (int)count);
			result = (jint)SPI_processed;
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_fetch");
		}
		PG_END_TRY();
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
		result = TupleDesc_create(((Portal)p2l.ptrVal)->tupDesc);
		END_NATIVE
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _invalidate
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Portal__1invalidate(JNIEnv* env, jclass clazz, jlong _this)
{
	/* We don't use error checking here since we don't want an exception
	 * caused by another exception when we attempt to close.
	 */
	if(_this != 0
	&& !currentInvocation->errorOccured
	&& !currentInvocation->inExprContextCB)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		BEGIN_NATIVE_NO_ERRCHECK
		Portal portal = (Portal)p2l.ptrVal;
		MemoryContext_dropNative(portal);

		/* Reset our own cleanup callback if needed. No need to come in
		 * the backway
		 */
		if(portal->cleanup == _pljavaPortalCleanup)
			portal->cleanup = s_originalCleanupProc;

		SPI_cursor_close(portal);
		END_NATIVE
	}
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
 * Method:    _isPosOverflow
 * Signature: (J)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Portal__1isPosOverflow(JNIEnv* env, jclass clazz, jlong _this)
{
	jboolean result = JNI_FALSE;
	if(_this != 0)
	{
		Ptr2Long p2l;
		p2l.longVal = _this;
		result = (jboolean)((Portal)p2l.ptrVal)->posOverflow;
	}
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Portal
 * Method:    _move
 * Signature: (ZI)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Portal__1move(JNIEnv* env, jclass clazz, jlong _this, jboolean forward, jint count)
{
	jint result = 0;
	if(_this != 0)
	{
		BEGIN_NATIVE
		Ptr2Long p2l;
		p2l.longVal = _this;
		PG_TRY();
		{
			SPI_cursor_move((Portal)p2l.ptrVal, forward == JNI_TRUE, (int)count);
			result = (jint)SPI_processed;
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("SPI_cursor_move");
		}
		PG_END_TRY();
		END_NATIVE
	}
	return result;
}
