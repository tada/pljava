/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
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

#if (!(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 0))
typedef Oid AclId;
#endif

/***********************************************************************
 * ACL related stuff.
 * 
 * @author Thomas Hallgren
 *
 ***********************************************************************/
extern jobject AclId_create(AclId aclId);

extern AclId AclId_getAclId(jobject aclId);

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
