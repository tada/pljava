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

static TypeClass s_StringClass;
static HashMap s_cache;
jclass s_String_class;

/*
 * Default type. Uses Posgres String conversion routines.
 */
static bool _String_canReplaceType(Type self, Type type)
{
	/* All known postgres types can perform String coercsions.
	 */
	return true;
}

jvalue _String_coerceDatum(Type self, JNIEnv* env, Datum arg)
{
	jvalue result;
	char* tmp = DatumGetCString(FunctionCall3(
					&((String)self)->textOutput,
					arg,
					ObjectIdGetDatum(((String)self)->elementType),
					Int32GetDatum(-1)));
	result.l = String_createJavaStringFromNTS(env, tmp);
	pfree(tmp);
	return result;
}

Datum _String_coerceObject(Type self, JNIEnv* env, jobject jstr)
{
	char* tmp = String_createNTS(env, jstr);
	Datum ret = FunctionCall3(
					&((String)self)->textInput,
					CStringGetDatum(tmp),
					ObjectIdGetDatum(((String)self)->elementType),
					Int32GetDatum(-1));
	pfree(tmp);
	return ret;
}

static String String_create(TypeClass cls, Form_pg_type pgType)
{
	String self = (String)TypeClass_allocInstance(cls);
	MemoryContext ctx = GetMemoryChunkContext(self);
	fmgr_info_cxt(pgType->typoutput, &self->textOutput, ctx);
	fmgr_info_cxt(pgType->typinput,  &self->textInput,  ctx);
	self->elementType = pgType->typelem;
	return self;
}

Type String_fromPgType(Oid typeId, Form_pg_type pgType)
{
	String self = (String)HashMap_getByOid(s_cache, typeId);
	if(self == 0)
	{
		/*
		 * Create the new type
		 */
		self = String_create(s_StringClass, pgType);
		HashMap_putByOid(s_cache, typeId, self);
	}
	return (Type)self;
}

Type String_obtain(Oid typeId)
{
	return (Type)StringClass_obtain(s_StringClass, typeId);
}

String StringClass_obtain(TypeClass self, Oid typeId)
{
	/* Check to see if we have a cached version for this
	 * postgres type
	 */
	String infant = (String)HashMap_getByOid(s_cache, typeId);
	if(infant == 0)
	{
		/*
		 * Retreive standard string conversion from the postgres
		 * type catalog.
		 */
		HeapTuple typeTup = PgObject_getValidTuple(TYPEOID, typeId, "type");
		infant = String_create(self, (Form_pg_type)GETSTRUCT(typeTup));
		ReleaseSysCache(typeTup);
		HashMap_putByOid(s_cache, typeId, infant);
	}
	return infant;
}

jstring String_createJavaString(JNIEnv* env, text* t)
{
	jstring result = 0;
	if(t != 0)
	{
		char* utf8;
		const char* src = VARDATA(t);
		int srcLen = VARSIZE(t) - VARHDRSZ;
		if(srcLen == 0)
			return 0;
	
		/* Would be nice if a direct conversion to UTF16 was provided.
		 */
		utf8 = (char*)pg_do_encoding_conversion(
			(unsigned char*)src, srcLen, GetDatabaseEncoding(), PG_UTF8);
		result = (*env)->NewStringUTF(env, utf8);
	
		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(utf8 != src)
			pfree(utf8);
	}
	return result;
}

jstring String_createJavaStringFromNTS(JNIEnv* env, const char* cp)
{
	jstring result = 0;
	if(cp != 0)
	{
		/* Would be nice if a direct conversion to UTF16 was provided.
		 */
		char* utf8 = (char*)pg_do_encoding_conversion(
			(unsigned char*)cp, strlen(cp), GetDatabaseEncoding(), PG_UTF8);
		result = (*env)->NewStringUTF(env, utf8);
	
		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(utf8 != cp)
			pfree(utf8);
	}
	return result;
}

text* String_createText(JNIEnv* env, jstring javaString)
{
	text* result = 0;
	if(javaString != 0)
	{
		/* Would be nice if a direct conversion from UTF16 was provided.
		 */
		const char* utf8 = (*env)->GetStringUTFChars(env, javaString, 0);
		char* denc = (char*)pg_do_encoding_conversion(
			(unsigned char*)utf8, strlen(utf8), PG_UTF8, GetDatabaseEncoding());
		int dencLen = strlen(denc);
		int varSize = dencLen + VARHDRSZ;
	
		/* Allocate and initialize the text structure.
		 */
		result = (text*)palloc(varSize);
		VARATT_SIZEP(result) = varSize;	/* Total size of structure, not just data */
		memcpy(VARDATA(result), denc, dencLen);

		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(denc != utf8)
			pfree(denc);
		(*env)->ReleaseStringUTFChars(env, javaString, utf8);
	}
	return result;
}

char* String_createNTS(JNIEnv* env, jstring javaString)
{
	char* result = 0;
	if(javaString != 0)
	{
		/* Would be nice if a direct conversion from UTF16 was provided.
		 */
		const char* utf8 = (*env)->GetStringUTFChars(env, javaString, 0);
		result = (char*)pg_do_encoding_conversion(
			(unsigned char*)utf8, strlen(utf8), PG_UTF8, GetDatabaseEncoding());
	
		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We always want a copy here.
		 */
		if(result == utf8)
			result = pstrdup(result);
		(*env)->ReleaseStringUTFChars(env, javaString, utf8);
	}
	return result;
}

void String_appendJavaString(JNIEnv* env, StringInfoData* buf, jstring javaString)
{
	if(javaString != 0)
	{
		/* Would be nice if a direct conversion from UTF16 was provided.
		 */
		const char* utf8 = (*env)->GetStringUTFChars(env, javaString, 0);
		char* dbEnc = (char*)pg_do_encoding_conversion(
			(unsigned char*)utf8, strlen(utf8), PG_UTF8, GetDatabaseEncoding());
	
		appendStringInfoString(buf, dbEnc);

		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(dbEnc != utf8)
			pfree(dbEnc);
		(*env)->ReleaseStringUTFChars(env, javaString, utf8);
	}
}

/* Make this datatype available to the postgres system.
 */
extern Datum String_initialize(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(String_initialize);
Datum String_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_String_class = (jclass)(*env)->NewGlobalRef(
		env, PgObject_getJavaClass(env, "java/lang/String"));

	s_StringClass = TypeClass_alloc2("type.String", sizeof(struct TypeClass_), sizeof(struct String_));
	s_StringClass->JNISignature   = "Ljava/lang/String;";
	s_StringClass->javaTypeName   = "java.lang.String";
	s_StringClass->canReplaceType = _String_canReplaceType;
	s_StringClass->coerceDatum    = _String_coerceDatum;
	s_StringClass->coerceObject   = _String_coerceObject;

	/*
	 * Initialize the type cache for the default types.
	 */
	s_cache = HashMap_create(13, TopMemoryContext);

	/*
	 * Registering known types will increase the performance
	 * a bit. The "default" is used when all else fails.
	 */
	Type_registerPgType(TEXTOID,   String_obtain);
	Type_registerPgType(CSTRINGOID,String_obtain);
	Type_registerPgType(BPCHAROID, String_obtain);
	Type_registerPgType(NAMEOID,   String_obtain);
	Type_registerPgType(VARCHAROID,String_obtain);

	Type_registerJavaType("java.lang.String", String_obtain);
	PG_RETURN_VOID();
}
