/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_type_Timestamp_h
#define __pljava_type_Timestamp_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

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
