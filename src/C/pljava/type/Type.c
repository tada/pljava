/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/type/String_priv.h"
#include "pljava/HashMap.h"

static HashMap s_typeByOid;
static HashMap s_obtainerByOid;
static HashMap s_obtainerByJavaName;

bool Type_canReplaceType(Type self, Type other)
{
	return self->m_class->canReplaceType(self, other);
}

jvalue Type_coerceDatum(Type self, JNIEnv* env, Datum value)
{
	return self->m_class->coerceDatum(self, env, value);
}

Datum Type_coerceObject(Type self, JNIEnv* env, jobject object)
{
	return self->m_class->coerceObject(self, env, object);
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

Datum Type_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, bool* wasNull)
{
	return self->m_class->invoke(self, env, cls, method, args, wasNull);
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

Type Type_fromOid(Oid typeId)
{
	Type type = (Type)HashMap_getByOid(s_typeByOid, typeId);
	if(type == 0)
	{
		TypeObtainer to = (TypeObtainer)HashMap_getByOid(s_obtainerByOid, typeId);
		if(to == 0)
		{
			HeapTuple typeTup = PgObject_getValidTuple(TYPEOID, typeId, "Type");
			Form_pg_type typeStruct = (Form_pg_type)GETSTRUCT(typeTup);
			if(OidIsValid(typeStruct->typrelid))
			{
				type = Type_fromJavaType(typeId, "org.postgresql.pljava.internal.TupleTable");
			}
			else
			{
				type = String_fromPgType(typeId, typeStruct);
			}
			ReleaseSysCache(typeTup);
		}
		else
			type = to(typeId);
		HashMap_putByOid(s_typeByOid, typeId, type);
	}
	return type;
}

bool _Type_canReplaceType(Type self, Type other)
{
	return self->m_class == other->m_class;
}

Datum _Type_invoke(Type self, JNIEnv* env, jclass cls, jmethodID method, jvalue* args, bool* wasNull)
{
	bool saveicj = isCallingJava;
	isCallingJava = true;
	jobject value = (*env)->CallStaticObjectMethodA(env, cls, method, args);
	isCallingJava = saveicj;
	if(value == 0)
	{
		*wasNull = true;
		return 0;
	}
	Datum ret = self->m_class->coerceObject(self, env, value);
	(*env)->DeleteLocalRef(env, value);
	return ret;
}

/*
 * Shortcuts to initializers of known types
 */
extern Datum Void_initialize(PG_FUNCTION_ARGS);
extern Datum Boolean_initialize(PG_FUNCTION_ARGS);
extern Datum Byte_initialize(PG_FUNCTION_ARGS);
extern Datum Short_initialize(PG_FUNCTION_ARGS);
extern Datum Integer_initialize(PG_FUNCTION_ARGS);
extern Datum Long_initialize(PG_FUNCTION_ARGS);
extern Datum Float_initialize(PG_FUNCTION_ARGS);
extern Datum Double_initialize(PG_FUNCTION_ARGS);
extern Datum BigDecimal_initialize(PG_FUNCTION_ARGS);

extern Datum Date_initialize(PG_FUNCTION_ARGS);
extern Datum Time_initialize(PG_FUNCTION_ARGS);
extern Datum Timestamp_initialize(PG_FUNCTION_ARGS);

extern Datum Oid_initialize(PG_FUNCTION_ARGS);
extern Datum AclId_initialize(PG_FUNCTION_ARGS);

extern Datum String_initialize(PG_FUNCTION_ARGS);
extern Datum byte_array_initialize(PG_FUNCTION_ARGS);

extern Datum NativeStruct_initialize(PG_FUNCTION_ARGS);
extern Datum ExecutionPlan_initialize(PG_FUNCTION_ARGS);
extern Datum Portal_initialize(PG_FUNCTION_ARGS);
extern Datum Relation_initialize(PG_FUNCTION_ARGS);
extern Datum SPITupleTable_initialize(PG_FUNCTION_ARGS);
extern Datum TriggerData_initialize(PG_FUNCTION_ARGS);
extern Datum Tuple_initialize(PG_FUNCTION_ARGS);
extern Datum TupleDesc_initialize(PG_FUNCTION_ARGS);
extern Datum TupleTable_initialize(PG_FUNCTION_ARGS);
extern Datum TupleTableSlot_initialize(PG_FUNCTION_ARGS);

/* Make this datatype available to the postgres system.
 */
PG_FUNCTION_INFO_V1(Type_initialize);

Datum Type_initialize(PG_FUNCTION_ARGS)
{
	s_typeByOid          = HashMap_create(57, TopMemoryContext);
	s_obtainerByOid      = HashMap_create(57, TopMemoryContext);
	s_obtainerByJavaName = HashMap_create(57, TopMemoryContext);

	String_initialize(fcinfo);
	
	Void_initialize(fcinfo);
	Boolean_initialize(fcinfo);
	Byte_initialize(fcinfo);
	Short_initialize(fcinfo);
	Integer_initialize(fcinfo);
	Long_initialize(fcinfo);
	Float_initialize(fcinfo);
	Double_initialize(fcinfo);

	BigDecimal_initialize(fcinfo);

	Date_initialize(fcinfo);
	Time_initialize(fcinfo);
	Timestamp_initialize(fcinfo);

	Oid_initialize(fcinfo);
	AclId_initialize(fcinfo);

	byte_array_initialize(fcinfo);

	NativeStruct_initialize(fcinfo);
	ExecutionPlan_initialize(fcinfo);
	Portal_initialize(fcinfo);
	TriggerData_initialize(fcinfo);
	Relation_initialize(fcinfo);
	SPITupleTable_initialize(fcinfo);
	TupleDesc_initialize(fcinfo);
	Tuple_initialize(fcinfo);
	TupleTable_initialize(fcinfo);
	TupleTableSlot_initialize(fcinfo);
	PG_RETURN_VOID();
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
	self->canReplaceType = _Type_canReplaceType;
	self->invoke         = _Type_invoke;
	self->coerceDatum    = (jvalue (*)(Type, JNIEnv*, Datum))_PgObject_pureVirtualCalled;
	self->coerceObject   = (Datum (*)(Type, JNIEnv*, jobject))_PgObject_pureVirtualCalled;
	return self;
}

/*
 * Register this type as the default mapping for a postgres type.
 */
void Type_registerPgType(Oid typeOid, TypeObtainer obtainer)
{
	HashMap_putByOid(s_obtainerByOid, typeOid, obtainer);
}	

/*
 * Register this type as the mapper for an explicit Java type.
 */
void Type_registerJavaType(const char* javaTypeName, TypeObtainer obtainer)
{
	HashMap_putByString(s_obtainerByJavaName, javaTypeName, obtainer);
}

