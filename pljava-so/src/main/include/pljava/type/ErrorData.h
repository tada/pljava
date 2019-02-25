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
#ifndef __pljava_ErrorData_h
#define __pljava_ErrorData_h

#ifdef __cplusplus
extern "C" {
#endif

/*****************************************************************
 * The ErrorData java class represents the native ErrorData.
 * 
 * @author Thomas Hallgren
 *****************************************************************/

/*
 * Create the org.postgresql.pljava.internal.ErrorData that represents
 * the current error obtaind from CopyErrorData().
 */
extern jobject pljava_ErrorData_getCurrentError(void);

/*
 * Extract the native ErrorData from a Java ErrorData.
 */
extern ErrorData* pljava_ErrorData_getErrorData(jobject jerrorData);
extern void pljava_ErrorData_initialize(void);

#ifdef __cplusplus
}
#endif
#endif
