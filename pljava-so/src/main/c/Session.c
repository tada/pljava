/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include <postgres.h>
#include <miscadmin.h>
#include "org_postgresql_pljava_internal_Session.h"
#include "pljava/Session.h"
#include "pljava/type/AclId.h"

/**
 * \addtogroup JNI
 * @{
 */

extern void Session_initialize(void);
void Session_initialize(void)
{
	JNINativeMethod methods[] = {
		{
		"_setUser",
		"(Lorg/postgresql/pljava/internal/AclId;Z)Z",
	  	Java_org_postgresql_pljava_internal_Session__1setUser
		},
		{ 0, 0, 0 }};

	PgObject_registerNatives("org/postgresql/pljava/internal/Session", methods);
}

/****************************************
 * JNI methods
 ****************************************/
/*
 * Class:     org_postgresql_pljava_internal_Session
 * Method:    _setUser
 * Signature: (Lorg/postgresql/pljava/internal/AclId;Z)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Session__1setUser(
	JNIEnv* env, jclass cls, jobject aclId, jboolean isLocalChange)
{
	bool wasLocalChange = false;
	int secContext;
	Oid dummy;
	/* No error checking since this might be a restore of user in
	 * a finally block after an exception.
	 */
	BEGIN_NATIVE_NO_ERRCHECK
	if (InSecurityRestrictedOperation())
		ereport(ERROR,	(errcode(ERRCODE_INSUFFICIENT_PRIVILEGE), errmsg(
			"cannot set parameter \"%s\" within security-restricted operation",
			"role")));
	GetUserIdAndSecContext(&dummy, &secContext);
	wasLocalChange = 0 != ( secContext & SECURITY_LOCAL_USERID_CHANGE );
	if ( isLocalChange )
		secContext |= SECURITY_LOCAL_USERID_CHANGE;
	else
		secContext &= ~SECURITY_LOCAL_USERID_CHANGE;
	SetUserIdAndSecContext(AclId_getAclId(aclId), secContext);
	END_NATIVE
	return wasLocalChange ? JNI_TRUE : JNI_FALSE;
}
/** @} */
