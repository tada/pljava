/*
 * This file contains software that has been made available under
 * The Mozilla Public License 1.1. Use and distribution hereof are
 * subject to the restrictions set forth therein.
 *
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include "pljava/PgObject_priv.h"
#include "pljava/Function.h"
#include "pljava/HashMap.h"
#include "pljava/type/TriggerData.h"

#include <catalog/pg_proc.h>
#include <ctype.h>
#include <alloca.h>

struct Function_
{
	struct PgObject_ PgObject_extension;

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

Datum Function_initialize(PG_FUNCTION_ARGS)
{
	s_funcMap = HashMap_create(57, TopMemoryContext);
	s_FunctionClass = PgObjectClass_create("Function", sizeof(struct Function_), _Function_finalize);
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
				Type repl = Type_fromJavaType(dfltIds[idx], sign.data);
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

static void Function_init(Function self, JNIEnv* env, Oid functionId, bool isTrigger)
{
	/* Obtain the tuple that corresponds to the function
	 */
	HeapTuple procTup = PgObject_getValidTuple(PROCOID, functionId, "function");
	Form_pg_proc procStruct = (Form_pg_proc)GETSTRUCT(procTup);

	/* The user's function definition must be the fully
	 * qualified name of a java method short of parameter
	 * signature.
	 */
	StringInfoData sign;
	text* procSource = &procStruct->prosrc;
	int32 len = VARSIZE(procSource) - VARHDRSZ;	/* Length of string */
	const char* bp = VARDATA(procSource);		/* Points to start */
	const char* ep = bp + len;					/* Points just after end */
	const char* nameEp = ep;

	const char* ip;
	const char* paramDecl = 0;
	const char* methodName;
	char* cp;
	char* className;

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
	className = (char*)alloca(len + 1); /* One of the null terminators will replace a dot. */

	/* Copy class name. Replace '.' with '/'.
	 */
	cp = className;
	while(bp < ip)
	{
		char c = *bp++;
		if(c == '.')
			c = '/';
		*cp++ = c;
	}
	*cp++ = 0;
	self->clazz = (jclass)(*env)->NewGlobalRef(env, PgObject_getJavaClass(env, className));
	
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

	MemoryContext ctx = GetMemoryChunkContext(self);
	if(isTrigger)
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
		int top = (int32)procStruct->pronargs;
		self->numParams = top;
		if(top > 0)
		{
			int idx;
			Oid* typeIds = procStruct->proargtypes;
			self->paramTypes = (Type*)MemoryContextAlloc(ctx, top * sizeof(Type));
			for(idx = 0; idx < top; ++idx)
				self->paramTypes[idx] = Type_fromOid(typeIds[idx]);

			if(paramDecl != 0)
				Function_parseParameters(self, typeIds, paramDecl);
		}
		else
			self->paramTypes = 0;

		self->returnType = Type_fromOid(procStruct->prorettype);
	}

	Function_buildSignature(self, &sign, self->returnType);

	/*
	 * We don't need the procStruct anymore so we release its
	 * parent.
	 */
	ReleaseSysCache(procTup);

	self->method = (*env)->GetStaticMethodID(
						env, self->clazz, methodName, sign.data);

	if(self->method == 0)
	{
		char* origSign = sign.data;
		if(Type_isPrimitive(self->returnType))
		{
			/*
			 * There's one valid reason for not finding the method, namely
			 * if the return type used in the signature is a primitive and
			 * the true return type of the method is the object class that
			 * corresponds to that primitive.
			 */
			Type objType = Type_getObjectType(self->returnType);
	
			(*env)->ExceptionClear(env);
			Function_buildSignature(self, &sign, objType);
	
			self->method = (*env)->GetStaticMethodID(
							env, self->clazz, methodName, sign.data);
	
			if(self->method == 0)
				PgObject_throwMemberError(methodName, origSign, true, true);
	
			pfree(origSign);
			self->returnType = objType;
		}
		else
			PgObject_throwMemberError(methodName, origSign, true, true);
	}
	pfree(sign.data);
}

static Function Function_create(JNIEnv* env, Oid functionId, bool isTrigger)
{
	Function self = (Function)PgObjectClass_allocInstance(s_FunctionClass, TopMemoryContext);
	Function_init(self, env, functionId, isTrigger);
	return self;
}

Function Function_getFunction(JNIEnv* env, Oid functionId, bool isTrigger)
{
	Function func = (Function)HashMap_getByOid(s_funcMap, functionId);
	if(func == 0)
	{
		func = Function_create(env, functionId, isTrigger);
		PgObject old = HashMap_putByOid(s_funcMap, functionId, func);
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
	Type*   types   = self->paramTypes;
	int32   top     = self->numParams;
	int32   argSz   = top * sizeof(jvalue);
	jvalue* args    = (jvalue*)alloca(argSz);
	int32   idx;

	for(idx = 0; idx < top; ++idx)
	{
		Type type = types[idx];
		if(PG_ARGISNULL(idx))
			/*
			 * Set this argument to zero (or null in case of object)
			 */
			args[idx].j = 0L;
		else
			args[idx] = Type_coerceDatum(type, env, PG_GETARG_DATUM(idx));
	}

	fcinfo->isnull = false;
	return Type_invoke(self->returnType,
		env, self->clazz, self->method, args, &fcinfo->isnull);
}

Datum Function_invokeTrigger(Function self, JNIEnv* env, PG_FUNCTION_ARGS)
{
	jvalue arg;
	Datum  ret;
	
	arg.l = TriggerData_create(env, (TriggerData*)fcinfo->context);
	if(arg.l == 0)
		return 0;

	bool dummy;
	Type_invoke(self->returnType, env, self->clazz, self->method, &arg, &dummy);

	fcinfo->isnull = false;
	if((*env)->ExceptionCheck(env))
		ret = 0;
	else
		ret = TriggerData_getTriggerReturnTuple(env, arg.l, &fcinfo->isnull);

	(*env)->DeleteLocalRef(env, arg.l);
	return ret;
}
