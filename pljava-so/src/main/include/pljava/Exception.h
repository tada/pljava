/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
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
 * Tests whether ex is an instance of UnhandledPGException, an SQLException
 * subclass that is created when an attempted call into PostgreSQL internals
 * cannot be made because of an earlier unhandled ServerException.
 * An UnhandledPGException will have, as its cause, the earlier ServerException.
 */
extern bool Exception_isPGUnhandled(jthrowable ex);

/*
 * Throws an UnsupportedOperationException informing the caller that the
 * requested feature doesn't exist in the current version, it was introduced
 * starting with the intro version.
 */
extern void	Exception_featureNotSupported(const char* requestedFeature, const char* introVersion);

/*
 * Like ereport(ERROR, ...) but this method will raise a Java SQLException and
 * return. It will NOT do a longjmp. Suitable in native code that is called
 * from Java (such code must return to Java in order to have the real exception
 * thrown).
 */
extern void Exception_throw(int errCode, const char* errMessage, ...)
/* This extension allows gcc to check the format string for consistency with
   the supplied arguments. */
pg_attribute_printf(2, 3);

/*
 * Like ereport(ERROR, ...) but this method will raise a Java IllegalArgumentException and
 * return. It will NOT do a longjmp. Suitable in native code that is called
 * from Java (such code must return to Java in order to have the real exception
 * thrown).
 */
extern void Exception_throwIllegalArgument(const char* errMessage, ...)
/* This extension allows gcc to check the format string for consistency with
   the supplied arguments. */
pg_attribute_printf(1, 2);

/*
 * Like ereport(ERROR, ...) but this method will raise a Java SQLException and
 * return. It will NOT do a longjmp.
 */
extern void Exception_throwSPI(const char* function, int errCode);

/*
 * This method will raise a Java ServerException based on an ErrorData obtained
 * by a call to CopyErrorData. It will NOT do a longjmp. Its intended use is
 * in PG_CATCH clauses.
 */
extern void Exception_throw_ERROR(const char* function);

/*
 * This method will raise a Java UnhandledPGException based on a ServerException
 * that has been stored at some earlier time and not yet resolved (as by
 * a rollback). Its intended use is from beginNative in JNICalls when
 * errorOccurred is found to be true.
 */
extern void Exception_throw_unhandled(void);

/*
 * Throw an exception indicating that wanted member could not be
 * found. This is an ereport(ERROR...) so theres' no return from
 * this function.
 */
extern void Exception_throwMemberError(const char* memberName, const char* signature, bool isMethod, bool isStatic);

extern jclass NoSuchFieldError_class;
extern jclass NoSuchMethodError_class;

#ifdef __cplusplus
}
#endif
#endif
