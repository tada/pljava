/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_TriggerData_h
#define __pljava_TriggerData_h

#include "pljava/type/NativeStruct.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <commands/trigger.h>

/**********************************************************************
 * The TriggerData java class extends the NativeStruct and provides JNI
 * access to some of the attributes of the TriggerData structure.
 * 
 * Author: Thomas Hallgren
 **********************************************************************/
 
/*
 * Create the org.postgresql.pljava.TriggerData object.
 */
extern jobject TriggerData_create(JNIEnv* env, TriggerData* triggerData);

/*
 * Obtains the returned Tuple after trigger has been processed.
 */
extern Datum TriggerData_getTriggerReturnTuple(JNIEnv* env, jobject jtd, bool* wasNull);

#ifdef __cplusplus
}
#endif
#endif
