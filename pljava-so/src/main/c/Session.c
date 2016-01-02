/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <miscadmin.h>
#include "org_postgresql_pljava_internal_Session.h"
#include "pljava/Session.h"
#include "pljava/type/AclId.h"

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
#if 80402<=PG_VERSION_NUM || \
	80309<=PG_VERSION_NUM && PG_VERSION_NUM<80400 || \
	80215<=PG_VERSION_NUM && PG_VERSION_NUM<80300
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
#elif PG_VERSION_NUM>=80206
	(void)secContext; /* away with your unused-variable warnings! */
	GetUserIdAndContext(&dummy, &wasLocalChange);
	SetUserIdAndContext(AclId_getAclId(aclId), (bool)isLocalChange);
#else
	(void)secContext;
	(void)dummy;
	SetUserId(AclId_getAclId(aclId));
#endif
	END_NATIVE
	return wasLocalChange ? JNI_TRUE : JNI_FALSE;
}

