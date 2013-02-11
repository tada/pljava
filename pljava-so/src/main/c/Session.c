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
	  	"(Lorg/postgresql/pljava/internal/AclId;)V",
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
 * Signature: (Lorg/postgresql/pljava/internal/AclId;)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Session__1setUser(JNIEnv* env, jclass cls, jobject aclId)
{
	/* No error checking since this might be a restore of user in
	 * a finally block after an exception.
	 */
	BEGIN_NATIVE_NO_ERRCHECK
#if ( \
    (PGSQL_MAJOR_VER > 8) \
    || (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER >= 3) \
    || (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 2 && PGSQL_PATCH_VER >= 6)  \
    || (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 1 && PGSQL_PATCH_VER >= 11) \
    || (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 0 && PGSQL_PATCH_VER >= 15) \
    )
	SetUserIdAndContext(AclId_getAclId(aclId), true);
#else
	SetUserId(AclId_getAclId(aclId));
#endif
	END_NATIVE
}

