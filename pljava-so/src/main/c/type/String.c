/*
 * Copyright (c) 2004-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB - Thomas Hallgren
 *   Chapman Flack
 *   Francisco Miguel Biete Banon
 */
#include "pljava/type/String_priv.h"
#include "pljava/HashMap.h"

static TypeClass s_StringClass;
jclass s_String_class;
jclass s_Object_class;
static jmethodID s_Object_toString;

static jobject s_CharsetDecoder_instance;
static jobject s_CharsetEncoder_instance;
static jmethodID s_CharsetDecoder_decode;
static jmethodID s_CharsetEncoder_encode;
static jfloat  s_CharsetEncoder_averageBytesPerChar;
static jobject s_CoderResult_OVERFLOW;
static jobject s_CoderResult_UNDERFLOW;
static jmethodID s_CoderResult_throwException;
static jclass  s_CharBuffer_class;
static jmethodID s_CharBuffer_wrap;
static jmethodID s_Buffer_position;
static jmethodID s_Buffer_remaining;
static jstring s_the_empty_string;

static int s_server_encoding;

/*
 * String_appendJavaString and String_createNTS can be called from
 * elogExceptionMessage in JNICalls.c if something goes off the rails before
 * or during initialization of this class. The statically initialized values
 * here will make appendJavaString use createNTS, and createNTS use a fallback
 * based on JNI_getStringUTFChars (and live with the possibility that it gets
 * non-BMP characters wrong).
 */
static bool uninitialized = true;
static bool s_two_step_conversion = true;

/*
 * Default type. Uses Posgres String conversion routines.
 */
static bool _String_canReplaceType(Type self, Type type)
{
	/* All known postgres types can perform String coercsions.
	 */
	return true;
}

jvalue _String_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	char* tmp = DatumGetCString(FunctionCall3(
					&((PLJString)self)->textOutput,
					arg,
					ObjectIdGetDatum(((PLJString)self)->elementType),
					Int32GetDatum(-1)));
	result.l = String_createJavaStringFromNTS(tmp);
	pfree(tmp);
	return result;
}

Datum _String_coerceObject(Type self, jobject jstr)
{
	char* tmp;
	Datum ret;
	if(jstr == 0)
		return 0;

	jstr = JNI_callObjectMethod(jstr, s_Object_toString);
	if(JNI_exceptionCheck())
		return 0;

	tmp = String_createNTS(jstr);
	JNI_deleteLocalRef(jstr);

	ret = FunctionCall3(
					&((PLJString)self)->textInput,
					CStringGetDatum(tmp),
					ObjectIdGetDatum(((PLJString)self)->elementType),
					Int32GetDatum(-1));
	pfree(tmp);
	return ret;
}

static PLJString String_create(TypeClass cls, Oid typeId)
{
	HeapTuple    typeTup = PgObject_getValidTuple(TYPEOID, typeId, "type");
	Form_pg_type pgType  = (Form_pg_type)GETSTRUCT(typeTup);
	PLJString self = (PLJString)TypeClass_allocInstance(cls, typeId);
	MemoryContext ctx = GetMemoryChunkContext(self);
	fmgr_info_cxt(pgType->typoutput, &self->textOutput, ctx);
	fmgr_info_cxt(pgType->typinput,  &self->textInput,  ctx);
	self->elementType = 'e' == pgType->typtype ? typeId : pgType->typelem;
	ReleaseSysCache(typeTup);
	return self;
}

Type String_obtain(Oid typeId)
{
	return (Type)StringClass_obtain(s_StringClass, typeId);
}

PLJString StringClass_obtain(TypeClass self, Oid typeId)
{
	return String_create(self, typeId);
}

jstring String_createJavaString(text* t)
{
	jstring result = 0;
	if(t != 0)
	{
		jobject bytebuf;
		jobject charbuf;
		char* src = VARDATA(t);
		char* utf8 = src;
		Size srcLen = VARSIZE(t) - VARHDRSZ;
		if(srcLen == 0)
			return s_the_empty_string;

		if ( s_two_step_conversion )
		{
			utf8 = (char*)pg_do_encoding_conversion((unsigned char*)src,
				(int)srcLen, s_server_encoding, PG_UTF8);
			/* pg_do_encoding_conversion may return the source argument
			 * unchanged in more circumstances than you'd expect. As the source
			 * argument isn't NUL-terminated, don't call strlen on it.
			 */
			if (utf8 != src)
				srcLen = strlen(utf8);
		}
		bytebuf = JNI_newDirectByteBuffer(utf8, srcLen);
		charbuf = JNI_callObjectMethodLocked(s_CharsetDecoder_instance,
			s_CharsetDecoder_decode, bytebuf);
		result = JNI_callObjectMethodLocked(charbuf, s_Object_toString);

		JNI_deleteLocalRef(bytebuf);
		JNI_deleteLocalRef(charbuf);
		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(utf8 != src)
			pfree(utf8);
	}
	return result;
}

