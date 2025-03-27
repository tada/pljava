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
#include <funcapi.h>
#include <miscadmin.h>
#include <access/genam.h>
#include <access/heaptoast.h>
#include <access/relation.h>
#include <access/tupdesc.h>
#include <commands/event_trigger.h>
#include <executor/spi.h>
#include <executor/tuptable.h>
#include <mb/pg_wchar.h>
#if PG_VERSION_NUM >= 160000
#include <nodes/miscnodes.h>
#endif
#include <utils/fmgroids.h>
#include <utils/inval.h>
#include <utils/rel.h>
#include <utils/resowner.h>
#include <utils/typcache.h>

#include "pljava/Backend.h"
#include "pljava/Exception.h"
#include "pljava/Invocation.h"
#include "pljava/PgObject.h"
#include "pljava/ModelUtils.h"
#include "pljava/VarlenaWrapper.h"

#include "org_postgresql_pljava_internal_SPI.h"
#include "org_postgresql_pljava_internal_SPI_EarlyNatives.h"

#include "org_postgresql_pljava_pg_CatalogObjectImpl_Addressed.h"
#include "org_postgresql_pljava_pg_CatalogObjectImpl_Factory.h"
#include "org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_DatumUtils.h"
#include "org_postgresql_pljava_pg_LookupImpl.h"
#include "org_postgresql_pljava_pg_MemoryContextImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_ResourceOwnerImpl_EarlyNatives.h"
#include "org_postgresql_pljava_pg_TupleDescImpl.h"
#include "org_postgresql_pljava_pg_TupleTableSlotImpl.h"

/*
 * A compilation unit collecting various native methods used in the pg model
 * implementation classes. This is something of a break with past PL/Java
 * practice of having a correspondingly-named C file for a Java class, made on
 * the belief that there won't be that many new methods here, and they will make
 * more sense collected together.
 *
 * Some of the native methods here may *not* include the elaborate fencing seen
 * in other PL/Java native methods, if they involve trivially simple functions
 * that do not require calling into PostgreSQL or other non-thread-safe code.
 * This is, of course, a careful exception made to the general rule. The calling
 * Java code is expected to have good reason to believe any state to be examined
 * by these methods won't be shifting underneath them.
 */

static jclass s_CatalogObjectImpl_Factory_class;
static jmethodID s_CatalogObjectImpl_Factory_invalidateRelation;
static jmethodID s_CatalogObjectImpl_Factory_syscacheInvalidate;

static jclass s_LookupImpl_class;
static jmethodID s_LookupImpl_dispatchNew;
static jmethodID s_LookupImpl_dispatch;
static jmethodID s_LookupImpl_dispatchInline;

static jclass s_MemoryContextImpl_class;
static jmethodID s_MemoryContextImpl_callback;
static void memoryContextCallback(void *arg);

static jclass s_ResourceOwnerImpl_class;
static jmethodID s_ResourceOwnerImpl_callback;
static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg);

static jclass s_TupleDescImpl_class;
static jmethodID s_TupleDescImpl_fromByteBuffer;

static jclass s_TupleTableSlotImpl_class;
static jmethodID s_TupleTableSlotImpl_newDeformed;

static void relCacheCB(Datum arg, Oid relid);
static void sysCacheCB(Datum arg, int cacheid, uint32 hash);

jobject pljava_TupleDescriptor_create(TupleDesc tupdesc, Oid reloid)
{
	jlong tupdesc_size = (jlong)TupleDescSize(tupdesc);
	jobject td_b = JNI_newDirectByteBuffer(tupdesc, tupdesc_size);

	jobject result = JNI_callStaticObjectMethodLocked(s_TupleDescImpl_class,
		s_TupleDescImpl_fromByteBuffer,
		td_b,
		(jint)tupdesc->tdtypeid, (jint)tupdesc->tdtypmod,
		(jint)reloid, (jint)tupdesc->tdrefcount);

	JNI_deleteLocalRef(td_b);
	return result;
}

/*
 * If NULL is passed for jtd, a Java TupleDescriptor will be created here from
 * tupdesc. Otherwise, the passed jtd must be a JNI local reference to an
 * existing Java TupleDescriptor corresponding to tupdesc, and on return, the
 * JNI local reference will have been deleted.
 */
jobject pljava_TupleTableSlot_create(
	TupleDesc tupdesc, jobject jtd, const TupleTableSlotOps *tts_ops, Oid reloid)
{
	int natts = tupdesc->natts;
	TupleTableSlot *tts = MakeSingleTupleTableSlot(tupdesc, tts_ops);
	jobject tts_b = JNI_newDirectByteBuffer(tts, (jlong)sizeof *tts);
	jobject vals_b = JNI_newDirectByteBuffer(tts->tts_values,
		(jlong)(natts * sizeof *tts->tts_values));
	jobject nuls_b = JNI_newDirectByteBuffer(tts->tts_isnull, (jlong)natts);
	jobject jtts;

	if ( NULL == jtd )
		jtd = pljava_TupleDescriptor_create(tupdesc, reloid);

	jtts = JNI_callStaticObjectMethodLocked(s_TupleTableSlotImpl_class,
		s_TupleTableSlotImpl_newDeformed, tts_b, jtd, vals_b, nuls_b);

	JNI_deleteLocalRef(nuls_b);
	JNI_deleteLocalRef(vals_b);
	JNI_deleteLocalRef(jtd);
	JNI_deleteLocalRef(tts_b);

	return jtts;
}

typedef struct RegProcedureLookup
{
	/*
	 * This member caches a JNI global reference to the Java RegProcedure.Lookup
	 * corresponding to the flinfo whose fn_extra member points here. The JNI
	 * global reference must be deleted when fn_mcxt goes away.
	 */
	jobject lookup;
	/*
	 * Tag and address of the fn_expr most recently seen here. If changed,
	 * the Java object may need to invalidate some cached information.
	 *
	 * No address retained in this struct from an earlier call is in any way
	 * assumed to be valid, other than for comparison to a corresponding address
	 * supplied in the current call.
	 */
	NodeTag exprTag;
	Node *expr;
	/*
	 * Members below hold most-recently seen values associated with fcinfo
	 * pointing to this flinfo. For any item whose tag and address (or nargs and
	 * address) have not changed, a new Java ByteBuffer needn't be created, as
	 * one retained from the earlier call still fits.
	 */
	short nargs;
	FunctionCallInfo fcinfo;
	NodeTag contextTag;
	Node *context;
	NodeTag resultinfoTag;
	Node *resultinfo;
}
RegProcedureLookup;

