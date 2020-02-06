/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
#include "org_postgresql_pljava_internal_Function.h"
#include "org_postgresql_pljava_internal_Function_EarlyNatives.h"
#include "pljava/PgObject_priv.h"
#include "pljava/Exception.h"
#include "pljava/InstallHelper.h"
#include "pljava/Invocation.h"
#include "pljava/Function.h"
#include "pljava/HashMap.h"
#include "pljava/Iterator.h"
#include "pljava/JNICalls.h"
#include "pljava/type/Composite.h"
#include "pljava/type/Oid.h"
#include "pljava/type/String.h"
#include "pljava/type/TriggerData.h"
#include "pljava/type/UDT.h"

#include <catalog/pg_proc.h>
#include <catalog/pg_language.h>
#include <catalog/pg_namespace.h>
#include <utils/builtins.h>
#include <ctype.h>
#include <funcapi.h>
#include <utils/typcache.h>

#ifdef _MSC_VER
#	define strcasecmp _stricmp
#	define strncasecmp _strnicmp
#endif

#define PARAM_OIDS(procStruct) (procStruct)->proargtypes.values

#if 90305<=PG_VERSION_NUM || \
	90209<=PG_VERSION_NUM && PG_VERSION_NUM<90300 || \
	90114<=PG_VERSION_NUM && PG_VERSION_NUM<90200 || \
	90018<=PG_VERSION_NUM && PG_VERSION_NUM<90100 || \
	80422<=PG_VERSION_NUM && PG_VERSION_NUM<90000
#else
#error "Need fallback for heap_copy_tuple_as_datum"
#endif

#define SETPARAMCOUNTS(refs, prims) \
	*(jshort *)(((char *)s_primitiveParameters) + \
	org_postgresql_pljava_internal_Function_s_offset_paramCounts) = \
	(jshort)(((refs) << 8) | ((prims) & 0xff))

static jclass s_Loader_class;
static jclass s_ClassLoader_class;
static jclass s_Function_class;
static jclass s_EntryPoints_class;
static jmethodID s_Loader_getSchemaLoader;
static jmethodID s_Loader_getTypeMap;
static jmethodID s_ClassLoader_loadClass;
static jmethodID s_Function_create;
static jmethodID s_Function_getClassIfUDT;
static jmethodID s_Function_udtReadHandle;
static jmethodID s_Function_udtParseHandle;
static jmethodID s_EntryPoints_refInvoke;
static jmethodID s_EntryPoints_invoke;
static jmethodID s_EntryPoints_udtWriteInvoke;
static jmethodID s_EntryPoints_udtToStringInvoke;
static jmethodID s_EntryPoints_udtReadInvoke;
static jmethodID s_EntryPoints_udtParseInvoke;
static PgObjectClass s_FunctionClass;
static Type s_pgproc_Type;

static jobjectArray s_referenceParameters;
static jvalue s_primitiveParameters [ 1 + 255 ];

struct Function_
{
	struct PgObject_ PgObject_extension;

	/**
	 * True if the function is not a volatile function (i.e. STABLE or
	 * IMMUTABLE). This means that the function is not allowed to have
	 * side effects.
	 */
	bool   readOnly;

	/**
	 * True if this is a UDT function (input/output/receive/send)
	 */
	bool   isUDT;

	/**
	 * Java class, i.e. the UDT class or the class where the static method
	 * is defined.
	 */
	jclass clazz;

	/**
	 * Weak global reference to the class loader for the schema in which this
	 * function is declared.
	 */
	jweak schemaLoader;

	union
	{
		struct
		{
		/*
		 * True if the function is a multi-call function and hence, will
		 * allocate a memory context of its own.
		 */
		bool      isMultiCall;
	
		/*
		 * The number of reference parameters
		 */
		uint16     numRefParams;

		/*
		 * The number of primitive parameters
		 */
		uint16     numPrimParams;
	
		/*
		 * Array containing one type for eeach parameter.
		 */
		Type*     paramTypes;
	
		/*
		 * The return type.
		 */
		Type      returnType;

		/*
		 * The type map used when mapping parameter and return types. We
		 * need to store it here in order to cope with dynamic types (any
		 * and anyarray)
		 */
		jobject typeMap;

