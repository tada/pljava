/*
 * Copyright (c) 2025 TADA AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
#include <postgres.h>

#include "pljava/PgObject.h"

extern void SQLChunkIOOrder_initialize(void);
void SQLChunkIOOrder_initialize(void)
{
	/*
	 * Nothing more is needed here than to cause the class's static initializer
	 * to run (at the chosen time, from native code, before user Java code could
	 * have altered the needed system properties).
	 *
	 * The JNI_FindClass mentions that it initializes the named class, but only
	 * says so in one place, does not clearly say it returns an initialized
	 * class, and does not mention ExceptionInInitializerError as a possible
	 * exception.
	 *
	 * GetStaticFieldID clearly says it causes an uninitialized class to be
	 * initialized, and lists ExceptionInInitializerError as a possible
	 * exception. So, just to be sure, a field ID is fetched here.
	 */
	jclass cls = PgObject_getJavaClass(
		"org/postgresql/pljava/jdbc/SQLChunkIOOrder");
	PgObject_getStaticJavaField(cls, "MIRROR_J2P", "Ljava/nio/ByteOrder;");
}