/*
 * At the time of writing, all of these nodes appear (happily) to be of fixed
 * size. (Even the one that is private.)
 */
static inline Size nodeTagToSize(NodeTag tag)
{
#define TO_SIZE(t) case T_##t: return sizeof (t)
	switch ( tag )
	{
		TO_SIZE(AggState);
		TO_SIZE(CallContext);
#if PG_VERSION_NUM >= 160000
		TO_SIZE(ErrorSaveContext);
#endif
		TO_SIZE(EventTriggerData);
		TO_SIZE(ReturnSetInfo);
		TO_SIZE(TriggerData);
		TO_SIZE(WindowAggState);
#if 0 /* this struct is private in nodeWindowAgg.c */
		TO_SIZE(WindowObjectData);
#endif
	default:
		return 0; /* never a valid Node size */
	}
#undef TO_SIZE
}

void pljava_ModelUtils_inlineDispatch(PG_FUNCTION_ARGS)
{
	Size len;
	jobject src;
	InlineCodeBlock *codeblock =
		castNode(InlineCodeBlock, DatumGetPointer(PG_GETARG_DATUM(0)));

	len = strlen(codeblock->source_text);
	src = JNI_newDirectByteBuffer(codeblock->source_text, (jlong)len);

	/*
	 * The atomic flag will also be passed to the handler in case it cares,
	 * but recording it in currentInvocation for SPI's use should always happen
	 * and this is the simplest place to do it.
	 */
	currentInvocation->nonAtomic = ! codeblock->atomic;

	JNI_callStaticVoidMethod(s_LookupImpl_class, s_LookupImpl_dispatchInline,
		(jint)codeblock->langOid, (jboolean)codeblock->atomic, src);
}

Datum pljava_ModelUtils_callDispatch(PG_FUNCTION_ARGS, bool forValidator)
{
	FmgrInfo *flinfo = fcinfo->flinfo;
	Oid oid = flinfo->fn_oid;
	MemoryContext mcxt = flinfo->fn_mcxt;
	Node *expr = flinfo->fn_expr;
	RegProcedureLookup *extra = (RegProcedureLookup *)flinfo->fn_extra;
	short nargs = fcinfo->nargs;
	Node *context = fcinfo->context;
	Node *resultinfo = fcinfo->resultinfo;
	NodeTag exprTag = T_Invalid;
	NodeTag contextTag = T_Invalid;
	NodeTag resultinfoTag = T_Invalid;
	jboolean j4v = forValidator ? JNI_TRUE : JNI_FALSE;
	jboolean hasExpr = NULL != expr ? JNI_TRUE : JNI_FALSE;
	jboolean newExpr = JNI_FALSE;
	jobject fcinfo_b = NULL;
	jobject context_b = NULL;
	jobject resultinfo_b = NULL;
	jobject lookup = NULL;
	Size size;
	Ptr2Long p2l_mcxt;
	Ptr2Long p2l_extra;

	/*
	 * If the caller has supplied an expression node representing the call site,
	 * get its tag. The handler can use the information to, for example, resolve
	 * the types of polymorphic parameters to concrete types from the call site.
	 */
	if ( NULL != expr )
		exprTag = nodeTag(expr);

	/*
	 * If the caller has supplied a context node with extra information about
	 * the call, get its tag. The handler will be able to consult its contents.
	 *
	 * The atomic flag (if it is a CallContext) or TriggerData (if that's what
	 * it is) will be recorded in currentInvocation right here, so that always
	 * happens without attention from the handler.
	 */
	if ( NULL != context )
	{
		contextTag = nodeTag(context);

		if ( T_CallContext == contextTag )
			currentInvocation->nonAtomic = ! ((CallContext *)context)->atomic;
		else if ( T_TriggerData == contextTag )
			currentInvocation->triggerData = (TriggerData *)context;
	}

	/*
	 * If the caller has supplied a resultinfo node to control how results are
	 * returned, get its tag.
	 */
	if ( NULL != resultinfo )
		resultinfoTag = nodeTag(resultinfo);

	/*
	 * If there is a RegProcedureLookup struct that was saved in extra during
	 * an earlier look at this call site, recover the existing Java LookupImpl
	 * object to call its dispatch method. A new ByteBuffer covering an fcinfo,
	 * context, or resultinfo struct, respectively, will be passed only if the
	 * presence, type, size, or location of the struct has changed; if not, a
	 * ByteBuffer from the earlier encounter can be used again. The newExpr and
	 * hasExpr params likewise indicate whether LookupImpl needs to refresh any
	 * expression information possibly cached from before. The target routine
	 * oid is passed here only as a sanity check; it had better match the one
	 * used when the LookupImpl was constructed.
	 *
	 * This block returns to the caller after invoking dispatch(...) and
	 * handling the result. XXX Result handling yet to be implemented; only
	 * returns void for now (the caller will see null if the handler poked
	 * fcinfo->isnull).
	 */
	if ( NULL != extra )
	{
		lookup = extra->lookup;
		Assert(NULL != lookup); /* extra with null lookup shouldn't be seen */

		if ( exprTag != extra->exprTag  ||  expr != extra->expr )
		{
			newExpr = JNI_TRUE;
			extra->exprTag = exprTag;
			extra->expr = expr;
		}

		if ( nargs != extra->nargs  ||  fcinfo != extra->fcinfo )
		{
			size = SizeForFunctionCallInfo(nargs);
			fcinfo_b = JNI_newDirectByteBuffer(fcinfo, (jlong)size);
			extra->nargs = nargs;
			extra->fcinfo = fcinfo;
		}

		if ( contextTag != extra->contextTag  ||  context != extra->context )
		{
			/*
			 * The size will be zero if it's a tag we don't support. The case of
			 * a change from an earlier-seen value *to* one we don't support is
			 * probably unreachable, but if it were to happen, we would need
			 * a way to tell the Java code not to go on using some stale buffer
			 * from before. Sending a zero-length buffer suffices for that; the
			 * inefficiency is of little concern considering it probably never
			 * happens, and it avoids passing an additional argument (just for
			 * something that probably never happens).
			 */
			size = nodeTagToSize(contextTag);
			context_b = JNI_newDirectByteBuffer(context, (jlong)size);
			extra->contextTag = contextTag;
			extra->context = context;
		}

		if ( resultinfoTag != extra->resultinfoTag
			||  resultinfo != extra->resultinfo )
		{
			size = nodeTagToSize(resultinfoTag);
			resultinfo_b = JNI_newDirectByteBuffer(resultinfo, (jlong)size);
			extra->resultinfoTag = resultinfoTag;
			extra->resultinfo = resultinfo;
		}

		JNI_callVoidMethod(lookup, s_LookupImpl_dispatch,
			oid, newExpr, hasExpr, fcinfo_b, context_b, resultinfo_b);

		PG_RETURN_VOID(); /* XXX for now */
	}

	/*
	 * Arrival here means extra was NULL: no Java LookupImpl exists yet.
	 * A RegProcedureLookup struct will be freshly allocated in the
	 * flinfo->fn_mcxt memory context and saved as flinfo->fn_extra, and
	 * LookupImpl's static dispatchNew method will be called. The new C struct
	 * will end up holding a JNI global reference to the new LookupImpl thanks
	 * to a _cacheReference JNI callback (below in this file) made in the course
	 * of dispatchNew.
	 *
	 * The remainder of the RegProcedureLookup struct is populated here with
	 * the tags and addresses of any expr, context, or resultinfo nodes supplied
	 * by the caller, and the argument count and address of the caller-supplied
	 * fcinfo. Those will be used on subsequent calls to notice if the presence,
	 * tag (hence likely size), or address of any of those pieces has changed.
	 *
	 * dispatchNew is passed the memory context of the RegProcedureLookup
	 * struct, to bound its lifespan; when the context is reset, the JNI global
	 * ref to the LookupImpl instance will be released. The method is also
	 * passed the fn_extra address (for use by the _cacheReference callback),
	 * the target routine oid, forValidator and hasExpr flags, and ByteBuffers
	 * windowing the fcinfo struct, and the context and resultinfo structs when
	 * present.
	 *
	 * Once dispatchNew returns, any returned result needs appropriate handling.
	 * XXX For now, void is unconditionally returned (the caller will see null
	 * if the handler has poked fcinfo->isnull).
	 */

	extra = MemoryContextAllocZero(mcxt, sizeof *extra);

	if ( T_Invalid != exprTag )
	{
		extra->exprTag = exprTag;
		extra->expr = expr;
	}

	if ( T_Invalid != contextTag )
	{
		extra->contextTag = contextTag;
		extra->context = context;
		size = nodeTagToSize(contextTag);
		if ( 0 < size )
			context_b = JNI_newDirectByteBuffer(context, (jlong)size);
	}

	if ( T_Invalid != resultinfoTag )
	{
		extra->resultinfoTag = resultinfoTag;
		extra->resultinfo = resultinfo;
		size = nodeTagToSize(resultinfoTag);
		if ( 0 < size )
			resultinfo_b = JNI_newDirectByteBuffer(resultinfo, (jlong)size);
	}

	extra->nargs = nargs;
	extra->fcinfo = fcinfo;
	size = SizeForFunctionCallInfo(nargs);
	fcinfo_b = JNI_newDirectByteBuffer(fcinfo, (jlong)size);

	p2l_mcxt.longVal = 0;
	p2l_mcxt.ptrVal = mcxt;

	p2l_extra.longVal = 0;
	p2l_extra.ptrVal = extra;

	flinfo->fn_extra = extra;

	JNI_callStaticVoidMethod(s_LookupImpl_class, s_LookupImpl_dispatchNew,
		p2l_mcxt.longVal, p2l_extra.longVal,
		oid, j4v, hasExpr, fcinfo_b, context_b, resultinfo_b);

	PG_RETURN_VOID(); /* XXX for now */
}

