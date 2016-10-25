/*
 * Copyright (c) 2004-2016 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#ifndef __pljava_pljava_h
#define __pljava_pljava_h

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * Misc stuff to tie Java to PostgreSQL. TRY/CATCH macros, thread
 * blocking, etc. resides here.
 *
 * @author Thomas Hallgren
 *****************************************************************/

#ifdef __STRICT_ANSI__
extern int vsnprintf(char* buf, size_t count, const char* format, va_list arg);
#endif

#include <postgres.h>
#include <lib/stringinfo.h>
#include <fmgr.h>
#include <mb/pg_wchar.h>
#include <utils/syscache.h>
#include <utils/memutils.h>
#include <tcop/tcopprot.h>

/*
 * AssertVariableIsOfType appeared in PG9.3. Can test for the macro directly.
 * Likewise for StaticAssertStmt.
 */
#ifndef AssertVariableIsOfType
#define AssertVariableIsOfType(varname, typename)
#endif

#ifndef StaticAssertStmt
#define StaticAssertStmt(condition, errmessage)
#endif


/*
 * GETSTRUCT require "access/htup_details.h" to be included in PG9.3
 */
#if PG_VERSION_NUM >= 90300
#include "access/htup_details.h"
#endif

/*
 * PG_*_{MIN,MAX} macros (which happen, conveniently, to match Java's datatypes
 * (the signed ones, anyway), appear in PG 9.5. Could test for them directly,
 * but explicit version conditionals may be easier to find and prune when the
 * back-compatibility horizon passes them. Here are only the ones being used.
 */
#if PG_VERSION_NUM < 90500
#define PG_INT32_MAX    (0x7FFFFFFF)
#endif


/* The errorOccured will be set when a call from Java into one of the
 * backend functions results in a elog that causes a longjmp (Levels >= ERROR)
 * that was trapped using the PLJAVA_TRY/PLJAVA_CATCH macros.
 * When this happens, all further calls from Java must be blocked since the
 * state of the current transaction is unknown. Further more, once the function
 * that initially called Java finally returns, the intended longjmp (the one
 * to the original value of Warn_restart) must be made.
 */
extern jlong mainThreadId;
extern bool pljavaEntryFence(JNIEnv* env);
extern JNIEnv* currentJNIEnv;
extern MemoryContext JavaMemoryContext;

/* The STACK_BASE_PUSH / STACK_BASE_POP macros are used to surround code where
 * a thread will be entering the backend that isn't necessarily the same one
 * that has called into PL/Java from the backend. (This can only occur when the
 * threadlock has been released by a call into PL/Java from whatever thread had
 * been executing in the backend, and the thread that is about to enter the
 * backend now holds the lock.) The backend does proactive checking of stack
 * depth as a precaution, and that check would be incorrect if comparing the
 * current stack pointer to some other thread's stack base. Therefore, these
 * macros will check whether the thread is indeed different from the last one
 * in the backend and, if so, substitute this thread's stack base for the old
 * value, which is then restored by STACK_BASE_POP.
 *
 * Large caution, though: what this WANTS to do is change PG's notion of the
 * BASE of this thread's stack, and that's not at all what it really does;
 * set_stack_base() uses the CURRENT stack position, which could be who knows
 * how deep already, and there is really no way to do better because the JVM
 * doesn't expose the base of a thread's stack anyway (or even drop any hints
 * about how it implements stacks). So, all this fussing around may indeed
 * prevent nuisance stack-overflow aborts caused by comparing pointers to
 * different stacks ... but it may only serve to conceal, or prevent recovery
 * from, actual excessive stack use.
 *
 * stack_base_ptr was static before PG 8.1. By executive decision, PL/Java now
 * has 8.1 as a back compatibility limit; no empty #defines here for earlier.
 */
#if 90104<=PG_VERSION_NUM || \
	90008<=PG_VERSION_NUM && PG_VERSION_NUM<90100 || \
	80412<=PG_VERSION_NUM && PG_VERSION_NUM<90000 || \
	80319<=PG_VERSION_NUM && PG_VERSION_NUM<80400
#define NEED_MISCADMIN_FOR_STACK_BASE
#define _STACK_BASE_TYPE pg_stack_base_t
#define _STACK_BASE_SET saveStackBasePtr = set_stack_base()
#define _STACK_BASE_RESTORE restore_stack_base(saveStackBasePtr)
#else
extern
#if PG_VERSION_NUM < 80300
DLLIMPORT
#else
PGDLLIMPORT
#endif
char* stack_base_ptr;
#define _STACK_BASE_TYPE char*
#define _STACK_BASE_SET \
	saveStackBasePtr = stack_base_ptr; \
	stack_base_ptr = (char*)&saveMainThreadId
#define _STACK_BASE_RESTORE stack_base_ptr = saveStackBasePtr
#endif

#define STACK_BASE_VARS \
	jlong saveMainThreadId = 0; \
	_STACK_BASE_TYPE saveStackBasePtr;

#define STACK_BASE_PUSH(threadId) \
	if(threadId != mainThreadId) \
	{ \
		_STACK_BASE_SET; \
		saveMainThreadId = mainThreadId; \
		mainThreadId = threadId; \
		elog(DEBUG2, "Set stack base for thread " UINT64_FORMAT, \
			(uint64)mainThreadId); \
	}

#define STACK_BASE_POP() \
	if(saveMainThreadId != 0) \
	{ \
		_STACK_BASE_RESTORE; \
		mainThreadId = saveMainThreadId; \
		elog(DEBUG2, "Restored stack base for thread " UINT64_FORMAT, \
			(uint64)mainThreadId); \
	}

/* NOTE!
 * When using the PG_TRY, PG_CATCH, PG_TRY_END family of macros,
 * it is an ABSOLUTE NECESSITY to use the PG_TRY_RETURN or
 * PG_TRY_RETURN_VOID in place of any return.
 */
#define PG_TRY_POP \
	PG_exception_stack = save_exception_stack; \
	error_context_stack = save_context_stack

#define PG_TRY_RETURN(retVal) { PG_TRY_POP; return retVal; }
#define PG_TRY_RETURN_VOID { PG_TRY_POP; return; }

/* Some error codes missing from errcodes.h
 * 
 * Class 07 - Dynamic SQL Exception
 */
#define ERRCODE_INVALID_DESCRIPTOR_INDEX		MAKE_SQLSTATE('0','7', '0','0','9')

/*
 * Union used when coercing void* to jlong and vice versa
 */
typedef union
{
	void*  ptrVal;
	jlong  longVal; /* 64 bit quantity */
	struct
	{
		/* Used when calculating pointer hash in systems where
		 * a pointer is 64 bit
		 */
		uint32 intVal_1;
		uint32 intVal_2;
	} x64;
} Ptr2Long;

struct Invocation_;
typedef struct Invocation_ Invocation;

struct Function_;
typedef struct Function_* Function;

#ifdef __cplusplus
}
#endif
#endif
