/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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
#include <catalog/pg_constraint.h>
#include <catalog/pg_trigger.h>
#include <catalog/pg_transform.h>
#include <catalog/pg_am.h>
#include <catalog/pg_tablespace.h>
#include <catalog/pg_foreign_data_wrapper.h>
#include <catalog/pg_foreign_server.h>

#include <commands/trigger.h>

#include <executor/tuptable.h>

#include <mb/pg_wchar.h>

#include <nodes/execnodes.h>
#include <nodes/memnodes.h>

#include "org_postgresql_pljava_pg_CatalogObjectImpl_Factory.h"
#include "org_postgresql_pljava_pg_LookupImpl.h"
#include "org_postgresql_pljava_pg_ModelConstants.h"
#include "org_postgresql_pljava_pg_ModelConstants_Natives.h"
#include "org_postgresql_pljava_pg_TriggerImpl.h"
#include "org_postgresql_pljava_pg_TupleTableSlotImpl.h"

#include <utils/acl.h>
#include "org_postgresql_pljava_pg_AclItem.h"

#include <utils/rel.h>

#include "pljava/PgObject.h"
#include "pljava/ModelConstants.h"

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
	CONSTANTEXPR(SIZEOF_LONG, sizeof (long)),
	CONSTANTEXPR(SIZEOF_SIZE, sizeof (Size)),

	CONSTANT(ALIGNOF_SHORT),
	CONSTANT(ALIGNOF_INT),
	CONSTANT(ALIGNOF_DOUBLE),
	CONSTANT(MAXIMUM_ALIGNOF),

	CONSTANT(NAMEDATALEN),



	CONSTANTEXPR(SIZEOF_varatt_indirect, sizeof (varatt_indirect)),
	CONSTANTEXPR(SIZEOF_varatt_expanded, sizeof (varatt_expanded)),
	CONSTANTEXPR(SIZEOF_varatt_external, sizeof (varatt_external)),

	TYPEOFFSET(RelationData,Relation,rd_id),


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

	TYPEOFFSET(NullableDatum, NullableDatum, isnull),
	CONSTANTEXPR(SIZEOF_NullableDatum, sizeof (NullableDatum)),

	TYPEOFFSET(FunctionCallInfoBaseData, fcinfo, fncollation),
	TYPEOFFSET(FunctionCallInfoBaseData, fcinfo, isnull),
	TYPEOFFSET(FunctionCallInfoBaseData, fcinfo, nargs),
	TYPEOFFSET(FunctionCallInfoBaseData, fcinfo, args),

	TYPEOFFSET(Bitmapset, Bitmapset, words),



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
	TYPEOFFSET(MemoryContextData, MCTX, mem_allocated),
	TYPEOFFSET(MemoryContextData, MCTX, parent),
	TYPEOFFSET(MemoryContextData, MCTX, firstchild),
	TYPEOFFSET(MemoryContextData, MCTX, prevchild),
	TYPEOFFSET(MemoryContextData, MCTX, nextchild),
	TYPEOFFSET(MemoryContextData, MCTX, name),
	TYPEOFFSET(MemoryContextData, MCTX, ident),



	CONSTANT(N_ACL_RIGHTS),
	CONSTANT(BITS_PER_BITMAPWORD),



	CONSTANT(T_Invalid),
	CONSTANT(T_AggState),
	CONSTANT(T_CallContext),
	CONSTANT(T_EventTriggerData),
	CONSTANT(T_ReturnSetInfo),
	CONSTANT(T_TriggerData),
	CONSTANT(T_WindowAggState),
	CONSTANT(T_WindowObjectData),
#if PG_VERSION_NUM >= 160000
	CONSTANT(T_Bitmapset),
	CONSTANT(T_ErrorSaveContext),
#else
	CONSTANTEXPR(T_Bitmapset, T_Invalid),
	CONSTANTEXPR(T_ErrorSaveContext, T_Invalid),