static void memoryContextCallback(void *arg)
{
	Ptr2Long p2l;

	p2l.longVal = 0L;
	p2l.ptrVal = arg;
	JNI_callStaticVoidMethodLocked(s_MemoryContextImpl_class,
								   s_MemoryContextImpl_callback,
								   p2l.longVal);
}

static void relCacheCB(Datum arg, Oid relid)
{
	JNI_callStaticObjectMethodLocked(s_CatalogObjectImpl_Factory_class,
		s_CatalogObjectImpl_Factory_invalidateRelation, (jint)relid);
}

static void resourceReleaseCB(ResourceReleasePhase phase,
							  bool isCommit, bool isTopLevel, void *arg)
{
	Ptr2Long p2l;

	/*
	 * This static assertion does not need to be in every file
	 * that uses Ptr2Long, but it should be somewhere once, so here it is.
	 */
	StaticAssertStmt(sizeof p2l.ptrVal <= sizeof p2l.longVal,
					 "Pointer will not fit in long on this platform");

	/*
	 * The way ResourceOwnerRelease is implemented, callbacks to loadable
	 * modules (like us!) happen /after/ all of the built-in releasey actions
	 * for a particular phase. So, by looking for RESOURCE_RELEASE_LOCKS here,
	 * we actually end up executing after all the built-in lock-related stuff
	 * has been released, but before any of the built-in stuff released in the
	 * RESOURCE_RELEASE_AFTER_LOCKS phase. Which, at least for the currently
	 * implemented DualState subclasses, is about the right time.
	 */
	if ( RESOURCE_RELEASE_LOCKS != phase )
		return;

	/*
	 * The void *arg is the NULL we supplied at registration time. The resource
	 * manager arranges for CurrentResourceOwner to be the one that is being
	 * released.
	 */
	p2l.longVal = 0L;
	p2l.ptrVal = CurrentResourceOwner;
	JNI_callStaticVoidMethodLocked(s_ResourceOwnerImpl_class,
								   s_ResourceOwnerImpl_callback,
								   p2l.longVal);

	if ( isTopLevel )
		Backend_warnJEP411(isCommit);
}

static void sysCacheCB(Datum arg, int cacheid, uint32 hash)
{
	switch ( cacheid )
	{
	case LANGOID:
	case PROCOID:
	case TYPEOID:
		JNI_callStaticObjectMethodLocked(s_CatalogObjectImpl_Factory_class,
			s_CatalogObjectImpl_Factory_syscacheInvalidate,
			(jint)cacheid, (jint)hash);
		break;
	default:
#ifdef USE_ASSERT_CHECKING
		elog(ERROR, "unhandled invalidation callback for cache id %d", cacheid);
#endif
		break;
	}
}

