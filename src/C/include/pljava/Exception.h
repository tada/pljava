/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Exception_h
#define __pljava_Exception_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

/*******************************************************************
 * Java exception related things
 * 
 * @author Thomas Hallgren
 *
 *******************************************************************/

/*
 * Trows an UnsupportedOperationException informing the caller that the
 * requested feature doesn't exist in the current version, it was introduced
 * starting with the intro version.
 */
extern void	Exception_featureNotSupported(JNIEnv* env, const char* requestedFeature, const char* introVersion);

/*
 * Like ereport(ERROR, ...) but this method will raise a Java SQLException and
 * return. It will NOT do a longjmp. Suitable in native code that is called
 * from Java (such code must return to Java in order to have the real exception
 * thrown).
 */
extern void Exception_throw(JNIEnv* env, int errCode, const char* errMessage, ...)
/* This extension allows gcc to check the format string for consistency with
   the supplied arguments. */
__attribute__((format(printf, 3, 4)));

/*
 * Like ereport(ERROR, ...) but this method will raise a Java IllegalArgumentException and
 * return. It will NOT do a longjmp. Suitable in native code that is called
 * from Java (such code must return to Java in order to have the real exception
 * thrown).
 */
extern void Exception_throwIllegalArgument(JNIEnv* env, const char* errMessage, ...)
/* This extension allows gcc to check the format string for consistency with
   the supplied arguments. */
__attribute__((format(printf, 2, 3)));

/*
 * Like ereport(ERROR, ...) but this method will raise a Java SQLException and
 * return. It will NOT do a longjmp.
 */
extern void Exception_throwSPI(JNIEnv* env, const char* function, int errCode);

/*
 * This method will raise a Java ServerException based on an ErrorData obtained
 * by a call to CopyErrorData. It will NOT do a longjmp. It's intended use is
 * in PG_CATCH clauses.
 */
extern void Exception_throw_ERROR(JNIEnv* env, const char* function);

/*
 * Checks if a Java exception has been thrown. If so, a check is made if the
 * exception was a ServerException. If it was, it's contained ErrorData is
 * re-thrown, otherwise, the function ereport(ERROR, ...) is called. There's
 * no return from this method if that happens. This method is called at the
 * completion of each call to a function or trigger.
 */
extern void Exception_checkException(JNIEnv* env);

/*
 * Throw an exception indicating that wanted member could not be
 * found. This is an ereport(ERROR...) so theres' no return from
 * this function.
 */
extern void Exception_throwMemberError(const char* memberName, const char* signature, bool isMethod, bool isStatic);

extern Datum Exception_initialize(PG_FUNCTION_ARGS);

#ifdef __cplusplus
}
#endif
#endif
