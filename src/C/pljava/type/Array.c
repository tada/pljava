/*
 * Copyright (c) 2004, 2005, 2006 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 *
 * @author Thomas Hallgren
 */
#include "pljava/type/Array.h"
#include "pljava/Invocation.h"

#if !(PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
void arraySetNull(bits8* bitmap, int offset, bool flag)
{
	if(bitmap != 0)
	{
		int bitmask = 1 << (offset % 8);	
		bitmap += offset / 8;
		if(flag)
			*bitmap &= ~bitmask;
		else
			*bitmap |= bitmask;
	}
}

bool arrayIsNull(const bits8* bitmap, int offset)
{
	return bitmap == 0 ? false : !(bitmap[offset / 8] & (1 << (offset % 8)));
}

ArrayType*
createArrayType(jsize nElems, size_t elemSize, Oid elemType, bool withNulls)
#else
ArrayType*
createArrayType(jsize nElems, size_t elemSize, Oid elemType)
#endif
{
	ArrayType* v;
	int nBytes = elemSize * nElems;
	MemoryContext currCtx = Invocation_switchToUpperContext();

#if (PGSQL_MAJOR_VER == 8 && PGSQL_MINOR_VER < 2)
#define LEAFKEY (1<<31)
	nBytes += ARR_OVERHEAD(1);
	v = (ArrayType*)palloc0(nBytes);
	v->flags &= ~LEAFKEY;
#else
	int dataoffset;
	if(withNulls)
	{
		dataoffset = ARR_OVERHEAD_WITHNULLS(1, nElems);
		nBytes += dataoffset;
	}
	else
	{
		dataoffset = 0;			/* marker for no null bitmap */
		nBytes += ARR_OVERHEAD_NONULLS(1);
	}
	v = (ArrayType*)palloc0(nBytes);
	v->dataoffset = dataoffset;
#endif
	MemoryContextSwitchTo(currCtx);

	ARR_SIZE(v) = nBytes;
	ARR_NDIM(v) = 1;
	ARR_ELEMTYPE(v) = elemType;
	*((int*)ARR_DIMS(v)) = nElems;
	*((int*)ARR_LBOUND(v)) = 1;
	return v;
}
