/*
 * Copyright (c) 2004-2016 Tada AB and other contributors, as listed below.
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
#include "pljava/PgObject_priv.h"
#include "pljava/Exception.h"
#include "pljava/InstallHelper.h"
#include "pljava/Invocation.h"
#include "pljava/Function.h"
#include "pljava/HashMap.h"
#include "pljava/Iterator.h"
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

static jclass s_Loader_class;
static jclass s_ClassLoader_class;
static jclass s_Function_class;
static jmethodID s_Loader_getSchemaLoader;
static jmethodID s_Loader_getTypeMap;
static jmethodID s_ClassLoader_loadClass;
static jmethodID s_Function_create;
static PgObjectClass s_FunctionClass;
static Type s_pgproc_Type;

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
		 * The number of parameters
		 */
		int32     numParams;
	
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

		/*
		 * The static method that should be called.
		 */
		jmethodID method;
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

typedef struct ParseResultData
{
	char* buffer;	/* The buffer to pfree once we are done */
	const char* returnType;
	const char* className;
	const char* methodName;
	const char* parameters;
	bool isUDT;
} ParseResultData;

typedef ParseResultData *ParseResult;

static HashMap s_funcMap = 0;

static jclass s_Loader_class;
static jmethodID s_Loader_getSchemaLoader;

static void _Function_finalize(PgObject func)
{
	Function self = (Function)func;
	JNI_deleteGlobalRef(self->clazz);
	if(!self->isUDT)
	{
		if(self->func.nonudt.typeMap != 0)
			JNI_deleteGlobalRef(self->func.nonudt.typeMap);
		if(self->func.nonudt.paramTypes != 0)
			pfree(self->func.nonudt.paramTypes);
	}
}

extern void Function_initialize(void);
void Function_initialize(void)
{
	JNINativeMethod functionMethods[] =
	{
		{
		"_storeToNonUDT",
		"(JLjava/lang/Class;ZZLjava/util/Map;IILjava/lang/String;[I[Ljava/lang/String;[Ljava/lang/String;)Z",
		Java_org_postgresql_pljava_internal_Function__1storeToNonUDT
		},
		{
		"_storeToUDT",
		"(JLjava/lang/Class;ZII)V",
		Java_org_postgresql_pljava_internal_Function__1storeToUDT
		},
		{
		"_reconcileTypes",
		"(J[Ljava/lang/String;[Ljava/lang/String;I)V",
		Java_org_postgresql_pljava_internal_Function__1reconcileTypes
		},
		{ 0, 0, 0 }
	};
	s_funcMap = HashMap_create(59, TopMemoryContext);
	
	s_Loader_class = JNI_newGlobalRef(PgObject_getJavaClass("org/postgresql/pljava/sqlj/Loader"));
	s_Loader_getSchemaLoader = PgObject_getStaticJavaMethod(s_Loader_class, "getSchemaLoader", "(Ljava/lang/String;)Ljava/lang/ClassLoader;");
	s_Loader_getTypeMap = PgObject_getStaticJavaMethod(s_Loader_class, "getTypeMap", "(Ljava/lang/String;)Ljava/util/Map;");

	s_ClassLoader_class = JNI_newGlobalRef(PgObject_getJavaClass("java/lang/ClassLoader"));
	s_ClassLoader_loadClass = PgObject_getJavaMethod(s_ClassLoader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");

	s_Function_class = JNI_newGlobalRef(PgObject_getJavaClass(
		"org/postgresql/pljava/internal/Function"));
	s_Function_create = PgObject_getStaticJavaMethod(s_Function_class, "create",
		"(JLjava/sql/ResultSet;Ljava/lang/String;Ljava/lang/String;Z)"
		"Ljava/lang/String;");

	PgObject_registerNatives2(s_Function_class, functionMethods);

	s_FunctionClass  = PgObjectClass_create("Function", sizeof(struct Function_), _Function_finalize);

	s_pgproc_Type = Composite_obtain(ProcedureRelation_Rowtype_Id);
}

static void buildSignature(Function self, StringInfo sign, Type retType, bool alt)
{
	Type* tp = self->func.nonudt.paramTypes;
	Type* ep = tp + self->func.nonudt.numParams;

	appendStringInfoChar(sign, '(');
	while(tp < ep)
		appendStringInfoString(sign, Type_getJNISignature(*tp++));

	if(!self->func.nonudt.isMultiCall && Type_isOutParameter(retType))
		appendStringInfoString(sign, Type_getJNISignature(retType));

	appendStringInfoChar(sign, ')');
	appendStringInfoString(sign, Type_getJNIReturnSignature(retType, self->func.nonudt.isMultiCall, alt));
}

static char* getAS(HeapTuple procTup, char** epHolder)
{
	char c;
	char* cp1;
	char* cp2;
	char* bp;
	bool  atStart = true;
	bool  passedFirst = false;
	bool  isNull = false;
	Datum tmp = SysCacheGetAttr(PROCOID, procTup, Anum_pg_proc_prosrc, &isNull);
	if(isNull)
	{
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("'AS' clause of Java function cannot be NULL")));
	}

	bp = pstrdup(DatumGetCString(DirectFunctionCall1(textout, tmp)));

	/* Strip all whitespace except the first one if it occures after
	 * some alpha numeric characers and before some other alpha numeric
	 * characters. We insert a '=' when that happens since it delimits
	 * the return value from the method name.
	 */
	cp1 = cp2 = bp;
	while((c = *cp1++) != 0)
	{
		if(isspace(c))
		{
			if(atStart || passedFirst)
				continue;

			while((c = *cp1++) != 0)
				if(!isspace(c))
					break;

			if(c == 0)
				break;

			if(isalpha(c))
				*cp2++ = '=';
			passedFirst = true;
		}
		atStart = false;
		if(!isalnum(c))
			passedFirst = true;
		*cp2++ = c;
	}
	*cp2 = 0;
	*epHolder = cp2;
	return bp;
}

