/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_Type_h
#define __pljava_type_Type_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <catalog/pg_type.h>

/*********************************************************************
 * The Type class is responsible for data type conversions between the
 * Postgres Datum and the Java jvalue. A Type can also perform optimized
 * JNI calls that are type dependent (returning primitives) such as
 * CallIntMethod(...) or CallBooleanMethod(...). Consequently, the Type
 * of the return value of a function is responsible for its invocation.
 * 
 * Types that are not mapped will default to a java.lang.String mapping
 * and use the Form_pg_type text conversion routines.
 * 
 * @author Thomas Hallgren
 *
 *********************************************************************/
struct Type_;
typedef struct Type_* Type;

struct TypeClass_;
typedef struct TypeClass_* TypeClass;

/*
 * Returns true if the Type is primitive (i.e. not a real object in
 * the Java domain).
 */
extern bool Type_isPrimitive(Type self);

/*
 * Returns true if this type uses the same postgres type the other type.
 * This is used when explicit java signatures are declared functions to
 * verify that the declared Java type is compatible with the SQL type.
 * 
 * At present, the type argument must be either equal to self, or if
 * self is a Boolean, Character, or any Number, the primitive that
 * corresponds to that number (i.e. java.lang.Short == short).
 */
extern bool Type_canReplaceType(Type self, Type type);

/*
 * Translate a given Datum into a jvalue accorging to the type represented
 * by this instance.
 */
extern jvalue Type_coerceDatum(Type self, Datum datum);

/*
 * Translate a given Object into a Datum accorging to the type represented
 * by this instance.
 */
extern Datum Type_coerceObject(Type self, jobject object);

/*
 * Return a Type based on a Postgres Oid.
 */
extern Type Type_fromPgType(Oid typeId, Form_pg_type pgType);

/*
 * Return a Type based on a Postgres Oid.
 */
extern Type Type_fromOid(Oid typeId);

/*
 * Return a Type based on a PostgreSQL Oid. If the found
 * type is a primitive, return it's object corresponcance
 */
extern Type Type_objectTypeFromOid(Oid typeId);

/*
 * Return a Type based on a default SQL type and a java type name.
 */
extern Type Type_fromJavaType(Oid dfltType, const char* javaTypeName);

/*
 * Returns the Java type name for the Type.
 */
extern const char* Type_getJavaTypeName(Type self);

/*
 * Returns the JNI signature for the Type.
 */
extern const char* Type_getJNISignature(Type self);

/*
 * Returns the object Type if the type is primitive and NULL if not.
 */
extern Type Type_getObjectType(Type self);

/*
 * Returns the Oid associated with this type.
 */
extern Oid Type_getOid(Type self);

/*
 * Returns the TupleDesc associated with this type.
 */
extern TupleDesc Type_getTupleDesc(Type self, PG_FUNCTION_ARGS);

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
extern Datum Type_invoke(Type self, jclass clazz, jmethodID method, jvalue* args, PG_FUNCTION_ARGS);

/*
 * Function used when obtaining a type based on an Oid
 * structure. In most cases, this function should return a
 * singleton. The only current exception from this is the
 * String since it makes use of functions stored in the
 * Form_pg_type structure.
 */
typedef Type (*TypeObtainer)(Oid typeId);

/*
 * Register this type as the default mapping for a postgres type.
 */
extern void Type_registerType(Oid typeId, const char* javaTypeName, TypeObtainer obtainer);

#ifdef __cplusplus
}
#endif
#endif
