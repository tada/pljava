/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/ComplexType_priv.h"
#include "pljava/backports.h"

ComplexType ComplexType_allocInstance(TypeClass complexTypeClass, Oid typeId)
{
	ComplexType infant = (ComplexType)TypeClass_allocInstance(complexTypeClass, typeId);
	infant->m_tupleDesc = 0;
	return infant;
}

static TupleDesc createGlobalTupleDescCopy(TupleDesc td)
{
	MemoryContext curr = MemoryContextSwitchTo(TopMemoryContext);
	td = CreateTupleDescCopyConstr(td);
	MemoryContextSwitchTo(curr);
	return td;
}

#if (PGSQL_MAJOR_VER >= 8)
ComplexType ComplexType_createType(TypeClass complexTypeClass, HashMap idCache, HashMap modCache, TupleDesc td)
{
	ComplexType infant;
	Oid key;

	if(td == 0)
	{
		ereport(ERROR,
				(errcode(ERRCODE_DATATYPE_MISMATCH),
				 errmsg("could not determine row description for complex type")));
	}

	key = td->tdtypeid;
	if(key == RECORDOID)
	{
		if(td->tdtypmod != -1)
		{
			key = (Oid)td->tdtypmod;
			infant = (ComplexType)HashMap_getByOid(modCache, key);
			if(infant == 0)
			{
				infant = ComplexType_allocInstance(complexTypeClass, td->tdtypeid);
				infant->m_tupleDesc = createGlobalTupleDescCopy(td);
				HashMap_putByOid(modCache, key, infant);
			}
		}
		else
		{
			/* Get the singleton instance from the idCache that represents
			 * anonymous RECORD. We *do not* assign a TupleDesc to this
			 * instance since it will vary between calls.
			 */
			infant = (ComplexType)HashMap_getByOid(idCache, key);
			if(infant == 0)
			{
				infant = ComplexType_allocInstance(complexTypeClass, key);
				HashMap_putByOid(idCache, key, infant);
			}
		}
	}
	else
	{
		infant = (ComplexType)HashMap_getByOid(idCache, key);
		if(infant == 0)
		{
			infant = ComplexType_allocInstance(complexTypeClass, key);
			infant->m_tupleDesc = createGlobalTupleDescCopy(td);
			HashMap_putByOid(idCache, key, infant);
		}
	}
	return infant;
}

static TupleDesc _ComplexType_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	TupleDesc td = ((ComplexType)self)->m_tupleDesc;
	if(td != 0)
		return td;

	switch(get_call_result_type(fcinfo, 0, &td))
	{
		case TYPEFUNC_COMPOSITE:
		case TYPEFUNC_RECORD:
			if(td->tdtypeid == RECORDOID && td->tdtypmod == -1)
				/*
				 * We can't hold on to this one. It's anonymous
				 * and may vary between calls.
				 */
				td = CreateTupleDescCopy(td);
			else
			{
				td = createGlobalTupleDescCopy(td);
				((ComplexType)self)->m_tupleDesc = td;
			}
			break;
		default:
			ereport(ERROR,
				(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
				 errmsg("function returning record called in context "
						"that cannot accept type record")));
	}
	return td;
}
#else
ComplexType ComplexType_createType(TypeClass complexTypeClass, HashMap idCache, Oid key, TupleDesc td)
{
	ComplexType infant;

	if(td == 0)
	{
		ereport(ERROR,
				(errcode(ERRCODE_DATATYPE_MISMATCH),
				 errmsg("could not determine row description for complex type")));
	}

	if(key == RECORDOID)
	{
		/* Get the singleton instance from the idCache that represents
		 * anonymous RECORD. We *do not* assign a TupleDesc to this
		 * instance since it will vary between calls.
		 */
		infant = (ComplexType)HashMap_getByOid(idCache, key);
		if(infant == 0)
		{
			infant = ComplexType_allocInstance(complexTypeClass, key);
			HashMap_putByOid(idCache, key, infant);
		}
	}
	else
	{
		infant = (ComplexType)HashMap_getByOid(idCache, key);
		if(infant == 0)
		{
			infant = ComplexType_allocInstance(complexTypeClass, key);
			infant->m_tupleDesc = createGlobalTupleDescCopy(td);
			HashMap_putByOid(idCache, key, infant);
		}
	}
	return infant;
}

static TupleDesc _ComplexType_getTupleDesc(Type self, PG_FUNCTION_ARGS)
{
	Oid typid;
	TupleDesc td = ((ComplexType)self)->m_tupleDesc;
	if(td != 0)
		return td;

	switch(get_call_result_type(fcinfo, &typid, &td))
	{
		case TYPEFUNC_COMPOSITE:
		case TYPEFUNC_RECORD:
			if(typid == RECORDOID)
				/*
				 * We can't hold on to this one. It's anonymous
				 * and may vary between calls.
				 */
				td = CreateTupleDescCopy(td);
			else
			{
				td = createGlobalTupleDescCopy(td);
				((ComplexType)self)->m_tupleDesc = td;
			}
			break;
		default:
			ereport(ERROR,
				(errcode(ERRCODE_FEATURE_NOT_SUPPORTED),
				 errmsg("function returning record called in context "
						"that cannot accept type record")));
	}
	return td;
}
#endif

TypeClass ComplexTypeClass_alloc(const char* typeName)
{
	TypeClass complexTypeClass = TypeClass_alloc2(typeName, sizeof(struct TypeClass_), sizeof(struct ComplexType_));
	complexTypeClass->getTupleDesc = _ComplexType_getTupleDesc;
	return complexTypeClass;
}
