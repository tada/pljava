/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Type_priv.h"

#include "pljava/type/Type_priv.h"
#include "pljava/type/HeapTupleHeader.h"

/*
 * void primitive type.
 */
static jclass s_SingleTupleReader_class;
static jmethodID s_SingleTupleReader_init;
static TypeClass s_SingleTupleReaderClass;
static Type s_SingleTupleReader;

static jvalue _SingleTupleReader_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jobject ttSlot = HeapTupleHeader_create(DatumGetHeapTupleHeader(arg));
	result.l = JNI_newObject(s_SingleTupleReader_class, s_SingleTupleReader_init, ttSlot);
	JNI_deleteLocalRef(ttSlot);
	return result;
}

static Datum _SingleTupleReader_coerceObject(Type self, jobject nothing)
{
	/* Should never be used here.
	 */
	return 0;
}

static Type SingleTupleReader_obtain(Oid typeId)
{
	return s_SingleTupleReader;
}

/* Make this datatype available to the postgres system.
 */
extern void SingleTupleReader_initialize(void);
void SingleTupleReader_initialize(void)
{
	s_SingleTupleReader_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/jdbc/SingleTupleReader"));
	s_SingleTupleReader_init = PgObject_getJavaMethod(s_SingleTupleReader_class, "<init>", "(Lorg/postgresql/pljava/internal/HeapTupleHeader;)V");

	s_SingleTupleReaderClass = TypeClass_alloc("type.SingleTupleReader");
	s_SingleTupleReaderClass->JNISignature = "Ljava/sql/ResultSet;";
	s_SingleTupleReaderClass->javaTypeName = "java.lang.ResultSet";
	s_SingleTupleReaderClass->coerceDatum  = _SingleTupleReader_coerceDatum;
	s_SingleTupleReaderClass->coerceObject = _SingleTupleReader_coerceObject;
	s_SingleTupleReader = TypeClass_allocInstance(s_SingleTupleReaderClass, InvalidOid);

	Type_registerJavaType("org.postgresql.pljava.jdbc.SingleTupleReader", SingleTupleReader_obtain);
}