		/**
		 * MethodHandle to the resolved Java method implementing the function.
		 */
		jobject methodHandle;
		} nonudt;
		
		struct
		{
		/**
		 * The UDT that this function is associated with
		 */
		UDT udt;

		/**
		 * The UDT function to call
		 */
		UDTFunction udtFunction;
		} udt;
	} func;
};

/*
 * Not fussing with initializer, relying on readOnly being false by C static
 * initial default.
 */
static struct Function_ s_initWriter;

Function Function_INIT_WRITER = &s_initWriter;

static HashMap s_funcMap = 0;

static jclass s_Loader_class;
static jmethodID s_Loader_getSchemaLoader;

static void _Function_finalize(PgObject func)
{
	Function self = (Function)func;
	JNI_deleteGlobalRef(self->clazz);
	if(!self->isUDT)
	{
		JNI_deleteGlobalRef(self->func.nonudt.methodHandle);
		if(self->func.nonudt.typeMap != 0)
			JNI_deleteGlobalRef(self->func.nonudt.typeMap);
		if(self->func.nonudt.paramTypes != 0)
			pfree(self->func.nonudt.paramTypes);
	}
}

extern void Function_initialize(void);
void Function_initialize(void)
{
	JNINativeMethod earlyMethods[] =
	{
		{
		"_parameterArea",
		"([Ljava/lang/Object;)Ljava/nio/ByteBuffer;",
		Java_org_postgresql_pljava_internal_Function_00024EarlyNatives__1parameterArea
		},
		{ 0, 0, 0 }
	};

	JNINativeMethod functionMethods[] =
	{
		{
		"_storeToNonUDT",
		"(JLjava/lang/ClassLoader;Ljava/lang/Class;ZZLjava/util/Map;IILjava/lang/String;[I[Ljava/lang/String;[Ljava/lang/String;)Z",
		Java_org_postgresql_pljava_internal_Function__1storeToNonUDT
		},
		{
		"_storeToUDT",
		"(JLjava/lang/ClassLoader;Ljava/lang/Class;ZII"
		"Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)V",
		Java_org_postgresql_pljava_internal_Function__1storeToUDT
		},
		{
		"_reconcileTypes",
		"(J[Ljava/lang/String;[Ljava/lang/String;I)V",
		Java_org_postgresql_pljava_internal_Function__1reconcileTypes
		},
		{ 0, 0, 0 }
	};

	jclass cls;

	StaticAssertStmt(org_postgresql_pljava_internal_Function_s_sizeof_jvalue
		== sizeof (jvalue), "Function.java has wrong size for Java JNI jvalue");

	s_funcMap = HashMap_create(59, TopMemoryContext);

	s_Loader_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/sqlj/Loader"));
	s_Loader_getSchemaLoader = PgObject_getStaticJavaMethod(s_Loader_class, "getSchemaLoader", "(Ljava/lang/String;)Ljava/lang/ClassLoader;");
	s_Loader_getTypeMap = PgObject_getStaticJavaMethod(s_Loader_class, "getTypeMap", "(Ljava/lang/String;)Ljava/util/Map;");

	s_ClassLoader_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/ClassLoader"));
	s_ClassLoader_loadClass = PgObject_getJavaMethod(s_ClassLoader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

	cls = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/Function$EarlyNatives"));
	PgObject_registerNatives2(cls, earlyMethods);

	s_Function_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/Function"));
	s_Function_create = PgObject_getStaticJavaMethod(s_Function_class, "create",
		"(JLjava/sql/ResultSet;Ljava/lang/String;Ljava/lang/String;Z)"
		"Ljava/lang/invoke/MethodHandle;");
	s_Function_getClassIfUDT = PgObject_getStaticJavaMethod(s_Function_class,
		"getClassIfUDT",
		"(Ljava/sql/ResultSet;Ljava/lang/String;)"
		"Ljava/lang/Class;");

	s_EntryPoints_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/EntryPoints"));
	s_EntryPoints_refInvoke = PgObject_getStaticJavaMethod(
		s_EntryPoints_class,
		"refInvoke", "(Ljava/lang/invoke/MethodHandle;)Ljava/lang/Object;");
	s_EntryPoints_invoke = PgObject_getStaticJavaMethod(s_EntryPoints_class,
		"invoke", "(Ljava/lang/invoke/MethodHandle;)V");

	s_EntryPoints_udtWriteInvoke = PgObject_getStaticJavaMethod(
		s_EntryPoints_class,
		"udtWriteInvoke", "(Ljava/sql/SQLData;Ljava/sql/SQLOutput;"
		")V");
	s_EntryPoints_udtToStringInvoke = PgObject_getStaticJavaMethod(
		s_EntryPoints_class,
		"udtToStringInvoke", "(Ljava/sql/SQLData;)Ljava/lang/String;");
	s_EntryPoints_udtReadInvoke = PgObject_getStaticJavaMethod(
		s_EntryPoints_class,
		"udtReadInvoke", "(Ljava/lang/invoke/MethodHandle;Ljava/sql/SQLInput;"
		"Ljava/lang/String;)Ljava/sql/SQLData;");
	s_EntryPoints_udtParseInvoke = PgObject_getStaticJavaMethod(
		s_EntryPoints_class,
		"udtParseInvoke", "(Ljava/lang/invoke/MethodHandle;Ljava/lang/String;"
		"Ljava/lang/String;)Ljava/sql/SQLData;");

	s_Function_udtReadHandle = PgObject_getStaticJavaMethod(s_Function_class,
		"udtReadHandle", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
	s_Function_udtParseHandle = PgObject_getStaticJavaMethod(s_Function_class,
		"udtParseHandle", "(Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");

	PgObject_registerNatives2(s_Function_class, functionMethods);

	s_FunctionClass  = PgObjectClass_create("Function", sizeof(struct Function_), _Function_finalize);

	s_pgproc_Type = Composite_obtain(ProcedureRelation_Rowtype_Id);
}

jobject pljava_Function_refInvoke(Function self)
{
	return JNI_callStaticObjectMethod(s_EntryPoints_class,
		s_EntryPoints_refInvoke, self->func.nonudt.methodHandle);
}

void pljava_Function_voidInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
}

jboolean pljava_Function_booleanInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].z;
}

