/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
#include <postgres.h>
#include <miscadmin.h>
#include <libpq/pqsignal.h>
#include <executor/spi.h>
#include <commands/trigger.h>
#include <utils/elog.h>
#include <utils/guc.h>
#include <fmgr.h>
#include <access/heapam.h>
#include <utils/syscache.h>
#include <catalog/pg_proc.h>
#include <catalog/pg_type.h>
#include <storage/ipc.h>
#include <storage/proc.h>
#include <stdio.h>
#include <ctype.h>
#include <unistd.h>

#include "pljava/Function.h"
#include "pljava/type/Type.h"
#include "pljava/type/NativeStruct.h"
#include "pljava/HashMap.h"
#include "pljava/Exception.h"
#include "pljava/Backend_JNI.h"
#include "pljava/SPI.h"
#include "pljava/type/String.h"

/* Example format: "/usr/local/pgsql/lib" */
#ifndef PKGLIBDIR
#error "PKGLIBDIR needs to be defined to compile this file."
#endif

bool elogErrorOccured;
bool isCallingJava;

#define LOCAL_REFERENCE_COUNT 32

static JNIEnv* s_mainEnv = 0;
static JavaVM* s_javaVM = 0;
static int callLevel = 0;

#ifdef PGSQL_CUSTOM_VARIABLES
static char* vmoptions;
static char* classpath;
#endif

static void initJavaVM(JNIEnv* env)
{
	Datum envDatum = PointerGetDatum(env);
	DirectFunctionCall1(Exception_initialize, envDatum);
	DirectFunctionCall1(Type_initialize, envDatum);
	DirectFunctionCall1(Function_initialize, envDatum);
}

static Datum callFunction(JNIEnv* env, PG_FUNCTION_ARGS)
{
	Datum retval;
	HashMap saveNativeStructCache;
	sigjmp_buf saveRestart;
	bool saveErrorOccured = elogErrorOccured;
	bool saveIsCallingJava = isCallingJava;
	Oid funcOid = fcinfo->flinfo->fn_oid;

	if(callLevel == 0)
	{
		/* Since the call does not originate from the JavaVM, we must
		 * push a local frame that ensures garbage collection of
		 * new objecs once popped (somewhat similar to palloc, but for
		 * Java objects).
		 */
		if((*env)->PushLocalFrame(env, LOCAL_REFERENCE_COUNT) < 0)
		{
			/* Out of memory
			 */
			(*env)->ExceptionClear(env);
			ereport(ERROR, (
				errcode(ERRCODE_OUT_OF_MEMORY),
				errmsg("Unable to create java frame for local references")));
		}
	}

	/* Save some static stuff on the stack that we want to preserve in
	 * case this function is reentered. This may happend if Java calls
	 * out to SQL which in turn invokes a new Java function.
	 */
	saveNativeStructCache = NativeStruct_pushCache();
	memcpy(&saveRestart, &Warn_restart, sizeof(saveRestart));
	elogErrorOccured = false;

	++callLevel;
	if(sigsetjmp(Warn_restart, 1) != 0)
	{
		/* Catch block.
		 */
		--callLevel;
		memcpy(&Warn_restart, &saveRestart, sizeof(Warn_restart));
		elogErrorOccured = saveErrorOccured;
		isCallingJava = saveIsCallingJava;
		NativeStruct_popCache(env, saveNativeStructCache);
		if(callLevel == 0)
			(*env)->PopLocalFrame(env, 0);
		siglongjmp(Warn_restart, 1);
	}

	if(CALLED_AS_TRIGGER(fcinfo))
	{
		/* Called as a trigger procedure
		 */
		Function function = Function_getFunction(env, funcOid, true);
		retval = Function_invokeTrigger(function, env, fcinfo);
	}
	else
	{
		/* Called as a function
		 */
		Function function = Function_getFunction(env, funcOid, false);
		retval = Function_invoke(function, env, fcinfo);
	}
	Exception_checkException(env);
	if(elogErrorOccured)
		longjmp(Warn_restart, 1);

	/* Pop of the "try/catch" block.
	 */
	--callLevel;
	memcpy(&Warn_restart, &saveRestart, sizeof(Warn_restart));
	elogErrorOccured = saveErrorOccured;
	isCallingJava = saveIsCallingJava;
	NativeStruct_popCache(env, saveNativeStructCache);
	if(callLevel == 0)
		(*env)->PopLocalFrame(env, 0);
	return retval;
}

bool pljavaEntryFence(JNIEnv* env)
{
	if(elogErrorOccured)
	{
		/* An elog with level higher than ERROR was issued. The transaction
		 * state is unknown. There's no way the JVM is allowed to enter the
		 * backend at this point.
		 */
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"An attempt was made to call a PostgreSQL backend function after an elog(ERROR) had been issued");
		return true;
	}
	if(!isCallingJava)
	{
		/* The backend is *not* awaiting the return of a call to the JVM
		 * so there's no way the JVM can be allowed to call out at this
		 * point.
		 */
		Exception_throw(env, ERRCODE_INTERNAL_ERROR,
			"An attempt was made to call a PostgreSQL backend function while main thread was not in the JVM");
		return true;
	}
	return false;
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

