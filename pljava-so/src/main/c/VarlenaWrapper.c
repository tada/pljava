/*
 * Copyright (c) 2018 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */

#include "pljava/VarlenaWrapper.h"
#include "pljava/DualState.h"

#include "pljava/PgObject.h"
#include "pljava/JNICalls.h"

static jclass s_VarlenaWrapper_Input_class;

static jmethodID s_VarlenaWrapper_Input_init;

jobject pljava_VarlenaWrapper_Input(Datum d, MemoryContext mc, ResourceOwner ro)
{
	jobject vr;
	jobject dbb;
	MemoryContext prevcxt;
	struct varlena *copy;
	Ptr2Long p2lro;
	Ptr2Long p2ldatum;

	prevcxt = MemoryContextSwitchTo(mc);
	copy = PG_DETOAST_DATUM_COPY(d);
	MemoryContextSwitchTo(prevcxt);

	p2lro.longVal = 0L;
	p2ldatum.longVal = 0L;

	p2lro.ptrVal = ro;
	p2ldatum.ptrVal = copy;

	dbb = JNI_newDirectByteBuffer(VARDATA(copy), VARSIZE_ANY_EXHDR(copy));

	vr = JNI_newObjectLocked(s_VarlenaWrapper_Input_class,
		s_VarlenaWrapper_Input_init, pljava_DualState_key(),
		p2lro.longVal, p2ldatum.longVal, dbb);
	JNI_deleteLocalRef(dbb);

	return vr;
}

void pljava_VarlenaWrapper_initialize(void)
{
	s_VarlenaWrapper_Input_class =
		(jclass)JNI_newGlobalRef(PgObject_getJavaClass(
			"org/postgresql/pljava/internal/VarlenaWrapper$Input"));

	s_VarlenaWrapper_Input_init = PgObject_getJavaMethod(
		s_VarlenaWrapper_Input_class, "<init>",
		"(Lorg/postgresql/pljava/internal/DualState$Key;"
		"JJLjava/nio/ByteBuffer;)V");
}
