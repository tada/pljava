/*
 * Copyright (c) 2003, 2004 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT.
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
 * @author Thomas Hallgren
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
