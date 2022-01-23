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
package org.postgresql.pljava.pg;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.As;

import org.postgresql.pljava.internal.CacheMap;
import org.postgresql.pljava.internal.Invocation;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.model.*;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import java.lang.annotation.Native;

import static java.lang.ref.Reference.reachabilityFence;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLException;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Implementation of the {@link CatalogObject CatalogObject} API for the
 * PL/Java case of JVM running in the PostgreSQL backend process.
 */
public class CatalogObjectImpl implements CatalogObject
{
	/**
	 * ByteBuffer representing the PostgreSQL object address: {@code classid},
	 * {@code objid}, {@code objsubid}.
	 *<p>
	 * This buffer has to be retained as a key in the lookup data structure
	 * anyway, so this class will keep just one reference to the buffer, and
	 * read the values from it as needed.
	 *<p>
	 * From the moment of construction here, the buffer must be treated as
	 * immutable. It may not actually be immutable: there is no way to alter an
	 * existing ByteBuffer to be readonly, but only to obtain a readonly copy,
	 * and the lookup data structure may have no API to reliably replace the key
	 * of an entry. But no reference to it should escape the lookup structure
	 * and this object, where it should be treated as if it cannot be written.
	 */
	private final ByteBuffer m_objectAddress;

	/**
	 * Hold the address during construction so it can be retrieved by this
	 * constructor without having to fuss with it in every subclass.
	 *<p>
	 * Largely a notation convenience; it can be done the longwinded way if it
	 * proves a bottleneck.
	 */
	private static final ThreadLocal<ByteBuffer>
		s_address = new ThreadLocal<>();

	private CatalogObjectImpl()
	{
		ByteBuffer b = s_address.get();

		/*
		 * Here is a bit of a hack. No CatalogObjectImpl should ever be without
		 * its address buffer, with AttributeImpl.Transient being the sole
		 * exception. It supplies null, and overrides all the methods that rely
		 * on it. Perhaps it should simply be an independent implementation of
		 * the Attribute interface, rather than extending this class and wasting
		 * the address slot, but for now, this is the way it works.
		 */
		if ( null != b )
			assert saneByteBuffer(b, 12, "CatalogObjectImpl address");
		else if ( ! (this instanceof AttributeImpl.Transient) )
			throw new IllegalStateException(
				"CatalogObjectImpl constructed without its address buffer");

		m_objectAddress = b;
	}

	public static CatalogObject of(int objId)
	{
		return Factory.form(InvalidOid, objId, 0);
	}

	public static <T extends CatalogObject.Addressed<T>>
		T of(RegClass.Known<T> classId, int objId)
	{
		return Factory.staticFormObjectId(classId, objId);
	}

	static boolean saneByteBuffer(ByteBuffer bb, int cap, String tag)
	{
		assert null != bb : tag + " null";
		assert cap == bb.capacity() : tag + " unexpected size";
		assert 0 == bb.position() : tag + " unexpected position";
		assert nativeOrder() == bb.order() : tag + " unexpected byte order";
		return true;
	}

	@Override
	protected final CatalogObjectImpl clone() throws CloneNotSupportedException
	{
		throw new CloneNotSupportedException();
	}

