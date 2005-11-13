/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/String_priv.h"
#include "pljava/type/TupleDesc.h"
#include "pljava/MemoryContext.h"
#include "pljava/HashMap.h"
#include "pljava/SPI.h"

static HashMap s_typeByOid;
static HashMap s_obtainerByOid;
static HashMap s_obtainerByJavaName;

bool Type_canReplaceType(Type self, Type other)
{
	return self->m_class->canReplaceType(self, other);
}

jvalue Type_coerceDatum(Type self, Datum value)
{
	return self->m_class->coerceDatum(self, value);
}

Datum Type_coerceObject(Type self, jobject object)
{
	return self->m_class->coerceObject(self, object);
}

const char* Type_getJavaTypeName(Type self)
{
	return self->m_class->javaTypeName;
}

const char* Type_getJNISignature(Type self)
{
	return self->m_class->JNISignature;
}

Type Type_getObjectType(Type self)
{
	return self->m_class->objectType;
}

Oid Type_getOid(Type self)
{
	return self->m_oid;
}

TupleDesc Type_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	return self->m_class->getTupleDesc(self, fcinfo);
}

Datum Type_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	return self->m_class->invoke(self, cls, method, args, fcinfo);
}

bool Type_isPrimitive(Type self)
{
	return self->m_class->objectType != 0;
}

Type Type_fromJavaType(Oid dfltTypeId, const char* javaTypeName)
{
	TypeObtainer to = (TypeObtainer)HashMap_getByString(
							s_obtainerByJavaName, javaTypeName);
	if(to == 0)
		ereport(ERROR, (
			errcode(ERRCODE_CANNOT_COERCE),
			errmsg("No java type mapping installed for \"%s\"", javaTypeName)));
	return to(dfltTypeId);
}

Type Type_fromPgType(Oid typeId, Form_pg_type typeStruct)
{
	Type type = (Type)HashMap_getByOid(s_typeByOid, typeId);
	if(type == 0)
	{
		TypeObtainer to = (TypeObtainer)HashMap_getByOid(s_obtainerByOid, typeId);
		if(to == 0)
			/*
			 * Default to String and standard textin/textout coersion.
			 */
			type = String_fromPgType(typeId, typeStruct);
		else
			type = to(typeId);
		HashMap_putByOid(s_typeByOid, typeId, type);
	}
	return type;
}

Type Type_fromOid(Oid typeId)
{
	Type type = (Type)HashMap_getByOid(s_typeByOid, typeId);
	if(type == 0)
	{
		TypeObtainer to = (TypeObtainer)HashMap_getByOid(s_obtainerByOid, typeId);
		if(to == 0)
		{
			/* Default to String and standard textin/textout coersion.
			 */
			HeapTuple typeTup = PgObject_getValidTuple(TYPEOID, typeId, "Type");
			Form_pg_type typeStruct = (Form_pg_type)GETSTRUCT(typeTup);
			type = String_fromPgType(typeId, typeStruct);
			ReleaseSysCache(typeTup);
		}
		else
			type = to(typeId);
		HashMap_putByOid(s_typeByOid, typeId, type);
	}
	return type;
}

Type Type_objectTypeFromOid(Oid typeId)
{
	Type type = Type_fromOid(typeId);
	Type objectType = type->m_class->objectType;
	return (objectType == 0) ? type : objectType;
}

bool _Type_canReplaceType(Type self, Type other)
{
	return self->m_class == other->m_class;
}

Datum _Type_invoke(Type self, jclass cls, jmethodID method, jvalue* args, PG_FUNCTION_ARGS)
{
	MemoryContext currCtx;
	Datum ret;
	jobject value = JNI_callStaticObjectMethodA(cls, method, args);
	if(value == 0)
	{
		fcinfo->isnull = true;
		return 0;
	}

	/* The return value cannot be created in the current context since it
	 * goes out of scope when SPI_finish is called.
	 */
	currCtx = MemoryContext_switchToUpperContext();
	ret = self->m_class->coerceObject(self, value);
	MemoryContextSwitchTo(currCtx);
	JNI_deleteLocalRef(value);
	return ret;
}

TupleDesc _Type_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	ereport(ERROR,
		(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
		 errmsg("Type is not associated with a record")));
	return 0;	/* Keep compiler happy */
}

/*
 * Shortcuts to initializers of known types
 */
