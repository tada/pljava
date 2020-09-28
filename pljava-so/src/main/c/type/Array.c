/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 */
#include "pljava/type/Type_priv.h"
#include "pljava/type/Array.h"
#include "pljava/Invocation.h"

void arraySetNull(bits8* bitmap, int offset, bool flag)
{
	if(bitmap != 0)
	{
		int bitmask = 1 << (offset % 8);	
		bitmap += offset / 8;
		*bitmap = (bits8)(flag? *bitmap & ~bitmask : *bitmap | bitmask);
	}
}

bool arrayIsNull(const bits8* bitmap, int offset)
{
	return bitmap == 0 ? false : !(bitmap[offset / 8] & (1 << (offset % 8)));
}

ArrayType* createArrayType(jsize nElems, size_t elemSize, Oid elemType, bool withNulls)
{
	ArrayType* v;
	Size nBytes = elemSize * nElems;
	MemoryContext currCtx = Invocation_switchToUpperContext();

	Size dataoffset;
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
	AssertVariableIsOfType(v->dataoffset, int32);
	v->dataoffset = (int32)dataoffset;
	MemoryContextSwitchTo(currCtx);

#if PG_VERSION_NUM < 80300
	ARR_SIZE(v) = nBytes;
#else
	SET_VARSIZE(v, nBytes);
#endif
	ARR_NDIM(v) = 1;
	ARR_ELEMTYPE(v) = elemType;
	*((int*)ARR_DIMS(v)) = nElems;
	*((int*)ARR_LBOUND(v)) = 1;
	return v;
}

static jvalue _Array_coerceDatum(Type self, Datum arg)
{
	jvalue result;
	jsize idx;
	Type  elemType    = Type_getElementType(self);
	int16 elemLength  = Type_getLength(elemType);
	char  elemAlign   = Type_getAlign(elemType);
	bool  elemByValue = Type_isByValue(elemType);
	ArrayType* v = DatumGetArrayTypeP(arg);
	jsize nElems = (jsize)ArrayGetNItems(ARR_NDIM(v), ARR_DIMS(v));
	jobjectArray objArray = JNI_newObjectArray(nElems, Type_getJavaClass(elemType), 0);
	const char* values = ARR_DATA_PTR(v);
	bits8* nullBitMap = ARR_NULLBITMAP(v);

	for(idx = 0; idx < nElems; ++idx)
	{
		if(arrayIsNull(nullBitMap, idx))
			JNI_setObjectArrayElement(objArray, idx, 0);
		else
		{
			Datum value = fetch_att(values, elemByValue, elemLength);
			jvalue obj = Type_coerceDatum(elemType, value);
			JNI_setObjectArrayElement(objArray, idx, obj.l);
			JNI_deleteLocalRef(obj.l);

#if PG_VERSION_NUM < 80300
			values = att_addlength(values, elemLength, PointerGetDatum(values));
			values = (char*)att_align(values, elemAlign);
#else
			values = att_addlength_datum(values, elemLength, PointerGetDatum(values));
			values = (char*)att_align_nominal(values, elemAlign);
#endif

		}
	}
	result.l = (jobject)objArray;
	return result;
}

static Datum _Array_coerceObject(Type self, jobject objArray)
{
	ArrayType* v;
	jsize idx;
	int    lowerBound = 1;
	Type   elemType = Type_getElementType(self);
	int    nElems   = (int)JNI_getArrayLength((jarray)objArray);
	Datum* values   = (Datum*)palloc(nElems * sizeof(Datum) + nElems * sizeof(bool));
	bool*  nulls    = (bool*)(values + nElems);

	for(idx = 0; idx < nElems; ++idx)
	{
		jobject obj = JNI_getObjectArrayElement(objArray, idx);
		if(obj == 0)
		{
			nulls[idx] = true;
			values[idx] = 0;
		}
		else
		{
			nulls[idx] = false;
			values[idx] = Type_coerceObject(elemType, obj);
			JNI_deleteLocalRef(obj);
		}
	}

	v = construct_md_array(
		values,
		nulls,
		1,
		&nElems,
		&lowerBound,
		Type_getOid(elemType),
		Type_getLength(elemType),
		Type_isByValue(elemType),
		Type_getAlign(elemType));

	pfree(values);
	PG_RETURN_ARRAYTYPE_P(v);
}

/*
 * For an array, canReplaceType can be computed a bit more generously.
 * The primitive types are coded so that a boxed scalar can replace its
 * corresponding primitive but not vice versa. For primitive arrays, we can also
 * accept the other direction, that is, when getObjectType(self) == other. That
 * will work because every primitive Type foo does contain _fooArray_coerceDatum
 * and _fooArray_coerceObject and can handle both directions.
 */
static bool _Array_canReplaceType(Type self, Type other)
{
	Type oe = Type_getElementType(other);
	if ( oe == 0 )
		return false;
	return Type_canReplaceType(Type_getElementType(self), oe)
		|| Type_getObjectType(self) == other;
}

Type Array_fromOid(Oid typeId, Type elementType)
{
	return Array_fromOid2(typeId, elementType, _Array_coerceDatum, _Array_coerceObject);
}

Type Array_fromOid2(Oid typeId, Type elementType, DatumCoercer coerceDatum, ObjectCoercer coerceObject)
{
	Type self;
	TypeClass arrayClass;
	const char* elemClassName    = PgObjectClass_getName(PgObject_getClass((PgObject)elementType));
	const char* elemJNISignature = Type_getJNISignature(elementType);
	const char* elemJavaTypeName = Type_getJavaTypeName(elementType);

	MemoryContext currCtx = MemoryContextSwitchTo(TopMemoryContext);

	char* tmp = palloc(strlen(elemClassName) + 3);
	sprintf(tmp, "%s[]", elemClassName);
	arrayClass = TypeClass_alloc(tmp);

	tmp = palloc(strlen(elemJNISignature) + 2);
	sprintf(tmp, "[%s", elemJNISignature);
	arrayClass->JNISignature = tmp;

	tmp = palloc(strlen(elemJavaTypeName) + 3);
	sprintf(tmp, "%s[]", elemJavaTypeName);
	arrayClass->javaTypeName = tmp;
	arrayClass->coerceDatum  = coerceDatum;
	arrayClass->coerceObject = coerceObject;
	arrayClass->canReplaceType = _Array_canReplaceType;
	self = TypeClass_allocInstance(arrayClass, typeId);
	MemoryContextSwitchTo(currCtx);

	self->elementType = elementType;
	Type_registerType(arrayClass->javaTypeName, self);

	if(Type_isPrimitive(elementType))
		self->objectType = Array_fromOid(typeId, Type_getObjectType(elementType));
	return self;
}