#if defined(CYGWIN) && !defined(GCJ)
		/**
		 * Translate "/cygdrive/<driverLetter>/" into "<driveLetter>:/" since
		 * the JVM dynamic loader will fail to recognize the former.
		 * 
		 * This is somewhat ugly and will be removed as soon as the native port
		 * of postgresql is released.
		 */
		if(len >= 11
		&& (*path == '/' || *path == '\\')
		&& strncmp(path + 1, "cygdrive", 8) == 0)
		{
			const char* cp = path + 9;
			if(*cp == '/' || *cp == '\\')
			{
				char driveLetter = *(++cp);
				if(isalnum(driveLetter))
				{
					char sep = *(++cp);
					if(sep == '/' || sep == '\\' || sep == ':' || sep == ';' || sep == 0)
					{
						/* Path starts with /cygdrive/<driveLetter>. Replace
						 * this with <driverLetter>:\
						 */
						appendStringInfoChar(&buf, driveLetter);
						appendStringInfo(&buf, ":\\");
						if(sep == '\\' || sep == '/')
							++cp;
						len -= (cp - path);
						path = cp;
					}
				}
			}
		}				
#endif

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
					errmsg("invalid macro name '%*s' in dynamic library path", len, path)));
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
#if defined(WIN32) || (defined(CYGWIN) && !defined(GCJ))
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
 * Get the Dynamic_library_path configuration parameter and the
 * LD_LIBRARY_PATH (or PATH in case of WIN32) environment variable
 * merged together. The components found in the Dynamic_library_path
 * are placed first in the result. Substitute for any macros appearing
 * in the given string. Result is always freshly palloc'd.
 *
 * NOTE Currently, we only allow the $libdir macro. All else will
 * result in an exception.
 */