void pljava_ResourceOwner_unregister(void)
{
	UnregisterResourceReleaseCallback(resourceReleaseCB, NULL);
}

void pljava_ModelUtils_initialize(void)
{
	jclass cls;

	JNINativeMethod catalogObjectAddressedMethods[] =
	{
		{
		"_lookupRowtypeTupdesc",
		"(II)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1lookupRowtypeTupdesc
		},
		{
		"_searchSysCacheCopy1",
		"(II)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy1
		},
		{
		"_searchSysCacheCopy2",
		"(III)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy2
		},
		{
		"_sysTableGetByOid",
		"(IIIIJ)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1sysTableGetByOid
		},
		{
		"_tupDescBootstrap",
		"()Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1tupDescBootstrap
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod catalogObjectFactoryMethods[] =
	{
		{
		"_currentDatabase",
		"()I",
		Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Factory__1currentDatabase
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod charsetMethods[] =
	{
		{
		"_serverEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding
		},
		{
		"_clientEncoding",
		"()I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding
		},
		{
		"_nameToOrdinal",
		"(Ljava/nio/ByteBuffer;)I",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal
		},
		{
		"_ordinalToName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName
		},
		{
		"_ordinalToIcuName",
		"(I)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod datumMethods[] =
	{
		{
		"_addressOf",
		"(Ljava/nio/ByteBuffer;)J",
		Java_org_postgresql_pljava_pg_DatumUtils__1addressOf
		},
		{
		"_map",
		"(JI)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_DatumUtils__1map
		},
		{
		"_mapCString",
		"(J)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_DatumUtils__1mapCString
		},
		{
		"_mapVarlena",
		"(Ljava/nio/ByteBuffer;JJJ)Lorg/postgresql/pljava/adt/spi/Datum$Input;",
		Java_org_postgresql_pljava_pg_DatumUtils__1mapVarlena
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod lookupImplMethods[] =
	{
		{
		"_cacheReference",
		"(Lorg/postgresql/pljava/pg/LookupImpl;J)V",
		Java_org_postgresql_pljava_pg_LookupImpl__1cacheReference
		},
		{
		"_get_fn_expr_variadic",
		"(Ljava/nio/ByteBuffer;)Z",
		Java_org_postgresql_pljava_pg_LookupImpl__1get_1fn_1expr_1variadic
		},
		{
		"_stableInputs",
		"(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)V",
		Java_org_postgresql_pljava_pg_LookupImpl__1stableInputs
		},
		{
		"_notionalCallResultType",
		"(Ljava/nio/ByteBuffer;[I)Lorg/postgresql/pljava/model/TupleDescriptor;",
		Java_org_postgresql_pljava_pg_LookupImpl__1notionalCallResultType
		},
		{
		"_resolveArgTypes",
		"(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;II)Z",
		Java_org_postgresql_pljava_pg_LookupImpl__1resolveArgTypes
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod memoryContextMethods[] =
	{
		{
		"_registerCallback",
		"(J)V",
		Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1registerCallback
		},
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod resourceOwnerMethods[] =
	{
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_ResourceOwnerImpl_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod spiMethods[] =
	{
		{
		"_window",
		"(Ljava/lang/Class;)[Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_SPI_00024EarlyNatives__1window
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod tdiMethods[] =
	{
		{
		"_assign_record_type_typmod",
		"(Ljava/nio/ByteBuffer;)I",
		Java_org_postgresql_pljava_pg_TupleDescImpl__1assign_1record_1type_1typmod
		},
		{
		"_synthesizeDescriptor",
		"(ILjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_pg_TupleDescImpl__1synthesizeDescriptor
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod ttsiMethods[] =
	{
		{
		"_getsomeattrs",
		"(Ljava/nio/ByteBuffer;I)V",
		Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1getsomeattrs
		},
		{
		"_store_heaptuple",
		"(Ljava/nio/ByteBuffer;JZ)V",
		Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1store_1heaptuple
		},
		{ 0, 0, 0 }
	};

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CatalogObjectImpl$Addressed");
	PgObject_registerNatives2(cls, catalogObjectAddressedMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CatalogObjectImpl$Factory");
	s_CatalogObjectImpl_Factory_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, catalogObjectFactoryMethods);
	JNI_deleteLocalRef(cls);
	s_CatalogObjectImpl_Factory_invalidateRelation =
		PgObject_getStaticJavaMethod(
		s_CatalogObjectImpl_Factory_class, "invalidateRelation", "(I)V");
	s_CatalogObjectImpl_Factory_syscacheInvalidate =
		PgObject_getStaticJavaMethod(
		s_CatalogObjectImpl_Factory_class, "syscacheInvalidate", "(II)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/CharsetEncodingImpl$EarlyNatives");
	PgObject_registerNatives2(cls, charsetMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/DatumUtils");
	PgObject_registerNatives2(cls, datumMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/LookupImpl");
	PgObject_registerNatives2(cls, lookupImplMethods);
	s_LookupImpl_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
	s_LookupImpl_dispatchNew =
		PgObject_getStaticJavaMethod(s_LookupImpl_class, "dispatchNew",
		"(JJIZZLjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)"
		"V");
	s_LookupImpl_dispatch =
		PgObject_getJavaMethod(s_LookupImpl_class, "dispatch",
		"(IZZLjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)"
		"V");
	s_LookupImpl_dispatchInline =
		PgObject_getStaticJavaMethod(s_LookupImpl_class, "dispatchInline",
		"(IZLjava/nio/ByteBuffer;)"
		"V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/MemoryContextImpl$EarlyNatives");
	PgObject_registerNatives2(cls, memoryContextMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/MemoryContextImpl");
	s_MemoryContextImpl_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
	s_MemoryContextImpl_callback = PgObject_getStaticJavaMethod(
		s_MemoryContextImpl_class, "callback", "(J)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/ResourceOwnerImpl$EarlyNatives");
	PgObject_registerNatives2(cls, resourceOwnerMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/ResourceOwnerImpl");
	s_ResourceOwnerImpl_class = JNI_newGlobalRef(cls);
	JNI_deleteLocalRef(cls);
	s_ResourceOwnerImpl_callback = PgObject_getStaticJavaMethod(
		s_ResourceOwnerImpl_class, "callback", "(J)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/internal/SPI$EarlyNatives");
	PgObject_registerNatives2(cls, spiMethods);
	JNI_deleteLocalRef(cls);

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/TupleDescImpl");
	s_TupleDescImpl_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, tdiMethods);
	JNI_deleteLocalRef(cls);

	s_TupleDescImpl_fromByteBuffer = PgObject_getStaticJavaMethod(
		s_TupleDescImpl_class,
		"fromByteBuffer",
		"(Ljava/nio/ByteBuffer;IIII)"
		"Lorg/postgresql/pljava/model/TupleDescriptor;");

	cls = PgObject_getJavaClass("org/postgresql/pljava/pg/TupleTableSlotImpl");
	s_TupleTableSlotImpl_class = JNI_newGlobalRef(cls);
	PgObject_registerNatives2(cls, ttsiMethods);
	JNI_deleteLocalRef(cls);

	s_TupleTableSlotImpl_newDeformed = PgObject_getStaticJavaMethod(
		s_TupleTableSlotImpl_class,
		"newDeformed",
		"(Ljava/nio/ByteBuffer;Lorg/postgresql/pljava/model/TupleDescriptor;"
		"Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)"
		"Lorg/postgresql/pljava/pg/TupleTableSlotImpl$Deformed;");

	RegisterResourceReleaseCallback(resourceReleaseCB, NULL);

	CacheRegisterRelcacheCallback(relCacheCB, 0);

	CacheRegisterSyscacheCallback(LANGOID, sysCacheCB, 0);
	CacheRegisterSyscacheCallback(PROCOID, sysCacheCB, 0);
	CacheRegisterSyscacheCallback(TYPEOID, sysCacheCB, 0);
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _lookupRowtypeTupdesc
 * Signature: (II)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1lookupRowtypeTupdesc(JNIEnv* env, jobject _cls, jint typeid, jint typmod)
{
	TupleDesc td;
	jlong length;
	jobject result = NULL;
	BEGIN_NATIVE_AND_TRY
	td = lookup_rowtype_tupdesc_noerror(typeid, typmod, true);
	if ( NULL != td )
	{
		/*
		 * Per contract, we return the tuple descriptor with its reference count
		 * incremented, but not registered with a resource owner for descriptor
		 * leak warnings. l_r_t_n() will have incremented already, but also
		 * registered for warnings. The proper dance is a second pure increment
		 * here, followed by a DecrTupleDescRefCount to undo what l_r_t_n() did.
		 * And none of that, of course, if the descriptor is not refcounted.
		 */
		if ( td->tdrefcount >= 0 )
		{
			++ td->tdrefcount;
			DecrTupleDescRefCount(td);
		}
		length = (jlong)TupleDescSize(td);
		result = JNI_newDirectByteBuffer((void *)td, length);
	}
	END_NATIVE_AND_CATCH("_lookupRowtypeTupdesc")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _searchSysCacheCopy1
 * Signature: (II)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy1(JNIEnv *env, jclass cls, jint cacheId, jint key1)
{
	jobject result = NULL;
	HeapTuple ht;
	BEGIN_NATIVE_AND_TRY
	ht = SearchSysCacheCopy1(cacheId, Int32GetDatum(key1));
	if ( HeapTupleIsValid(ht) )
	{
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}
	END_NATIVE_AND_CATCH("_searchSysCacheCopy1")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _searchSysCacheCopy2
 * Signature: (III)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1searchSysCacheCopy2(JNIEnv *env, jclass cls, jint cacheId, jint key1, jint key2)
{
	jobject result = NULL;
	HeapTuple ht;
	BEGIN_NATIVE_AND_TRY
	ht = SearchSysCacheCopy2(cacheId, Int32GetDatum(key1), Int32GetDatum(key2));
	if ( HeapTupleIsValid(ht) )
	{
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}
	END_NATIVE_AND_CATCH("_searchSysCacheCopy2")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _sysTableGetByOid
 * Signature: (IIIIJ)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1sysTableGetByOid(JNIEnv *env, jclass cls, jint relOid, jint objOid, jint oidCol, jint indexOid, jlong tupleDesc)
{
	jobject result = NULL;
	HeapTuple ht;
	Relation rel;
	SysScanDesc scandesc;
	ScanKeyData entry[1];
	Ptr2Long p2l;

	p2l.longVal = tupleDesc;

	BEGIN_NATIVE_AND_TRY
	rel = relation_open((Oid)relOid, AccessShareLock);

	ScanKeyInit(&entry[0], (AttrNumber)oidCol, BTEqualStrategyNumber, F_OIDEQ,
		ObjectIdGetDatum((Oid)objOid));

	scandesc = systable_beginscan(
		rel, (Oid)indexOid, InvalidOid != indexOid, NULL, 1, entry);

	ht = systable_getnext(scandesc);

	/*
	 * As in the extension.c code from which this is brazenly copied, we assume
	 * there can be at most one matching tuple. (Oid ought to be the primary key
	 * of a catalog table we care about, so it's not a daring assumption.)
	 */
	if ( HeapTupleIsValid(ht) )
	{
		/*
		 * We wish to return a tuple satisfying the same conditions as if it had
		 * been obtained from the syscache, including that it has no external
		 * TOAST pointers. (Inline-compressed values, it could still have.)
		 */
		if ( HeapTupleHasExternal(ht) )
			ht = toast_flatten_tuple(ht, p2l.ptrVal);
		else
			ht = heap_copytuple(ht);
		result = JNI_newDirectByteBuffer(ht, HEAPTUPLESIZE + ht->t_len);
	}

	systable_endscan(scandesc);
	relation_close(rel, AccessShareLock);
	END_NATIVE_AND_CATCH("_sysTableGetByOid")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Addressed
 * Method:    _tupDescBootstrap
 * Signature: ()Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Addressed__1tupDescBootstrap(JNIEnv* env, jobject _cls)
{
	Relation rel;
	TupleDesc td;
	jlong length;
	jobject result = NULL;
	BEGIN_NATIVE_AND_TRY
	rel = relation_open(RelationRelationId, AccessShareLock);
	td = RelationGetDescr(rel);
	/*
	 * Per contract, we return the tuple descriptor with its reference count
	 * incremented, without registering it with a resource owner for descriptor
	 * leak warnings.
	 */
	++ td->tdrefcount;
	/*
	 * Can close the relation now that the td reference count is bumped.
	 */
	relation_close(rel, AccessShareLock);
	length = (jlong)TupleDescSize(td);
	result = JNI_newDirectByteBuffer((void *)td, length);
	END_NATIVE_AND_CATCH("_tupDescBootstrap")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CatalogObjectImpl_Factory
 * Method:    _currentDatabase
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CatalogObjectImpl_00024Factory__1currentDatabase(JNIEnv *env, jclass cls)
{
	return MyDatabaseId;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _serverEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1serverEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = GetDatabaseEncoding();
	END_NATIVE_AND_CATCH("_serverEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _clientEncoding
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1clientEncoding(JNIEnv *env, jclass cls)
{
	int result = -1;
	BEGIN_NATIVE_AND_TRY
	result = pg_get_client_encoding();
	END_NATIVE_AND_CATCH("_clientEncoding")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _nameToOrdinal
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1nameToOrdinal(JNIEnv *env, jclass cls, jobject bb)
{
	int result = -1;
	char const *name = (*env)->GetDirectBufferAddress(env, bb);
	if ( NULL == name )
		return result;
	BEGIN_NATIVE_AND_TRY
	result = pg_char_to_encoding(name);
	END_NATIVE_AND_CATCH("_nameToOrdinal")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = pg_encoding_to_char(ordinal);
	if ( '\0' != *name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToName")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_CharsetEncodingImpl_EarlyNatives
 * Method:    _ordinalToIcuName
 * Signature: (I)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_CharsetEncodingImpl_00024EarlyNatives__1ordinalToIcuName(JNIEnv *env, jclass cls, jint ordinal)
{
	jobject result = NULL;
	char const *name;
	BEGIN_NATIVE_AND_TRY
	name = get_encoding_name_for_icu(ordinal);
	if ( NULL != name )
		result = JNI_newDirectByteBuffer((void *)name, (jint)strlen(name));
	END_NATIVE_AND_CATCH("_ordinalToIcuName")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _addressOf
 * Signature: (Ljava/nio/ByteBuffer;)J
 */
JNIEXPORT jlong JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1addressOf(JNIEnv* env, jobject _cls, jobject bb)
{
	Ptr2Long p2l;
	p2l.longVal = 0;
	p2l.ptrVal = (*env)->GetDirectBufferAddress(env, bb);
	return p2l.longVal;
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _map
 * Signature: (JI)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1map(JNIEnv* env, jobject _cls, jlong nativeAddress, jint length)
{
	Ptr2Long p2l;
	p2l.longVal = nativeAddress;
	return (*env)->NewDirectByteBuffer(env, p2l.ptrVal, length);
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _mapCString
 * Signature: (J)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1mapCString(JNIEnv* env, jobject _cls, jlong nativeAddress)
{
	jlong length;
	void *base;
	Ptr2Long p2l;

	p2l.longVal = nativeAddress;
	base = p2l.ptrVal;
	length = (jlong)strlen(base);
	return (*env)->NewDirectByteBuffer(env, base, length);
}

/*
 * Class:     org_postgresql_pljava_pg_DatumUtils
 * Method:    _mapVarlena
 * Signature: (Ljava/nio/ByteBuffer;JJJ)Lorg/postgresql/pljava/adt/spi/Datum$Input;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_DatumUtils__1mapVarlena(JNIEnv* env, jobject _cls, jobject bb, jlong offset, jlong resowner, jlong memcontext)
{
	Ptr2Long p2lvl;
	Ptr2Long p2lro;
	Ptr2Long p2lmc;
	jobject result = NULL;

	p2lvl.longVal = 0;
	if ( NULL != bb )
	{
		p2lvl.ptrVal = (*env)->GetDirectBufferAddress(env, bb);
		if ( NULL == p2lvl.ptrVal )
			return NULL;
	}
	p2lvl.longVal += offset;

	p2lro.longVal = resowner;
	p2lmc.longVal = memcontext;

	BEGIN_NATIVE_AND_TRY
	result =  pljava_VarlenaWrapper_Input(PointerGetDatum(p2lvl.ptrVal),
		(MemoryContext)p2lmc.ptrVal, (ResourceOwner)p2lro.ptrVal);
	END_NATIVE_AND_CATCH("_mapVarlena")
	return result;
}


/*
 * Class:     org_postgresql_pljava_pg_LookupImpl
 * Method:    _cacheReference
 * Signature: (Lorg/postgresql/pljava/pg/LookupImpl;J)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_LookupImpl__1cacheReference(JNIEnv* env, jobject _cls, jobject lref, jlong extra)
{
	Ptr2Long p2l;
	RegProcedureLookup *extraStruct;

	p2l.longVal = extra;
	extraStruct = (RegProcedureLookup *)p2l.ptrVal;
	extraStruct->lookup = (*env)->NewGlobalRef(env, lref);
}

/*
 * Class:     org_postgresql_pljava_pg_LookupImpl
 * Method:    _get_fn_expr_variadic
 * Signature: (Ljava/nio/ByteBuffer;)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_pg_LookupImpl__1get_1fn_1expr_1variadic(JNIEnv* env, jobject _cls, jobject fcinfo_b)
{
	bool result;
	FunctionCallInfo fcinfo = (*env)->GetDirectBufferAddress(env, fcinfo_b);
	if ( NULL == fcinfo )
		return JNI_FALSE; /* shouldn't happen; there's probably an exception */

	BEGIN_NATIVE_AND_TRY
	result = get_fn_expr_variadic(fcinfo->flinfo);
	END_NATIVE_AND_CATCH("_get_fn_expr_variadic")

	return result ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_pg_LookupImpl
 * Method:    _stableInputs
 * Signature: (Ljava/nio/ByteBuffer;)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_LookupImpl__1stableInputs(JNIEnv* env, jobject _cls, jobject fcinfo_b, jobject bits_b)
{
	FunctionCallInfo fcinfo = (*env)->GetDirectBufferAddress(env, fcinfo_b);
	Bitmapset *bits = (*env)->GetDirectBufferAddress(env, bits_b);
	FmgrInfo *flinfo;
	int idx;

	if ( NULL == fcinfo  ||  NULL == bits )
		return; /* shouldn't happen; there's probably an exception */

	flinfo = fcinfo->flinfo;

	BEGIN_NATIVE_AND_TRY

	/*
	 * The caller has set one guard bit at the next higher index beyond the
	 * bits of interest. Find that one, then bms_prev_member loop from there.
	 */
	idx = bms_prev_member(bits, -1);
	if ( -2 != idx )
	{
		while ( -2 != (idx = bms_prev_member(bits, idx)) )
		{
			if ( ! get_fn_expr_arg_stable(flinfo, idx) )
				bms_del_member(bits, idx);
		}
	}

	END_NATIVE_AND_CATCH("_stableInputs")
}

/*
 * Class:     org_postgresql_pljava_pg_LookupImpl
 * Method:    _notionalCallResultType
 * Signature: (Ljava/nio/ByteBuffer;[I)Lorg/postgresql/pljava/model/TupleDescriptor;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_LookupImpl__1notionalCallResultType(JNIEnv* env, jobject _cls, jobject fcinfo_b, jintArray returnTypeOid)
{
	FunctionCallInfo fcinfo = (*env)->GetDirectBufferAddress(env, fcinfo_b);
	Oid typeId;
	jint joid;
	TupleDesc td = NULL;
	jobject result = NULL;

	if ( NULL == fcinfo )
		return NULL; /* shouldn't happen; there's probably an exception */

	BEGIN_NATIVE_AND_TRY

	get_call_result_type(fcinfo, &typeId, &td); /* simple so far */
	joid = typeId;
	JNI_setIntArrayRegion(returnTypeOid, 0, 1, &joid);

	if ( NULL == td ) /* no real td; make a notional one */
	{
		if ( VOIDOID != typeId  &&  RECORDOID != typeId )
		{
			bool isArray;
			HeapTuple tp;
			Form_pg_type typform;

			/*
			 * This feels like a nutty amount of work just to decide whether
			 * 1 or 0 makes the better dummy value to pass as attdim to
			 * TupleDescInitEntry.
			 */
			tp = SearchSysCache1(TYPEOID, ObjectIdGetDatum(typeId));
			if ( ! HeapTupleIsValid(tp) )
				elog(ERROR, "cache lookup failed for type %u", typeId);
			typform = (Form_pg_type) GETSTRUCT(tp);
			isArray = (InvalidOid != typform->typelem);
			ReleaseSysCache(tp);

			td = CreateTemplateTupleDesc(1);
			TupleDescInitEntry(td, 1, "", typeId, -1, isArray ? 1 : 0);
			TupleDescInitEntryCollation(td, 1, fcinfo->fncollation);
		}
	}

	if ( NULL != td )
		result = pljava_TupleDescriptor_create(td, InvalidOid);

	END_NATIVE_AND_CATCH("_notionalCallResultType")
	return result;
}

/*
 * Class:     org_postgresql_pljava_pg_LookupImpl
 * Method:    _resolveArgTypes
 * Signature: (Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;II)Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_pg_LookupImpl__1resolveArgTypes(JNIEnv* env, jobject _cls, jobject fcinfo_b, jobject types_b, jobject unresolved_b, int tplSz, int argSz)
{
	FunctionCallInfo fcinfo = (*env)->GetDirectBufferAddress(env, fcinfo_b);
	Oid *types = (*env)->GetDirectBufferAddress(env, types_b);
	Bitmapset *unresolved = (*env)->GetDirectBufferAddress(env, unresolved_b);
	FmgrInfo *flinfo;
	int idx;
	bool result = false;

	if ( NULL == fcinfo  ||  NULL == types_b  ||  NULL == unresolved_b )
		return JNI_FALSE; /* shouldn't happen; there's probably an exception */

	flinfo = fcinfo->flinfo;

	BEGIN_NATIVE_AND_TRY

	/*
	 * If the types array is longer than the template (the spread variadic "any"
	 * case), grab all the arg types beyond the end of the template.
	 */
	for ( idx = tplSz ; idx < argSz ; ++ idx )
		types[idx] = get_fn_expr_argtype(flinfo, idx);

	/*
	 * Check the template's unresolved types for the "any" type and grab
	 * those types too. resolve_polymorphic_argtypes will only attend to
	 * the civilized polymorphic types.
	 *
	 * The caller has set one guard bit in the Bitmapset beyond the last bit
	 * of interest. Find that one, then bms_prev_member loop from there.
	 */
	idx = bms_prev_member(unresolved, -1);
	if ( -2 != idx )
	{
		while ( -2 != (idx = bms_prev_member(unresolved, idx)) )
			if ( ANYOID == types[idx] )
				types[idx] = get_fn_expr_argtype(flinfo, idx);
	}

	/*
	 * resolve_polymorphic_argtypes will do the rest of the job.
	 * It only needs to look at the first tplSz types.
	 */
	result = resolve_polymorphic_argtypes(tplSz, types, NULL, flinfo->fn_expr);

	END_NATIVE_AND_CATCH("_resolveArgTypes")
	return result ? JNI_TRUE : JNI_FALSE;
}


/*
 * Class:     org_postgresql_pljava_pg_MemoryContext_EarlyNatives
 * Method:    _registerCallback
 * Signature: (J)V;
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1registerCallback(JNIEnv* env, jobject _cls, jlong nativeAddress)
{
	Ptr2Long p2l;
	MemoryContext cxt;
	MemoryContextCallback *cb;

	p2l.longVal = nativeAddress;
	cxt = p2l.ptrVal;
	BEGIN_NATIVE_AND_TRY
	/*
	 * Optimization? Use MemoryContextAllocExtended with NO_OOM, and do without
	 * the AND_TRY/AND_CATCH to catch a PostgreSQL ereport.
	 */
	cb = MemoryContextAlloc(cxt, sizeof *cb);
	cb->func = memoryContextCallback;
	cb->arg = cxt;
	MemoryContextRegisterResetCallback(cxt, cb);
	END_NATIVE_AND_CATCH("_registerCallback")
}

/*
 * Class:     org_postgresql_pljava_pg_MemoryContext_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * holding the well-known memory contexts. The indices into the array are
 * assigned arbitrarily in the API class CatalogObject.Factory and inherited
 * from it in CatalogObjectImpl.Factory, from which the native .h makes them
 * visible here. A peculiar consequence is that the code in MemoryContextImpl
 * can be ignorant of them, and just fetch the array element at the index passed
 * from the API class.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_MemoryContextImpl_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)10, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, \
	&tag##Context, sizeof tag##Context);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_pg_CatalogObjectImpl_Factory_MCX_##tag, \
	b);\
} while (0)

	POPULATE(CurrentMemory);
	POPULATE(TopMemory);
	POPULATE(Error);
	POPULATE(Postmaster);
	POPULATE(CacheMemory);
	POPULATE(Message);
	POPULATE(TopTransaction);
	POPULATE(CurTransaction);
	POPULATE(Portal);
	POPULATE(JavaMemory);

#undef POPULATE

	return r;
}


/*
 * Class:     org_postgresql_pljava_pg_ResourceOwnerImpl_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * holding the well-known resource owners. The indices into the array are
 * assigned arbitrarily in the API class CatalogObject.Factory and inherited
 * from it in CatalogObjectImpl.Factory, from which the native .h makes them
 * visible here. A peculiar consequence is that the code in ResourceOwnerImpl
 * can be ignorant of them, and just fetch the array element at the index passed
 * from the API class.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_ResourceOwnerImpl_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)4, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, \
	&tag##ResourceOwner, sizeof tag##ResourceOwner);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_pg_CatalogObjectImpl_Factory_RSO_##tag, \
	b);\
} while (0)

	POPULATE(Current);
	POPULATE(CurTransaction);
	POPULATE(TopTransaction);
	POPULATE(AuxProcess);

#undef POPULATE

	return r;
}


/*
 * Class:     org_postgresql_pljava_internal_SPI_EarlyNatives
 * Method:    _window
 * Signature: ()[Ljava/nio/ByteBuffer;
 *
 * Return an array of ByteBuffers constructed to window the PostgreSQL globals
 * SPI_result, SPI_processed, and SPI_tuptable. The indices into the array are
 * assigned arbitrarily in the internal class SPI, from which the native .h
 * makes them visible here.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_internal_SPI_00024EarlyNatives__1window(JNIEnv* env, jobject _cls, jclass component)
{
	jobject r = (*env)->NewObjectArray(env, (jsize)3, component, NULL);
	if ( NULL == r )
		return NULL;

#define POPULATE(tag) do {\
	jobject b = (*env)->NewDirectByteBuffer(env, &tag, sizeof tag);\
	if ( NULL == b )\
		return NULL;\
	(*env)->SetObjectArrayElement(env, r, \
	(jsize)org_postgresql_pljava_internal_SPI_##tag, \
	b);\
} while (0)

	POPULATE(SPI_result);
	POPULATE(SPI_processed);
	POPULATE(SPI_tuptable);

#undef POPULATE

	return r;
}


/*
 * Class:     org_postgresql_pljava_pg_TupleDescImpl
 * Method:    _assign_record_type_typmod
 * Signature: (Ljava/nio/ByteBuffer;)I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_pg_TupleDescImpl__1assign_1record_1type_1typmod(JNIEnv* env, jobject _cls, jobject td_b)
{
	TupleDesc td = (*env)->GetDirectBufferAddress(env, td_b);
	if ( NULL == td )
		return -1;

	BEGIN_NATIVE_AND_TRY
	assign_record_type_typmod(td);
	END_NATIVE_AND_CATCH("_assign_record_type_typmod")
	return td->tdtypmod;
}

/*
 * Class:     org_postgresql_pljava_pg_TupleDescImpl
 * Method:    _synthesizeDescriptor
 * Signature: (ILjava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;
 *
 * When synthesizing a TupleDescriptor from only a list of types and names, it
 * is tempting to make an ephemeral descriptor all in Java and avoid any JNI
 * call. On the other hand, TupleDescInitEntry is more likely to know what to
 * store in fields of the struct we don't care about, or added in new versions.
 *
 * The Java caller passes n (the number of attributes wanted) and one ByteBuffer
 * in which the sequence (int32 typoid, int32 typmod, bool array, encodedname\0)
 * occurs n times, INTALIGN'd between.
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_pg_TupleDescImpl__1synthesizeDescriptor(JNIEnv* env, jobject _cls, jint n, jobject in_b)
{
	jobject result = NULL;
	jlong tupdesc_size;
	int i;
	Oid typoid;
	int32 typmod;
	bool isArray;
	TupleDesc td;
	int32 *in_i;
	char *in_c = (*env)->GetDirectBufferAddress(env, in_b);
	if ( NULL == in_c )
		return NULL;

	BEGIN_NATIVE_AND_TRY

	td = CreateTemplateTupleDesc(n);

	for ( i = 0 ; i < n ; ++ i )
	{
		in_i = (int32 *)INTALIGN((uintptr_t)in_c);
		typoid = *(in_i++);
		typmod = *(in_i++);
		in_c = (char *)(uintptr_t)in_i;
		isArray = *(in_c++);

		TupleDescInitEntry(td, 1 + i, in_c, typoid, typmod, isArray ? 1 : 0);

		in_c += strlen(in_c) + 1;
	}

	tupdesc_size = (jlong)TupleDescSize(td);
	result = JNI_newDirectByteBuffer(td, tupdesc_size);

	END_NATIVE_AND_CATCH("_synthesizeDescriptor")
	return result;
}


/*
 * Class:     org_postgresql_pljava_pg_TupleTableSlotImpl
 * Method:    _getsomeattrs
 * Signature: (Ljava/nio/ByteBuffer;I)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1getsomeattrs(JNIEnv* env, jobject _cls, jobject tts_b, jint attnum)
{
	TupleTableSlot *tts = (*env)->GetDirectBufferAddress(env, tts_b);
	if ( NULL == tts )
		return;

	BEGIN_NATIVE_AND_TRY
	slot_getsomeattrs_int(tts, attnum);
	END_NATIVE_AND_CATCH("_getsomeattrs")
}

/*
 * Class:     org_postgresql_pljava_pg_TupleTableSlotImpl
 * Method:    _store_heaptuple
 * Signature: (Ljava/nio/ByteBuffer;JZ)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_pg_TupleTableSlotImpl__1store_1heaptuple(JNIEnv* env, jobject _cls, jobject tts_b, jlong ht, jboolean shouldFree)
{
	Ptr2Long p2l;
	HeapTuple htp;
	TupleTableSlot *tts = (*env)->GetDirectBufferAddress(env, tts_b);
	if ( NULL == tts )
		return;

	BEGIN_NATIVE_AND_TRY
	p2l.longVal = ht;
	htp = p2l.ptrVal;
	ExecStoreHeapTuple(htp, tts, JNI_TRUE == shouldFree);
	END_NATIVE_AND_CATCH("_store_heaptuple")
}
