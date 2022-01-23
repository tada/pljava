/*
 * Copyright (c) 2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.pg.adt;

import java.io.IOException;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;
import java.nio.IntBuffer;

import java.security.AccessController;
import java.security.PrivilegedAction;

import java.sql.SQLException;

import java.util.List;
import static java.util.Objects.requireNonNull;

import java.util.stream.IntStream;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.Contract;

import org.postgresql.pljava.adt.Array.AsFlatList;
import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.model.Attribute;
import org.postgresql.pljava.model.RegClass;
import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.TupleDescriptor;
import org.postgresql.pljava.model.TupleTableSlot;

import static org.postgresql.pljava.pg.CatalogObjectImpl.of;
import static org.postgresql.pljava.pg.DatumUtils.indexedTupleSlot;
import static org.postgresql.pljava.pg.DatumUtils.mapFixedLength;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_ArrayType_ndim;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_ArrayType_ndim;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_ArrayType_elemtype;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_ArrayType_elemtype;
import static
	org.postgresql.pljava.pg.ModelConstants.SIZEOF_ArrayType_dataoffset;
import static
	org.postgresql.pljava.pg.ModelConstants.OFFSET_ArrayType_dataoffset;
import static org.postgresql.pljava.pg.ModelConstants.OFFSET_ArrayType_DIMS;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_ArrayType_DIM;
import static org.postgresql.pljava.pg.ModelConstants.VARHDRSZ;

import static org.postgresql.pljava.pg.ModelConstants.MAXIMUM_ALIGNOF;

/*
 * The representation details are found in include/utils/array.h
 */

/**
 * PostgreSQL arrays represented as something or other.
 */
public class ArrayAdapter<T,E> extends Adapter.Array<T,E>
{
	private static final Configuration s_config;

	public static final
		ArrayAdapter<List<String>,?> FLAT_STRING_LIST_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(ArrayAdapter.class, Via.DATUM));

		s_config = config;

		FLAT_STRING_LIST_INSTANCE = new ArrayAdapter<>(
			AsFlatList.of(AsFlatList::nullsIncludedCopy), TextAdapter.INSTANCE);
	}

	public static <T,E> ArrayAdapter<T,E>
	arrayAdapter(Contract.Array<T,E> contract, Adapter.As<E,?> element)
	{
		try
		{
			return new ArrayAdapter<>(contract, element);
		}
		catch ( Throwable t )
		{
			t.printStackTrace();
			throw t;
		}
	}

	public ArrayAdapter(Contract.Array<T,E> contract, Adapter.As<E,?> element)
	{
		super(contract, element, null, s_config);
	}

	@Override
	public boolean canFetch(RegType pgType)
	{
		RegType elementType = pgType.element();
		return elementType.isValid() && m_elementAdapter.canFetch(elementType);
	}

	public T fetch(Attribute a, Datum.Input in)
	throws SQLException, IOException
	{
		try
		{
			in.pin();
			ByteBuffer bb = in.buffer().order(nativeOrder());

			assert 4 == SIZEOF_ArrayType_ndim : "ArrayType.ndim size change";
			int nDims = bb.getInt(OFFSET_ArrayType_ndim);

			assert 4 == SIZEOF_ArrayType_elemtype
				: "ArrayType.elemtype size change";
			RegType elementType =
				of(RegType.CLASSID, bb.getInt(OFFSET_ArrayType_elemtype));

			if ( ! m_elementAdapter.canFetch(elementType) )
				throw new IllegalArgumentException(String.format(
					"cannot fetch array element of type %s using %s",
						elementType, m_elementAdapter));

			assert 4 == SIZEOF_ArrayType_dataoffset
				: "ArrayType.dataoffset size change";
			int dataOffset = bb.getInt(OFFSET_ArrayType_dataoffset);

			boolean hasNulls = 0 != dataOffset;

			int dimsOffset = OFFSET_ArrayType_DIMS;
			int dimsBoundsLength = 2 * nDims * SIZEOF_ArrayType_DIM;

			IntBuffer dimsAndBounds =
				mapFixedLength(bb, dimsOffset, dimsBoundsLength).asIntBuffer();

			int nItems =
				IntStream.range(0, nDims).map(dimsAndBounds::get)
				.reduce(1, Math::multiplyExact);

			ByteBuffer nulls;

			if ( hasNulls )
			{
				int nullsOffset = dimsOffset + dimsBoundsLength;
				int nullsLength = (nItems + 7) / 8;
				nulls = mapFixedLength(bb, nullsOffset, nullsLength);
				/*
				 * In the with-nulls case, PostgreSQL has supplied dataOffset.
				 * But it includes VARHDRSZ, and a VarlenaWrapper doesn't
				 * include that first word.
				 */
				dataOffset -= VARHDRSZ;
			}
			else
			{
				nulls = null;
				/*
				 * In the no-nulls case, computing dataOffset is up to us.
				 */
				dataOffset = dimsOffset + dimsBoundsLength;
				dataOffset +=
					- bb.alignmentOffset(dataOffset, MAXIMUM_ALIGNOF)
						& (MAXIMUM_ALIGNOF - 1);
			}

			ByteBuffer values =
				mapFixedLength(bb, dataOffset, bb.capacity() - dataOffset);

			TupleTableSlot.Indexed tti =
				indexedTupleSlot(elementType, nItems, nulls, values);

			int[] dimsBoundsArray = new int [ dimsAndBounds.capacity() ];
			dimsAndBounds.get(dimsBoundsArray);

			return m_contract.construct(
				nDims, dimsBoundsArray, m_elementAdapter, tti);
		}
		finally
		{
			in.unpin();
			in.close();
		}
	}
}