#endif



	TYPEOFFSET(Trigger, TRG, tgoid),
	TYPEOFFSET(Trigger, TRG, tgname),
	TYPEOFFSET(Trigger, TRG, tgfoid),
	TYPEOFFSET(Trigger, TRG, tgtype),
	TYPEOFFSET(Trigger, TRG, tgenabled),
	TYPEOFFSET(Trigger, TRG, tgisinternal),
	TYPEOFFSET(Trigger, TRG, tgisclone),
	TYPEOFFSET(Trigger, TRG, tgconstrrelid),
	TYPEOFFSET(Trigger, TRG, tgconstrindid),
	TYPEOFFSET(Trigger, TRG, tgconstraint),
	TYPEOFFSET(Trigger, TRG, tgdeferrable),
	TYPEOFFSET(Trigger, TRG, tginitdeferred),
	TYPEOFFSET(Trigger, TRG, tgnargs),
	TYPEOFFSET(Trigger, TRG, tgnattr),
	TYPEOFFSET(Trigger, TRG, tgattr),
	TYPEOFFSET(Trigger, TRG, tgargs),
	TYPEOFFSET(Trigger, TRG, tgqual),
	TYPEOFFSET(Trigger, TRG, tgoldtable),
	TYPEOFFSET(Trigger, TRG, tgnewtable),
	CONSTANTEXPR(SIZEOF_Trigger, sizeof (Trigger)),



	TYPEOFFSET(TriggerData, TRGD, tg_event),
	TYPEOFFSET(TriggerData, TRGD, tg_relation),
	TYPEOFFSET(TriggerData, TRGD, tg_trigtuple),
	TYPEOFFSET(TriggerData, TRGD, tg_newtuple),
	TYPEOFFSET(TriggerData, TRGD, tg_trigger),
	TYPEOFFSET(TriggerData, TRGD, tg_updatedcols),

	TYPEOFFSET(ReturnSetInfo, RSI, allowedModes),
	TYPEOFFSET(ReturnSetInfo, RSI, isDone),
	TYPEOFFSET(ReturnSetInfo, RSI, returnMode),
	CONSTANTEXPR(SIZEOF_RSI_isDone, sizeof ((ReturnSetInfo *)0)->isDone),
	CONSTANTEXPR(SIZEOF_RSI_returnMode,sizeof ((ReturnSetInfo *)0)->returnMode),



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
	CONSTANT(CONSTROID),
	CONSTANT(TRFOID),
	CONSTANT(TRFTYPELANG),
	CONSTANT(AMOID),
	CONSTANT(TABLESPACEOID),
	CONSTANT(FOREIGNDATAWRAPPEROID),
	CONSTANT(FOREIGNSERVEROID),



};

#undef CONSTANT
#undef CONSTANTEXPR

