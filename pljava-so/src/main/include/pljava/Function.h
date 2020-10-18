/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
#ifndef __pljava_Function_h
#define __pljava_Function_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <commands/trigger.h>

/*******************************************************************
 * The Function instance is the most central class of the Pl/Java
 * system. Parsing of the "AS" (later to become "EXTERNAL NAME")
 * and class/method lookups is done here.
 * 
 * A Function instance knows how to coerce all Datum parameters into
 * their Java equivalent, how to call its assocated Java class and
 * method and how to coerce the Java return value into a Datum.
 * 
 * Functions are cached using their Oid. They live in TopMemoryContext.
 * 
 * @author Thomas Hallgren
 *
 *******************************************************************/

/*
 * Clear all cached function to method entries. This is called after a
 * successful replace_jar operation.
 */
extern void Function_clearFunctionCache(void);

/*
 * Get a Function using a function Oid. If the function is not found, one
 * will be created based on the class and method name denoted in the "AS"
 * clause, the parameter types, and the return value of the function
 * description. If "forTrigger" is true, the parameter type and
 * return value of the function will be fixed to:
 * void <method name>(org.postgresql.pljava.TriggerData td)
 *
 * If forValidator is true, forTrigger is disregarded, and will be determined
 * from the function's pg_proc entry. If forValidator is false, checkBody has no
 * meaning.
 */
extern Function Function_getFunction(
	Oid funcOid, bool trusted, bool forTrigger,
	bool forValidator, bool checkBody);

/*
 * Determine whether the type represented by typeId is declared as a
 * "Java-based scalar" a/k/a BaseUDT and, if so, return a freshly-registered
 * UDT Type for it; otherwise return NULL.
 */
extern Type Function_checkTypeBaseUDT(Oid typeId, Form_pg_type typeStruct);

/*
 * Invoke a trigger. Wrap the TriggerData in org.postgresql.pljava.TriggerData
 * object, make the call, and unwrap the resulting Tuple.
 */
extern Datum Function_invokeTrigger(Function self, PG_FUNCTION_ARGS);

/*
 * Invoke a function, i.e. coerce the parameters, call the java method, and
 * coerce the return value back to a Datum. The return-value coercion is handled
 * by a convention where this call will delegate to the Type representing the
 * SQL return type. That will call back on one of the flavors of fooInvoke below
 * corresponding to the return type of the Java method, and then coerce that to
 * the intended SQL type.
 */
extern Datum Function_invoke(Function self, PG_FUNCTION_ARGS);

/*
 * Most slots in the parameter area are set directly in invoke() or
 * invokeTrigger() above. The only caller of this is Composite_invoke, which
 * needs to set one parameter (always the last one, and a reference type).
 * So this function, though with an API that could be general, for now only
 * handles the case where index is -1 and the last parameter has reference type.
 */
extern void pljava_Function_setParameter(Function self, int idx, jvalue val);

/*
 * Not intended for any caller other than Invocation_popInvocation.
 */
extern void pljava_Function_popFrame(void);

/*
 * These actually invoke a target Java method (returning, respectively, a
 * reference type or one of the Java primitive types). The arguments to the
 * method have already been coerced, and segregated into reference types (stored
 * in the Object array references) and primitives (stored in a C array of jvalue
 * covered by a direct byte buffer, primitives).
 */
extern jobject pljava_Function_refInvoke(Function self);
extern void pljava_Function_voidInvoke(Function self);
extern jboolean pljava_Function_booleanInvoke(Function self);
extern jbyte pljava_Function_byteInvoke(Function self);
extern jshort pljava_Function_shortInvoke(Function self);
extern jchar pljava_Function_charInvoke(Function self);
extern jint pljava_Function_intInvoke(Function self);
extern jfloat pljava_Function_floatInvoke(Function self);
extern jlong pljava_Function_longInvoke(Function self);
extern jdouble pljava_Function_doubleInvoke(Function self);

/*
 * Call the invocable that was returned by the invocation of a set-returning
 * user function that observes the SFRM_ValuePerCall protocol. Call with
 * close == JNI_FALSE to retrieve the next row if any, JNI_TRUE when done (which
 * may be before all rows have been retrieved). Returns JNI_TRUE/JNI_FALSE to
 * indicate whether a row was retrieved, AND puts a value (or null) in *result.
 */
extern jboolean pljava_Function_vpcInvoke(
	jobject invocable, jobject rowcollect, jlong call_cntr, jboolean close,
	jobject *result);

extern void pljava_Function_udtWriteInvoke(
	jobject invocable, jobject value, jobject stream);
extern jstring pljava_Function_udtToStringInvoke(
	jobject invocable, jobject value);
extern jobject pljava_Function_udtReadInvoke(
	jobject invocable, jobject stream, jstring typeName);
extern jobject pljava_Function_udtParseInvoke(
	jobject invocable, jstring stringRep, jstring typeName);

extern jobject pljava_Function_udtWriteHandle(
	jclass clazz, char *langName, bool trusted);
extern jobject pljava_Function_udtToStringHandle(
	jclass clazz, char *langName, bool trusted);
extern jobject pljava_Function_udtReadHandle(
	jclass clazz, char *langName, bool trusted);
extern jobject pljava_Function_udtParseHandle(
	jclass clazz, char *langName, bool trusted);

/*
 * Returns the Type Map that is associated with the function
 */
extern jobject Function_getTypeMap(Function self);

/*
 * Returns true if the currently executing function is non volatile, i.e. stable
 * or immutable. Such functions are not allowed to have side effects.
 */
extern bool Function_isCurrentReadOnly(void);

/*
 * Return a local reference to the initiating (schema) class loader used to load
 * the currently-executing function, or NULL if there is no currently-executing
 * function or the schema loaders have been cleared and that loader is gone.
 */
extern jobject Function_currentLoader(void);

/*
 * A nameless Function singleton with the property ! isCurrentReadOnly()
 */
extern Function Function_INIT_WRITER;

#ifdef __cplusplus
}
#endif
#endif
