/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "org_postgresql_pljava_internal_MemoryContextManaged.h"
#include "pljava/type/Type_priv.h"
#include "pljava/type/MemoryContextManaged.h"
#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/PLJavaMemoryContext.h"

static jclass    s_MemoryContextManaged_class;
static jfieldID  s_MemoryContextManaged_m_pointer;

MemoryContext JavaMemoryContext;

void* MemoryContextManaged_getPointer(JNIEnv* env, jobject managed)
{
	Ptr2Long p2l;
	if(managed == 0)
	{
		Exception_throw(env, ERRCODE_INTERNAL_ERROR, "Null MemoryContextManaged object");
		return 0;
	}

	p2l.longVal = (*env)->GetLongField(env, managed, s_MemoryContextManaged_m_pointer);
	if(p2l.ptrVal == 0)
	{
		/* Stale handle.
		 */
		Exception_throw(env, ERRCODE_INTERNAL_ERROR, "Stale Handle to native structure");
	}
	return p2l.ptrVal;
}

static Datum _MemoryContextManaged_coerceObject(Type self, JNIEnv* env, jobject nStruct)
{
	return PointerGetDatum(MemoryContextManaged_getPointer(env, nStruct));
}

static void setObjectStale(JNIEnv* env, jobject managed)
{
	if(managed != 0)
		(*env)->SetLongField(env, managed, s_MemoryContextManaged_m_pointer, 0);
}

TypeClass MemoryContextManagedClass_alloc(const char* name)
{
	TypeClass self = TypeClass_alloc(name);
	self->coerceObject = _MemoryContextManaged_coerceObject;
	return self;
}

/* Make this datatype available to the postgres system.
 */
extern Datum MemoryContextManaged_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(MemoryContextManaged_initialize);
Datum MemoryContextManaged_initialize(PG_FUNCTION_ARGS)
{
	JNINativeMethod methods[] = {
		{
		"_free",
	  	"(J)V",
	  	Java_org_postgresql_pljava_internal_MemoryContextManaged__1free
		},
		{ 0, 0, 0 }};

	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_MemoryContextManaged_class = (*env)->NewGlobalRef(
				env, PgObject_getJavaClass(env, "org/postgresql/pljava/internal/MemoryContextManaged"));

	PgObject_registerNatives2(env, s_MemoryContextManaged_class, methods);

	s_MemoryContextManaged_m_pointer = PgObject_getJavaField(
				env, s_MemoryContextManaged_class, "m_pointer", "J");

	JavaMemoryContext = PLJavaMemoryContext_create(TopMemoryContext, "PL/Java", setObjectStale);
	elog(DEBUG1, "JavaMemoryContext created");

	PG_RETURN_VOID();
}

/*
 * Class:     org_postgresql_pljava_internal_MemoryContextManaged
 * Method:    _free
 * Signature: (J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_MemoryContextManaged__1free(JNIEnv* env, jobject _this, jlong pointer)
{
	if(pointer != 0)
	{
		/* Avoid callback when explicitly freed from Java code
		 */
		Ptr2Long p2l;
		p2l.longVal = pointer;
		PLJavaMemoryContext_setJavaObject(env, p2l.ptrVal, 0);
		pfree(p2l.ptrVal);
	}
}
