/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_PgObject_h
#define __pljava_PgObject_h

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * The PgObject class is the abstract base class for all classes in the
 * Pl/Java system. It provides the basic instance to class mapping,
 * a virtual destructor, and some convenience methods used when finding
 * Java classes and their members.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/
struct PgObject_;
typedef struct PgObject_* PgObject;

struct PgObjectClass_;
typedef struct PgObjectClass_* PgObjectClass;

/*
 * Calles the virtual finalizer and deallocates memory occupided by the
 * PgObject structure.
 */
extern void PgObject_free(PgObject object);

/*
 * Obtains a java class. Calls elog(ERROR, ...) on failure so that
 * there is no return if the method fails.
 */
extern jclass PgObject_getJavaClass(JNIEnv* env, const char* className);

/*
 * Obtains a java method. Calls elog(ERROR, ...) on failure so that
 * there is no return if the method fails.
 */
extern jmethodID PgObject_getJavaMethod(JNIEnv* env, jclass cls, const char* methodName, const char* signature);

/*
 * Obtains a static java method. Calls elog(ERROR, ...) on failure so that
 * there is no return if the method fails.
 */
extern jmethodID PgObject_getStaticJavaMethod(JNIEnv* env, jclass cls, const char* methodName, const char* signature);

/*
 * Obtain a HeapTuple from the system cache and throw an excption
 * on failure.
 */
extern HeapTuple PgObject_getValidTuple(int cacheId, Oid tupleId, const char* tupleType);

/*
 * Obtains a java field. Calls elog(ERROR, ...) on failure so that
 * there is no return if the method fails.
 */
extern jfieldID PgObject_getJavaField(JNIEnv* env, jclass cls, const char* fieldName, const char* signature);

extern jobject PgObject_newJavaObject(JNIEnv* env, jclass cls, jmethodID ctor, ...);

/*
 * Obtains a static java field. Calls elog(ERROR, ...) on failure so that
 * there is no return if the method fails.
 */
extern jfieldID PgObject_getStaticJavaField(JNIEnv* env, jclass cls, const char* fieldName, const char* signature);

/*
 * Register native methods with a class. Last entry in the methods array must
 * have all values set to NULL.
 */
extern void PgObject_registerNatives(JNIEnv* env, const char* className, JNINativeMethod* methods);

extern void PgObject_registerNatives2(JNIEnv* env, jclass cls, JNINativeMethod* methods);

#ifdef __cplusplus
}
#endif
#endif
