/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_type_Type_priv_h
#define __pljava_type_Type_priv_h

#include "pljava/PgObject_priv.h"
#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/* This is the "abstract" Type class. The Type is responsible for
 * value coercsions between Java types and PostGreSQL types.
 */
struct TypeClass_
{
	struct PgObjectClass_ extendedClass;

	/*
	 * Contains the JNI compliant signature for the type.
	 */
	const char* JNISignature;
	
	/*
	 * Contains the Java type name.
	 */
	const char* javaTypeName;

	/*
	 * Points to the object type that corresponds to this type
	 * if this type is a primitive. For non primitives, this attribute
	 * will be NULL.
	 */
	Type objectType;

	/*
	 * Returns true if this type uses the same postgres type the other type.
	 * This is used when explicit java signatures are declared functions to
	 * verify that the declared Java type is compatible with the SQL type.
	 * 
	 * At present, the type argument must be either equal to self, or if
	 * self is a Boolean, Character, or any Number, the primitive that
	 * corresponds to that number (i.e. java.lang.Short == short).
	 */
	bool (*canReplaceType)(Type self, Type type);

	/*
	 * Translate a given Datum into a jvalue accorging to the type represented
	 * by this instance.
	 */
	jvalue (*coerceDatum)(Type self, JNIEnv* env, Datum datum);

	/*
	 * Translate a given Object into a Datum accorging to the type represented
	 * by this instance.
	 */
	Datum (*coerceObject)(Type self, JNIEnv* env, jobject object);

	/*
	 * Calls a java method using one of the Call<type>MethodA routines where
	 * <type> corresponds to the type represented by this instance and
	 * coerces the returned value into a Datum.
	 * 
	 * The method will set the value pointed to by the wasNull parameter
	 * to true if the Java method returned null. The method expects that
	 * the wasNull parameter is set to false by the caller prior to the
	 * call.
	 */
	Datum (*invoke)(Type self, JNIEnv* env, jclass clazz, jmethodID method, jvalue* args, PG_FUNCTION_ARGS);
};

struct Type_
{
	TypeClass m_class;
	
	Oid m_oid;
};

/*
 * Default version of canReplaceType. Returns true when
 * self and other are equal.
 */
extern bool _Type_canReplaceType(Type self, Type other);

/*
 * Default version of invoke. Will make a JNI CallObjectMethod call and then
 * a call to self->coerceObject to create the Datum.
 */
extern Datum _Type_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS);

/*
 * Create a TypeClass with default sizes for TypeClass and Type.
 */
extern TypeClass TypeClass_alloc(const char* className);

/*
 * Create a TypeClass for a specific TypeClass size and a specific Type size.
 */
extern TypeClass TypeClass_alloc2(const char* className, Size classSize, Size instanceSize);

/*
 * Types are always allocated in global context.
 */
extern Type TypeClass_allocInstance(TypeClass cls, Oid typeId);

#ifdef __cplusplus
}
#endif
#endif
/*
Yet to implement
LOG:  Type name = 'abstime'
LOG:  Type name = 'box'
LOG:  Type name = 'cid'
LOG:  Type name = 'lseg'
LOG:  Type name = 'path'
LOG:  Type name = 'point'
LOG:  Type name = 'reltime'
LOG:  Type name = 'tid'
LOG:  Type name = 'tinterval'
LOG:  Type name = 'xid'
*/
