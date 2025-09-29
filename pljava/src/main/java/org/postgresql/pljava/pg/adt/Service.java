/*
 * Copyright (c) 2023-2025 Tada AB and other contributors, as listed below.
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

import java.lang.reflect.Type;

import java.security.Permission;

import java.sql.SQLException;
import java.sql.SQLDataException;

import static java.util.Arrays.copyOf;
import static java.util.Objects.requireNonNull;

import java.util.function.Consumer;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.Array;
import org.postgresql.pljava.Adapter.ArrayBuilder;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsBoolean;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.Adapter.AsChar;
import org.postgresql.pljava.Adapter.AsDouble;
import org.postgresql.pljava.Adapter.AsFloat;
import org.postgresql.pljava.Adapter.AsInt;
import org.postgresql.pljava.Adapter.AsLong;
import org.postgresql.pljava.Adapter.AsShort;
import org.postgresql.pljava.Adapter.TypeWrapper;

import org.postgresql.pljava.adt.spi.AbstractType.MultiArray;
import org.postgresql.pljava.adt.spi.AbstractType.MultiArray.Sized.Allocated;

import org.postgresql.pljava.internal.Backend;

import org.postgresql.pljava.model.RegType;
import org.postgresql.pljava.model.TupleTableSlot.Indexed;

/**
 * Implementation of a service defined by {@link Adapter} for data types.
 *<p>
 * Handles operations such as creating a properly-typed {@link ArrayAdapter}
 * with dimensions and types computed from an adapter for the component type.
 */
public final class Service extends Adapter.Service
{
	@Override
	protected <TA,TI> Array<TA>
		buildArrayAdapterImpl(ArrayBuilder<TA,TI> builder, TypeWrapper w)
	{
		return staticBuildArrayAdapter(
			builder, adapter(builder), multiArray(builder), requireNonNull(w));
	}

	@Override
	protected Consumer<Permission> permissionChecker()
	{
		return Backend.CHECKER;
	}

	@Override
	protected Array<RegType> elementTypeAdapter()
	{
		return ArrayAdapter.TYPE_OBTAINING_INSTANCE;
	}

	/**
	 * Functional interface representing the initial logic of multiarray
	 * creation, verifying that the dimensions match, and allocating the Java
	 * array using the sizes from the PostgreSQL array datum.
	 */
	@FunctionalInterface
	private interface MultiArrayBuilder
	{
		Allocated<?,?>
			build(int nDims, int[] dimsAndBounds) throws SQLException;
	}

	/**
	 * Instantiate an array adapter, given the builder, and the component
	 * adapter and the {@link MultiArray} representing the desired array shape,
	 * both extracted from the builder in the protected caller above.
	 *
	 * A {@link TypeWrapper} has been supplied, to be populated here with the
	 * computed type, and passed as the 'witness' to the appropriate
	 * {@code ArrayAdapter} constructor.
	 */
	private static <TA,TI> Array<TA> staticBuildArrayAdapter(
		ArrayBuilder<TA,TI> builder,
		Adapter<?,?> componentAdapter,
		MultiArray shape,
		TypeWrapper w)
	{
		w.setWrappedType(shape.arrayType());

		/*
		 * Build an 'init' lambda that closes over 'shape'.
		 */
		final MultiArrayBuilder init = (nDims, dimsAndBounds) ->
		{
			if ( shape.dimensions != nDims )
				throw new SQLDataException(
					shape.dimensions + "-dimension array adapter " +
					"applied to " + nDims + "-dimension value", "2202E");

			return shape.size(copyOf(dimsAndBounds, nDims)).allocate();
		};

		/*
		 * A lambda implementing the rest of the array contract (closed over
		 * the 'init' created above) has to be specialized to the component type
		 * (reference or one of the primitives) that its inner loop will have to
		 * contend with. That can be determined from the subclass of Adapter.
		 */
		if ( componentAdapter instanceof AsLong<?> )
		{
			return new ArrayAdapter<TA>(
				(AsLong<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsLong<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,long[]> multi = (Allocated<TA,long[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( long[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsDouble<?> )
		{
			return new ArrayAdapter<TA>(
				(AsDouble<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsDouble<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,double[]> multi = (Allocated<TA,double[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( double[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsInt<?> )
		{
			return new ArrayAdapter<TA>(
				(AsInt<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsInt<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,int[]> multi = (Allocated<TA,int[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( int[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsFloat<?> )
		{
			return new ArrayAdapter<TA>(
				(AsFloat<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsFloat<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,float[]> multi = (Allocated<TA,float[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( float[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsShort<?> )
		{
			return new ArrayAdapter<TA>(
				(AsShort<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsShort<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,short[]> multi = (Allocated<TA,short[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( short[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsChar<?> )
		{
			return new ArrayAdapter<TA>(
				(AsChar<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsChar<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,char[]> multi = (Allocated<TA,char[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( char[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsByte<?> )
		{
			return new ArrayAdapter<TA>(
				(AsByte<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsByte<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,byte[]> multi = (Allocated<TA,byte[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( byte[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof AsBoolean<?> )
		{
			return new ArrayAdapter<TA>(
				(AsBoolean<?>)componentAdapter, w,
				(int nDims, int[] dimsAndBounds, AsBoolean<?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,boolean[]> multi = (Allocated<TA,boolean[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( boolean[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		else if ( componentAdapter instanceof As<?,?> )
		{
			@SuppressWarnings("unchecked")
			As<Object,?> erasedComponent = (As<Object,?>)componentAdapter;

			return new ArrayAdapter<TA>(
				erasedComponent, w,
				(int nDims, int[] dimsAndBounds, As<Object,?> adapter,
					Indexed slot) ->
				{
					@SuppressWarnings("unchecked")
					Allocated<TA,Object[]> multi = (Allocated<TA,Object[]>)
						init.build(nDims, dimsAndBounds);

					int n = slot.elements();
					int i = 0;

					for ( Object[] a : multi )
						for ( int j = 0; j < a.length; ++ j )
							a[j] = slot.get(i++, adapter);
					assert i == n;
					return multi.array();
				}
			);
		}
		throw new AssertionError("unhandled type building array adapter");
	}
}