static void parseUDT(ParseResult info, char* bp, char* ep)
{
	char* ip = ep - 1;
	while(ip > bp && *ip != ']')
		--ip;

	if(ip == bp)
	{
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("Missing ending ']' in UDT declaration")));
	}
	*ip = 0; /* Terminate class name */
	info->className = bp;
	info->methodName = ip + 1;
	info->isUDT = true;
}

/*
 * Zeros info before setting any of its fields.
 */
static void parseFunction(ParseResult info, HeapTuple procTup)
{
	/* The user's function definition must be the fully
	 * qualified name of a java method short of parameter
	 * signature.
	 */
	char* ip;
	char* ep;
	char* bp = getAS(procTup, &ep);

	memset(info, 0, sizeof(ParseResultData));
	info->buffer = bp;

	/* The AS clause can have two formats
	 *
	 * <class name> "." <method name> [ "(" <parameter decl> ["," <parameter decl> ... ] ")" ]
	 *   or
	 * "UDT" "[" <class name> "]" <UDT function type>
	 * where <UDT function type> is one of "input", "output", "receive" or "send"
	 */
	if(ep - bp >= 4 && strncasecmp(bp, "udt[", 4) == 0)
	{
		parseUDT(info, bp + 4, ep);
		return;
	}

	info->isUDT = false;

	/* Scan backwards from ep.
	 */
	ip = ep - 1;
	if(*ip == ')')
	{
		/* We have an explicit parameter type declaration
		 */
		*ip-- = 0;
		while(ip > bp && *ip != '(')
			--ip;

		if(ip == bp)
		{
			ereport(ERROR, (
				errcode(ERRCODE_SYNTAX_ERROR),
				errmsg("Unbalanced parenthesis")));
		}

		info->parameters = ip + 1;
		*ip-- = 0;
	}

	/* Find last '.' occurrence.
	*/
	while(ip > bp && *ip != '.')
		--ip;

	if(ip == bp)
	{
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("Did not find <fully qualified class>.<method name>")));
	}
	info->methodName = ip + 1;
	*ip = 0;
	
	/* Check if we have a return type declaration
	 */
	while(--ip > bp)
	{
		if(*ip == '=')
		{
			info->className = ip + 1;
			*ip = 0;
			break;
		}
	}

	if(info->className != 0)
		info->returnType = bp;
	else
		info->className = bp;

	elog(DEBUG3, "className = '%s', methodName = '%s', parameters = '%s', returnType = '%s'",
		info->className == 0 ? "null" : info->className,
		info->methodName == 0 ? "null" : info->methodName,
		info->parameters == 0 ? "null" : info->parameters,
		info->returnType == 0 ? "null" : info->returnType);
}