jbyte pljava_Function_byteInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].b;
}

jshort pljava_Function_shortInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].s;
}

jchar pljava_Function_charInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].c;
}

jint pljava_Function_intInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].i;
}

jfloat pljava_Function_floatInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].f;
}

jlong pljava_Function_longInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].j;
}

jdouble pljava_Function_doubleInvoke(Function self)
{
	JNI_callStaticVoidMethod(s_EntryPoints_class,
		s_EntryPoints_invoke, self->func.nonudt.methodHandle);
	return s_primitiveParameters[0].d;
}

void pljava_Function_udtWriteInvoke(jobject value, jobject stream)
{
	JNI_callStaticObjectMethod(s_EntryPoints_class,
		s_EntryPoints_udtWriteInvoke, value, stream);
}

jstring pljava_Function_udtToStringInvoke(jobject value)
{
	return JNI_callStaticObjectMethod(s_EntryPoints_class,
		s_EntryPoints_udtToStringInvoke, value);
}

jobject pljava_Function_udtReadInvoke(
	jobject readMH, jobject stream, jstring typeName)
{
	return JNI_callStaticObjectMethod(s_EntryPoints_class,
		s_EntryPoints_udtReadInvoke, readMH, stream, typeName);
}

jobject pljava_Function_udtParseInvoke(
	jobject parseMH, jstring stringRep, jstring typeName)
{
	return JNI_callStaticObjectMethod(s_EntryPoints_class,
		s_EntryPoints_udtParseInvoke, parseMH, stringRep, typeName);
}

jobject pljava_Function_udtReadHandle(jclass clazz)
{
	return JNI_callStaticObjectMethod(s_Function_class,
		s_Function_udtReadHandle, clazz);
}

jobject pljava_Function_udtParseHandle(jclass clazz)
{
	return JNI_callStaticObjectMethod(s_Function_class,
		s_Function_udtParseHandle, clazz);
}

static jstring getSchemaName(int namespaceOid)
{
	HeapTuple nspTup = PgObject_getValidTuple(NAMESPACEOID, namespaceOid, "namespace");
	Form_pg_namespace nspStruct = (Form_pg_namespace)GETSTRUCT(nspTup);
	jstring schemaName = String_createJavaStringFromNTS(NameStr(nspStruct->nspname));
	ReleaseSysCache(nspTup);
	return schemaName;
}

