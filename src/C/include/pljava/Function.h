/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
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
 * Author: Thomas Hallgren
 *
 *******************************************************************/
struct Function_;
typedef struct Function_* Function;

/*
 * Get a Function using a function Oid. If the function is not found, one
 * will be created based on the class and method name denoted in the "AS"
 * clause, the parameter types, and the return value of the function
 * description. If "isTrigger" is set to true, the parameter type and
 * return value of the function will be fixed to:
 * 
 * org.postgresql.pljava.Tuple <method name>(org.postgresql.pljava.TriggerData td)
 */
extern Function Function_getFunction(JNIEnv* env, Oid functionId, bool isTrigger);

/*
 * Invoke a function, i.e. coerce the parameters, call the java method, and
 * coerce the return value back to a Datum.
 */
extern Datum Function_invoke(Function self, JNIEnv* env, PG_FUNCTION_ARGS);

/*
 * Invoke a trigger. Wrap the TriggerData in org.postgresql.pljava.TriggerData
 * object, make the call, and unwrap the resulting Tuple.
 */
extern Datum Function_invokeTrigger(Function self, JNIEnv* env, PG_FUNCTION_ARGS);

/*
 * Initialize the Function class.
 */
extern Datum Function_initialize(PG_FUNCTION_ARGS);

#ifdef __cplusplus
}
#endif
#endif