static jstring getSchemaName(int namespaceOid)
{
	HeapTuple nspTup = PgObject_getValidTuple(NAMESPACEOID, namespaceOid, "namespace");
	Form_pg_namespace nspStruct = (Form_pg_namespace)GETSTRUCT(nspTup);
	jstring schemaName = String_createJavaStringFromNTS(NameStr(nspStruct->nspname));
	ReleaseSysCache(nspTup);
	return schemaName;
}

static jclass Function_loadClass(
	jstring schemaName, char const *className, jweak *loaderref);

Type Function_checkTypeUDT(Oid typeId, Form_pg_type typeStruct)
{
	ParseResultData info;
	HeapTuple procTup;
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
	parseFunction(&info, procTup);
	if ( ! info.isUDT )
		goto finally;

	procStruct = (Form_pg_proc)GETSTRUCT(procTup);
	schemaName = getSchemaName(procStruct->pronamespace);
	clazz = Function_loadClass(schemaName, info.className, NULL);
	JNI_deleteLocalRef(schemaName);
	t = (Type)UDT_registerUDT(clazz, typeId, typeStruct, 0, true);

finally:
	pfree(info.buffer);
	ReleaseSysCache(procTup);
	return t;
}

static void Function_getMethodID(Function self, jstring methodNameJ)
{
	char *className = PgObject_getClassName(self->clazz);
	char *methodName = String_createNTS(methodNameJ);
	StringInfoData sign;
	initStringInfo(&sign);
	buildSignature(self, &sign, self->func.nonudt.returnType, false);

	elog(DEBUG2, "Obtaining method %s.%s %s", className, methodName, sign.data);
	self->func.nonudt.method = JNI_getStaticMethodIDOrNull(self->clazz,
		methodName, sign.data);

	if(self->func.nonudt.method == 0)
	{
		char* origSign = sign.data;
		Type altType = 0;
		Type realRetType = self->func.nonudt.returnType;

		elog(DEBUG2, "Method %s.%s %s not found", className, methodName, origSign);

		if(Type_isPrimitive(self->func.nonudt.returnType))
		{
			/*
			 * One valid reason for not finding the method is when
			 * the return type used in the signature is a primitive and
			 * the true return type of the method is the object class that
			 * corresponds to that primitive.
			 */
			altType = Type_getObjectType(self->func.nonudt.returnType);
			realRetType = altType;
		}
		else if(strcmp(Type_getJavaTypeName(self->func.nonudt.returnType), "java.sql.ResultSet") == 0)
		{
			/*
			 * Another reason might be that we expected a ResultSetProvider
			 * but the implementation returns a ResultSetHandle that needs to be
			 * wrapped. The wrapping is internal so we retain the original
			 * return type anyway.
			 */
			altType = realRetType;
		}

		if(altType != 0)
		{
			JNI_exceptionClear();
			initStringInfo(&sign);
			buildSignature(self, &sign, altType, true);

			elog(DEBUG2, "Obtaining method %s.%s %s", className, methodName, sign.data);
			self->func.nonudt.method =
				JNI_getStaticMethodIDOrNull(self->clazz, methodName, sign.data);
	
			if(self->func.nonudt.method != 0)
				self->func.nonudt.returnType = realRetType;
		}
		if(self->func.nonudt.method == 0)
			PgObject_throwMemberError(self->clazz, methodName, origSign,
				true, true);

		if(sign.data != origSign)
			pfree(origSign);
	}
	pfree(sign.data);
	pfree(className);
	pfree(methodName);
}

