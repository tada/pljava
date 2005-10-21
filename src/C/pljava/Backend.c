/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include <postgres.h>
#include <miscadmin.h>
#ifndef WIN32
#include <libpq/pqsignal.h>
#endif
#include <executor/spi.h>
#include <commands/trigger.h>
#include <utils/elog.h>
#include <utils/guc.h>
#include <fmgr.h>
#include <access/heapam.h>
#include <utils/syscache.h>
#include <catalog/catalog.h>
#include <catalog/pg_proc.h>
#include <catalog/pg_type.h>
#include <storage/ipc.h>
#include <storage/proc.h>
#include <stdio.h>
#include <ctype.h>
#include <unistd.h>

#include "org_postgresql_pljava_internal_Backend.h"
#include "org_postgresql_pljava_jdbc_Invocation.h"
#include "pljava/CallContext.h"
#include "pljava/Function.h"
#include "pljava/type/ExecutionPlan.h"
#include "pljava/HashMap.h"
#include "pljava/Exception.h"
#include "pljava/EOXactListener.h"
#include "pljava/Backend.h"
#include "pljava/MemoryContext.h"
#include "pljava/Session.h"
#include "pljava/SPI.h"
#include "pljava/type/JavaHandle.h"
#include "pljava/type/String.h"
/* Example format: "/usr/local/pgsql/lib" */
#ifndef PKGLIBDIR
#error "PKGLIBDIR needs to be defined to compile this file."
#endif

#define LOCAL_REFERENCE_COUNT 128

jlong mainThreadId;

static JavaVM* s_javaVM = 0;
static jclass  s_Backend_class;
static jmethodID s_setTrusted;
static bool    s_currentTrust = false;

static char* vmoptions;
static char* classpath;
static int statementCacheSize;
static bool  pljavaDebug;
static bool  pljavaReleaseLingeringSavepoints;
static jmethodID s_Invocation_onExit;

extern void Exception_initialize(void);
extern void HashMap_initialize(void);
extern void SPI_initialize(void);
extern void Type_initialize(void);
extern void Function_initialize(void);
extern void Session_initialize(void);

static void initPLJavaClasses(void)
{
	jfieldID tlField;
	jclass cls;

	JNINativeMethod backendMethods[] =
	{
		{
		"isCallingJava",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Backend_isCallingJava
		},
		{
		"isReleaseLingeringSavepoints",
	  	"()Z",
	  	Java_org_postgresql_pljava_internal_Backend_isReleaseLingeringSavepoints
		},
		{
		"_getConfigOption",
		"(Ljava/lang/String;)Ljava/lang/String;",
		Java_org_postgresql_pljava_internal_Backend__1getConfigOption
		},
		{
		"_getStatementCacheSize",
		"()I",
		Java_org_postgresql_pljava_internal_Backend__1getStatementCacheSize
		},
		{
		"_log",
		"(ILjava/lang/String;)V",
		Java_org_postgresql_pljava_internal_Backend__1log
		},
		{
		"_addEOXactListener",
		"(Lorg/postgresql/pljava/internal/EOXactListener;)V",
		Java_org_postgresql_pljava_internal_Backend__1addEOXactListener
		},
		{
		"_removeEOXactListener",
		"(Lorg/postgresql/pljava/internal/EOXactListener;)V",
		Java_org_postgresql_pljava_internal_Backend__1removeEOXactListener
		},
		{ 0, 0, 0 }
	};


	JNINativeMethod invocationMethods[] =
	{
		{
		"_getCurrent",
		"()Lorg/postgresql/pljava/jdbc/Invocation;",
		Java_org_postgresql_pljava_jdbc_Invocation__1getCurrent
		},
		{
		"_getNestingLevel",
		"()I",
		Java_org_postgresql_pljava_jdbc_Invocation__1getNestingLevel
		},
		{
		"_clearErrorCondition",
		"()V",
		Java_org_postgresql_pljava_jdbc_Invocation__1clearErrorCondition
		},
		{
		"_register",
		"()V",
		Java_org_postgresql_pljava_jdbc_Invocation__1register
		},
		{ 0, 0, 0 }
	};

	elog(DEBUG1, "Getting Backend class pljava.jar");
	s_Backend_class = PgObject_getJavaClass("org/postgresql/pljava/internal/Backend");
	elog(DEBUG1, "Backend class was there");
	PgObject_registerNatives2(s_Backend_class, backendMethods);

	tlField = PgObject_getStaticJavaField(s_Backend_class, "THREADLOCK", "Ljava/lang/Object;");
	JNI_setThreadLock(JNI_getStaticObjectField(s_Backend_class, tlField));

	s_setTrusted = PgObject_getStaticJavaMethod(s_Backend_class, "setTrusted", "(Z)V");

	cls = PgObject_getJavaClass("org/postgresql/pljava/jdbc/Invocation");
	PgObject_registerNatives2(cls, invocationMethods);
	s_Invocation_onExit = PgObject_getJavaMethod(cls, "onExit", "()V");
	JNI_deleteLocalRef(cls);

	Exception_initialize();
	SPI_initialize();
	Type_initialize();
	Function_initialize();
	Session_initialize();
}