Type Function_checkTypeUDT(Oid typeId, Form_pg_type typeStruct)
{
	HeapTuple procTup;
	Datum d;
	Form_pg_proc procStruct;
	Type t = NULL;
	jstring schemaName;
	jclass clazz;

	if (   ! InstallHelper_isPLJavaFunction(typeStruct->typinput)
		|| ! InstallHelper_isPLJavaFunction(typeStruct->typoutput)
		|| ! InstallHelper_isPLJavaFunction(typeStruct->typreceive)
		|| ! InstallHelper_isPLJavaFunction(typeStruct->typsend) )
		return NULL;

	/* typinput as good as any, all four had better be in same class */
	procTup = PgObject_getValidTuple(PROCOID, typeStruct->typinput, "function");

	procStruct = (Form_pg_proc)GETSTRUCT(procTup);
	schemaName = getSchemaName(procStruct->pronamespace);

	d = heap_copy_tuple_as_datum(procTup, Type_getTupleDesc(s_pgproc_Type, 0));

	clazz = (jclass)JNI_callStaticObjectMethod(s_Function_class,
		s_Function_getClassIfUDT, Type_coerceDatum(s_pgproc_Type, d),
		schemaName);

	pfree((void *)d);
	JNI_deleteLocalRef(schemaName);
	ReleaseSysCache(procTup);

	if ( NULL != clazz )
		t = (Type)
			UDT_registerUDT(clazz, typeId, typeStruct, 0, true, NULL, NULL);

	return t;
}

static Function Function_create(PG_FUNCTION_ARGS)
{
	Function self =
		(Function)PgObjectClass_allocInstance(s_FunctionClass,TopMemoryContext);
	HeapTuple procTup =
		PgObject_getValidTuple(PROCOID, fcinfo->flinfo->fn_oid, "function");
	Form_pg_proc procStruct = (Form_pg_proc)GETSTRUCT(procTup);
	HeapTuple lngTup =
		PgObject_getValidTuple(LANGOID, procStruct->prolang, "language");
	Form_pg_language lngStruct = (Form_pg_language)GETSTRUCT(lngTup);
	jstring lname = String_createJavaStringFromNTS(NameStr(lngStruct->lanname));
	jstring schemaName;
	Ptr2Long p2l;
	Datum d;
	jobject handle;

	p2l.longVal = 0;
	p2l.ptrVal = (void *)self;

	d = heap_copy_tuple_as_datum(procTup, Type_getTupleDesc(s_pgproc_Type, 0));

	schemaName = getSchemaName(procStruct->pronamespace);
	handle = JNI_callStaticObjectMethod(s_Function_class, s_Function_create,
		p2l.longVal, Type_coerceDatum(s_pgproc_Type, d), lname,
		schemaName,
		CALLED_AS_TRIGGER(fcinfo)? JNI_TRUE : JNI_FALSE);
	pfree((void *)d);
	JNI_deleteLocalRef(schemaName);
	ReleaseSysCache(lngTup);
	ReleaseSysCache(procTup);

	if ( NULL != handle )
	{
		self->func.nonudt.methodHandle = JNI_newGlobalRef(handle);
		JNI_deleteLocalRef(handle);
	}

	return self;
}

/*
 * In all cases, this Function has been stored in currentInvocation->function
 * upon succesful return from here.
 */
Function Function_getFunction(PG_FUNCTION_ARGS)
{
	Oid funcOid = fcinfo->flinfo->fn_oid;
	Function func = (Function)HashMap_getByOid(s_funcMap, funcOid);
	if(func == 0)
	{
		func = Function_create(fcinfo);
		HashMap_putByOid(s_funcMap, funcOid, func);
	}
	currentInvocation->function = func;
	return func;
}

jobject Function_getTypeMap(Function self)
{
	return self->func.nonudt.typeMap;
}

static bool Function_inUse(Function func)
{
	Invocation* ic = currentInvocation;
	while(ic != 0)
	{
		if(ic->function == func)
			return true;
		ic = ic->previous;
	}
	return false;
}

