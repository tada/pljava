/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_Backend_h
#define __pljava_Backend_h

#include "pljava/Function.h"

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Backend contains the call handler, initialization of the
 * PL/Java, access to config variables, and logging.
 * 
 * @author Thomas Hallgren
 *****************************************************************/
extern bool integerDateTimes;

extern void Backend_assertConnect(void);

extern void Backend_assertDisconnect(void);

extern void Backend_pushCallContext(CallContext* ctx, bool trusted);

extern void Backend_popCallContext(void);

extern void Backend_pushJavaFrame(void);

extern void Backend_popJavaFrame(void);

#ifdef __cplusplus
}
#endif

#endif /* !__pljava_Backend_h */
