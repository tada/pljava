/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

#include "pljava/DualState.h"

#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"
static jclass s_DualState_class;

static jmethodID s_DualState_resourceOwnerRelease;
static jmethodID s_DualState_cleanEnqueuedInstances;

static jobject s_DualState_key;

static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg);

jobject pljava_DualState_key(void)
{
	return s_DualState_key;
}

void pljava_DualState_cleanEnqueuedInstances(void)
{
	JNI_callStaticVoidMethodLocked(s_DualState_class,
								   s_DualState_cleanEnqueuedInstances);
}

void pljava_DualState_initialize(void)
{
	jclass clazz;
	jmethodID ctor;

	s_DualState_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState"));

	s_DualState_resourceOwnerRelease = PgObject_getStaticJavaMethod(
		s_DualState_class, "resourceOwnerRelease", "(J)V");
	s_DualState_cleanEnqueuedInstances = PgObject_getStaticJavaMethod(
		s_DualState_class, "cleanEnqueuedInstances", "()V");

	clazz = (jclass)PgObject_getJavaClass(
		"org/postgresql/pljava/internal/DualState$Key");
	ctor = PgObject_getJavaMethod(clazz, "<init>", "()V");
	s_DualState_key = JNI_newGlobalRef(JNI_newObject(clazz, ctor));
	JNI_deleteLocalRef(clazz);

	RegisterResourceReleaseCallback(resourceReleaseCB, NULL);
}

static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg)
{
	Ptr2Long p2l;

	/*
	 * This static assertion does not need to be in every file
	 * that uses Ptr2Long, but it should be somewhere once, so here it is.
	 */
	StaticAssertStmt(sizeof p2l.ptrVal <= sizeof p2l.longVal,
					 "Pointer will not fit in long on this platform");

	if ( RESOURCE_RELEASE_AFTER_LOCKS != phase )
		return;

	p2l.longVal = 0L;
	p2l.ptrVal = CurrentResourceOwner;
	JNI_callStaticVoidMethodLocked(s_DualState_class,
								   s_DualState_resourceOwnerRelease,
								   p2l.longVal);
}
