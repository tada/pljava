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
 * @author Thomas Hallgren
 *
 * Since commit 639a86e in PostgreSQL upstream, this struct can no longer
 * have the name String, which is why it is now PLJString but in files still
 * named String.[hc].
 *
 **************************************************************************/

extern jclass s_Object_class;
extern jclass s_String_class;
struct String_;
typedef struct String_* PLJString;

/*
 * Create a Java String object from a null terminated string. Conversion is
 * made from the encoding used by the database into UTF8 used when creating
 * the Java String. NULL Is accepted as a valid input and will yield
 * a NULL result.
 */
extern jstring String_createJavaStringFromNTS(const char* cp);

/*
 * Create a Java String object from a text. Conversion is made from the
 * encoding used by the database into UTF8 used when creating the Java String.
 * NULL Is accepted as a valid input and will yield a NULL result.
 */
extern jstring String_createJavaString(text* cp);

/*
 * Create a null terminated string from a Java String. The UTF8 encoded string
 * obtained from the Java string is first converted into the encoding used by
 * the database.
 * The returned string is allocated using palloc. It's the callers responsability
 * to free it.
 */
extern char* String_createNTS(jstring javaString);

/*
 * The UTF8 encoded string obtained from the Java string is first converted into
 * the encoding used by the database and then appended to the StringInfoData
 * buffer.
 */
extern void String_appendJavaString(StringInfoData* buf, jstring javaString);

/*
 * Create a text from a Java String. The UTF8 encoded string obtained from
 * the Java string is first converted into the encoding used by the
 * database.
 * The returned text is allocated using palloc. It's the callers responsability
 * to free it.
 */
extern text* String_createText(jstring javaString);

extern Type String_obtain(Oid typeId);

extern PLJString StringClass_obtain(TypeClass self, Oid typeId);

#ifdef __cplusplus
}
#endif
#endif
