/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
 */
#ifndef __pljava_type_Timestamp_h
#define __pljava_type_Timestamp_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The Timestamp java class represents the native Timestamp.
 * 
 * @author Thomas Hallgren
 *****************************************************************/
 
#include <utils/timestamp.h>

/*
 * Returns the current timezone.
 */
extern int Timestamp_getCurrentTimeZone(void);

/*
 * Returns the timezone fo the given Timestamp.
 */
extern int Timestamp_getTimeZone(Timestamp t);

#ifdef __cplusplus
}
#endif
#endif