void Function_clearFunctionCache(void)
{
	Entry entry;

	HashMap oldMap = s_funcMap;
	Iterator itor = Iterator_create(oldMap);

	s_funcMap = HashMap_create(59, TopMemoryContext);
	while((entry = Iterator_next(itor)) != 0)
	{
		Function func = (Function)Entry_getValue(entry);
		if(func != 0)
		{
			if(Function_inUse(func))
			{
				/* This is the replace_jar function or similar. Just
				 * move it to the new map.
				 */
				HashMap_put(s_funcMap, Entry_getKey(entry), func);
			}
			else
			{
				Entry_setValue(entry, 0);
				PgObject_free((PgObject)func);
			}
		}
	}
	PgObject_free((PgObject)itor);
	PgObject_free((PgObject)oldMap);
}

/*
 * Type_isPrimitive() by itself returns true for both, say, int and int[].
 * That is sometimes relied on, as in the code that would accept Integer[]
 * as a replacement for int[].
 *
 * However, it isn't correct for determining whether the thing should be passed
 * to Java as a primitive or a reference, because of course no Java array is a
 * primitive. Hence this method, which requires both Type_isPrimitive to be true
 * and that the type is not an array.
 */
static bool passAsPrimitive(Type t)
{
	return Type_isPrimitive(t) && (NULL == Type_getElementType(t));
}

Datum Function_invoke(Function self, PG_FUNCTION_ARGS)
{
	Datum retVal;
	jsize refArgCount;
	jsize primArgCount;
	Size passedArgCount;
	Type  invokerType;

	fcinfo->isnull = false;

	if(self->isUDT)
		return self->func.udt.udtFunction(self->func.udt.udt, fcinfo);

	/* a class loader or other mechanism might have connected already. This
	 * connection must be dropped since its parent context is wrong.
	 */
	if(self->func.nonudt.isMultiCall && SRF_IS_FIRSTCALL())
		Invocation_assertDisconnect();

	refArgCount = self->func.nonudt.numRefParams;
	primArgCount = self->func.nonudt.numPrimParams;
	SETPARAMCOUNTS(refArgCount, primArgCount);

	invokerType = self->func.nonudt.returnType;

	passedArgCount = PG_NARGS();

	if(passedArgCount > 0)
	{
		int32 idx;
		int32 refIdx = 0;
		int32 primIdx = 0;
		Type* types = self->func.nonudt.paramTypes;
		jvalue coerced;

		if(Type_isDynamic(invokerType))
			invokerType = Type_getRealType(invokerType,
				get_fn_expr_rettype(fcinfo->flinfo), self->func.nonudt.typeMap);

		for(idx = 0; idx < passedArgCount; ++idx)
		{
			Type paramType = types[idx];
			bool passPrimitive = passAsPrimitive(paramType);

			if(PG_ARGISNULL(idx))
			{
				/*
				 * Set this argument to zero (or null in case of object)
				 */
				if ( passPrimitive )
					s_primitiveParameters[primIdx++].j = 0L;
				else
					++ refIdx; /* array element is already initially null */
			}
			else
			{
				if(Type_isDynamic(paramType))
					paramType = Type_getRealType(paramType,
						get_fn_expr_argtype(fcinfo->flinfo, idx),
						self->func.nonudt.typeMap);
				coerced = Type_coerceDatum(paramType, PG_GETARG_DATUM(idx));
				if ( passPrimitive )
					s_primitiveParameters[primIdx++] = coerced;
				else
					JNI_setObjectArrayElement(
						s_referenceParameters, refIdx++, coerced.l);
			}
		}
	}

	retVal = self->func.nonudt.isMultiCall
		? Type_invokeSRF(invokerType, self, fcinfo)
		: Type_invoke(invokerType, self, fcinfo);

	return retVal;
}