static bool s_topLocalFrameInstalled = false;
static unsigned int s_callLevel = 0;

static void popJavaFrameCB(MemoryContext ctx, bool isDelete)
{
	if(s_callLevel == 0 && s_topLocalFrameInstalled)
	{
		/* Pop this frame. This might call finalizers.
		 */
		Backend_popJavaFrame();
		s_topLocalFrameInstalled = false;
	}
}

/**
 *  Initialize security
 */
static void setJavaSecurity(bool trusted)
{
/* GCJ has major issues here. Real work on SecurityManager and
 * related classes has just started in version 4.0.0.
 */
#ifndef GCJ
	JNI_callStaticVoidMethod(s_Backend_class, s_setTrusted, (jboolean)trusted);
	if(JNI_exceptionCheck())
	{
		JNI_exceptionDescribe();
		JNI_exceptionClear();
		ereport(ERROR, (
			errcode(ERRCODE_INTERNAL_ERROR),
			errmsg("Unable to initialize java security")));
	}
#endif
}

bool integerDateTimes = false;
CallContext* currentCallContext;

void Backend_pushCallContext(CallContext* ctx, bool trusted)
{
	ctx->invocation      = 0;
	ctx->function        = 0;
	ctx->trusted         = trusted;
	ctx->hasConnected    = false;
	ctx->upperContext    = CurrentMemoryContext;
	ctx->errorOccured    = false;
	ctx->inExprContextCB = false;
	ctx->previous        = currentCallContext;
	currentCallContext   = ctx;

	if(trusted != s_currentTrust)
	{
		setJavaSecurity(trusted);
		s_currentTrust = trusted;
	}
}

void Backend_popCallContext(void)
{
	CallContext* ctx = currentCallContext->previous;
	if(ctx != 0)
	{
		if(ctx->trusted != s_currentTrust)
		{
			setJavaSecurity(ctx->trusted);
			s_currentTrust = ctx->trusted;
		}
		MemoryContextSwitchTo(ctx->upperContext);
	}
	currentCallContext = ctx;
}

void Backend_pushJavaFrame(void)
{
	if(JNI_pushLocalFrame(LOCAL_REFERENCE_COUNT) < 0)
	{
		/* Out of memory
		 */
		JNI_exceptionClear();
		ereport(ERROR, (
			errcode(ERRCODE_OUT_OF_MEMORY),
			errmsg("Unable to create java frame for local references")));
	}
}

void Backend_popJavaFrame(void)
{
	JNI_popLocalFrame(0);
}

/**
 * Special purpose logging function called from JNI when verbose is enabled.
 */
static jint JNICALL my_vfprintf(FILE* fp, const char* format, va_list args)
{
	char buf[1024];
	char* ep;
	char* bp = buf;

    vsnprintf(buf, sizeof(buf), format, args);

    /* Trim off trailing newline and other whitespace.
     */
	ep = bp + strlen(bp) - 1;
    while(ep >= bp && isspace(*ep))
 		--ep;
 	++ep;
 	*ep = 0;

    elog(LOG, buf);
    return 0;
}

