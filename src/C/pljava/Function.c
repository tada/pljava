/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/PgObject_priv.h"
#include "pljava/backports.h"
#include "pljava/Exception.h"
#include "pljava/Backend.h"
#include "pljava/HashMap.h"
#include "pljava/MemoryContext.h"
#include "pljava/type/Oid.h"
#include "pljava/type/SingleRowWriter.h"
#include "pljava/type/ResultSetProvider.h"
#include "pljava/type/String.h"
#include "pljava/type/TriggerData.h"

#include <catalog/pg_proc.h>
#include <catalog/pg_namespace.h>
#include <utils/builtins.h>
#include <ctype.h>
#include <funcapi.h>

static jclass s_Loader_class;
static jclass s_ClassLoader_class;
static jmethodID s_Loader_getSchemaLoader;
static jmethodID s_ClassLoader_loadClass;

struct Function_
{
	struct PgObject_ PgObject_extension;

	/*
	 * True if the function is not a volatile function (i.e. STABLE or
	 * IMMUTABLE). This means that the function is not allowed to have
	 * side effects.
	 */
	bool      readOnly;

	/*
	 * True if the function is a multi-call function and hence, will
	 * allocate a memory context of its own.
	 */
	bool      isMultiCall;

	/*
	 * True if the function returns a complex type.
	 */
	bool      returnComplex;

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
	 * Java class where the static method is defined.
	 */
	jclass    clazz;

	/*
	 * The static method that should be called.
	 */
	jmethodID method;
};

static HashMap s_funcMap = 0;
static PgObjectClass s_FunctionClass;

static void _Function_finalize(PgObject self)
{
	Type* bp = ((Function)self)->paramTypes;
	if(bp != 0)
	{
		Type* tp = bp;
		Type* ep = bp + ((Function)self)->numParams;
		while(tp < ep)
			PgObject_free((PgObject)*tp++);
		pfree(bp);
	}
}

PG_FUNCTION_INFO_V1(Function_initialize);

static jclass s_Loader_class;
static jmethodID s_Loader_getSchemaLoader;

