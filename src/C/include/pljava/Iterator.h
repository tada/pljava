/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_Iterator_h
#define __pljava_Iterator_h

#include "pljava/HashMap.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * An Iterator that backed by the given HashMap. The
 * Iterator will indicate no more entries if the HashMap grows
 * so that it needs to rehash.
 * 
 * The Iterator is allocated using the same MemoryContext
 * as the HashMap.
 * 
 * Author: Thomas Hallgren
 *
 ***********************************************************************/

/*
 * Creates an Iterator.
 */
extern Iterator Iterator_create(HashMap source);

/*
 * Return true if the Iterator has more entries.
 */
extern bool Iterator_hasNext(Iterator self);

/*
 * Return the next Entry from the backing HashMap or NULL when
 * no more entries exists.
 */
extern Entry Iterator_next(Iterator self);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
