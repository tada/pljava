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
#ifndef __pljava_TriggerData_h
#define __pljava_TriggerData_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

#include <commands/trigger.h>

/**********************************************************************
 * The TriggerData java class provides JNI
 * access to some of the attributes of the TriggerData structure.
 * 
 * @author Thomas Hallgren
 **********************************************************************/
 
/*
 * Create the org.postgresql.pljava.TriggerData object.
 */
extern jobject pljava_TriggerData_create(TriggerData* triggerData);

/*
 * Obtains the returned Tuple after trigger has been processed.
 * Note: starting with PG 10, it is the caller's responsibility to ensure SPI
 * is connected (and that a longer-lived memory context than SPI's is selected,
 * if the caller wants the result to survive SPI_finish).
 */
extern HeapTuple pljava_TriggerData_getTriggerReturnTuple(
	jobject jtd, bool* wasNull);
extern void pljava_TriggerData_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