Datum Function_initialize(PG_FUNCTION_ARGS)
{
	JNIEnv* env = (JNIEnv*)PG_GETARG_POINTER(0);

	s_funcMap = HashMap_create(59, TopMemoryContext);
	s_FunctionClass = PgObjectClass_create("Function", sizeof(struct Function_), _Function_finalize);
	
	s_Loader_class = (*env)->NewGlobalRef(
						env, PgObject_getJavaClass(env, "org/postgresql/pljava/sqlj/Loader"));
	s_Loader_getSchemaLoader = PgObject_getStaticJavaMethod(
						env, s_Loader_class, "getSchemaLoader", "(Ljava/lang/String;)Ljava/lang/ClassLoader;");

	s_ClassLoader_class = (*env)->NewGlobalRef(
						env, PgObject_getJavaClass(env, "java/lang/ClassLoader"));
	s_ClassLoader_loadClass = PgObject_getJavaMethod(
						env, s_ClassLoader_class, "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
	PG_RETURN_VOID();
}

static void Function_buildSignature(Function self, StringInfoData* sign, Type retType)
{
	Type* tp = self->paramTypes;
	Type* ep = tp + self->numParams;

	initStringInfo(sign);
	appendStringInfoChar(sign, '(');
	while(tp < ep)
		appendStringInfoString(sign, Type_getJNISignature(*tp++));
	appendStringInfoChar(sign, ')');
	appendStringInfoString(sign, Type_getJNISignature(retType));
}

/*
 * This method assumes that the paramDecl ends with a ')' and will
 * fail miserably if it doesn't. From the place it's called, this
 * is quite safe.
 */
static void Function_parseParameters(Function self, Oid* dfltIds, const char* paramDecl)
{
	int idx = 0;
	int top = self->numParams;
	bool spaceSeen = false;
	StringInfoData sign;
	initStringInfo(&sign);
	for(;;)
	{
		char c = *paramDecl++;

		if(isspace(c))
		{
			if(sign.len > 0)
				spaceSeen = true;
			continue;
		}

		if(idx >= top)
			ereport(ERROR, (
				errcode(ERRCODE_SYNTAX_ERROR),
				errmsg("To many parameters - expected %d ", top)));

		if(c == ',' || c == ')')
		{
			Type deflt = self->paramTypes[idx];
			const char* jtName = Type_getJavaTypeName(deflt);
			if(strcmp(jtName, sign.data) != 0)
			{
				Oid did;
				Type repl;
				if(self->returnComplex && idx == self->numParams - 1)
					/*
					 * Last parameter is the OUT parameter. It has no corresponding
					 * entry in the dfltIds array.
					 */
					did = InvalidOid;
				else
					did = dfltIds[idx];

				repl = Type_fromJavaType(did, sign.data);
				if(!Type_canReplaceType(repl, deflt))
					ereport(ERROR, (
						errcode(ERRCODE_SYNTAX_ERROR),
						errmsg("Default type %s cannot be replaced by %s",
							jtName, Type_getJavaTypeName(repl))));
				self->paramTypes[idx] = repl;
			}
			pfree(sign.data);

			++idx;
			if(c == ')')
			{
				/*
				 * We are done.
				 */
				if(idx != top)
					ereport(ERROR, (
						errcode(ERRCODE_SYNTAX_ERROR),
						errmsg("To few parameters - expected %d ", top)));
				break;
			}

			/*
			 * Initialize next parameter.
			 */
			initStringInfo(&sign);
			spaceSeen = false;
		}
		else
		{
			if(spaceSeen)
				ereport(ERROR, (
					errcode(ERRCODE_SYNTAX_ERROR),
					errmsg("Syntax error in parameter list. Expected ',' or ')'")));
			appendStringInfoChar(&sign, c);
		}
	}
}

static void Function_init(Function self, JNIEnv* env, PG_FUNCTION_ARGS)
{
	const char* ip;
	const char* paramDecl = 0;
	const char* methodName;
	char* cp;
	char* className;
	bool  saveIcj = isCallingJava;
	bool  isNull = false;
	HeapTuple nspTup;
	Form_pg_namespace nspStruct;
	jobject loader;
	jstring jname;
	jstring schemaName;
	jobject loaded;
	MemoryContext ctx;
	const char* bp;
	const char* ep;
	const char* nameEp;
	int32 len;
	bool isResultSetProvider = false;

	/* Obtain the tuple that corresponds to the function
	 */
	HeapTuple procTup = PgObject_getValidTuple(PROCOID, fcinfo->flinfo->fn_oid, "function");
	Form_pg_proc procStruct = (Form_pg_proc)GETSTRUCT(procTup);

	/* The user's function definition must be the fully
	 * qualified name of a java method short of parameter
	 * signature.
	 */
	StringInfoData sign;
	Datum tmp = SysCacheGetAttr(PROCOID, procTup, Anum_pg_proc_prosrc, &isNull);
	if(isNull)
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("'AS' clause of Java function cannot be NULL")));

	bp     = DatumGetCString(DirectFunctionCall1(textout, tmp));
	elog(DEBUG1, "prosrc = \"%s\"", bp);
	len    = strlen(bp);
	ep     = bp + len;					/* Points just after end */

	/* Trim off leading and trailing whitespace.
	 */
	while(bp < ep && isspace(*bp))
	{
		++bp;
		--len;
	}
	while(ep > bp && isspace(*(ep-1)))
	{
		--ep;
		--len;
	}

	/* Scan backwards from ep.
	 */
	ip = ep - 1;
	nameEp = ep;
	if(*ip == ')')
	{
		/* We have an explicit parameter type declaration
		 */
		while(--ip >= bp)
			if(*ip == '(')
			{
				paramDecl = ip + 1;

				/* Might be preceded by whitespace.
				 */
				while(--ip >= bp && isspace(*ip))
					;
				nameEp = ip + 1;
				break;
			}
	}

	/* Find last '.' occurence.
	 */
	while(ip >= bp)
	{
		if(*ip == '.')
			break;
		--ip;
	}

	if(ip <= bp)
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("'AS' clause of Java function must consist of <fully qualified class>.<method name>")));

	/* ip now points just after class name. */

	/* Allocate a buffer large enough to hold both the class and method
	 * name and their respective null terminations.
	 */
	className = (char*)palloc(len + 1); /* One of the null terminators will replace a dot. */

	/* Copy class name
	 */
	cp = className;
	while(bp < ip)
		*cp++ = *bp++;
	*cp++ = 0;

	nspTup = PgObject_getValidTuple(NAMESPACEOID, procStruct->pronamespace, "namespace");
	nspStruct = (Form_pg_namespace)GETSTRUCT(nspTup);

	ip = NameStr(nspStruct->nspname);
	schemaName = String_createJavaStringFromNTS(env, ip);

	elog(DEBUG1, "Obtaining classloader for schema %s", ip);
	isCallingJava = true;
	loader = (*env)->CallStaticObjectMethod(env, s_Loader_class, s_Loader_getSchemaLoader, schemaName);
	isCallingJava = saveIcj;

	(*env)->DeleteLocalRef(env, schemaName);
	ReleaseSysCache(nspTup);
	Exception_checkException(env);

	jname  = String_createJavaStringFromNTS(env, className);

	elog(DEBUG1, "Loading class %s", className);
	isCallingJava = true;
	loaded = (*env)->CallObjectMethod(env, loader, s_ClassLoader_loadClass, jname);
	isCallingJava = saveIcj;

	(*env)->DeleteLocalRef(env, jname);
	(*env)->DeleteLocalRef(env, loader);

	Exception_checkException(env);

	self->returnComplex = false;
	self->readOnly = (procStruct->provolatile != PROVOLATILE_VOLATILE);
	self->clazz = (jclass)(*env)->NewGlobalRef(env, loaded);
	(*env)->DeleteLocalRef(env, loaded);

	methodName = cp;

	++bp;	/* Skip last '.' so that bp now points to the method name. */
	while(bp < nameEp)
	{
		char c = *bp++;
		if(!isalnum(c))
			break;
		*cp++ = c;
	}
	*cp = 0;

	if(bp < nameEp)
		/*
		 * We should have reached end of string by now.
		 */
		ereport(ERROR, (
			errcode(ERRCODE_SYNTAX_ERROR),
			errmsg("Extranious characters at end of method name '%s'", methodName)));

	ctx = GetMemoryChunkContext(self);
	if(CALLED_AS_TRIGGER(fcinfo))
	{
		if(paramDecl != 0)
			ereport(ERROR, (
				errcode(ERRCODE_SYNTAX_ERROR),
				errmsg("Triggers can not have a java parameter declaration")));

		self->returnType = Type_fromJavaType(InvalidOid, "void");

		/* Parameters are not used when calling triggers.
		 */
		self->numParams  = 1;
		self->paramTypes = (Type*)MemoryContextAlloc(ctx, sizeof(Type));
		self->paramTypes[0] = Type_fromJavaType(
				InvalidOid, "org.postgresql.pljava.TriggerData");
	}
	else
	{
		int top;
		Type complex = 0;
		Oid retTypeId = InvalidOid;
		TupleDesc retTuple = 0;

		self->numParams = (int32)procStruct->pronargs;
		self->isMultiCall = procStruct->proretset;

		switch(get_call_result_type(fcinfo, &retTypeId, &retTuple))
		{
			case TYPEFUNC_SCALAR:
				if(self->isMultiCall)
					self->returnType = Type_fromJavaType(retTypeId, "java.util.Iterator");
				else
				{
					HeapTuple typeTup = PgObject_getValidTuple(TYPEOID, retTypeId, "type");
					Form_pg_type pgType = (Form_pg_type)GETSTRUCT(typeTup);
					self->returnType = Type_fromPgType(retTypeId, pgType);
					ReleaseSysCache(typeTup);
				}
				break;

			case TYPEFUNC_COMPOSITE:
			case TYPEFUNC_RECORD:
				if(self->isMultiCall)
				{
					isResultSetProvider = true;
					self->returnType = ResultSetProvider_createType(retTypeId, retTuple);
				}
				else
				{
					self->numParams++;
					self->returnComplex = true;
					self->returnType = Type_fromOid(BOOLOID);
					complex = SingleRowWriter_createType(retTypeId, retTuple);
				}
				break;

			case TYPEFUNC_OTHER:
				ereport(ERROR, (
					errcode(ERRCODE_SYNTAX_ERROR),
					errmsg("PL/Java functions cannot return type %s",
						format_type_be(procStruct->prorettype))));
		}
		top = self->numParams;
		if(top > 0)
		{
			int idx;
#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 0)
			Oid* typeIds = procStruct->proargtypes;
#else
			Oid* typeIds = procStruct->proargtypes.values;
#endif
			self->paramTypes = (Type*)MemoryContextAlloc(ctx, top * sizeof(Type));

			if(complex != 0)
				--top; /* Last argument is not present in typeIds */

			for(idx = 0; idx < top; ++idx)
			{
				Oid typeId = typeIds[idx];
				HeapTuple typeTup = PgObject_getValidTuple(TYPEOID, typeId, "type");
				Form_pg_type pgType = (Form_pg_type)GETSTRUCT(typeTup);
				if(pgType->typtype == 'c')
				{
					self->paramTypes[idx] = Type_fromJavaType(
						InvalidOid,
						"org.postgresql.pljava.jdbc.SingleTupleReader");
				}
				else
					self->paramTypes[idx] = Type_fromPgType(typeId, pgType);
				ReleaseSysCache(typeTup);
			}

			if(complex != 0)
				self->paramTypes[idx] = complex;

			if(paramDecl != 0)
				Function_parseParameters(self, typeIds, paramDecl);
		}
		else
			self->paramTypes = 0;
	}

	Function_buildSignature(self, &sign, self->returnType);

	/*
	 * We don't need the procStruct anymore so we release its
	 * parent.
	 */
	ReleaseSysCache(procTup);

	elog(DEBUG1, "Obtaining method %s.%s %s", className, methodName, sign.data);
	isCallingJava = true;
	self->method = (*env)->GetStaticMethodID(
						env, self->clazz, methodName, sign.data);
	isCallingJava = saveIcj;

	if(self->method == 0)
	{
		char* origSign = sign.data;
		Type altType = 0;
		Type realRetType = self->returnType;

		elog(DEBUG1, "Method %s.%s %s not found", className, methodName, origSign);

		if(Type_isPrimitive(self->returnType))
		{
			/*
			 * One valid reason for not finding the method is when
			 * the return type used in the signature is a primitive and
			 * the true return type of the method is the object class that
			 * corresponds to that primitive.
			 */
			altType = Type_getObjectType(self->returnType);
			realRetType = altType;
		}
		else if(isResultSetProvider)
		{
			/*
			 * Another reason might be that we expected a ResultSetProvider
			 * but the implementation returns a ResultSetHandle that needs to be
			 * wrapped. The wrapping is internal so we retain the original
			 * return type anyway.
			 */
			altType = Type_fromJavaType(InvalidOid, "org.postgresql.pljava.ResultSetHandle");
		}

		if(altType != 0)
		{
			(*env)->ExceptionClear(env);
			Function_buildSignature(self, &sign, altType);
	
			elog(DEBUG1, "Obtaining method %s.%s %s", className, methodName, sign.data);
			isCallingJava = true;
			self->method = (*env)->GetStaticMethodID(
							env, self->clazz, methodName, sign.data);
			isCallingJava = saveIcj;
	
			if(self->method != 0)
				self->returnType = realRetType;
		}
		if(self->method == 0)
			PgObject_throwMemberError(env, self->clazz, methodName, origSign, true, true);

		if(sign.data != origSign)
			pfree(origSign);
	}
	pfree(sign.data);
	pfree(className);
}

