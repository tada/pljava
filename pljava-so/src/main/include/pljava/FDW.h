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
#ifndef PLJAVA_SO_FDW_H
#define PLJAVA_SO_FDW_H

// temporary name...
extern Datum blackhole_fdw_handler(PG_FUNCTION_ARGS);
extern Datum blackhole_fdw_validator(PG_FUNCTION_ARGS);

struct JNI_FDW_Wrapper_;
struct JNI_FDW_Server_;
struct JNI_FDW_User_;    // or 'UserMapping' ?
struct JNI_FDW_Table_;

struct JNI_FDW_PlanState_;
struct JNI_FDW_ScanState_;

// permanent objects (with OID)
typedef struct JNI_FDW_Wrapper_ JNI_FDW_Wrapper;
typedef struct JNI_FDW_Server_ JNI_FDW_Server;
typedef struct JNI_FDW_User_ JNI_FDW_User;    // or 'UserMapping' ?
typedef struct JNI_FDW_Table_ JNI_FDW_Table;

// temporary objects (no OID)
typedef struct JNI_FDW_PlanState_ JNI_FDW_PlanState;
typedef struct JNI_FDW_ScanState_ JNI_FDW_ScanState;

#endif //PLJAVA_SO_FDW_H
