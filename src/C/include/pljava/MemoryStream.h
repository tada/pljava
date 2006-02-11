/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_MemoryStream_h
#define __pljava_MemoryStream_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * MemoryStream related stuff.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/
#include <lib/stringinfo.h>

jobject MemoryStream_createOutputStream(StringInfo buffer);
void MemoryStream_closeOutputStream(jobject outputStream);

jobject MemoryStream_createInputStream(void* data, size_t dataSize);
void MemoryStream_closeInputStream(jobject object);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