static void dummy(Bitmapset *bitmapset, ReturnSetInfo *rsi)
{
	StaticAssertStmt(SIZEOF_DATUM == SIZEOF_VOID_P,
		"PostgreSQL SIZEOF_DATUM and SIZEOF_VOID_P no longer equivalent?");

	AssertVariableIsOfType(bitmapset->nwords, int); /* DatumUtils.java */
	AssertVariableIsOfType(rsi->allowedModes, int); /* LookupImpl.java */

/*
 * BEGIN:CONFIRMCONST for CatalogObjectImpl.Factory constants
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_CatalogObjectImpl_Factory_##c), \
	"Java/C value mismatch for " #c)

	CONFIRMCONST( InvalidOid );

	CONFIRMCONST(         TableSpaceRelationId );
	CONFIRMCONST(               TypeRelationId );
	CONFIRMCONST(          AttributeRelationId );
	CONFIRMCONST(          ProcedureRelationId );
	CONFIRMCONST(           RelationRelationId );
	CONFIRMCONST(             AuthIdRelationId );
	CONFIRMCONST(           DatabaseRelationId );
	CONFIRMCONST(      ForeignServerRelationId );
	CONFIRMCONST( ForeignDataWrapperRelationId );
	CONFIRMCONST(       AccessMethodRelationId );
	CONFIRMCONST(         ConstraintRelationId );
	CONFIRMCONST(           LanguageRelationId );
	CONFIRMCONST(          NamespaceRelationId );
	CONFIRMCONST(           OperatorRelationId );
	CONFIRMCONST(            TriggerRelationId );
	CONFIRMCONST(          ExtensionRelationId );
	CONFIRMCONST(          CollationRelationId );
	CONFIRMCONST(          TransformRelationId );
	CONFIRMCONST(       TSDictionaryRelationId );
	CONFIRMCONST(           TSConfigRelationId );

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
	CONFIRMCONST(       TRIGGEROID );
	CONFIRMCONST(     REGCONFIGOID );
	CONFIRMCONST( REGDICTIONARYOID );
	CONFIRMCONST(  REGNAMESPACEOID );
	CONFIRMCONST(       REGROLEOID );
	CONFIRMCONST(  REGCOLLATIONOID );

	/*
	 * The PG polymorphic pseudotypes. Of these, only ANYARRAYOID is in
	 * CatalogObject.Factory (because API has RegType.ANYARRAY), while the rest
	 * are in CatalogObjectImpl.Factory.
	 */
	CONFIRMCONST( ANYOID );

	CONFIRMCONST(      ANYARRAYOID );
	CONFIRMCONST(    ANYELEMENTOID );
	CONFIRMCONST(   ANYNONARRAYOID );
	CONFIRMCONST(       ANYENUMOID );
	CONFIRMCONST(      ANYRANGEOID );
#if PG_VERSION_NUM >= 140000
	CONFIRMCONST( ANYMULTIRANGEOID );

	CONFIRMCONST( ANYCOMPATIBLEMULTIRANGEOID );
#endif
#if PG_VERSION_NUM >= 130000
	CONFIRMCONST(           ANYCOMPATIBLEOID );
	CONFIRMCONST(      ANYCOMPATIBLEARRAYOID );
	CONFIRMCONST(   ANYCOMPATIBLENONARRAYOID );
	CONFIRMCONST(      ANYCOMPATIBLERANGEOID );
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

/*
 * END:CONFIRMCONST for CatalogObjectImpl.Factory constants
 *
 * BEGIN:CONFIRMCONST  for AclItem constants
 * BEGIN:CONFIRMOFFSET for AclItem constants
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_AclItem_##c), \
	"Java/C value mismatch for " #c)

#define CONFIRMOFFSET(typ,fld) \
StaticAssertStmt(offsetof(typ,fld) == \
(org_postgresql_pljava_pg_AclItem_OFFSET_##fld), \
	"Java/C offset mismatch for " #fld)

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
#if PG_VERSION_NUM >= 170000
	CONFIRMCONST( ACL_MAINTAIN     );
#endif
	CONFIRMCONST( ACL_ID_PUBLIC    );

	CONFIRMOFFSET( AclItem, ai_grantee );
	CONFIRMOFFSET( AclItem, ai_grantor );
	CONFIRMOFFSET( AclItem, ai_privs );

#undef CONFIRMCONST
#undef CONFIRMOFFSET

/*
 * END:CONFIRMCONST  for AclItem constants
 * END:CONFIRMOFFSET for AclItem constants
 *
 * BEGIN:CONFIRMCONST for ModelConstants constants
 * BEGIN:CONFIRMEXPR for ModelConstants constants
 *
 * BEGIN:CONFIRMSIZEOF for ModelConstants FormData_* structs
 * BEGIN:CONFIRMOFFSET for ModelConstants FormData_* structs
 * BEGIN:CONFIRMATTNUM for ModelConstants Anum_* constants
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_ModelConstants_##c), \
	"Java/C value mismatch for " #c)
#define CONFIRMEXPR(c,expr) \
StaticAssertStmt((expr) == \
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

	CONFIRMATTNUM( pg_extension, oid );
	CONFIRMCONST( ExtensionOidIndexId );

	CONFIRMATTNUM( pg_trigger, oid );
	CONFIRMCONST( TriggerOidIndexId );

#undef CONFIRMSIZEOF
#undef CONFIRMOFFSET
#undef CONFIRMATTNUM

/*
 * END:CONFIRMSIZEOF for ModelConstants FormData_* structs
 * END:CONFIRMOFFSET for ModelConstants FormData_* structs
 * END:CONFIRMATTNUM for ModelConstants Anum_* constants
 *
 * BEGIN:CONFIRMOFFSET for ModelConstants, arbitrary structs
 * BEGIN:CONFIRMSIZEOF for ModelConstants, arbitrary structs
 * BEGIN:CONFIRMSIZETAG lets the ModelConstants name use a shorter tag
 *
 * BEGIN:CONFIRMVLOFFSET for varlena offsets, which in Java don't count VARHDRSZ
 */

#define CONFIRMOFFSET(strct,fld) \
StaticAssertStmt(offsetof(strct,fld) == \
(org_postgresql_pljava_pg_ModelConstants_OFFSET_##strct##_##fld), \
	"Java/C offset mismatch for " #strct "." #fld)
#define CONFIRMSIZEOF(strct,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_ModelConstants_SIZEOF_##strct##_##fld), \
	"Java/C sizeof mismatch for " #strct "." #fld)
#define CONFIRMSIZETAG(strct,tag,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_ModelConstants_SIZEOF_##tag##_##fld), \
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

	CONFIRMEXPR( SIZEOF_NodeTag, sizeof (NodeTag) );
	CONFIRMEXPR( SIZEOF_Oid,     sizeof (Oid) );

	CONFIRMSIZETAG( FunctionCallInfoBaseData, fcinfo, fncollation );
	CONFIRMSIZETAG( FunctionCallInfoBaseData, fcinfo, isnull );
	CONFIRMSIZETAG( FunctionCallInfoBaseData, fcinfo, nargs );

#undef CONFIRMVLOFFSET
#undef CONFIRMSIZETAG
#undef CONFIRMSIZEOF
#undef CONFIRMOFFSET

#undef CONFIRMCONST
#undef CONFIRMEXPR

/*
 * END:CONFIRMOFFSET for ModelConstants, arbitrary structs
 * END:CONFIRMSIZEOF for ModelConstants, arbitrary structs
 * END:CONFIRMSIZETAG lets the ModelConstants name use a shorter tag
 * END:CONFIRMVLOFFSET for varlena offsets, which in Java don't count VARHDRSZ
 *
 * END:CONFIRMCONST for ModelConstants constants
 * END:CONFIRMEXPR for ModelConstants constants
 *
 * BEGIN:CONFIRMCONST for TupleTableSlotImpl constants
 * BEGIN:CONFIRMSIZEOF for TupleTableSlotImpl FormData_* structs
 * BEGIN:CONFIRMOFFSET for TupleTableSlotImpl FormData_* structs
 */

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

	CONFIRMOFFSET( NullableDatum, value );

#undef CONFIRMCONST
#undef CONFIRMSIZEOF
#undef CONFIRMOFFSET

/*
 * END:CONFIRMCONST for TupleTableSlotImpl constants
 * END:CONFIRMSIZEOF for TupleTableSlotImpl FormData_* structs
 * END:CONFIRMOFFSET for TupleTableSlotImpl FormData_* structs
 *
 * BEGIN:CONFIRMCONST for TriggerImpl constants
 * BEGIN:CONFIRMSIZEOF for TriggerImpl constants
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_TriggerImpl_##c), \
	"Java/C value mismatch for " #c)
#define CONFIRMSIZETAG(strct,tag,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_TriggerImpl_SIZEOF_##tag##_##fld), \
	"Java/C sizeof mismatch for " #strct "." #fld)

	CONFIRMCONST( TRIGGER_FIRES_ON_ORIGIN );
	CONFIRMCONST( TRIGGER_FIRES_ALWAYS );
	CONFIRMCONST( TRIGGER_FIRES_ON_REPLICA );
	CONFIRMCONST( TRIGGER_DISABLED );

	CONFIRMCONST( TRIGGER_TYPE_ROW );
	CONFIRMCONST( TRIGGER_TYPE_BEFORE );
	CONFIRMCONST( TRIGGER_TYPE_INSERT );
	CONFIRMCONST( TRIGGER_TYPE_DELETE );
	CONFIRMCONST( TRIGGER_TYPE_UPDATE );
	CONFIRMCONST( TRIGGER_TYPE_TRUNCATE );
	CONFIRMCONST( TRIGGER_TYPE_INSTEAD );

	CONFIRMCONST( TRIGGER_TYPE_LEVEL_MASK );
	CONFIRMCONST( TRIGGER_TYPE_STATEMENT );

	CONFIRMCONST( TRIGGER_TYPE_TIMING_MASK );
	CONFIRMCONST( TRIGGER_TYPE_AFTER );
	CONFIRMCONST( TRIGGER_TYPE_EVENT_MASK );

#undef CONFIRMCONST
#undef CONFIRMSIZETAG

/*
 * END:CONFIRMCONST for TriggerImpl constants
 * END:CONFIRMSIZETAG for TriggerImpl constants
 *
 * BEGIN:CONFIRMCONST for LookupImpl constants
 * BEGIN:CONFIRMOFFSET for LookupImpl constants
 * BEGIN:CONFIRMSIZEOF for LookupImpl constants
 * BEGIN:CONFIRMSIZETAG for LookupImpl constants
 */

#define CONFIRMCONST(c) \
StaticAssertStmt((c) == \
(org_postgresql_pljava_pg_LookupImpl_##c), \
	"Java/C value mismatch for " #c)
#define CONFIRMOFFSET(strct,fld) \
StaticAssertStmt(offsetof(strct,fld) == \
(org_postgresql_pljava_pg_LookupImpl_OFFSET_##strct##_##fld), \
	"Java/C offset mismatch for " #strct "." #fld)
#define CONFIRMSIZEOF(strct,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_LookupImpl_SIZEOF_##strct##_##fld), \
	"Java/C sizeof mismatch for " #strct "." #fld)
#define CONFIRMSIZETAG(strct,tag,fld) \
StaticAssertStmt((sizeof ((strct *)0)->fld) == \
(org_postgresql_pljava_pg_LookupImpl_SIZEOF_##tag##_##fld), \
	"Java/C sizeof mismatch for " #strct "." #fld)

	CONFIRMOFFSET( CallContext, atomic );
	CONFIRMSIZEOF( CallContext, atomic );

	CONFIRMSIZETAG( TriggerData, TRGD, tg_event );

	CONFIRMCONST( TRIGGER_EVENT_INSERT );
	CONFIRMCONST( TRIGGER_EVENT_DELETE );
	CONFIRMCONST( TRIGGER_EVENT_UPDATE );
	CONFIRMCONST( TRIGGER_EVENT_TRUNCATE );
	CONFIRMCONST( TRIGGER_EVENT_OPMASK );
	CONFIRMCONST( TRIGGER_EVENT_ROW );
	CONFIRMCONST( TRIGGER_EVENT_BEFORE );
	CONFIRMCONST( TRIGGER_EVENT_AFTER );
	CONFIRMCONST( TRIGGER_EVENT_INSTEAD );
	CONFIRMCONST( TRIGGER_EVENT_TIMINGMASK );
	CONFIRMCONST( FirstLowInvalidHeapAttributeNumber );

	CONFIRMCONST( SFRM_ValuePerCall );
	CONFIRMCONST( SFRM_Materialize );
	CONFIRMCONST( SFRM_Materialize_Random );
	CONFIRMCONST( SFRM_Materialize_Preferred );

	CONFIRMCONST( ExprSingleResult );
	CONFIRMCONST( ExprMultipleResult );
	CONFIRMCONST( ExprEndResult );

#undef CONFIRMCONST
#undef CONFIRMSIZETAG

/*
 * END:CONFIRMCONST for LookupImpl constants
 * END:CONFIRMSIZETAG for LookupImpl constants
 */
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
