/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_pljava_h
#define __pljava_pljava_h

#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

#include <postgres.h>
#include <lib/stringinfo.h>
#include <fmgr.h>
#include <mb/pg_wchar.h>
#include <utils/syscache.h>
#include <utils/memutils.h>

#ifdef USE_THREADS
/* Mutex declarations for critical sections
 */
#include <pthread.h>
extern pthread_t pljava_mainThread;

#define THREAD_FENCE(retVal) if(!pthread_equal(pthread_self(), pljava_mainThread)) \
{ Exception_threadException(env);  return retVal; }

#define THREAD_FENCE_VOID if(!pthread_equal(pthread_self(), pljava_mainThread)) \
{ Exception_threadException(env); return; }

#define DECLARE_MUTEX(mutexName) static pthread_mutex_t mutexName = PTHREAD_MUTEX_INITIALIZER;
#define BEGIN_CRITICAL(mutexName) pthread_mutex_lock(&mutexName);
#define END_CRITICAL(mutexName) pthread_mutex_unlock(&mutexName);
#else
#define THREAD_FENCE(retVal)
#define THREAD_FENCE_VOID
#define DECLARE_MUTEX(mutexName)
#define BEGIN_CRITICAL(mutexName)
#define END_CRITICAL(mutexName)

#endif


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
	};
} Ptr2Long;

#ifdef __cplusplus
}
#endif
#endif