/*
 * Return a global ref to the loaded class. Store a weak global ref to the
 * initiating loader at *loaderref if non-null.
 */
static jclass Function_loadClass(
	jstring schemaName, char const *className, jweak *loaderref)
{
	jobject tmp;
	jobject loader;
	jstring classJstr;
	jclass clazz;
	/* Get the ClassLoader for the schema that this function belongs to
	 */
	loader = JNI_callStaticObjectMethod(s_Loader_class,
		s_Loader_getSchemaLoader, schemaName);

	elog(DEBUG2, "Loading class %s", className);
	classJstr = String_createJavaStringFromNTS(className);

	tmp = JNI_callObjectMethod(loader, s_ClassLoader_loadClass, classJstr);

	if ( NULL != loaderref )
		*loaderref = JNI_newWeakGlobalRef(loader);

	JNI_deleteLocalRef(loader);
	JNI_deleteLocalRef(classJstr);

	clazz = (jclass)JNI_newGlobalRef(tmp);
	JNI_deleteLocalRef(tmp);
	return clazz;
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
	Ptr2Long p2l;
	jstring methodName;

	p2l.longVal = 0;
	p2l.ptrVal = (void *)self;

#if 90305<=PG_VERSION_NUM || \
	90209<=PG_VERSION_NUM && PG_VERSION_NUM<90300 || \
	90114<=PG_VERSION_NUM && PG_VERSION_NUM<90200 || \
	90018<=PG_VERSION_NUM && PG_VERSION_NUM<90100 || \
	80422<=PG_VERSION_NUM && PG_VERSION_NUM<90000
	Datum d = heap_copy_tuple_as_datum(procTup,
		Type_getTupleDesc(s_pgproc_Type, 0));
#else
#error "Need fallback for heap_copy_tuple_as_datum"
#endif

	methodName = JNI_callStaticObjectMethod(s_Function_class, s_Function_create,
		p2l.longVal, Type_coerceDatum(s_pgproc_Type, d), lname,
		getSchemaName(procStruct->pronamespace),
		CALLED_AS_TRIGGER(fcinfo)? JNI_TRUE : JNI_FALSE);
	pfree((void *)d);
	ReleaseSysCache(lngTup);
	ReleaseSysCache(procTup);

	if ( NULL != methodName )
	{
		Function_getMethodID(self, methodName);
		JNI_deleteLocalRef(methodName);
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

Datum Function_invoke(Function self, PG_FUNCTION_ARGS)
{
	Datum retVal;
	int32 top;
	jvalue* args;
	Type  invokerType;

	fcinfo->isnull = false;

	if(self->isUDT)
		return self->func.udt.udtFunction(self->func.udt.udt, fcinfo);

	/* a class loader or other mechanism might have connected already. This
	 * connection must be dropped since its parent context is wrong.
	 */
	if(self->func.nonudt.isMultiCall && SRF_IS_FIRSTCALL())
		Invocation_assertDisconnect();

	top = self->func.nonudt.numParams;
	
	/* Leave room for one extra parameter. Functions that returns unmapped
	 * composite types must have a single row ResultSet as an OUT parameter.
	 */
	args  = (jvalue*)palloc((top + 1) * sizeof(jvalue));
	invokerType = self->func.nonudt.returnType;

	if(top > 0)
	{
		int32 idx;
		Type* types = self->func.nonudt.paramTypes;

		if(Type_isDynamic(invokerType))
			invokerType = Type_getRealType(invokerType, get_fn_expr_rettype(fcinfo->flinfo), self->func.nonudt.typeMap);

		for(idx = 0; idx < top; ++idx)
		{
			if(PG_ARGISNULL(idx))
				/*
				 * Set this argument to zero (or null in case of object)
				 */
				args[idx].j = 0L;
			else
			{
				Type paramType = types[idx];
				if(Type_isDynamic(paramType))
					paramType = Type_getRealType(paramType, get_fn_expr_argtype(fcinfo->flinfo, idx), self->func.nonudt.typeMap);
				args[idx] = Type_coerceDatum(paramType, PG_GETARG_DATUM(idx));
			}
		}
	}

	retVal = self->func.nonudt.isMultiCall
		? Type_invokeSRF(invokerType, self->clazz, self->func.nonudt.method, args, fcinfo)
		: Type_invoke(invokerType, self->clazz, self->func.nonudt.method, args, fcinfo);

	pfree(args);
	return retVal;
}

Datum Function_invokeTrigger(Function self, PG_FUNCTION_ARGS)
{
	jvalue arg;
	Datum  ret;

	TriggerData *td = (TriggerData*)fcinfo->context;
	arg.l = pljava_TriggerData_create(td);
	if(arg.l == 0)
		return 0;

#if PG_VERSION_NUM >= 100000
	currentInvocation->triggerData = td;
	/* Also starting in PG 10, Invocation_assertConnect must be called before
	 * the getTriggerReturnTuple below. That could be done right here, but at
	 * the risk of changing the memory context from what the invoked trigger
	 * function expects. More cautiously, add the assertConnect later, after
	 * the trigger function has returned.
	 */
#endif
	Type_invoke(self->func.nonudt.returnType, self->clazz, self->func.nonudt.method, &arg, fcinfo);

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
					arg.l, &fcinfo->isnull));

		/* Triggers are not allowed to set the fcinfo->isnull, even when
		 * they return null.
		 */
		fcinfo->isnull = false;

		MemoryContextSwitchTo(currCtx);
	}

	JNI_deleteLocalRef(arg.l);
	return ret;
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
 * Class:     org_postgresql_pljava_internal_Function
 * Method:    _storeToNonUDT
 * Signature: (JLjava/lang/Class;ZZLjava/util/Map;IILjava/lang/String;[I[Ljava/lang/String;[Ljava/lang/String;)Z
 */
JNIEXPORT jboolean JNICALL
	Java_org_postgresql_pljava_internal_Function__1storeToNonUDT(
	JNIEnv *env, jclass jFunctionClass, jlong wrappedPtr, jclass clazz,
	jboolean readOnly, jboolean isMultiCall, jobject typeMap,
	jint numParams, jint returnType, jstring returnJType,
	jintArray paramTypes, jobjectArray paramJTypes, jobjectArray outJTypes)
{
	Ptr2Long p2l;
	Function self;
	MemoryContext ctx;
	jstring jtn;
	int i = 0;
	bool returnTypeIsOutParameter;

	p2l.longVal = wrappedPtr;
	self = (Function)p2l.ptrVal;
	ctx = GetMemoryChunkContext(self);

	BEGIN_NATIVE_NO_ERRCHECK
	PG_TRY();
	{
		self->isUDT = false;
		self->readOnly = (JNI_TRUE == readOnly);
		self->clazz = JNI_newGlobalRef(clazz);
		self->func.nonudt.isMultiCall = (JNI_TRUE == isMultiCall);
		self->func.nonudt.typeMap =
			(NULL == typeMap) ? NULL : JNI_newGlobalRef(typeMap);
		self->func.nonudt.numParams = numParams;

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

	return returnTypeIsOutParameter;
}

/*
 * Class:     org_postgresql_pljava_internal_Function
 * Method:    _storeToUDT
 * Signature: (JLjava/lang/Class;ZII)V
 */
JNIEXPORT void JNICALL
	Java_org_postgresql_pljava_internal_Function__1storeToUDT(
	JNIEnv *env, jclass jFunctionClass, jlong wrappedPtr, jclass clazz,
	jboolean readOnly, jint funcInitial, jint udtId)
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
		self->clazz = JNI_newGlobalRef(clazz);

		typeTup = PgObject_getValidTuple(TYPEOID, udtId, "type");
		pgType = (Form_pg_type)GETSTRUCT(typeTup);
		self->func.udt.udt =
			UDT_registerUDT(self->clazz, udtId, pgType, 0, true);
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
			index = self->func.nonudt.numParams;
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
			self->func.nonudt.paramTypes[index] = replType;

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