static Function Function_create(JNIEnv* env, PG_FUNCTION_ARGS)
{
	Function self = (Function)PgObjectClass_allocInstance(s_FunctionClass, TopMemoryContext);
	Function_init(self, env, fcinfo);
	return self;
}

Function Function_getFunction(JNIEnv* env, PG_FUNCTION_ARGS)
{
	Oid funcOid = fcinfo->flinfo->fn_oid;
	Function func = (Function)HashMap_getByOid(s_funcMap, funcOid);
	if(func == 0)
	{
		PgObject old;
		func = Function_create(env, fcinfo);
		old = HashMap_putByOid(s_funcMap, funcOid, func);
		if(old != 0)
		{
			/* Can happen in a multithreaded environment. Extremely
			 * rare and no big deal. Just delete the duplicate so
			 * we avoid memory leaks.
			 */
			PgObject_free(old);
		}
	}
	return func;
}

Datum Function_invoke(Function self, JNIEnv* env, PG_FUNCTION_ARGS)
{
	Datum retVal;
	int32 top = self->numParams;

	fcinfo->isnull = false;
	currentCallContext->function = self;
	if(top > 0)
	{
		int32   idx;
		Type    invokerType;
		jvalue* args;
		Type*   types = self->paramTypes;

		/* a class loader or other mechanism might have connected already. This
		 * connection must be dropped since its parent context is wrong.
		 */
		if(self->isMultiCall && SRF_IS_FIRSTCALL())
			Backend_assertDisconnect();

		args  = (jvalue*)palloc(top * sizeof(jvalue));
		if(self->returnComplex)
		{
			--top; /* Last argument is not present in fcinfo */
			invokerType = types[top];
		}
		else
			invokerType = self->returnType;

		for(idx = 0; idx < top; ++idx)
		{
			if(PG_ARGISNULL(idx))
				/*
				 * Set this argument to zero (or null in case of object)
				 */
				args[idx].j = 0L;
			else
				args[idx] = Type_coerceDatum(
								types[idx], env, PG_GETARG_DATUM(idx));
		}

		retVal = Type_invoke(invokerType,
			env, self->clazz, self->method, args, fcinfo);
	
		pfree(args);
	}
	else
	{
		retVal = Type_invoke(self->returnType,
			env, self->clazz, self->method, NULL, fcinfo);
	}

	return retVal;
}

