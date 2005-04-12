/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/ComplexType_priv.h"

ComplexType ComplexType_allocInstance(TypeClass complexTypeClass, Oid typeId)
{
	ComplexType infant = (ComplexType)TypeClass_allocInstance(complexTypeClass, typeId);
	infant->m_tupleDesc = 0;
	return infant;
}

ComplexType ComplexType_createType(TypeClass complexTypeClass, HashMap cache, Oid typeId, TupleDesc tupleDesc)
{
	ComplexType infant;
	if(typeId == RECORDOID)
	{
		/* Don't put this one in the oid based cache.
		 */
		infant = ComplexType_allocInstance(complexTypeClass, typeId);
	}
	else
	{
		infant = (ComplexType)HashMap_getByOid(cache, typeId);
		if(infant == 0)
		{
			infant = ComplexType_allocInstance(complexTypeClass, typeId);
			HashMap_putByOid(cache, typeId, infant);
		}
	}

	if(tupleDesc != 0 && infant->m_tupleDesc == 0)
	{
		MemoryContext curr = MemoryContextSwitchTo(TopMemoryContext);
		infant->m_tupleDesc = CreateTupleDescCopyConstr(tupleDesc);
		MemoryContextSwitchTo(curr);
	}
	return infant;
}

static TupleDesc _ComplexType_getTupleDesc(Type self)
{
	TupleDesc td = ((ComplexType)self)->m_tupleDesc;
	if(td == 0)
	{
		td = _Type_getTupleDesc(self);
		((ComplexType)self)->m_tupleDesc = td;
	}
	return td;
}

TypeClass ComplexTypeClass_alloc(const char* typeName)
{
	TypeClass complexTypeClass = TypeClass_alloc2(typeName, sizeof(struct TypeClass_), sizeof(struct ComplexType_));
	complexTypeClass->getTupleDesc = _ComplexType_getTupleDesc;
	return complexTypeClass;
}
