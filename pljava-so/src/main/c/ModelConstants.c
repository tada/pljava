/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */

#include <postgres.h>

#if PG_VERSION_NUM < 140000
#include <catalog/indexing.h>
#endif
#include <catalog/pg_type.h>
#include <catalog/pg_attribute.h>
#include <catalog/pg_proc.h>
#include <catalog/pg_class.h>
#include <catalog/pg_authid.h>
#include <catalog/pg_language.h>
#include <catalog/pg_database.h>
#include <catalog/pg_namespace.h>
#include <catalog/pg_operator.h>
#include <catalog/pg_extension.h>
#include <catalog/pg_collation.h>
#include <catalog/pg_ts_dict.h>
#include <catalog/pg_ts_config.h>

#include <executor/tuptable.h>

#include <mb/pg_wchar.h>

#include <nodes/memnodes.h>

#include "org_postgresql_pljava_pg_CatalogObjectImpl_Factory.h"
#include "org_postgresql_pljava_pg_ModelConstants.h"
#include "org_postgresql_pljava_pg_ModelConstants_Natives.h"
#include "org_postgresql_pljava_pg_TupleTableSlotImpl.h"

#include <utils/acl.h>
#include "org_postgresql_pljava_pg_AclItem.h"

#include "pljava/PgObject.h"
#include "pljava/ModelConstants.h"

#if PG_VERSION_NUM < 120000
#define TupleDescData tupleDesc
#endif

/*
 * A compilation unit collecting various machine- or PostgreSQL-related
 * constants that have to be known in Java code. Those that are expected to be
 * stable can be defined in Java code, included from the Java-generated .h files
 * and simply confirmed here (in the otherwise-unused dummy() method) by static
 * assertions comparing them to the real values. Those that are expected to vary
 * (between PostgreSQL versions, or target platforms, or both) are a bit more
 * effort: their values are compiled into the constants[] array here, at indexes
 * known to the Java code, and the _statics() native method will return a direct
 * ByteBuffer through which the Java code can read them.
 *
 * To confirm the expected order of the array elements, each constant gets two
 * consecutive array members, first the expected index, then the value. The
 * CONSTANT macro below generates both, for the common case where the constant
 * is known here by the name FOO and the Java index is in static field IDX_FOO
 * in the ModelConstants class. CONSTANTEXPR is for the cases without that
 * direct name correspondence.
 *
 * NOCONSTANT supplies the value ModelConstants.NOCONSTANT, intended for when
 * the version of PG being built for does not define the constant in question
 * (and when the NOCONSTANT value wouldn't be a valid value of the constant!).
 */

#define CONSTANT(c) (org_postgresql_pljava_pg_ModelConstants_IDX_##c), (c)
#define CONSTANTEXPR(c,v) (org_postgresql_pljava_pg_ModelConstants_IDX_##c), (v)
#define NOCONSTANT(c) \
	CONSTANTEXPR(c,org_postgresql_pljava_pg_ModelConstants_NOCONSTANT)