/*
 * Append those parts of path that has not yet been appended. The HashMap unique is
 * keeping track of what has been appended already. First appended part will be
 * prefixed with prefix.
 */
static void appendPathParts(const char* path, StringInfoData* bld, HashMap unique, const char* prefix)
{
	StringInfoData buf;
	if(path == 0 || strlen(path) == 0)
		return;

	for (;;)
	{
		char* pathPart;
		size_t len;
		if(*path == 0)
			break;

		len = strcspn(path, ";:");

		if(len == 1 && *(path+1) == ':' && isalnum(*path))
			/*
			 * Windows drive designator, leave it "as is".
			 */
			len = strcspn(path+2, ";:") + 2;
		else
		if(len == 0)
			{
			/* Ignore zero length components.
			 */
			++path;
			continue;
			}

		initStringInfo(&buf);
		if(*path == '$')
		{
			if(len == 7 || (strcspn(path, "/\\") == 7 && strncmp(path, "$libdir", 7) == 0))
			{
				len -= 7;
				path += 7;
				appendStringInfo(&buf, PKGLIBDIR);
			}
			else
				ereport(ERROR, (
					errcode(ERRCODE_INVALID_NAME),
					errmsg("invalid macro name '%*s' in dynamic library path", (int)len, path)));
		}

		if(len > 0)
		{
			appendBinaryStringInfo(&buf, path, len);
			path += len;
		}

		pathPart = buf.data;
		if(HashMap_getByString(unique, pathPart) == 0)
		{
			if(HashMap_size(unique) == 0)
				appendStringInfo(bld, prefix);
			else
#if defined(WIN32)
				appendStringInfoChar(bld, ';');
#else
				appendStringInfoChar(bld, ':');
#endif
			appendStringInfo(bld, pathPart);
			HashMap_putByString(unique, pathPart, (void*)1);
		}
		pfree(pathPart);
		if(*path == 0)
			break;
		++path; /* Skip ':' */
	}
}

/*
 * Get the CLASSPATH. Result is always freshly palloc'd.
 */
static char* getClassPath(const char* prefix)
{
	char* path;
	HashMap unique = HashMap_create(13, CurrentMemoryContext);
	StringInfoData buf;
	initStringInfo(&buf);
	appendPathParts(classpath, &buf, unique, prefix);
	appendPathParts(getenv("CLASSPATH"), &buf, unique, prefix);
	PgObject_free((PgObject)unique);
	path = buf.data;
	if(strlen(path) == 0)
	{
		pfree(path);
		path = 0;
	}
	return path;
}

#if !defined(WIN32)

static void pljavaStatementCancelHandler(int signum)
{
	if(!proc_exit_inprogress)
	{
		/* Never service the interrupt immediately. In order to find out if
		 * its safe, we would need to know what kind of threading mechanism
		 * the VM uses. That would count for a lot of conditional code.
		 */
		QueryCancelPending = true;
		InterruptPending = true;
	}
}

static void pljavaDieHandler(int signum)
{
	if(!proc_exit_inprogress)
	{
		/* Never service the interrupt immediately. In order to find out if
		 * its safe, we would need to know what kind of threading mechanism
		 * the VM uses. That would count for a lot of conditional code.
		 */
		ProcDiePending = true;
		InterruptPending = true;
	}
}

static void pljavaQuickDieHandler(int signum)
{
	/* Just die. No ereporting here since we don't know what thread this is.
	 */
	exit(1);
}

static sigjmp_buf recoverBuf;
static void terminationTimeoutHandler(int signum)
{
	kill(MyProcPid, SIGQUIT);
	
	/* Some sleep to get the SIGQUIT a chance to generate
	 * the needed output.
	 */
	pg_usleep(1);

	/* JavaVM did not die within the alloted time
	 */
	siglongjmp(recoverBuf, 1);
}
#endif

