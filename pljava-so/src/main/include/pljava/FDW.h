/**
 * Actual implementation of minimal FDW based on the 'blackhole_fdw'
 * project. For simplicity all of the comments and unused functions
 * have been removed.
 *
 * The purpose of this file is to demonstrate the ability of C-based
 * FDW implementation to successfully interact with a java object
 * that implements the FDW interfaces.
 *
 * The first milestone is simply sending a NOTICE from the java
 * method.
 */
#ifndef PLJAVA_SO_BLACKHOLEFDW_H
#define PLJAVA_SO_BLACKHOLEFDW_H

// temporary name...
extern Datum blackhole_fdw_handler(PG_FUNCTION_ARGS);
extern Datum blackhole_fdw_validator(PG_FUNCTION_ARGS);

typedef struct JNI_FDW_Validator JNI_FDW_Validator;
JNI_FDW_Validator newValidator(JNIEnv *env, const char *validator_classname);

#endif //PLJAVA_SO_BLACKHOLEFDW_H