jstring String_createJavaStringFromNTS(const char* cp)
{
	jstring result = 0;
	if(cp != 0)
	{
		jobject bytebuf;
		jobject charbuf;
		Size sz = strlen(cp);
		char const * utf8 = cp;
		if ( s_two_step_conversion )
		{
			utf8 = (char*)pg_do_encoding_conversion((unsigned char*)cp,
				(int)sz, s_server_encoding, PG_UTF8);
			/* Here the source is NUL-terminated, so calling strlen on it
			 * would be safe, but unnecessary all the same.
			 */
			if ( utf8 != cp )
				sz = strlen(utf8);
		}
		bytebuf = JNI_newDirectByteBuffer((void *)utf8, sz);
		charbuf = JNI_callObjectMethodLocked(s_CharsetDecoder_instance,
			s_CharsetDecoder_decode, bytebuf);
		result = JNI_callObjectMethodLocked(charbuf, s_Object_toString);

		JNI_deleteLocalRef(bytebuf);
		JNI_deleteLocalRef(charbuf);
		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. We don't want to accidentally
		 * free that pointer.
		 */
		if(utf8 != cp)
			pfree((void *)utf8);
	}
	return result;
}

static void appendCharBuffer(StringInfoData*, jobject);

text* String_createText(jstring javaString)
{
	text* result = 0;
	if(javaString != 0)
	{
		char* denc;
		Size dencLen;
		Size varSize;
		jobject charbuf = JNI_callStaticObjectMethodLocked(s_CharBuffer_class,
			s_CharBuffer_wrap, javaString);
		StringInfoData sid;
		initStringInfo(&sid);
		appendCharBuffer(&sid, charbuf);
		JNI_deleteLocalRef(charbuf);
		denc = sid.data;
		dencLen = sid.len;
		if ( s_two_step_conversion )
		{
			denc = (char*)pg_do_encoding_conversion(
				(unsigned char*)denc, (int)dencLen, PG_UTF8, s_server_encoding);
			/* pg_do_encoding_conversion may return the source argument
			 * unchanged in more circumstances than you'd expect. As the source
			 * argument isn't NUL-terminated, don't call strlen on it.
			 */
			if (denc != sid.data)
				dencLen = strlen(denc);
		}
		varSize = dencLen + VARHDRSZ;

		/* Allocate and initialize the text structure.
		 */
		result = (text*)palloc(varSize);
		SET_VARSIZE(result, varSize);	/* Total size of structure, not just data */
		memcpy(VARDATA(result), denc, dencLen);

		if(denc != sid.data)
			pfree(denc);
		pfree(sid.data);
	}
	return result;
}

char* String_createNTS(jstring javaString)
{
	char* result = 0;

	if( 0 == javaString )
		return result;

	if ( uninitialized )
	{
		const char* u8buf;

		s_server_encoding = GetDatabaseEncoding();
		u8buf = JNI_getStringUTFChars( javaString, NULL);
		if ( 0 == u8buf )
			return result;
		result = (char*)pg_do_encoding_conversion(
			(unsigned char *)u8buf, (int)strlen( u8buf),
			PG_UTF8, s_server_encoding);
		if ( result == u8buf )
			result = pstrdup( result);
		JNI_releaseStringUTFChars( javaString, u8buf);
		return result;
	}
	else
	{
		jobject charbuf = JNI_callStaticObjectMethodLocked(s_CharBuffer_class,
			s_CharBuffer_wrap, javaString);
		StringInfoData sid;
		initStringInfo(&sid);
		appendCharBuffer(&sid, charbuf);
		JNI_deleteLocalRef(charbuf);

		result = (char*)pg_do_encoding_conversion(
			(unsigned char *)sid.data, sid.len, PG_UTF8, s_server_encoding);

		/* pg_do_encoding_conversion will return the source argument
		 * when no conversion is required. Don't free it in that case.
		 */
		if(result != sid.data)
			pfree(sid.data);
	}

	return result;
}