/*
 * proc_exit callback to tear down the JVM
 */
static void _destroyJavaVM(int status, Datum dummy)
{
	if(s_javaVM != 0)
	{
		CallContext ctx;
#if !defined(WIN32)
		pqsigfunc saveSigAlrm;

		Backend_pushCallContext(&ctx, false);
		if(sigsetjmp(recoverBuf, 1) != 0)
		{
			elog(DEBUG1, "JavaVM destroyed with force");
			s_javaVM = 0;
			return;
		}

		saveSigAlrm = pqsignal(SIGALRM, terminationTimeoutHandler);
		enable_sig_alarm(5000, false);

		elog(DEBUG1, "Destroying JavaVM...");
		JNI_destroyVM(s_javaVM);
		disable_sig_alarm(false);
		pqsignal(SIGALRM, saveSigAlrm);
#else
		Backend_pushCallContext(&ctx, false);
		elog(DEBUG1, "Destroying JavaVM...");
		JNI_destroyVM(s_javaVM);
#endif
		elog(DEBUG1, "JavaVM destroyed");
		s_javaVM = 0;
		Backend_popCallContext();
	}
}

typedef struct {
	JavaVMOption* options;
	unsigned int  size;
	unsigned int  capacity;
} JVMOptList;

static void JVMOptList_init(JVMOptList* jol)
{
	jol->options  = (JavaVMOption*)palloc(10 * sizeof(JavaVMOption));
	jol->size     = 0;
	jol->capacity = 10;
}

static void JVMOptList_delete(JVMOptList* jol)
{
	JavaVMOption* opt = jol->options;
	JavaVMOption* top = opt + jol->size;
	while(opt < top)
	{
		pfree(opt->optionString);
		opt++;
	}
	pfree(jol->options);
}

static void JVMOptList_add(JVMOptList* jol, const char* optString, void* extraInfo, bool mustCopy)
{
	JavaVMOption* added;

	int newPos = jol->size;
	if(newPos >= jol->capacity)
	{
		int newCap = jol->capacity * 2;
		JavaVMOption* newOpts = (JavaVMOption*)palloc(newCap * sizeof(JavaVMOption));
		memcpy(newOpts, jol->options, newPos * sizeof(JavaVMOption));
		pfree(jol->options);
		jol->options = newOpts;
		jol->capacity = newCap;
	}
	added = jol->options + newPos;
	if(mustCopy)
		optString = pstrdup(optString);

	added->optionString = (char*)optString;
	added->extraInfo    = extraInfo;
	jol->size++;

	elog(DEBUG1, "Added JVM option string \"%s\"", optString);		
}

/* Split JVM options. The string is split on whitespace unless the
 * whitespace is found within a string or is escaped by backslash. A
 * backslash escaped quote is not considered a string delimiter.
 */
static void addUserJVMOptions(JVMOptList* optList)
{
	const char* cp = vmoptions;
	
	if(cp != NULL)
	{
		StringInfoData buf;
		char quote = 0;
		char c;

		initStringInfo(&buf);
		for(;;)
		{
			c = *cp++;
			switch(c)
			{
				case 0:
					break;

				case '"':
				case '\'':
					if(quote == c)
						quote = 0;
					else
						quote = c;
					appendStringInfoChar(&buf, c);
					continue;

				case '\\':
					appendStringInfoChar(&buf, '\\');
					c = *cp++;	/* Interpret next character verbatim */
					if(c == 0)
						break;
					appendStringInfoChar(&buf, c);
					continue;
					
				default:
					if(quote == 0 && isspace((int)c))
					{
						while((c = *cp++) != 0)
						{
							if(!isspace((int)c))
								break;
						}

						if(c == 0)
							break;

						if(c != '-')
							appendStringInfoChar(&buf, ' ');
						else if(buf.len > 0)
						{
							/* Whitespace followed by '-' triggers new
							 * option declaration.
							 */
							JVMOptList_add(optList, buf.data, 0, true);
							buf.len = 0;
							buf.data[0] = 0;
						}
					}
					appendStringInfoChar(&buf, c);
					continue;
			}
			break;
		}
		if(buf.len > 0)
			JVMOptList_add(optList, buf.data, 0, true);
		pfree(buf.data);
	}
}

