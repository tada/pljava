/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 * 
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_ErrorData.h"
#include "pljava/Exception.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/ErrorData.h"
#include "pljava/type/String.h"

static TypeClass s_ErrorDataClass;
static Type      s_ErrorData;
static jclass    s_ErrorData_class;
static jmethodID s_ErrorData_init;

/*
 * org.postgresql.pljava.type.Tuple type.
 */
jobject ErrorData_create(JNIEnv* env, ErrorData* ed)
{
	jobject jed;
	if(ed == 0)
		return 0;

	jed = MemoryContext_lookupNative(env, ed);
	if(jed == 0)
	{
		jed = PgObject_newJavaObject(env, s_ErrorData_class, s_ErrorData_init);
		NativeStruct_init(env, jed, ed);
	}
	return jed;
}

ErrorData* ErrorData_getErrorData(JNIEnv* env, jobject jed)
{
	return (ErrorData*)NativeStruct_getStruct(env, jed);
}
	
static jvalue _ErrorData_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	result.l = ErrorData_create(env, (ErrorData*)DatumGetPointer(arg));
	return result;
}

static Type ErrorData_obtain(Oid typeId)
{
	return s_ErrorData;
}

/* Make this datatype available to the postgres system.
 */
extern Datum ErrorData_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(ErrorData_initialize);
Datum ErrorData_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"getErrorLevel",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_ErrorData_getErrorLevel
		},
		{
		"isOutputToServer",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_ErrorData_isOutputToServer
		},
		{
		"isOutputToClient",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_ErrorData_isOutputToClient
		},
		{
		"isShowFuncname",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_ErrorData_isShowFuncname
		},
		{
		"getFilename",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getFilename
		},
		{
		"getLineno",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_ErrorData_getLineno
		},
		{
		"getFuncname",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getFuncname
		},
		{
		"getSqlState",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getSqlState
		},
		{
		"getMessage",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getMessage
		},
		{
		"getDetail",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getDetail
		},
		{
		"getHint",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getHint
		},
		{
		"getContextMessage",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getContextMessage
		},
		{
		"getCursorPos",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_ErrorData_getCursorPos
		},
		{
		"getInternalPos",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_ErrorData_getInternalPos
		},
		{
		"getInternalQuery",
	  	"()Ljava/lang/String;",
	  	Java_org_postgresql_pljava_internal_ErrorData_getInternalQuery
		},
		{
		"getSavedErrno",
	  	"()I",
	  	Java_org_postgresql_pljava_internal_ErrorData_getSavedErrno
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_ErrorData_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/ErrorData"));

	PgObject_registerNatives2(env, s_ErrorData_class, methods);

	s_ErrorData_init = PgObject_getJavaMethod(
				env, s_ErrorData_class, "<init>", "()V");

	s_ErrorDataClass = NativeStructClass_alloc("type.ErrorData");
	s_ErrorDataClass->JNISignature   = "Lorg/postgresql/pljava/internal/ErrorData;";
	s_ErrorDataClass->javaTypeName   = "org.postgresql.pljava.internal.ErrorData";
	s_ErrorDataClass->coerceDatum    = _ErrorData_coerceDatum;
	s_ErrorData = TypeClass_allocInstance(s_ErrorDataClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.internal.ErrorData", ErrorData_obtain);

	PG_RETURN_VOID();
}

/****************************************
 * JNI methods
 ****************************************/

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getErrorLevel
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_ErrorData_getErrorLevel(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : (jint)ed->elevel;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getMessage
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getMessage(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->message);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getSqlState
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getSqlState(JNIEnv* env, jobject _this)
{
	char buf[6];
	int errCode;
	int idx;
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	if(ed == 0)
		return 0;

	/* unpack MAKE_SQLSTATE code
	 */
	errCode = ed->sqlerrcode;
	for (idx = 0; idx < 5; ++idx)
	{
		buf[idx] = PGUNSIXBIT(errCode);
		errCode >>= 6;
	}
	buf[idx] = 0;
	return String_createJavaStringFromNTS(env, buf);
}

#if (PGSQL_MAJOR_VER >= 8)

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToServer
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isOutputToServer(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? JNI_FALSE : (jboolean)ed->output_to_server;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToClient
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isOutputToClient(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? JNI_FALSE : (jboolean)ed->output_to_client;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isShowFuncname
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isShowFuncname(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? JNI_FALSE : (jboolean)ed->show_funcname;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFilename
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getFilename(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->filename);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getLineno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getLineno(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : (jint)ed->lineno;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFuncname
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getFuncname(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->funcname);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getDetail
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getDetail(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->detail);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getHint
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getHint(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->hint);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getContextMessage
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getContextMessage(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->context);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getCursorPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getCursorPos(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : (jint)ed->cursorpos;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getInternalPos(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : (jint)ed->internalpos;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalQuery
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getInternalQuery(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : String_createJavaStringFromNTS(env, ed->internalquery);
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getSavedErrno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getSavedErrno(JNIEnv* env, jobject _this)
{
	ErrorData* ed = (ErrorData*)NativeStruct_getStruct(env, _this);
	return (ed == 0) ? 0 : (jint)ed->saved_errno;
}

#else
/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToServer
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isOutputToServer(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.isOutputToServer", "8.0");
	return JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isOutputToClient
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isOutputToClient(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.isOutputToClient", "8.0");
	return JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    isShowFuncname
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_postgresql_pljava_internal_ErrorData_isShowFuncname(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.isShowFuncname", "8.0");
	return JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFilename
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getFilename(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getFilename", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getLineno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getLineno(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getLineno", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getFuncname
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getFuncname(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getFuncname", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getDetail
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getDetail(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getDetail", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getHint
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getHint(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getHint", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getContextMessage
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getContextMessage(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getContextMessage", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getCursorPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getCursorPos(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getCursorPos", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalPos
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getInternalPos(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getInternalPos", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getInternalQuery
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_postgresql_pljava_internal_ErrorData_getInternalQuery(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getInternalQuery", "8.0");
	return 0;
}

/*
 * Class:     org_postgresql_pljava_internal_ErrorData
 * Method:    getSavedErrno
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_postgresql_pljava_internal_ErrorData_getSavedErrno(JNIEnv* env, jobject _this)
{
	Exception_featureNotSupported(env, "org.postgresql.pljava.internal.ErrorData.getSavedErrno", "8.0");
	return 0;
}
#endif
