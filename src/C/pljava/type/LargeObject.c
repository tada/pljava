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
jobject LargeObject_create(LargeObjectDesc* lo)
{
	jobject jlo;
	if(lo == 0)
		return 0;

	jlo = MemoryContext_lookupNative(lo);
	if(jlo == 0)
	{
		jlo = JNI_newObject(s_LargeObject_class, s_LargeObject_init);
		JavaHandle_init(jlo, lo);
	}
	return jlo;
}

static jvalue _LargeObject_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	result.l = LargeObject_create((LargeObjectDesc*)DatumGetPointer(arg));
	return result;
}

static Type LargeObject_obtain(Oid typeId)
{
	return s_LargeObject;
}

extern void LargeObject_initialize(void);
void LargeObject_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_create",
	  	"(I)Lorg/postgresql/pljava/internal/Oid;",
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

	s_LargeObject_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/internal/LargeObject"));
	PgObject_registerNatives2(s_LargeObject_class, methods);
	s_LargeObject_init = PgObject_getJavaMethod(s_LargeObject_class, "<init>", "()V");

	s_LargeObjectClass = JavaHandleClass_alloc("type.LargeObject");
	s_LargeObjectClass->JNISignature   = "Lorg/postgresql/pljava/internal/LargeObject;";
	s_LargeObjectClass->javaTypeName   = "org.postgresql.pljava.internal.LargeObject";
	s_LargeObjectClass->coerceDatum    = _LargeObject_coerceDatum;
	s_LargeObject = TypeClass_allocInstance(s_LargeObjectClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.LargeObject", LargeObject_obtain);
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
	jobject result = 0;

	BEGIN_NATIVE
	PG_TRY();
	{
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 0)
		LargeObjectDesc* lo = inv_create((int)flags);
		result = Oid_create(lo->id);
		pfree(lo);
#else
		result = Oid_create(inv_create((int)flags));
#endif
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("inv_create");
	}
	PG_END_TRY();
	END_NATIVE

	return result;
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
	BEGIN_NATIVE
	PG_TRY();
	{
		result = inv_drop(Oid_getOid(oid));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("inv_drop");
	}
	PG_END_TRY();
	END_NATIVE
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
	jobject result = 0;
	BEGIN_NATIVE
	PG_TRY();
	{
		result = LargeObject_create(inv_open(Oid_getOid(oid), (int)flags));
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("inv_open");
	}
	PG_END_TRY();
	END_NATIVE
	return result;
}


/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _close
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1close(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE
	LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
	if(self != 0)
	{
		PG_TRY();
		{
			inv_close(self);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("inv_close");
		}
		PG_END_TRY();
	}
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_LargeObject
 * Method:    _getId
 * Signature: ()Lorg/postgresql/pljava/internal/Oid;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_LargeObject__1getId(JNIEnv* env, jobject _this)
{
	jobject result = 0;
	BEGIN_NATIVE
	LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
	if(self != 0)
		result = Oid_create(self->id);
	END_NATIVE
	return result;
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
	BEGIN_NATIVE
	LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
	if(self != 0)
	{
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
			Exception_throw_ERROR("inv_seek");
		}
		PG_END_TRY();
	}
	END_NATIVE
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
	BEGIN_NATIVE
	LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
	if(self != 0)
	{
		PG_TRY();
		{
			result = (jlong)inv_seek(self, (int)pos, (int)whence);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("inv_seek");
		}
		PG_END_TRY();
	}
	END_NATIVE
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
	BEGIN_NATIVE
	LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
	if(self != 0)
	{
		PG_TRY();
		{
			result = (jlong)inv_tell(self);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("inv_tell");
		}
		PG_END_TRY();
	}
	END_NATIVE
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
	BEGIN_NATIVE

	if(buf != 0)
	{
		jint nBytes = JNI_getArrayLength(buf);
		if(nBytes != 0)
		{
			LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
			if(self != 0)
			{
				jbyte* byteBuf = JNI_getByteArrayElements(buf, 0);
				if(byteBuf != 0)
				{
					PG_TRY();
					{
						result = (jint)inv_read(self, (char*)byteBuf, (int)nBytes);
						JNI_releaseByteArrayElements(buf, byteBuf, 0);
					}
					PG_CATCH();
					{
						JNI_releaseByteArrayElements(buf, byteBuf, JNI_ABORT);
						Exception_throw_ERROR("inv_read");
					}
					PG_END_TRY();
				}
			}
		}
	}
	END_NATIVE
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
	BEGIN_NATIVE

	if(buf != 0)
	{
		jint nBytes = JNI_getArrayLength(buf);
		if(nBytes != 0)
		{
			LargeObjectDesc* self = (LargeObjectDesc*)JavaHandle_getStruct(_this);
			if(self != 0)
			{
				jbyte* byteBuf = JNI_getByteArrayElements(buf, 0);
				if(byteBuf != 0)
				{
					PG_TRY();
					{
						result = (jint)inv_write(self, byteBuf, nBytes);
						
						/* No need to copy bytes back, hence the JNI_ABORT */
						JNI_releaseByteArrayElements(buf, byteBuf, JNI_ABORT);
					}
					PG_CATCH();
					{
						JNI_releaseByteArrayElements(buf, byteBuf, JNI_ABORT);
						Exception_throw_ERROR("inv_write");
					}
					PG_END_TRY();
				}
			}
		}
	}
	END_NATIVE
	return result;
}
