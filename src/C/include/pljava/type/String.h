/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#ifndef __pljava_type_String_h
#define __pljava_type_String_h

#include "pljava/type/Type.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * The String class extends the Type and adds the members necessary to
 * perform standard Postgres textin/textout conversion. An instance of this
 * class will be used for all types that are not explicitly mapped.
 * 
 * The class also has some convenience routings for Java String manipulation.
 * 
 * Author: Thomas Hallgren
 *
 **************************************************************************/

extern jclass s_String_class;
struct String_;
typedef struct String_* String;

/*
 * Create a Java String object from a null terminated string. Conversion is
 * made from the encoding used by the database into UTF8 used when creating
 * the Java String. NULL Is accepted as a valid input and will yield
 * a NULL result.
 */
extern jstring String_createJavaStringFromNTS(JNIEnv* env, const char* cp);

/*
 * Create a Java String object from a text. Conversion is made from the
 * encoding used by the database into UTF8 used when creating the Java String.
 * NULL Is accepted as a valid input and will yield a NULL result.
 */
extern jstring String_createJavaString(JNIEnv* env, text* cp);

/*
 * Create a null terminated string from a Java String. The UTF8 encoded string
 * obtained from the Java string is first converted into the encoding used by
 * the database.
 * The returned string is allocated using palloc. It's the callers responsability
 * to free it.
 */
extern char* String_createNTS(JNIEnv* env, jstring javaString);

/*
 * The UTF8 encoded string obtained from the Java string is first converted into
 * the encoding used by the database and then appended to the StringInfoData
 * buffer.
 */
extern void String_appendJavaString(JNIEnv* env, StringInfoData* buf, jstring javaString);

/*
 * Create a text from a Java String. The UTF8 encoded string obtained from
 * the Java string is first converted into the encoding used by the
 * database.
 * The returned text is allocated using palloc. It's the callers responsability
 * to free it.
 */
extern text* String_createText(JNIEnv* env, jstring javaString);

extern Type String_obtain(Oid typeId);

extern Type String_fromPgType(Oid typeId, Form_pg_type pgType);

extern String StringClass_obtain(TypeClass self, Oid typeId);

#ifdef __cplusplus
}
#endif
#endif