static char* getLibraryPath(const char* prefix)
{
	char* path;
	StringInfoData buf;
	HashMap unique = HashMap_create(13, CurrentMemoryContext);
	const char* dynPath = GetConfigOption("dynamic_library_path");

	initStringInfo(&buf);
	appendPathParts(dynPath, &buf, unique, prefix);

#if WIN32 || CYGWIN
	appendPathParts(getenv("PATH"), &buf, unique, prefix); /* DLL's are found using standard system path */
#else
	appendPathParts(getenv("LD_LIBRARY_PATH"), &buf, unique, prefix);
#endif

	PgObject_free((PgObject)unique);
	path = buf.data;
	if(strlen(path) == 0)
	{
		pfree(path);
		path = 0;
	}
	return path;
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
#ifdef PGSQL_CUSTOM_VARIABLES
	appendPathParts(classpath, &buf, unique, prefix);
#endif
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

#if !defined(WIN32) && !defined(CYGWIN)
static pqsigfunc s_jvmSigQuit;
static sigjmp_buf recoverBuf;

static void alarmHandler(int signum)
{
	kill(MyProcPid, SIGQUIT);
	
	/* Some sleep to get the SIGQUIT a chance to generate
	 * the needed output.
	 */
	sleep(1);

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
#if !defined(WIN32) && !defined(CYGWIN)
		pqsigfunc saveSigQuit;
		pqsigfunc saveSigAlrm;

		if(sigsetjmp(recoverBuf, 1) != 0)
		{
			elog(LOG, "JavaVM destroyed with force");
			s_javaVM = 0;
			return;
		}

		saveSigQuit = pqsignal(SIGQUIT, s_jvmSigQuit);
		saveSigAlrm = pqsignal(SIGALRM, alarmHandler);

		enable_sig_alarm(5000, false);
#endif

		elog(LOG, "Destroying JavaVM...");

		isCallingJava = true;
		(*s_javaVM)->DestroyJavaVM(s_javaVM);
		isCallingJava = false;

#if !defined(WIN32) && !defined(CYGWIN)
		disable_sig_alarm(false);

		pqsignal(SIGQUIT, saveSigQuit);
		pqsignal(SIGALRM, saveSigAlrm);
#endif

		elog(LOG, "JavaVM destroyed");
		s_javaVM = 0;
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
}

#ifdef PGSQL_CUSTOM_VARIABLES
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
#endif

static void initializeJavaVM()
{
#if !defined(WIN32) && !defined(CYGWIN)
	pqsigfunc saveSigInt;
	pqsigfunc saveSigTerm;
	pqsigfunc saveSigHup;
	pqsigfunc saveSigQuit;
#endif
	const char* tmp;
	jboolean jstat;
 
	JavaVMInitArgs vm_args;
	JVMOptList optList;
	
	JVMOptList_init(&optList);

	DirectFunctionCall1(HashMap_initialize, 0);

#ifdef PGSQL_CUSTOM_VARIABLES
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

	EmittWarningsOnPlaceholders("pljava");

	addUserJVMOptions(&optList);
#endif

	tmp = getClassPath("-Djava.class.path=");
	if(tmp != 0)
	{
		JVMOptList_add(&optList, tmp, 0, false);
	}

	/**
	 * The JVM needs the java.library.path to find its way back to
	 * the loaded module.
	 */
	tmp = getLibraryPath("-Djava.library.path=");
	if(tmp != 0)
	{
		JVMOptList_add(&optList, tmp, 0, false);
	}

	/**
	 * Default LoggingManager initializer.
	 */
	JVMOptList_add(&optList,
		"-Djava.util.logging.config.class=org.postgresql.pljava.internal.LoggerConfigurator",
		0, true);

	/**
	 * As stipulated by JRT-2003
	 */
	JVMOptList_add(&optList, 
		"-Dsqlj.defaultconnection=jdbc:default:connection",
		0, true);

	JVMOptList_add(&optList, "vfprintf", (void*)my_vfprintf, true);

#if !defined(WIN32) && !defined(CYGWIN)
	/* Save current state of some signal handlers. The JVM will
	 * redefine them. This redefinition can be avoided by passing
	 * -Xrs to the JVM but we don't want that since it would make
	 * it impossible to get a thread dump.
	 */
	saveSigInt  = pqsignal(SIGINT,  SIG_DFL);
	saveSigTerm = pqsignal(SIGTERM, SIG_DFL);
	saveSigHup  = pqsignal(SIGHUP,  SIG_DFL);
	saveSigQuit = pqsignal(SIGQUIT, SIG_DFL);
#else
	/* We implement this when PostgreSQL have a native port for
	 * win32. Sure, cygwin has signals but that don't help much
	 * since the JVM dll is unaware of cygwin and uses Win32
	 * constructs.
	 */
	JVMOptList_add(&optList, "-Xrs", 0, true);
#endif

	vm_args.nOptions = optList.size;
	vm_args.options  = optList.options;
	vm_args.version  = JNI_VERSION_1_4;
	vm_args.ignoreUnrecognized = JNI_TRUE;

	elog(LOG, "Creating JavaVM");


	isCallingJava = true;
	jstat = JNI_CreateJavaVM(&s_javaVM, (void **)&s_mainEnv, &vm_args);
	isCallingJava = false;

	JVMOptList_delete(&optList);

	if(jstat != JNI_OK)
		ereport(ERROR, (errmsg("Failed to create Java VM")));

#if !defined(WIN32) && !defined(CYGWIN)
	/* Restore the PostgreSQL signal handlers and retrieve the
	 * ones installed by the JVM. We'll use them when the JVM
	 * is destroyed.
	 */
	pqsignal(SIGINT,  saveSigInt);
	pqsignal(SIGTERM, saveSigTerm);
	pqsignal(SIGHUP,  saveSigHup);
	s_jvmSigQuit = pqsignal(SIGQUIT, saveSigQuit);
#endif

	/* Register an on_proc_exit handler that destroys the VM
	 */
	on_proc_exit(_destroyJavaVM, 0);
	initJavaVM(s_mainEnv);
}

extern Datum java_call_handler(PG_FUNCTION_ARGS);
PG_FUNCTION_INFO_V1(java_call_handler);

/*
 * This is the entry point for all calls. The java_call_handler must be
 * defined in plsql like this:
 *
 */
Datum java_call_handler(PG_FUNCTION_ARGS)
{
	Datum ret;
	if(s_javaVM == 0)
		initializeJavaVM();

	SPI_connect();
	ret = callFunction(s_mainEnv, fcinfo);
	SPI_finish();
	return ret;
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
	char* key;
	jstring result;

	PLJAVA_ENTRY_FENCE(0)
	key = String_createNTS(env, jkey);
	if(key == 0)
		return 0;

	result = 0;
	PLJAVA_TRY
	{
		const char* value = GetConfigOption(key);
		pfree(key);
		if(value != 0)
			result = String_createJavaStringFromNTS(env, value);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "GetConfigOption");
	}
	PLJAVA_TCEND
	return result;
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    _log
 * Signature: (ILjava/lang/String;)V
 */
JNIEXPORT void JNICALL
JNICALL Java_org_postgresql_pljava_internal_Backend__1log(JNIEnv* env, jclass cls, jint logLevel, jstring jstr)
{
	char* str;
	PLJAVA_ENTRY_FENCE_VOID
	str = String_createNTS(env, jstr);
	if(str == 0)
		return;

	PLJAVA_TRY
	{
		elog(logLevel, str);
		pfree(str);
	}
	PLJAVA_CATCH
	{
		Exception_throw_ERROR(env, "elog");
	}
	PLJAVA_TCEND
}

/*
 * Class:     org_postgresql_pljava_internal_Backend
 * Method:    isCallingJava
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL
Java_org_postgresql_pljava_internal_Backend_isCallingJava(JNIEnv* env, jclass cls)
{
	return isCallingJava ? JNI_TRUE : JNI_FALSE;
}
