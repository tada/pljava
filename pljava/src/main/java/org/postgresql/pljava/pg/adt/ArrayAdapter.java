/*
 * Copyright (c) 2022-2025 Tada AB and other contributors, as listed below.
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

import java.lang.reflect.Type;

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
import static org.postgresql.pljava.model.RegType.ANYARRAY;
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
 * Ancestor of adapters that can map a PostgreSQL array to some representation
 * <var>{@literal <T>}</var>.
 * @param <T> Java type to represent the entire array.
 */
public class ArrayAdapter<T> extends Adapter.Array<T>
{
	private static final Configuration s_config;

	/**
	 * An {@code ArrayAdapter} that maps any PostgreSQL array with element type
	 * compatible with {@link TextAdapter TextAdapter} to flat (disregarding the
	 * PostgreSQL array's dimensionality) {@code List} of {@code String},
	 * with any null elements mapped to Java null.
	 */
	public static final
		ArrayAdapter<List<String>> FLAT_STRING_LIST_INSTANCE;

	public static final
		ArrayAdapter<RegType> TYPE_OBTAINING_INSTANCE;

	static
	{
		@SuppressWarnings("removal") // JEP 411
		Configuration config = AccessController.doPrivileged(
			(PrivilegedAction<Configuration>)() ->
				configure(ArrayAdapter.class, Via.DATUM));

		s_config = config;

		FLAT_STRING_LIST_INSTANCE = new ArrayAdapter<>(
			TextAdapter.INSTANCE, AsFlatList.of(AsFlatList::nullsIncludedCopy));

		TYPE_OBTAINING_INSTANCE = new ArrayAdapter<RegType>(
			Opaque.INSTANCE, new ElementTypeContract());
	}

	/**
	 * Constructs an array adapter given an adapter that returns a reference
	 * type {@literal <E>} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public <E> ArrayAdapter(
		Adapter.As<E,?> element, Contract.Array<T,E,Adapter.As<E,?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code long} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsLong<?> element,
		Contract.Array<T,Long,Adapter.AsLong<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code double} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsDouble<?> element,
		Contract.Array<T,Double,Adapter.AsDouble<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code int} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsInt<?> element,
		Contract.Array<T,Integer,Adapter.AsInt<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code float} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsFloat<?> element,
		Contract.Array<T,Float,Adapter.AsFloat<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code short} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsShort<?> element,
		Contract.Array<T,Short,Adapter.AsShort<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code char} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsChar<?> element,
		Contract.Array<T,Character,Adapter.AsChar<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code byte} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsByte<?> element,
		Contract.Array<T,Byte,Adapter.AsByte<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	/**
	 * Constructs an array adapter given an adapter that returns a primitive
	 * {@code boolean} for the element type, and a corresponding array
	 * contract producing <var>{@literal <T>}</var>.
	 */
	public ArrayAdapter(
		Adapter.AsBoolean<?> element,
		Contract.Array<T,Boolean,Adapter.AsBoolean<?>> contract)
	{
		super(contract, element, null, s_config);
	}

	<E> ArrayAdapter(
		Adapter.As<E,?> element, Type witness,
		Contract.Array<T,E,Adapter.As<E,?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsLong<?> element, Type witness,
		Contract.Array<T,Long,Adapter.AsLong<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsDouble<?> element, Type witness,
		Contract.Array<T,Double,Adapter.AsDouble<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsInt<?> element, Type witness,
		Contract.Array<T,Integer,Adapter.AsInt<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsFloat<?> element, Type witness,
		Contract.Array<T,Float,Adapter.AsFloat<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsShort<?> element, Type witness,
		Contract.Array<T,Short,Adapter.AsShort<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsChar<?> element, Type witness,
		Contract.Array<T,Character,Adapter.AsChar<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsByte<?> element, Type witness,
		Contract.Array<T,Byte,Adapter.AsByte<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	ArrayAdapter(
		Adapter.AsBoolean<?> element, Type witness,
		Contract.Array<T,Boolean,Adapter.AsBoolean<?>> contract)
	{
		super(contract, element, witness, s_config);
	}

	/**
	 * Whether this adapter can be applied to the given PostgreSQL type.
	 *<p>
	 * If not overridden, simply requires that <var>pgType</var> is an array
	 * type and that its declared element type is acceptable to {@code canFetch}
	 * of the configured element adapter.
	 */
	@Override
	public boolean canFetch(RegType pgType)
	{
		RegType elementType = pgType.element();
		if ( elementType.isValid() && m_elementAdapter.canFetch(elementType) )
			return true;
		return
			ANYARRAY == pgType && Opaque.INSTANCE == m_elementAdapter;
	}

	/**
	 * Returns the result of applying the configured element adapter and
	 * {@link Contract.Array array contract} to the contents of the array
	 * <var>in</var>.
	 */
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

			assert 4 == SIZEOF_ArrayType_DIM : "ArrayType dim size change";
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

			/*
			 * The accessible constructors ensured that m_elementAdapter and
			 * m_contract have compatible parameterized types. They were stored
			 * as raw types to avoid having extra type parameters on array
			 * adapters that are of no interest to code that makes use of them.
			 */
			@SuppressWarnings("unchecked")
			T result = (T)m_contract.construct(
				nDims, dimsBoundsArray, m_elementAdapter, tti);

			return result;
		}
		finally
		{
			in.unpin();
			in.close();
		}
	}

	/**
	 * A contract that cannot retrieve any element, but returns the array's
	 * internally-recorded element type.
	 */
	private static class ElementTypeContract
	implements Contract.Array<RegType, Void, Adapter.As<Void,?>>
	{
		@Override
		public RegType construct(
			int nDims, int[] dimsAndBounds, Adapter.As<Void,?> adapter,
			TupleTableSlot.Indexed slot)
		throws SQLException
		{
			return slot.descriptor().get(0).type();
		}
	}
}