/**
 *  Initialize the session
 */
static void initJavaSession(void)
{
	jclass sessionClass = PgObject_getJavaClass("org/postgresql/pljava/internal/Session");
	jmethodID init = PgObject_getStaticJavaMethod(sessionClass, "init", "()J");
	mainThreadId = JNI_callStaticLongMethod(sessionClass, init);
	JNI_deleteLocalRef(sessionClass);

	if(JNI_exceptionCheck())
	{
		JNI_exceptionDescribe();
		JNI_exceptionClear();
		ereport(ERROR, (
			errcode(ERRCODE_INTERNAL_ERROR),
			errmsg("Unable to initialize java session")));
	}
}

static void checkIntTimeType(void)
{
	const char* idt = GetConfigOption("integer_datetimes");
	integerDateTimes = (strcmp(idt, "on") == 0);
	elog(DEBUG1, integerDateTimes ? "Using integer_datetimes" : "Not using integer_datetimes");
}

static bool s_firstTimeInit = true;

static void initializeJavaVM(void)
{
	jboolean jstat;
	JavaVMInitArgs vm_args;
	JVMOptList optList;

	JVMOptList_init(&optList);

	if(s_firstTimeInit)
	{
		s_firstTimeInit = false;

		checkIntTimeType();
		HashMap_initialize();
	
		DefineCustomStringVariable(
			"pljava.vmoptions",
			"Options sent to the JVM when it is created",
			NULL,
			&vmoptions,
			PGC_USERSET,
			NULL, NULL);
	
		DefineCustomStringVariable(
			"pljava.classpath",
			"Classpath used by the JVM",
			NULL,
			&classpath,
			PGC_USERSET,
			NULL, NULL);
	
		DefineCustomBoolVariable(
			"pljava.debug",
			"Stop the backend to attach a debugger",
			NULL,
			&pljavaDebug,
			PGC_USERSET,
			NULL, NULL);
	
		DefineCustomIntVariable(
			"pljava.statement_cache_size",
			"Size of the prepared statement MRU cache",
			NULL,
			&statementCacheSize,
			0, 512,
			PGC_USERSET,
			NULL, NULL);
	
		DefineCustomBoolVariable(
			"pljava.release_lingering_savepoints",
			"If true, lingering savepoints will be released on function exit. If false, the will be rolled back",
			NULL,
			&pljavaReleaseLingeringSavepoints,
			PGC_USERSET,
			NULL, NULL);
	
		EmitWarningsOnPlaceholders("pljava");
			s_firstTimeInit = false;
	}

#ifdef PLJAVA_DEBUG
	/* Hard setting for debug. Don't forget to recompile...
	 */
	pljavaDebug = 1;
#endif

	addUserJVMOptions(&optList);
	effectiveClassPath = getClassPath("-Djava.class.path=");
	if(effectiveClassPath != 0)
	{
		JVMOptList_add(&optList, effectiveClassPath, 0, true);
	}

	/**
	 * As stipulated by JRT-2003
	 */
	JVMOptList_add(&optList, 
		"-Dsqlj.defaultconnection=jdbc:default:connection",
		0, true);

	JVMOptList_add(&optList, "vfprintf", (void*)my_vfprintf, true);
	JVMOptList_add(&optList, "-Xrs", 0, true);

	if(pljavaDebug)
	{
		elog(INFO, "Backend pid = %d. Attach the debugger and set pljavaDebug to false to continue", getpid());
		while(pljavaDebug)
			pg_usleep(1000000L);
	}

	vm_args.nOptions = optList.size;
	vm_args.options  = optList.options;
	vm_args.version  = JNI_VERSION_1_4;
	vm_args.ignoreUnrecognized = JNI_FALSE;

	elog(DEBUG1, "Creating JavaVM");

	jstat = JNI_createVM(&s_javaVM, &vm_args);

	if(jstat == JNI_OK && JNI_exceptionCheck())
	{
		JNI_exceptionDescribe();
		JNI_exceptionClear();
		jstat = JNI_ERR;
	}
	JVMOptList_delete(&optList);

	if(jstat != JNI_OK)
		ereport(ERROR, (errmsg("Failed to create Java VM")));

#if !defined(WIN32)
	pqsignal(SIGINT,  pljavaStatementCancelHandler);
	pqsignal(SIGTERM, pljavaDieHandler);
	pqsignal(SIGQUIT, pljavaQuickDieHandler);
#endif
	elog(DEBUG1, "JavaVM created");

	/* Register an on_proc_exit handler that destroys the VM
	 */
	on_proc_exit(_destroyJavaVM, 0);
	initPLJavaClasses();
	initJavaSession();
}