Datum Function_invokeTrigger(Function self, PG_FUNCTION_ARGS)
{
	jobject jtd;
	Datum  ret;

	TriggerData *td = (TriggerData*)fcinfo->context;
	jtd = pljava_TriggerData_create(td);
	if(jtd == 0)
		return 0;

	JNI_setObjectArrayElement(s_referenceParameters, 0, jtd);
	SETPARAMCOUNTS(1, 0); /* (refs, prims) */

#if PG_VERSION_NUM >= 100000
	currentInvocation->triggerData = td;
	/* Also starting in PG 10, Invocation_assertConnect must be called before
	 * the getTriggerReturnTuple below. That could be done right here, but at
	 * the risk of changing the memory context from what the invoked trigger
	 * function expects. More cautiously, add the assertConnect later, after
	 * the trigger function has returned.
	 */
#endif
	Type_invoke(self->func.nonudt.returnType, self, fcinfo);

	fcinfo->isnull = false;
	if(JNI_exceptionCheck())
		ret = 0;
	else
	{
		/* A new Tuple may or may not be created here. Ensure that, if it is,
		 * it is created in the upper context (even after connecting SPI, should
		 * that be necessary).
		 */
#if PG_VERSION_NUM >= 100000
		/* If the invoked trigger function didn't connect SPI, do that here
		 * (getTriggerReturnTuple now needs it), but there will be no need to
		 * register the triggerData in that case.
		 */
		currentInvocation->triggerData = NULL;
		Invocation_assertConnect();
#endif
		MemoryContext currCtx = Invocation_switchToUpperContext();
		ret = PointerGetDatum(
				pljava_TriggerData_getTriggerReturnTuple(
					jtd, &fcinfo->isnull));

		/* Triggers are not allowed to set the fcinfo->isnull, even when
		 * they return null.
		 */
		fcinfo->isnull = false;

		MemoryContextSwitchTo(currCtx);
	}

	JNI_deleteLocalRef(jtd);
	return ret;
}

/*
 * Most slots in the parameter area are set directly in invoke() or
 * invokeTrigger() above. The only caller of this is Composite_invoke, which
 * needs to set one parameter (always the last one, and a reference type).
 * So this function, though with an API that could be general, for now only
 * handles the case where index is -1 and the last parameter has reference type.
 */
void pljava_Function_setParameter(Function self, int index, jvalue value)
{
	int numRefs = self->func.nonudt.numRefParams;
	if ( -1 != index  ||  1 > numRefs )
		elog(ERROR, "unsupported index in pljava_Function_setParameter");
	JNI_setObjectArrayElement(s_referenceParameters, numRefs - 1, value.l);
}

bool Function_isCurrentReadOnly(void)
{
	/* function will be 0 during resolve of class and java function. At
	 * that time, no updates are allowed (or needed).
	 */
	if (currentInvocation->function == 0)
		return true;
	return currentInvocation->function->readOnly;
}

jobject Function_currentLoader(void)
{
	Function f;
	jweak weakRef;

	if ( NULL == currentInvocation )
		return NULL;
	f = currentInvocation->function;
	if ( NULL == f )
		return NULL;
	weakRef = f->schemaLoader;
	if ( NULL == weakRef )
		return NULL;
	return JNI_newLocalRef(weakRef);
}

/*
 * Class:     org_postgresql_pljava_internal_Function_EarlyNatives
 * Method:    _parameterArea
 * Signature: ([Ljava/lang/Object;)Ljava/nio/ByteBuffer;
 */
JNIEXPORT jobject JNICALL
	Java_org_postgresql_pljava_internal_Function_00024EarlyNatives__1parameterArea(
	JNIEnv *env, jclass cls, jobjectArray referenceParams)
{
	/*
	 * This native method will use *env directly, not BEGIN_NATIVE / END_NATIVE:
	 * it is only called once in early initialization on the primordial thread.
	 */
	s_referenceParameters = (*env)->NewGlobalRef(env, referenceParams);
	return (*env)->NewDirectByteBuffer(
		env, &s_primitiveParameters, sizeof s_primitiveParameters);
}