	@Override
	public int oid()
	{
		return m_objectAddress.getInt(4);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends CatalogObject.Addressed<T>> T of(RegClass.Known<T> c)
	{
		if ( classOid() == c.oid() )
			return (T) this;
		if ( classValid()  &&  isValid() )
			throw new RuntimeException("XXX I'm not one of those");
		return Factory.staticFormObjectId(c, oid());
	}

	public int classOid()
	{
		return m_objectAddress.getInt(0);
	}

	public int subId()
	{
		return m_objectAddress.getInt(8);
	}

	@Override
	public boolean isValid()
	{
		return InvalidOid != oid();
	}

	public boolean classValid()
	{
		return InvalidOid != classOid();
	}

	@Override
	public boolean equals(Object other)
	{
		if ( this == other )
			return true;
		if ( ! (other instanceof CatalogObjectImpl) )
			return false;
		return
			m_objectAddress.equals(((CatalogObjectImpl)other).m_objectAddress);
	}

	@Override
	public int hashCode()
	{
		return m_objectAddress.hashCode();
	}

	@Override
	public String toString()
	{
		Class<?> c = getClass();
		String pfx = c.getCanonicalName();
		return pfx.substring(1 + c.getPackageName().length()) + '[' +
			Integer.toUnsignedString(classOid()) + ',' +
			Integer.toUnsignedString(oid()) + ',' +
			Integer.toUnsignedString(subId()) + ']';
	}

	/**
	 * Provider of the {@link CatalogObject.Factory CatalogObject.Factory}
	 * service, linking the {@link org.postgresql.pljava.model} API to the
	 * implementations in this package.
	 */
	public static final class Factory extends CatalogObject.Factory
	{
		public Factory() { }

		/*
		 * Include one @Native-annotated constant here to trigger header
		 * generation for this class. The generated header also includes
		 * all the static primitive constants inherited from Factory, so
		 * they all can be statically checked against the PostgreSQL values
		 * in ModelConstants.c.
		 */
		@Native static final int InvalidOid = CatalogObject.InvalidOid;

		private static final CacheMap<CatalogObject>
			s_map = CacheMap.newConcurrent(
				() -> ByteBuffer.allocate(12).order(nativeOrder()));

		@Override
		protected <T extends CatalogObject.Addressed<T>> RegClass.Known<T>
		formClassIdImpl(int classId, Class<? extends T> clazz)
		{
			return staticFormClassId(classId, clazz);
		}

		@Override
		protected <T extends CatalogObject.Addressed<T>>
		T formObjectIdImpl(RegClass.Known<T> classId, int objId)
		{
			return staticFormObjectId(classId, objId);
		}

		@Override
		protected RegRole.Grantee publicGranteeImpl()
		{
			return (RegRole.Grantee)form(AuthIdRelationId, InvalidOid, 0);
		}

		@Override
		protected CharsetEncoding serverEncoding()
		{
			return CharsetEncodingImpl.serverEncoding();
		}

		@Override
		protected CharsetEncoding clientEncoding()
		{
			return CharsetEncodingImpl.clientEncoding();
		}

		@Override
		protected CharsetEncoding encodingFromOrdinal(int ordinal)
		{
			return CharsetEncodingImpl.fromOrdinal(ordinal);
		}

		@Override
		protected CharsetEncoding encodingFromName(String name)
		{
			return CharsetEncodingImpl.fromName(name);
		}

		@Override
		protected ResourceOwner resourceOwner(int which)
		{
			return ResourceOwnerImpl.known(which);
		}

		@Override
		protected MemoryContext memoryContext(int which)
		{
			return MemoryContextImpl.known(which);
		}

		@Override
		protected MemoryContext upperMemoryContext()
		{
			return Invocation.upperExecutorContext();
		}

		@SuppressWarnings("unchecked")
		static <T extends CatalogObject.Addressed<T>> RegClass.Known<T>
		staticFormClassId(int classId, Class<? extends T> clazz)
		{
			return (RegClass.Known<T>)form(RelationRelationId, classId, 0);
		}

		@SuppressWarnings("unchecked")
		static <T extends CatalogObject.Addressed<T>>
		T staticFormObjectId(RegClass.Known<T> classId, int objId)
		{
			return (T)form(classId.oid(), objId, 0);
		}

		@SuppressWarnings("unchecked")
		static <T extends CatalogObject.Addressed<T>>
		T findObjectId(RegClass.Known<T> classId, int objId)
		{
			CacheMap.Entry<CatalogObject> e = s_map.find(k ->
				k.putInt(classId.oid()).putInt(objId).putInt(0));
			if ( null == e )
				return null;
			return (T)e.get(); // may be null if it's been found unreachable
		}

		static void forEachValue(Consumer<CatalogObject> action)
		{
			s_map.forEachValue(action);
		}

		static RegType formMaybeModifiedType(int typeId, int typmod)
		{
			if ( -1 == typmod )
				return (RegType)form(TypeRelationId, typeId, 0);

			int subId = (0 == typmod) ? -1 : typmod;

			RegType result =
				(RegType)s_map.weaklyCache(
					b -> b.putInt(TypeRelationId).putInt(typeId).putInt(subId),
					b ->
					{
						if ( RECORDOID == typeId )
							return
								constructWith(RegTypeImpl.Blessed::new, b);
						/*
						 * Look up the unmodified base type. This is a plain
						 * find(), not a cache(), because ConcurrentHashMap's
						 * computeIfAbsent contract requires that the action
						 * "must not attempt to update any other mappings of
						 * this map." If not found, we will have to return null
						 * from this attempt, then retry after caching the base.
						 */
						CacheMap.Entry<CatalogObject> e = s_map.find(k ->
							k.putInt(TypeRelationId).putInt(typeId).putInt(0));
						if ( null == e )
							return null;
						RegTypeImpl.NoModifier base =
							(RegTypeImpl.NoModifier)e.get();
						if ( null == base ) // e isn't a strong reference
							return null;

						return constructWith(
							() -> new RegTypeImpl.Modified(base), b);
					}
				);

			if ( null != result )
				return result;

			RegTypeImpl.NoModifier base =
				(RegTypeImpl.NoModifier)form(TypeRelationId, typeId, 0);

			return
				(RegType)s_map.weaklyCache(
					b -> b.putInt(TypeRelationId).putInt(typeId).putInt(subId),
					b -> constructWith(
							() -> new RegTypeImpl.Modified(base), b));
		}

		static CatalogObject form(int classId, int objId, int objSubId)
		{
			assert classId != TypeRelationId || 0 == objSubId :
				"nonzero objSubId passed to form() for a type";

			/*
			 * As attributes aren't built here anymore, there is now no valid
			 * use of this method with a nonzero objSubId. See formAttribute.
			 */
			if ( 0 != objSubId )
				throw new UnsupportedOperationException(
					"CatalogObjectImpl.Factory.form with nonzero objSubId");

			Supplier<CatalogObjectImpl> ctor =
				Optional.ofNullable(ctorIfKnown(classId, objId, objSubId))
					.orElseGet(() ->
						InvalidOid == classId
							? CatalogObjectImpl::new : Addressed::new);

			return
				s_map.weaklyCache(
					b -> b.putInt(classId).putInt(objId).putInt(objSubId),
					b -> constructWith(ctor, b)
				);
		}

		/**
		 * Called only by {@code TupleDescImpl}, which is the only way
		 * cataloged attribute instances should be formed.
		 *<p>
		 * {@code TupleDescImpl} is expected and trusted to supply only valid
		 * (positive) attribute numbers, and a {@code Supplier} that will
		 * construct the attribute with a reference to its correct corresponding
		 * {@code RegClass} (not checked here). Because {@code TupleDescImpl}
		 * constructs a bunch of attributes at once, that reduces overhead.
		 */
		static Attribute formAttribute(
			int relId, int attNum, Supplier<CatalogObjectImpl> ctor)
		{
			assert attNum > 0 : "formAttribute attribute number validity";
			return (Attribute)
				s_map.weaklyCache(
					b -> b.putInt(RelationRelationId)
						.putInt(relId).putInt(attNum),
					b -> constructWith(ctor, b)
				);
		}

		/**
		 * Invokes a supplied {@code CatalogObjectImpl} constructor, with the
		 * {@code ByteBuffer} containing its address in thread-local storage,
		 * so it isn't necessary for all constructors of all subtypes to pass
		 * the thing all the way up.
		 */
		static CatalogObjectImpl constructWith(
			Supplier<CatalogObjectImpl> ctor, ByteBuffer b)
		{
			try
			{
				s_address.set(b);
				return ctor.get();
			}
			finally
			{
				s_address.remove();
			}
		}

		/**
		 * Returns the constructor for the right subtype of
		 * {@code CatalogObject} if the <var>classId</var> identifies one
		 * for which an implementation is available; null otherwise.
		 */
		static Supplier<CatalogObjectImpl> ctorIfKnown(
			int classId, int objId, int objSubId)
		{
			/*
			 * Used to read a static field of whatever class we will return
			 * a constructor for, to ensure its static initializer has already
			 * run and cannot be triggered by the instance creation, which
			 * happens within the CacheMap's computeIfAbsent and therefore could
			 * pose a risk of deadlock if the class must also create instances
			 * to populate its own statics.
			 */
			RegClass fieldRead = null;

			try
			{
				switch ( classId )
				{
				case TypeRelationId:
					fieldRead = RegType.CLASSID;
					return RegTypeImpl.NoModifier::new;
				case AuthIdRelationId:
					fieldRead = RegRole.CLASSID;
					return RegRoleImpl::new;
				case NamespaceRelationId:
					fieldRead = RegNamespace.CLASSID;
					return RegNamespaceImpl::new;
				case RelationRelationId:
					fieldRead = RegClass.CLASSID;
					assert 0 == objSubId :
						"CatalogObjectImpl.Factory.form attribute";
					if ( null != ctorIfKnown(objId, InvalidOid, 0) )
						return RegClassImpl.Known::new;
					return RegClassImpl::new;
				default:
					return null;
				}
			}
			finally
			{
				reachabilityFence(fieldRead); // insist the read really happens
			}
		}
	}

	@SuppressWarnings("unchecked")
	static class Addressed<T extends CatalogObject.Addressed<T>>
	extends CatalogObjectImpl implements CatalogObject.Addressed<T>
	{
		@Override
		public RegClass.Known<T> classId()
		{
			return CatalogObjectImpl.Factory.staticFormClassId(
				classOid(), (Class<? extends T>)getClass());
		}

		@Override
		public boolean exists()
		{
			throw notyet();
		}
	}

	/**
	 * Mixin supplying a {@code shared()} method that returns false without
	 * having to materialize the {@code classId}.
	 */
	interface Nonshared<T extends CatalogObject.Addressed<T>>
	extends CatalogObject.Addressed<T>
	{
		@Override
		default boolean shared()
		{
			return false;
		}
	}

	/**
	 * Mixin supplying a {@code shared()} method that returns true without
	 * having to materialize the {@code classId}.
	 */
	interface Shared<T extends CatalogObject.Addressed<T>>
	extends CatalogObject.Addressed<T>
	{
		@Override
		default boolean shared()
		{
			return true;
		}
	}

	/*
	 * Note to self: name() should, of course, fail or return null
	 * when ! isValid(). That seems generally sensible, but code
	 * in interface RegRole contains the first conscious reliance on it.
	 */
	interface Named<T extends Identifier.Unqualified<T>>
		extends CatalogObject.Named<T>
	{
		@Override
		default T name()
		{
			throw notyet();
		}
	}

	interface Namespaced<T extends Identifier.Unqualified<T>>
		extends Named<T>, CatalogObject.Namespaced<T>
	{
		@Override
		default RegNamespace namespace()
		{
			throw notyet();
		}
	}

	interface Owned extends CatalogObject.Owned
	{
		@Override
		default RegRole owner()
		{
			throw notyet();
		}
	}

	interface AccessControlled<T extends Grant>
	extends CatalogObject.AccessControlled<T>
	{
		@Override
		default List<T> grants()
		{
			throw notyet();
		}

		@Override
		default List<T> grants(RegRole grantee)
		{
			throw notyet();
		}
	}

	private static final StackWalker s_walker =
		StackWalker.getInstance(Set.<StackWalker.Option>of(), 2);

	static UnsupportedOperationException notyet()
	{
		String what = s_walker.walk(s -> s
			.skip(1)
			.map(StackWalker.StackFrame::toStackTraceElement)
			.findFirst()
			.map(e -> " " + e.getClassName() + "." + e.getMethodName())
			.orElse("")
		);
		return new UnsupportedOperationException(
			"CatalogObject API" + what);
	}

	static UnsupportedOperationException notyet(String what)
	{
		return new UnsupportedOperationException(
			"CatalogObject API " + what);
	}
}
