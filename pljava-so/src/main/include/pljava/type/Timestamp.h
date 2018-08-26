/*
 * Copyright (c) 2004-2018 Tada AB and other contributors, as listed below.
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
 
/*
 * Returns the current timezone.
 */
extern int Timestamp_getCurrentTimeZone(void);

/*
 * Returns the timezone for the given Timestamp. This is an internal function
 * and only declared here because Date.c uses it, and always this int64 variant,
 * regardless of whether the backend was compiled with integer datetimes. The
 * argument is not a PostgreSQL int64 Timestamp, but rather a PostgreSQL int64
 * Timestamp divided by two. The result is a time zone offset in seconds west
 * of Greenwich.
 */
extern int32 Timestamp_getTimeZone_id(int64 t);

#ifdef __cplusplus
}
#endif
#endif