void
Backend_assertConnect(void)
{
	if(!currentCallContext->hasConnected)
	{
		SPI_connect();
		currentCallContext->hasConnected = true;
	}
}

void
Backend_assertDisconnect(void)
{
	if(currentCallContext->hasConnected)
	{
		SPI_finish();
		currentCallContext->hasConnected = false;
	}
}

static Datum internalCallHandler(bool trusted, PG_FUNCTION_ARGS);

extern Datum javau_call_handler(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(javau_call_handler);

/*
 * This is the entry point for all untrusted calls.
 */
Datum javau_call_handler(PG_FUNCTION_ARGS)
{
	return internalCallHandler(false, fcinfo);
}

extern Datum java_call_handler(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(java_call_handler);

/*
 * This is the entry point for all trusted calls.
 */
Datum java_call_handler(PG_FUNCTION_ARGS)
{
	return internalCallHandler(true, fcinfo);
}

static Datum internalCallHandler(bool trusted, PG_FUNCTION_ARGS)
{
	CallContext ctx;
	Datum retval = 0;

	if(s_javaVM == 0)
	{
		/* Initialize the VM, we pass the s_currentTrust here
		 * to ensure that the pushCallContext doesn't call on
		 * Java until the JVM is initialized.
		 */
		Backend_pushCallContext(&ctx, s_currentTrust);
		PG_TRY();
		{
			initializeJavaVM();
		}
		PG_CATCH();
		{
			Backend_popCallContext();

			/* JVM initialization failed for some reason. Destroy
			 * the VM if it exists. Perhaps the user will try
			 * fixing the pljava.classpath and make a new attempt.
			 */
			_destroyJavaVM(0, 0);			

			/* We can't stay here...
			 */
			PG_RE_THROW();
		}
		PG_END_TRY();
		Backend_popCallContext();

		/* Force initial setting
		 */
		s_currentTrust = !trusted;
	}

	Backend_pushCallContext(&ctx, trusted);
	if(s_callLevel == 0 && !s_topLocalFrameInstalled)
	{
		Backend_pushJavaFrame();
		s_topLocalFrameInstalled = true;
		MemoryContext_addEndOfScopeCB(CurrentMemoryContext, popJavaFrameCB);
	}

	++s_callLevel;
	PG_TRY();
	{
		Function function = Function_getFunction(fcinfo);
		if(CALLED_AS_TRIGGER(fcinfo))
		{
			/* Called as a trigger procedure
			 */
			retval = Function_invokeTrigger(function, fcinfo);
		}
		else
		{
			/* Called as a function
			 */
			retval = Function_invoke(function, fcinfo);
		}
		if(ctx.invocation != 0)
		{
			JNI_callVoidMethod(ctx.invocation, s_Invocation_onExit);
			JNI_deleteGlobalRef(ctx.invocation);
		}

		--s_callLevel;
		Backend_assertDisconnect();
		Backend_popCallContext();
	}
	PG_CATCH();
	{
		--s_callLevel;
		if(ctx.invocation != 0)
			JNI_deleteGlobalRef(ctx.invocation);
		Backend_assertDisconnect();
		Backend_popCallContext();
		PG_RE_THROW();
	}
	PG_END_TRY();
	return retval;
}

/****************************************
 * JNI methods
 ****************************************/
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved)
{
	return JNI_VERSION_1_4;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _getConfigOption
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
JNICALL Java_org_postgresql_pljava_internal_Backend__1getConfigOption(JNIEnv* env, jclass cls, jstring jkey)
{
	jstring result = 0;
	
	BEGIN_NATIVE
	char* key = String_createNTS(jkey);
	if(key != 0)
	{
		PG_TRY();
		{
			const char* value = GetConfigOption(key);
			pfree(key);
			if(value != 0)
				result = String_createJavaStringFromNTS(value);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("GetConfigOption");
		}
		PG_END_TRY();
	}
	END_NATIVE
	return result;
}


/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _getStatementCacheSize
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_internal_Backend__1getStatementCacheSize(JNIEnv* env, jclass cls)
{
	return statementCacheSize;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _log
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
JNICALL Java_org_postgresql_pljava_internal_Backend__1log(JNIEnv* env, jclass cls, jint logLevel, jstring jstr)
{
	BEGIN_NATIVE_NO_ERRCHECK
	char* str = String_createNTS(jstr);
	if(str != 0)
	{
		/* elog uses printf formatting but the logger does not so we must escape all
		 * '%' in the string.
		 */
		char c;
		const char* cp;
		int percentCount = 0;
		for(cp = str; (c = *cp) != 0; ++cp)
		{
			if(c == '%')
				++percentCount;
		}
	
		if(percentCount > 0)
		{
			/* Make room to expand all "%" to "%%"
			 */
			char* str2 = palloc((cp - str) + percentCount + 1);
			char* cp2 = str2;
	
			/* Expand... */
			for(cp = str; (c = *cp) != 0; ++cp)
			{
				if(c == '%')
					*cp2++ = c;
				*cp2++ = c;
			}
			*cp2 = 0;
			pfree(str);
			str = str2;
		}
	
		PG_TRY();
		{
			elog(logLevel, str);
			pfree(str);
		}
		PG_CATCH();
		{
			Exception_throw_ERROR("ereport");
		}
		PG_END_TRY();
	}
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    isCallingJava
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_isCallingJava(JNIEnv* env, jclass cls)
{
	return JNI_isCallingJava();
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    isReleaseLingeringSavepoints
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_isReleaseLingeringSavepoints(JNIEnv* env, jclass cls)
{
	return pljavaReleaseLingeringSavepoints ? JNI_TRUE : JNI_FALSE;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _addEOXactListener
 * Signature: (Lorg/postgresql/pljava/internal/EOXactListener;)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Backend__1addEOXactListener(JNIEnv* env, jclass cls, jobject listener)
{
	BEGIN_NATIVE
	PG_TRY();
	{
		EOXactListener_register(listener);
	}
	PG_CATCH();
	{
		Exception_throw_ERROR("RegisterEOXactCallback");
	}
	PG_END_TRY();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _removeEOXactListener
 * Signature: (Lorg/postgresql/pljava/internal/EOXactListener;)V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_internal_Backend__1removeEOXactListener(JNIEnv* env, jclass cls, jobject listener)
{
	BEGIN_NATIVE
	EOXactListener_unregister();
	END_NATIVE
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _getNestingLevel
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1getNestingLevel(JNIEnv* env, jclass cls)
{
	return s_callLevel;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _getCurrent
 * Signature: ()Lorg/postgresql/pljava/jdbc/Invocation;
 */
JNIEXPORT jobject JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1getCurrent(JNIEnv* env, jclass cls)
{
	return currentCallContext->invocation;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _clearErrorCondition
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1clearErrorCondition(JNIEnv* env, jclass cls)
{
	currentCallContext->errorOccured = false;
}

/*
 * Class:     org_postgresql_pljava_jdbc_Invocation
 * Method:    _register
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_org_postgresql_pljava_jdbc_Invocation__1register(JNIEnv* env, jobject _this)
{
	BEGIN_NATIVE
	currentCallContext->invocation = JNI_newGlobalRef(_this);
	END_NATIVE
}