/*
 * Class:     org_postgresql_pljava_internal_Function
 * Method:    _storeToNonUDT
 * Signature: (JLjava/lang/ClassLoader;Ljava/lang/Class;ZZLjava/util/Map;IILjava/lang/String;[I[Ljava/lang/String;[Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
	Java_org_postgresql_pljava_internal_Function__1storeToNonUDT(
	JNIEnv *env, jclass jFunctionClass, jlong wrappedPtr, jobject schemaLoader,
	jclass clazz, jboolean readOnly, jboolean isMultiCall, jobject typeMap,
	jint numParams, jint returnType, jstring returnJType,
	jintArray paramTypes, jobjectArray paramJTypes, jobjectArray outJTypes)
{
	Ptr2Long p2l;
	Function self;
	MemoryContext ctx;
	jstring jtn;
	int i = 0;
	uint16 refParams = 0;
	uint16 primParams = 0;
	bool returnTypeIsOutParameter;

	p2l.longVal = wrappedPtr;
	self = (Function)p2l.ptrVal;
	ctx = GetMemoryChunkContext(self);

	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		self->isUDT = false;
		self->readOnly = (JNI_TRUE == readOnly);
		self->schemaLoader = JNI_newWeakGlobalRef(schemaLoader);
		self->clazz = JNI_newGlobalRef(clazz);
		self->func.nonudt.isMultiCall = (JNI_TRUE == isMultiCall);
		self->func.nonudt.typeMap =
			(NULL == typeMap) ? NULL : JNI_newGlobalRef(typeMap);

		if ( NULL != returnJType )
		{
			char *rjtc = String_createNTS(returnJType);
			self->func.nonudt.returnType = Type_fromJavaType(returnType, rjtc);
			pfree(rjtc);
		}
		else
			self->func.nonudt.returnType = Type_fromOid(returnType, typeMap);

		if ( 0 < numParams )
		{
			self->func.nonudt.paramTypes =
				(Type *)MemoryContextAlloc(ctx, numParams * sizeof (Type));
			jint *paramOids = JNI_getIntArrayElements(paramTypes, NULL);
			for ( i = 0 ; i < numParams ; ++ i )
			{
				if ( NULL != paramJTypes )
				{
					jstring pjt = JNI_getObjectArrayElement(paramJTypes, i);
					if ( NULL != pjt )
					{
						char *pjtc = String_createNTS(pjt);
						JNI_deleteLocalRef(pjt);
						self->func.nonudt.paramTypes[i] =
							Type_fromJavaType(paramOids[i], pjtc);
						pfree(pjtc);
						continue;
					}
				}
				self->func.nonudt.paramTypes[i] =
					Type_fromOid(paramOids[i], typeMap);
			}
			JNI_releaseIntArrayElements(paramTypes, paramOids, JNI_ABORT);

			for ( i = 0 ; i < numParams ; ++ i )
			{
				jtn = String_createJavaStringFromNTS(Type_getJavaTypeName(
					self->func.nonudt.paramTypes[i]));
				JNI_setObjectArrayElement(outJTypes, i, jtn);
				JNI_deleteLocalRef(jtn);
				if ( passAsPrimitive(self->func.nonudt.paramTypes[i]) )
					++ primParams;
				else
					++ refParams;
			}
		}

		/* Store Java type name of return type at outJTypes[i], where i (after
		 * all of the above) indexes the last element of outJTypes.
		 */
		jtn = String_createJavaStringFromNTS(Type_getJavaTypeName(
			self->func.nonudt.returnType));
		JNI_setObjectArrayElement(outJTypes, i, jtn);
		JNI_deleteLocalRef(jtn);

		returnTypeIsOutParameter =
			Type_isOutParameter(self->func.nonudt.returnType);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(PG_FUNCNAME_MACRO);
	}
	PG_END_TRY();
	END_NATIVE

	if ( returnTypeIsOutParameter  &&  JNI_TRUE != isMultiCall )
		++ refParams;

	self->func.nonudt.numRefParams = refParams;
	self->func.nonudt.numPrimParams = primParams;

	return returnTypeIsOutParameter;
}

/*
 * Class:     org_postgresql_pljava_internal_Function
 * Method:    _storeToUDT
 * Signature: (JLjava/lang/ClassLoader;Ljava/lang/Class;ZIILjava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)V
 */
