/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_AclId_h
#define __pljava_type_AclId_h

#include "pljava/PgObject.h"

#ifdef __cplusplus
extern "C" {
#endif

/***********************************************************************
 * ACL related stuff.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/
extern jobject AclId_create(JNIEnv* env, AclId aclId);

extern AclId AclId_getAclId(JNIEnv* env, jobject aclId);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