#define FORMOFFSET(form,fld) \
	CONSTANTEXPR(OFFSET_##form##_##fld, offsetof(FormData_##form,fld))

#define TYPEOFFSET(type,tag,fld) \
	CONSTANTEXPR(OFFSET_##tag##_##fld, offsetof(type,fld))

static int32 constants[] = {
	CONSTANT(PG_VERSION_NUM),

	CONSTANT(SIZEOF_DATUM),
	CONSTANTEXPR(SIZEOF_INT, sizeof (int)),
	CONSTANTEXPR(SIZEOF_SIZE, sizeof (Size)),

	CONSTANT(ALIGNOF_SHORT),
	CONSTANT(ALIGNOF_INT),
	CONSTANT(ALIGNOF_DOUBLE),
	CONSTANT(MAXIMUM_ALIGNOF),

	CONSTANT(NAMEDATALEN),



	CONSTANTEXPR(SIZEOF_varatt_indirect, sizeof (varatt_indirect)),
	CONSTANTEXPR(SIZEOF_varatt_expanded, sizeof (varatt_expanded)),
	CONSTANTEXPR(SIZEOF_varatt_external, sizeof (varatt_external)),



	CONSTANT(HEAPTUPLESIZE),
	CONSTANTEXPR(OFFSET_TTS_NVALID, offsetof(TupleTableSlot, tts_nvalid)),
	CONSTANTEXPR(SIZEOF_TTS_NVALID, sizeof ((TupleTableSlot *)0)->tts_nvalid),

#if PG_VERSION_NUM >= 120000
	CONSTANT(TTS_FLAG_EMPTY),
	CONSTANT(TTS_FLAG_FIXED),
	CONSTANTEXPR(OFFSET_TTS_FLAGS, offsetof(TupleTableSlot, tts_flags)),
	NOCONSTANT(OFFSET_TTS_EMPTY),
	NOCONSTANT(OFFSET_TTS_FIXED),
	CONSTANTEXPR(OFFSET_TTS_TABLEOID, offsetof(TupleTableSlot, tts_tableOid)),
#else
	NOCONSTANT(TTS_FLAG_EMPTY),
	NOCONSTANT(TTS_FLAG_FIXED),
	NOCONSTANT(OFFSET_TTS_FLAGS),
	CONSTANTEXPR(OFFSET_TTS_EMPTY, offsetof(TupleTableSlot, tts_isempty)),
#if PG_VERSION_NUM >= 110000
	CONSTANTEXPR(OFFSET_TTS_FIXED,
		offsetof(TupleTableSlot, tts_fixedTupleDescriptor)),
#else
	NOCONSTANT(OFFSET_TTS_FIXED),
#endif /* 110000 */
	NOCONSTANT(OFFSET_TTS_TABLEOID),
#endif /* 120000 */



	CONSTANTEXPR(OFFSET_TUPLEDESC_ATTRS, offsetof(struct TupleDescData, attrs)),
	CONSTANTEXPR(OFFSET_TUPLEDESC_TDREFCOUNT,
		offsetof(struct TupleDescData, tdrefcount)),
	CONSTANTEXPR(SIZEOF_TUPLEDESC_TDREFCOUNT,
		sizeof ((struct TupleDescData *)0)->tdrefcount),
	CONSTANTEXPR(OFFSET_TUPLEDESC_TDTYPEID,
		offsetof(struct TupleDescData, tdtypeid)),
	CONSTANTEXPR(OFFSET_TUPLEDESC_TDTYPMOD,
		offsetof(struct TupleDescData, tdtypmod)),



	CONSTANTEXPR(SIZEOF_FORM_PG_ATTRIBUTE, sizeof (FormData_pg_attribute)),
	CONSTANT(ATTRIBUTE_FIXED_PART_SIZE),
	FORMOFFSET( pg_attribute, atttypid ),
	FORMOFFSET( pg_attribute, attlen ),
	FORMOFFSET( pg_attribute, attcacheoff ),
	FORMOFFSET( pg_attribute, atttypmod ),
	FORMOFFSET( pg_attribute, attbyval ),
	FORMOFFSET( pg_attribute, attalign ),
	FORMOFFSET( pg_attribute, attnotnull ),
	FORMOFFSET( pg_attribute, attisdropped ),



	CONSTANT(CLASS_TUPLE_SIZE),
	CONSTANT( Anum_pg_class_reltype ),



	CONSTANTEXPR(SIZEOF_MCTX, sizeof (MemoryContextData)),
	TYPEOFFSET(MemoryContextData, MCTX, isReset),
#if PG_VERSION_NUM >= 130000
	TYPEOFFSET(MemoryContextData, MCTX, mem_allocated),
#else
	NOCONSTANT(OFFSET_MCTX_mem_allocated),
#endif
	TYPEOFFSET(MemoryContextData, MCTX, parent),
	TYPEOFFSET(MemoryContextData, MCTX, firstchild),
	TYPEOFFSET(MemoryContextData, MCTX, prevchild),
	TYPEOFFSET(MemoryContextData, MCTX, nextchild),
	TYPEOFFSET(MemoryContextData, MCTX, name),
	TYPEOFFSET(MemoryContextData, MCTX, ident),



	CONSTANT(N_ACL_RIGHTS),



	CONSTANT(ATTNUM),
	CONSTANT(AUTHMEMMEMROLE),
	CONSTANT(AUTHMEMROLEMEM),
	CONSTANT(AUTHOID),
	CONSTANT(COLLOID),
	CONSTANT(DATABASEOID),
	CONSTANT(LANGOID),
	CONSTANT(NAMESPACEOID),
	CONSTANT(OPEROID),
	CONSTANT(PROCOID),
	CONSTANT(RELOID),
	CONSTANT(TSCONFIGOID),
	CONSTANT(TSDICTOID),
	CONSTANT(TYPEOID),



};

#undef CONSTANT
#undef CONSTANTEXPR

static void dummy()
{
	StaticAssertStmt(SIZEOF_DATUM == SIZEOF_VOID_P,
		"PostgreSQL SIZEOF_DATUM and SIZEOF_VOID_P no longer equivalent?");

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_CatalogObjectImpl_Factory_##c), \
	"Java/C value mismatch for " #c)

	CONFIRMCONST( InvalidOid );

	CONFIRMCONST(         TypeRelationId );
	CONFIRMCONST(    AttributeRelationId );
	CONFIRMCONST(    ProcedureRelationId );
	CONFIRMCONST(     RelationRelationId );
	CONFIRMCONST(       AuthIdRelationId );
	CONFIRMCONST(     DatabaseRelationId );
	CONFIRMCONST(     LanguageRelationId );
	CONFIRMCONST(    NamespaceRelationId );
	CONFIRMCONST(     OperatorRelationId );
	CONFIRMCONST(    ExtensionRelationId );
	CONFIRMCONST(    CollationRelationId );
	CONFIRMCONST( TSDictionaryRelationId );
	CONFIRMCONST(     TSConfigRelationId );

	/*
	 * PG types good to have around because of corresponding JDBC types.
	 */
	CONFIRMCONST(        BOOLOID );
	CONFIRMCONST(       BYTEAOID );
	CONFIRMCONST(        CHAROID );
	CONFIRMCONST(        INT8OID );
	CONFIRMCONST(        INT2OID );
	CONFIRMCONST(        INT4OID );
	CONFIRMCONST(         XMLOID );
	CONFIRMCONST(      FLOAT4OID );
	CONFIRMCONST(      FLOAT8OID );
	CONFIRMCONST(      BPCHAROID );
	CONFIRMCONST(     VARCHAROID );
	CONFIRMCONST(        DATEOID );
	CONFIRMCONST(        TIMEOID );
	CONFIRMCONST(   TIMESTAMPOID );
	CONFIRMCONST( TIMESTAMPTZOID );
	CONFIRMCONST(      TIMETZOID );
	CONFIRMCONST(         BITOID );
	CONFIRMCONST(      VARBITOID );
	CONFIRMCONST(     NUMERICOID );

	/*
	 * PG types not mentioned in JDBC but bread-and-butter to PG devs.
	 */
	CONFIRMCONST(        TEXTOID );
	CONFIRMCONST(     UNKNOWNOID );
	CONFIRMCONST(      RECORDOID );
	CONFIRMCONST(     CSTRINGOID );
	CONFIRMCONST(        VOIDOID );

	/*
	 * PG types used in modeling PG types themselves.
	 */
	CONFIRMCONST(          NAMEOID );
	CONFIRMCONST(       REGPROCOID );
	CONFIRMCONST(           OIDOID );
	CONFIRMCONST(  PG_NODE_TREEOID );
	CONFIRMCONST(       ACLITEMOID );
	CONFIRMCONST(  REGPROCEDUREOID );
	CONFIRMCONST(       REGOPEROID );
	CONFIRMCONST(   REGOPERATOROID );
	CONFIRMCONST(      REGCLASSOID );
	CONFIRMCONST(       REGTYPEOID );
	CONFIRMCONST(     REGCONFIGOID );
	CONFIRMCONST( REGDICTIONARYOID );
	CONFIRMCONST(  REGNAMESPACEOID );
	CONFIRMCONST(       REGROLEOID );
#if PG_VERSION_NUM >= 130000
	CONFIRMCONST(  REGCOLLATIONOID );
#endif

	/*
	 * The well-known, pinned procedural languages.
	 */
	CONFIRMCONST( INTERNALlanguageId );
	CONFIRMCONST(        ClanguageId );
	CONFIRMCONST(      SQLlanguageId );

	/*
	 * The well-known, pinned namespaces.
	 */
	CONFIRMCONST( PG_CATALOG_NAMESPACE );
	CONFIRMCONST(   PG_TOAST_NAMESPACE );

	/*
	 * The well-known, pinned collations.
	 */
	CONFIRMCONST( DEFAULT_COLLATION_OID );
	CONFIRMCONST(       C_COLLATION_OID );
	CONFIRMCONST(   POSIX_COLLATION_OID );

#undef CONFIRMCONST

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_AclItem_##c), \
	"Java/C value mismatch for " #c)

	CONFIRMCONST( ACL_INSERT       );
	CONFIRMCONST( ACL_SELECT       );
	CONFIRMCONST( ACL_UPDATE       );
	CONFIRMCONST( ACL_DELETE       );
	CONFIRMCONST( ACL_TRUNCATE     );
	CONFIRMCONST( ACL_REFERENCES   );
	CONFIRMCONST( ACL_TRIGGER      );
	CONFIRMCONST( ACL_EXECUTE      );
	CONFIRMCONST( ACL_USAGE        );
	CONFIRMCONST( ACL_CREATE       );
	CONFIRMCONST( ACL_CREATE_TEMP  );
	CONFIRMCONST( ACL_CONNECT      );
#if PG_VERSION_NUM >= 150000
	CONFIRMCONST( ACL_SET          );
	CONFIRMCONST( ACL_ALTER_SYSTEM);
#endif
	CONFIRMCONST( ACL_ID_PUBLIC    );

#define CONFIRMOFFSET(typ,fld) \
StaticAssertStmt(offsetof(typ,fld) == \
(org_postgresql_pljava_pg_AclItem_OFFSET_##fld), \
	"Java/C offset mismatch for " #fld)

	CONFIRMOFFSET( AclItem, ai_grantee );
	CONFIRMOFFSET( AclItem, ai_grantor );
	CONFIRMOFFSET( AclItem, ai_privs );

#undef CONFIRMCONST
#undef CONFIRMOFFSET

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_ModelConstants_##c), \
	"Java/C value mismatch for " #c)
#define CONFIRMSIZEOF(form,fld) \
StaticAssertStmt((sizeof ((FormData_##form *)0)->fld) == \
(org_postgresql_pljava_pg_ModelConstants_SIZEOF_##form##_##fld), \
	"Java/C sizeof mismatch for " #form "." #fld)
#define CONFIRMOFFSET(form,fld) \
StaticAssertStmt(offsetof(FormData_##form,fld) == \
(org_postgresql_pljava_pg_ModelConstants_OFFSET_##form##_##fld), \
	"Java/C offset mismatch for " #form "." #fld)
#define CONFIRMATTNUM(form,fld) \
StaticAssertStmt(Anum_##form##_##fld == \
(org_postgresql_pljava_pg_ModelConstants_Anum_##form##_##fld), \
	"Java/C attribute number mismatch for " #form "." #fld)
#define CONFIRMEXPR(c,expr) \
StaticAssertStmt((expr) == \
(org_postgresql_pljava_pg_ModelConstants_##c), \
	"Java/C value mismatch for " #c)

	CONFIRMCONST( PG_SQL_ASCII );
	CONFIRMCONST( PG_UTF8 );
	CONFIRMCONST( PG_LATIN1 );
	CONFIRMCONST( PG_ENCODING_BE_LAST );

	CONFIRMCONST( VARHDRSZ );
	CONFIRMCONST( VARHDRSZ_EXTERNAL );
	CONFIRMCONST( VARTAG_INDIRECT );
	CONFIRMCONST( VARTAG_EXPANDED_RO );
	CONFIRMCONST( VARTAG_EXPANDED_RW );
	CONFIRMCONST( VARTAG_ONDISK );

	CONFIRMATTNUM( pg_attribute, attname );

	CONFIRMSIZEOF( pg_attribute, atttypid );
	CONFIRMSIZEOF( pg_attribute, attlen );
	CONFIRMSIZEOF( pg_attribute, attcacheoff );
	CONFIRMSIZEOF( pg_attribute, atttypmod );
	CONFIRMSIZEOF( pg_attribute, attbyval );
	CONFIRMSIZEOF( pg_attribute, attalign );
	CONFIRMSIZEOF( pg_attribute, attnotnull );
	CONFIRMSIZEOF( pg_attribute, attisdropped );

#if PG_VERSION_NUM >= 120000
	CONFIRMATTNUM( pg_extension, oid );
#endif
	CONFIRMCONST( ExtensionOidIndexId );

#undef CONFIRMSIZEOF
#undef CONFIRMOFFSET
#define CONFIRMSIZEOF(strct,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_ModelConstants_SIZEOF_##strct##_##fld), \
	"Java/C sizeof mismatch for " #strct "." #fld)
#define CONFIRMVLOFFSET(strct,fld) \
StaticAssertStmt(offsetof(strct,fld) - VARHDRSZ == \
(org_postgresql_pljava_pg_ModelConstants_OFFSET_##strct##_##fld), \
	"Java/C offset mismatch for " #strct "." #fld)

	CONFIRMSIZEOF( ArrayType, ndim );
	CONFIRMSIZEOF( ArrayType, dataoffset );
	CONFIRMSIZEOF( ArrayType, elemtype );

	CONFIRMVLOFFSET( ArrayType, ndim );
	CONFIRMVLOFFSET( ArrayType, dataoffset );
	CONFIRMVLOFFSET( ArrayType, elemtype );

#if 0
	/*
	 * Given the way ARR_DIMS is defined in PostgreSQL's array.h, there seems
	 * to be no way to construct a static assertion for this offset acceptable
	 * to a compiler that forbids "the conversions of a reinterpret_cast" in
	 * a constant expression. This will have to be checked in an old-fashioned
	 * runtime assertion in _initialize, losing the benefit of compile-time
	 * detection.
	 */
	CONFIRMEXPR( OFFSET_ArrayType_DIMS,
		(((char*)ARR_DIMS(0)) - (char *)0) - VARHDRSZ );
#endif

	CONFIRMEXPR( SIZEOF_ArrayType_DIM, sizeof *ARR_DIMS(0) );

#undef CONFIRMSIZEOF
#undef CONFIRMVLOFFSET
#undef CONFIRMCONST
#undef CONFIRMATTNUM
#undef CONFIRMEXPR

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_TupleTableSlotImpl_##c), \
	"Java/C value mismatch for " #c)
#define CONFIRMSIZEOF(form,fld) \
StaticAssertStmt((sizeof ((form *)0)->fld) == \
(org_postgresql_pljava_pg_TupleTableSlotImpl_SIZEOF_##form##_##fld), \
	"Java/C sizeof mismatch for " #form "." #fld)
#define CONFIRMOFFSET(form,fld) \
StaticAssertStmt(offsetof(form,fld) == \
(org_postgresql_pljava_pg_TupleTableSlotImpl_OFFSET_##form##_##fld), \
	"Java/C offset mismatch for " #form "." #fld)

	CONFIRMOFFSET( HeapTupleData, t_len );
	CONFIRMOFFSET( HeapTupleData, t_tableOid );

	CONFIRMSIZEOF( HeapTupleData, t_len );
	CONFIRMSIZEOF( HeapTupleData, t_tableOid );

	CONFIRMOFFSET( HeapTupleHeaderData, t_infomask );
	CONFIRMOFFSET( HeapTupleHeaderData, t_infomask2 );
	CONFIRMOFFSET( HeapTupleHeaderData, t_hoff );
	CONFIRMOFFSET( HeapTupleHeaderData, t_bits );

	CONFIRMSIZEOF( HeapTupleHeaderData, t_infomask );
	CONFIRMSIZEOF( HeapTupleHeaderData, t_infomask2 );
	CONFIRMSIZEOF( HeapTupleHeaderData, t_hoff );

	CONFIRMCONST( HEAP_HASNULL );
	CONFIRMCONST( HEAP_HASEXTERNAL );
	CONFIRMCONST( HEAP_NATTS_MASK );

#undef CONFIRMCONST
#undef CONFIRMSIZEOF
#undef CONFIRMOFFSET

}

void pljava_ModelConstants_initialize(void)
{
	ArrayType dummyArray;
	jclass cls;

	JNINativeMethod methods[] =
	{
		{
		"_statics",
		"()Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_ModelConstants_00024Natives__1statics
		},
		{ 0, 0, 0 },
		{ 0, 0, dummy } /* so C compiler won't warn that dummy is unused */
	};

	cls = PgObject_getJavaClass(
		"org/postgresql/pljava/pg/ModelConstants$Natives");
	PgObject_registerNatives2(cls, methods);
	JNI_deleteLocalRef(cls);

	/*
	 * Don't really use PostgreSQL Assert for this; it goes behind elog's back.
	 */
	if (org_postgresql_pljava_pg_ModelConstants_OFFSET_ArrayType_DIMS !=
		(((char*)ARR_DIMS(&dummyArray)) - (char *)&dummyArray) - VARHDRSZ )
		elog(ERROR,
			"PL/Java built with mismatched value for OFFSET_ArrayType_DIMS");
}

/*
 * Class:     org_postgresql_pljava_pg_ModelConstants_Natives
 * Method:    _statics
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_ModelConstants_00024Natives__1statics(JNIEnv* env, jobject _cls)
{
	/*
	 * None of the usual PL/Java BEGIN_NATIVE fencing here, because this is not
	 * a call into PostgreSQL; it's pure JNI to grab a static constant address.
	 */
	return (*env)->NewDirectByteBuffer(env, constants, sizeof constants);
}
