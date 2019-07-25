/*
 * Copyright (c) 2004-2019 Tada AB and other contributors, as listed below.
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
extern int32 Timestamp_getCurrentTimeZone(void);

/*
 * Calls Java method SPIConnection.utcMasquerade, which see for details.
 */
extern jlong Timestamp_utcMasquerade(jlong msecsFromJavaEpoch, jboolean unmask);

#ifdef __cplusplus
}
#endif
#endif
