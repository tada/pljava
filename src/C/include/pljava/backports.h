#ifndef __pljava_backports_h
#define __pljava_backports_h

#include "pljava/pljava.h"

#ifdef __cplusplus
extern "C" {
#endif

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER == 0)

#include <utils/lsyscache.h>

/*----------
 *	Support to ease writing functions returning composite types
 *
 * External declarations:
 * get_call_result_type:
 *      Given a function's call info record, determine the kind of datatype
 *      it is supposed to return.  If resultTypeId isn't NULL, *resultTypeId
 *      receives the actual datatype OID (this is mainly useful for scalar
 *      result types).  If resultTupleDesc isn't NULL, *resultTupleDesc
 *      receives a pointer to a TupleDesc when the result is of a composite
 *      type, or NULL when it's a scalar result or the rowtype could not be
 *      determined.  NB: the tupledesc should be copied if it is to be
 *      accessed over a long period.
 *----------
 */

extern TypeFuncClass get_call_result_type(FunctionCallInfo fcinfo,
										  Oid *resultTypeId,
										  TupleDesc *resultTupleDesc);

extern bool resolve_polymorphic_argtypes(int numargs, Oid *argtypes,
										 Node *call_expr);

#else
#include <funcapi.h>
#endif

#ifdef __cplusplus
} /* end of extern "C" declaration */
#endif
#endif
