/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>

#include "org_postgresql_pljava_internal_LargeObject.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/Oid.h"
#include "pljava/type/LargeObject.h"

static Type      s_LargeObject;
static TypeClass s_LargeObjectClass;
static jclass    s_LargeObject_class;
static jmethodID s_LargeObject_init;

/*
 * org.postgresql.pljava.type.LargeObject type.
 */
jobject LargeObject_create(JNIEnv* env, LargeObjectDesc* lo)
{
	jobject jlo;
	if(lo == 0)
		return 0;

	jlo = MemoryContext_lookupNative(env, lo);
	if(jlo == 0)
	{
		jlo = PgObject_newJavaObject(env, s_LargeObject_class, s_LargeObject_init);
		NativeStruct_init(env, jlo, lo);
	}
	return jlo;
}

static jvalue _LargeObject_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = LargeObject_create(env, (LargeObjectDesc*)DatumGetPointer(arg));
	return result;
}

static Type LargeObject_obtain(Oid typeId)
{
	return s_LargeObject;
}

/* Make this datatype available to the postgres system.
 */
extern Datum LargeObject_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(LargeObject_initialize);
Datum LargeObject_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_create",
	  	"(I)Lorg/postgresql/pljava/internal/LargeObject;",
	  	Java_org_postgresql_pljava_internal_LargeObject__1create
		},
		{
		"_drop",
	  	"(Lorg/postgresql/pljava/internal/Oid;)I",
	  	Java_org_postgresql_pljava_internal_LargeObject__1drop
		},
		{
		"_open",
	  	"(Lorg/postgresql/pljava/internal/Oid;I)Lorg/postgresql/pljava/internal/LargeObject;",
	  	Java_org_postgresql_pljava_internal_LargeObject__1open
		},
		{
		"_close",
	  	"()V",
	  	Java_org_postgresql_pljava_internal_LargeObject__1close
		},
		{
		"_getId",
	  	"()Lorg/postgresql/pljava/internal/Oid;",
	  	Java_org_postgresql_pljava_internal_LargeObject__1getId
		},
		{
		"_length",
	  	"()J",
	  	Java_org_postgresql_pljava_internal_LargeObject__1length
		},
		{
		"_seek",
	  	"(JI)J",
	  	Java_org_postgresql_pljava_internal_LargeObject__1seek
		},
		{
		"_tell",
	  	"()J",
	  	Java_org_postgresql_pljava_internal_LargeObject__1tell
		},
		{
		"_read",
	  	"([B)I",
	  	Java_org_postgresql_pljava_internal_LargeObject__1read
		},
		{
		"_write",
	  	"([B)I",
	  	Java_org_postgresql_pljava_internal_LargeObject__1write
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_LargeObject_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/LargeObject"));

	PgObject_registerNatives2(env, s_LargeObject_class, methods);

	s_LargeObject_init = PgObject_getJavaMethod(
				env, s_LargeObject_class, "<init>", "()V");

	s_LargeObjectClass = NativeStructClass_alloc("type.LargeObject");
	s_LargeObjectClass->JNISignature   = "Lorg/postgresql/pljava/internal/LargeObject;";
	s_LargeObjectClass->javaTypeName   = "org.postgresql.pljava.internal.LargeObject";
	s_LargeObjectClass->coerceDatum    = _LargeObject_coerceDatum;
	s_LargeObject = TypeClass_allocInstance(s_LargeObjectClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.LargeObject", LargeObject_obtain);
	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/
 
/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _create
 * Signature: (I)Lorg/postgresql/pljava/internal/LargeObject;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1create(JNIEnv* env, jclass cls, jint flags)
{
	jobject jlo = 0;
	PLJAVA_ENTRY_FENCE(0)

	PG_TRY();
	{
		jlo = LargeObject_create(env, inv_create((int)flags));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_create");
	}
	PG_END_TRY();
	return jlo;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _drop
 * Signature: (Lorg/postgresql/pljava/internal/Oid;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1drop(JNIEnv* env, jclass cls, jobject oid)
{
	jint result = -1;
	PLJAVA_ENTRY_FENCE(0)

	PG_TRY();
	{
		result = inv_drop(Oid_getOid(env, oid));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_drop");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _open
 * Signature: (Lorg/postgresql/pljava/internal/Oid;I)Lorg/postgresql/pljava/internal/LargeObject;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1open(JNIEnv* env, jclass cls, jobject oid, jint flags)
{
	jobject jlo = 0;
	PLJAVA_ENTRY_FENCE(0)

	PG_TRY();
	{
		jlo = LargeObject_create(env, inv_open(Oid_getOid(env, oid), (int)flags));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_open");
	}
	PG_END_TRY();
	return jlo;
}


/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _close
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1close(JNIEnv* env, jobject _this)
{
	LargeObjectDesc* self;

	PLJAVA_ENTRY_FENCE_VOID
	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return;

	PG_TRY();
	{
		inv_close(self);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_close");
	}
	PG_END_TRY();
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _getId
 * Signature: ()Lorg/postgresql/pljava/internal/Oid;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1getId(JNIEnv* env, jobject _this)
{
	LargeObjectDesc* self;

	PLJAVA_ENTRY_FENCE(0)
	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;
	return Oid_create(env, self->id);
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _length
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1length(JNIEnv* env, jobject _this)
{
	jlong result = 0;
	LargeObjectDesc* self;

	PLJAVA_ENTRY_FENCE(0)
	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		/* There's no inv_length call so we use inv_seek on
		 * a temporary LargeObjectDesc.
		 */
		LargeObjectDesc lod;
		memcpy(&lod, self, sizeof(LargeObjectDesc));
		result = (jlong)inv_seek(&lod, 0, SEEK_END);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_seek");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _seek
 * Signature: (JI)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1seek(JNIEnv* env, jobject _this, jlong pos, jint whence)
{
	jlong result = 0;
	LargeObjectDesc* self;

	PLJAVA_ENTRY_FENCE(0)
	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		result = (jlong)inv_seek(self, (int)pos, (int)whence);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_seek");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _tell
 * Signature: ()J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1tell(JNIEnv* env, jobject _this)
{
	jlong result = 0;
	LargeObjectDesc* self;

	PLJAVA_ENTRY_FENCE(0)
	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	PG_TRY();
	{
		result = (jlong)inv_tell(self);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(env, "inv_tell");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _read
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1read(JNIEnv* env, jobject _this, jbyteArray buf)
{
	jint result = -1;
	LargeObjectDesc* self;
	jbyte* byteBuf;
	jint   nBytes;

	PLJAVA_ENTRY_FENCE(0)
	if(buf == 0)
		return 0;

	nBytes  = (*env)->GetArrayLength(env, buf);
	if(nBytes == 0)
		return 0;

	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	byteBuf = (*env)->GetByteArrayElements(env, buf, 0);
	if(byteBuf == 0)
		return 0;	/* OutOfMemoryException will be thrown */

	PG_TRY();
	{
		result = (jint)inv_read(self, (char*)byteBuf, (int)nBytes);
		(*env)->ReleaseByteArrayElements(env, buf, byteBuf, 0);
	}
	PG_CATCH();
	{
		(*env)->ReleaseByteArrayElements(env, buf, byteBuf, JNI_ABORT);
		Exception_throw_ERROR(env, "inv_read");
	}
	PG_END_TRY();
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _write
 * Signature: ([B)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1write(JNIEnv* env, jobject _this, jbyteArray buf)
{
	jint result = -1;
	LargeObjectDesc* self;
	jbyte* byteBuf;
	jint   nBytes;

	PLJAVA_ENTRY_FENCE(0)
	if(buf == 0)
		return 0;

	nBytes  = (*env)->GetArrayLength(env, buf);
	if(nBytes == 0)
		return 0;

	self = (LargeObjectDesc*)NativeStruct_getStruct(env, _this);
	if(self == 0)
		return 0;

	byteBuf = (*env)->GetByteArrayElements(env, buf, 0);
	if(byteBuf == 0)
		return 0;	/* OutOfMemoryException will be thrown */

	PG_TRY();
	{
		result = (jint)inv_write(self, byteBuf, nBytes);
		
		/* No need to copy bytes back, hence the JNI_ABORT */
		(*env)->ReleaseByteArrayElements(env, buf, byteBuf, JNI_ABORT);
	}
	PG_CATCH();
	{
		(*env)->ReleaseByteArrayElements(env, buf, byteBuf, JNI_ABORT);
		Exception_throw_ERROR(env, "inv_write");
	}
	PG_END_TRY();
	return result;
}