extern void Void_initialize(void);
extern void Boolean_initialize(void);
extern void Byte_initialize(void);
extern void Short_initialize(void);
extern void Integer_initialize(void);
extern void Long_initialize(void);
extern void Float_initialize(void);
extern void Double_initialize(void);
extern void BigDecimal_initialize(void);

extern void Date_initialize(void);
extern void Time_initialize(void);
extern void Timestamp_initialize(void);

extern void Oid_initialize(void);
extern void AclId_initialize(void);
extern void ErrorData_initialize(void);
extern void LargeObject_initialize(void);

extern void String_initialize(void);
extern void byte_array_initialize(void);

extern void JavaWrapper_initialize(void);
extern void JavaHandle_initialize(void);
extern void ExecutionPlan_initialize(void);
extern void Portal_initialize(void);
extern void Relation_initialize(void);
extern void TriggerData_initialize(void);
extern void Tuple_initialize(void);
extern void TupleDesc_initialize(void);
extern void TupleTable_initialize(void);
extern void HeapTupleHeader_initialize(void);

extern void SingleTupleReader_initialize(void);
extern void SingleRowWriter_initialize(void);
extern void ValueSetProvider_initialize(void);
extern void ResultSetProvider_initialize(void);

extern void Type_initialize(void);
void Type_initialize(void)
{
	s_typeByOid          = HashMap_create(59, TopMemoryContext);
	s_obtainerByOid      = HashMap_create(59, TopMemoryContext);
	s_obtainerByJavaName = HashMap_create(59, TopMemoryContext);

	String_initialize();
	
	Void_initialize();
	Boolean_initialize();
	Byte_initialize();
	Short_initialize();
	Integer_initialize();
	Long_initialize();
	Float_initialize();
	Double_initialize();

	BigDecimal_initialize();

	Date_initialize();
	Time_initialize();
	Timestamp_initialize();

	Oid_initialize();
	AclId_initialize();
	ErrorData_initialize();
	LargeObject_initialize();

	byte_array_initialize();

	JavaWrapper_initialize();
	JavaHandle_initialize();
	ExecutionPlan_initialize();
	Portal_initialize();
	TriggerData_initialize();
	Relation_initialize();
	TupleDesc_initialize();
	Tuple_initialize();
	TupleTable_initialize();
	HeapTupleHeader_initialize();

	SingleTupleReader_initialize();
	SingleRowWriter_initialize();
	ValueSetProvider_initialize();
	ResultSetProvider_initialize();
}

/*
 * Abstract Type constructor
 */
TypeClass TypeClass_alloc(const char* typeName)
{
	return TypeClass_alloc2(typeName, sizeof(struct TypeClass_), sizeof(struct Type_));
}

TypeClass TypeClass_alloc2(const char* typeName, Size classSize, Size instanceSize)
{
	TypeClass self = (TypeClass)MemoryContextAlloc(TopMemoryContext, classSize);
	PgObjectClass_init((PgObjectClass)self, typeName, instanceSize, 0);
	self->JNISignature   = "";
	self->javaTypeName   = "";
	self->objectType     = 0;
	self->canReplaceType = _Type_canReplaceType;
	self->coerceDatum    = (jvalue (*)(Type, Datum))_PgObject_pureVirtualCalled;
	self->coerceObject   = (Datum (*)(Type, jobject))_PgObject_pureVirtualCalled;
	self->invoke         = _Type_invoke;
	self->getTupleDesc   = _Type_getTupleDesc;
	return self;
}

/*
 * Types are always allocated in global context.
 */
Type TypeClass_allocInstance(TypeClass cls, Oid oid)
{
	Type t = (Type)PgObjectClass_allocInstance((PgObjectClass)(cls), TopMemoryContext);
	t->m_oid = oid;
	return t;
}

/*
 * Register this type as the default mapping for a postgres type.
 */
void Type_registerPgType(Oid typeOid, TypeObtainer obtainer)
{
	HashMap_putByOid(s_obtainerByOid, typeOid, (void*)obtainer);
}	

/*
 * Register this type as the mapper for an explicit Java type.
 */
void Type_registerJavaType(const char* javaTypeName, TypeObtainer obtainer)
{
	HashMap_putByString(s_obtainerByJavaName, javaTypeName, (void*)obtainer);
}