void String_appendJavaString(StringInfoData* buf, jstring javaString)
{
	if ( 0 == javaString )
		return;
	if ( ! s_two_step_conversion )
	{
		jobject charbuf = JNI_callStaticObjectMethodLocked(s_CharBuffer_class,
			s_CharBuffer_wrap, javaString);
		appendCharBuffer(buf, charbuf);
		JNI_deleteLocalRef(charbuf);
	}
	else
	{
		char* dbEnc = String_createNTS(javaString);
		if ( 0 == dbEnc ) /* this can happen if a JNI call fails */
			return;
		appendStringInfoString(buf, dbEnc);
		pfree(dbEnc);
	}
}

static void appendCharBuffer(StringInfoData* buf, jobject charbuf)
{
	Size nchars;
	char *bp;
	Size cap;
	jobject bytebuf;
	jobject coderresult;

	for ( ;; )
	{
		/*
		 * Invariant: charbuf has some chars to encode, buf _might_ have room.
		 * Broken StringInfo invariant: within this loop, might lack end NUL.
		 */
		nchars = JNI_callIntMethodLocked(charbuf, s_Buffer_remaining);
		/*
		 * enlargeStringInfo does nothing if it's already large enough, and
		 * enlarges generously if it isn't, not by nickels and dimes.
		 */
		cap = (Size)(s_CharsetEncoder_averageBytesPerChar * (double)nchars);
		enlargeStringInfo(buf, (int)cap);
		/*
		 * Give the JVM a window into the unused portion of buf.
		 */
		bp = buf->data + buf->len;
		cap = buf->maxlen - buf->len;
		bytebuf = JNI_newDirectByteBuffer(bp, cap);
		/*
		 * Encode as much as will fit, then update StringInfo len to reflect it.
		 */
		coderresult = JNI_callObjectMethodLocked(s_CharsetEncoder_instance,
			s_CharsetEncoder_encode, charbuf, bytebuf, (jboolean)JNI_TRUE);
		buf->len += JNI_callIntMethodLocked(bytebuf, s_Buffer_position);
		JNI_deleteLocalRef(bytebuf);

		if ( ! JNI_isSameObject(coderresult, s_CoderResult_OVERFLOW) )
			break;
		JNI_deleteLocalRef(coderresult);
	}
	/*
	 * Remember the StringInfo-is-NUL-terminated invariant might not hold here.
	 */
	if ( JNI_isSameObject(coderresult, s_CoderResult_UNDERFLOW) )
		if ( 0 == JNI_callIntMethodLocked(charbuf, s_Buffer_remaining) )
		{
			JNI_deleteLocalRef(coderresult);
			enlargeStringInfo(buf, 1); /* MOST PROBABLY a no-op */
			buf->data[buf->len] = '\0'; /* I want my invariant back! */
			return;
		}
	JNI_callVoidMethodLocked(coderresult, s_CoderResult_throwException);
}

extern void String_initialize(void);
static void String_initialize_codec(void);
void String_initialize(void)
{
	s_Object_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/Object"));
	s_Object_toString = PgObject_getJavaMethod(s_Object_class, "toString", "()Ljava/lang/String;");
	s_String_class = (jclass)JNI_newGlobalRef(PgObject_getJavaClass("java/lang/String"));

	s_StringClass = TypeClass_alloc2("type.String", sizeof(struct TypeClass_), sizeof(struct String_));
	s_StringClass->JNISignature   = "Ljava/lang/String;";
	s_StringClass->javaTypeName   = "java.lang.String";
	s_StringClass->canReplaceType = _String_canReplaceType;
	s_StringClass->coerceDatum    = _String_coerceDatum;
	s_StringClass->coerceObject   = _String_coerceObject;

	/*
	 * Frame push/pop hoisted here out of String_initialize_codec to mollify
	 * pre-C99 compilers that don't want that function to have declarations
	 * after a statement.
	 */
	JNI_pushLocalFrame(16);
	String_initialize_codec();
	JNI_popLocalFrame(NULL);

	/*
	 * Registering known types will increase the performance
	 * a bit. The "default" is used when all else fails.
	 */
	Type_registerType2(TEXTOID,    0, String_obtain);
	Type_registerType2(CSTRINGOID, 0, String_obtain);
	Type_registerType2(BPCHAROID,  0, String_obtain);
	Type_registerType2(NAMEOID,    0, String_obtain);
	Type_registerType2(VARCHAROID, "java.lang.String", String_obtain);
}