JNIEXPORT void JNICALL
	Java_org_postgresql_pljava_internal_Function__1storeToUDT(
	JNIEnv *env, jclass jFunctionClass, jlong wrappedPtr, jobject schemaLoader,
	jclass clazz, jboolean readOnly, jint funcInitial, jint udtId,
	jobject parseMH, jobject readMH)
{
	Ptr2Long p2l;
	Function self;
	HeapTuple typeTup;
	Form_pg_type pgType;

	p2l.longVal = wrappedPtr;
	self = (Function)p2l.ptrVal;

	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		self->isUDT = true;
		self->readOnly = (JNI_TRUE == readOnly);
		self->schemaLoader = JNI_newWeakGlobalRef(schemaLoader);
		self->clazz = JNI_newGlobalRef(clazz);

		typeTup = PgObject_getValidTuple(TYPEOID, udtId, "type");
		pgType = (Form_pg_type)GETSTRUCT(typeTup);
		self->func.udt.udt =
			UDT_registerUDT(
				self->clazz, udtId, pgType, 0, true, parseMH, readMH);
		ReleaseSysCache(typeTup);

		switch ( funcInitial )
		{
		case 'i': self->func.udt.udtFunction = UDT_input; break;
		case 'o': self->func.udt.udtFunction = UDT_output; break;
		case 'r': self->func.udt.udtFunction = UDT_receive; break;
		case 's': self->func.udt.udtFunction = UDT_send; break;
		default:
			elog(ERROR,
				"PL/Java jar/native code mismatch: unexpected UDT func ID");
		}
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(PG_FUNCNAME_MACRO);
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Function
 * Method:    _reconcileTypes
 * Signature: (J[Ljava/lang/String;[Ljava/lang/String;I)V
 */
JNIEXPORT void JNICALL
	Java_org_postgresql_pljava_internal_Function__1reconcileTypes(
	JNIEnv *env, jclass jFunctionClass, jlong wrappedPtr,
	jobjectArray resolvedTypes, jobjectArray explicitTypes, jint index)
{
	Ptr2Long p2l;
	Function self;
	Type origType;
	Type replType;
	Oid typeId;
	char *javaName;
	jstring javaNameString;

	/* The Java code will pass index -1 to indicate the special case of
	 * reconciling the return type instead of a parameter type. This is
	 * a bit convoluted in order to reproduce the behavior of the
	 * original C parseParameters. The explicit return type is at numParams.
	 * OR ... the Java code will pass -2 in a *different* case of adapting the
	 * return type, which in this case is the only element in a length-one
	 * explicitTypes array ... and in this case a coercer, if needed, will be
	 * built with getCoerceOut instead of getCoerceIn. (The use of getCoerceIn
	 * for the -1 case seems unconvincing; it is a faithful copy of what the
	 * C parseParameters did, but applying it to the return type may have been
	 * an oversight.) The resolvedTypes array in this case is still full length,
	 * and the resulting return type name still goes at the end of it.
	 */
	bool actOnReturnType = ( -1 == index ||  -2 == index );
	bool coerceOutAndSingleton = ( -2 == index );

	p2l.longVal = wrappedPtr;
	self = (Function)p2l.ptrVal;

	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		if ( actOnReturnType )
		{
			index = JNI_getArrayLength(resolvedTypes) - 1;
			origType = self->func.nonudt.returnType;
			typeId = InvalidOid;
		}
		else
		{
			origType = self->func.nonudt.paramTypes[index];
			typeId = Type_getOid(origType);
		}

		javaNameString = JNI_getObjectArrayElement(explicitTypes,
			coerceOutAndSingleton ? 0 : index);

		javaName = String_createNTS(javaNameString);

		replType = Type_fromJavaType(typeId, javaName);
		pfree(javaName);

		if ( ! Type_canReplaceType(replType, origType) )
		{
			if ( coerceOutAndSingleton )
				replType = Type_getCoerceOut(replType, origType);
			else
				replType = Type_getCoerceIn(replType, origType);
		}

		if ( actOnReturnType )
			self->func.nonudt.returnType = replType;
		else
		{
			self->func.nonudt.paramTypes[index] = replType;
			if ( passAsPrimitive(origType) != passAsPrimitive(replType) )
			{
				if ( Type_isPrimitive(replType) )
				{
					-- self->func.nonudt.numRefParams;
					++ self->func.nonudt.numPrimParams;
				}
				else
				{
					++ self->func.nonudt.numRefParams;
					-- self->func.nonudt.numPrimParams;
				}
			}
		}

		javaNameString =
			String_createJavaStringFromNTS(Type_getJavaTypeName(replType));

		JNI_setObjectArrayElement(resolvedTypes, index, javaNameString);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR(PG_FUNCNAME_MACRO);
	}
	PG_END_TRY();

	END_NATIVE
}