Datum Function_invokeTrigger(Function self, JNIEnv* env, PG_FUNCTION_ARGS)
{
	jvalue arg;
	Datum  ret;
	
	arg.l = TriggerData_create(env, (TriggerData*)fcinfo->context);
	if(arg.l == 0)
		return 0;

	currentCallContext->function = self;
	Type_invoke(self->returnType, env, self->clazz, self->method, &arg, fcinfo);

	fcinfo->isnull = false;
	if((*env)->ExceptionCheck(env))
		ret = 0;
	else
	{
		/* A new Tuple may or may not be created here. If it is, ensure that
		 * it is created in the upper SPI context.
		 */
		MemoryContext currCtx = MemoryContext_switchToUpperContext();
		ret = TriggerData_getTriggerReturnTuple(env, arg.l, &fcinfo->isnull);

		/* Triggers are not allowed to set the fcinfo->isnull, even when
		 * they return null.
		 */
		fcinfo->isnull = false;

		MemoryContextSwitchTo(currCtx);
	}

	(*env)->DeleteLocalRef(env, arg.l);
	return ret;
}

bool Function_isCurrentReadOnly(void)
{
	/* function will be 0 during resolve of class and java function. At
	 * that time, no updates are allowed (or needed).
	 */
	return (currentCallContext->function == 0)
		? true
		: currentCallContext->function->readOnly;
}