static void String_initialize_codec()
{
	/*
	 * Wondering why this function doesn't bother deleting its many local refs?
	 * The call is wrapped in pushLocalFrame/popLocalFrame in the caller.
	 */
	jmethodID string_intern = PgObject_getJavaMethod(s_String_class,
		"intern", "()Ljava/lang/String;");
	jstring empty = JNI_newStringUTF( "");
	jclass charset_class =
		PgObject_getJavaClass("java/nio/charset/Charset");
	jmethodID charset_newDecoder = PgObject_getJavaMethod(charset_class,
		"newDecoder", "()Ljava/nio/charset/CharsetDecoder;");
	jmethodID charset_newEncoder = PgObject_getJavaMethod(charset_class,
		"newEncoder", "()Ljava/nio/charset/CharsetEncoder;");
	jclass decoder_class =
		PgObject_getJavaClass("java/nio/charset/CharsetDecoder");
	jclass encoder_class =
		PgObject_getJavaClass("java/nio/charset/CharsetEncoder");
	jmethodID encoder_abpc =
		PgObject_getJavaMethod(encoder_class, "averageBytesPerChar", "()F");
	jclass result_class = PgObject_getJavaClass("java/nio/charset/CoderResult");
	jfieldID overflow = PgObject_getStaticJavaField(result_class, "OVERFLOW",
		"Ljava/nio/charset/CoderResult;");
	jfieldID underflow = PgObject_getStaticJavaField(result_class, "UNDERFLOW",
		"Ljava/nio/charset/CoderResult;");
	jclass buffer_class = PgObject_getJavaClass("java/nio/Buffer");
	jobject servercs;

	/*
	 * Records what the final state of s_two_step_conversion will be, but the
	 * static is left at its initial value until all preparations are complete.
	 */
	bool two_step_when_ready = s_two_step_conversion;

	s_server_encoding = GetDatabaseEncoding();

	if ( PG_SQL_ASCII == s_server_encoding )
	{
		jmethodID forname =
			PgObject_getStaticJavaMethod(charset_class,
				"forName", "(Ljava/lang/String;)Ljava/nio/charset/Charset;");
		jstring sql_ascii = JNI_newStringUTF("X-PGSQL_ASCII");

		two_step_when_ready = false;

		servercs = JNI_callStaticObjectMethodLocked(charset_class,
			forname, sql_ascii);
	}
	else
	{
		jclass scharset_class =
			PgObject_getJavaClass("java/nio/charset/StandardCharsets");
		jfieldID scharset_UTF_8 = PgObject_getStaticJavaField(scharset_class,
			"UTF_8", "Ljava/nio/charset/Charset;");

		two_step_when_ready = PG_UTF8 != s_server_encoding;

		servercs = JNI_getStaticObjectField(scharset_class, scharset_UTF_8);
	}

	s_CharsetDecoder_instance =
		JNI_newGlobalRef(JNI_callObjectMethod(servercs, charset_newDecoder));
	s_CharsetEncoder_instance =
		JNI_newGlobalRef(JNI_callObjectMethod(servercs, charset_newEncoder));
	s_CharsetDecoder_decode = PgObject_getJavaMethod(decoder_class, "decode",
		"(Ljava/nio/ByteBuffer;)Ljava/nio/CharBuffer;");
	s_CharsetEncoder_encode = PgObject_getJavaMethod(encoder_class, "encode",
		"(Ljava/nio/CharBuffer;Ljava/nio/ByteBuffer;Z)"
		"Ljava/nio/charset/CoderResult;");
	s_CharsetEncoder_averageBytesPerChar =
		JNI_callFloatMethod(s_CharsetEncoder_instance, encoder_abpc);
	s_CoderResult_OVERFLOW = JNI_newGlobalRef(
		JNI_getStaticObjectField(result_class, overflow));
	s_CoderResult_UNDERFLOW = JNI_newGlobalRef(
		JNI_getStaticObjectField(result_class, underflow));
	s_CoderResult_throwException = PgObject_getJavaMethod(result_class,
		"throwException", "()V");
	s_CharBuffer_class = (jclass)JNI_newGlobalRef(
		PgObject_getJavaClass("java/nio/CharBuffer"));
	s_CharBuffer_wrap = PgObject_getStaticJavaMethod(s_CharBuffer_class,
		"wrap", "(Ljava/lang/CharSequence;)Ljava/nio/CharBuffer;");
	s_Buffer_position = PgObject_getJavaMethod(buffer_class,
		"position", "()I");
	s_Buffer_remaining = PgObject_getJavaMethod(buffer_class,
		"remaining", "()I");

	s_the_empty_string = JNI_newGlobalRef(
		JNI_callObjectMethod(empty, string_intern));

	s_two_step_conversion = two_step_when_ready;
	uninitialized = false;
}
