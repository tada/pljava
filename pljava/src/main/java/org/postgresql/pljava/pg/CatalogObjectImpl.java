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

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.CacheMap;
import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.Invocation;
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import static org.postgresql.pljava.internal.SwitchPointCache.setConstant;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.adt.Array.AsFlatList;
import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.model.*;
import static org.postgresql.pljava.model.MemoryContext.JavaMemoryContext;

import static org.postgresql.pljava.pg.MemoryContextImpl.allocatingIn;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import org.postgresql.pljava.pg.adt.ArrayAdapter;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCLASS_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import org.postgresql.pljava.pg.adt.TextAdapter;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import java.io.IOException;

import java.lang.annotation.Native;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;
import java.lang.invoke.SwitchPoint;

import static java.lang.ref.Reference.reachabilityFence;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
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
		protected Database currentDatabaseImpl(RegClass.Known<Database> classId)
		{
			return staticFormObjectId(classId, _currentDatabase());
		}

		private static native int _currentDatabase();

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
				case DatabaseRelationId:
					fieldRead = Database.CLASSID;
					return DatabaseImpl::new;
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

		/**
		 * Called from native code with a relation oid when one relation's
		 * metadata has been invalidated, or with {@code InvalidOid} to flush
		 * all relation metadata.
		 */
		private static void invalidateRelation(int relOid)
		{
			assert threadMayEnterPG() : "RegClass invalidate thread";

			List<SwitchPoint> sps = new ArrayList<>();
			List<Runnable> postOps = new ArrayList<>();

			if ( InvalidOid != relOid )
			{
				RegClassImpl c = (RegClassImpl)
					findObjectId(RegClass.CLASSID, relOid);
				if ( null != c )
					c.invalidate(sps, postOps);
			}
			else // invalidate all RegClass instances
			{
				forEachValue(o ->
				{
					if ( o instanceof RegClassImpl )
						((RegClassImpl)o).invalidate(sps, postOps);
				});
			}

			if ( sps.isEmpty() )
				return;

			SwitchPoint.invalidateAll(sps.stream().toArray(SwitchPoint[]::new));

			postOps.forEach(Runnable::run);
		}

		/**
		 * Called from native code with the {@code catcache} hash of the type
		 * Oid (inconvenient, as that is likely different from the hash Java
		 * uses), or zero to flush metadata for all cached types.
		 */
		private static void invalidateType(int oidHash)
		{
			assert threadMayEnterPG() : "RegType invalidate thread";

			List<SwitchPoint> sps = new ArrayList<>();
			List<Runnable> postOps = new ArrayList<>();

			forEachValue(o ->
			{
				if ( ! ( o instanceof RegTypeImpl ) )
					return;
				if ( 0 == oidHash  ||  oidHash == murmurhash32(o.oid()) )
					((RegTypeImpl)o).invalidate(sps, postOps);
			});

			if ( sps.isEmpty() )
				return;

			SwitchPoint.invalidateAll(sps.stream().toArray(SwitchPoint[]::new));

			postOps.forEach(Runnable::run);
		}
	}

	/*
	 * Go ahead and reserve fixed slot offsets for the common tuple/name/
	 * namespace/owner/acl slots all within Addressed; those that
	 * correspond to interfaces a given subclass doesn't implement won't
	 * get used. Being fussier about it here would only complicate the code.
	 */
	static final int SLOT_TUPLE     = 0;
	static final int SLOT_NAME      = 1;
	static final int SLOT_NAMESPACE = 2;
	static final int SLOT_OWNER     = 3;
	static final int SLOT_ACL       = 4;
	static final int NSLOTS         = 5;

	@SuppressWarnings("unchecked")
	static class Addressed<T extends CatalogObject.Addressed<T>>
	extends CatalogObjectImpl implements CatalogObject.Addressed<T>
	{
		/**
		 * Invalidation {@code SwitchPoint} for catalog objects that do not have
		 * their own selective invalidation callbacks.
		 *<p>
		 * PostgreSQL only has a limited number of callback slots, so we do not
		 * consume one for every type of catalog object. Many will simply depend
		 * on this {@code SwitchPoint}, which will be invalidated at every
		 * transaction, subtransaction, or command counter change.
		 *<p>
		 * XXX This is not strictly conservative: those are common points where
		 * PostgreSQL processes invalidations, but there are others (such as
		 * lock acquisitions) less easy to predict or intercept.
		 */
		static final SwitchPoint[] s_globalPoint = { new SwitchPoint() };
		static final UnaryOperator<MethodHandle[]> s_initializer;
		final MethodHandle[] m_slots;

		static
		{
			s_initializer =
				new Builder<>(CatalogObjectImpl.Addressed.class)
				.withLookup(lookup())
				.withSwitchPoint(o -> s_globalPoint[0])
				.withCandidates(
					CatalogObjectImpl.Addressed.class.getDeclaredMethods())
				.withSlots(o -> o.m_slots)
				.withDependent("cacheTuple", SLOT_TUPLE)
				.build();
		}

		static TupleTableSlot cacheTuple(CatalogObjectImpl.Addressed o)
		{
			ByteBuffer heapTuple;

			/*
			 * The longest we can hold a tuple (non-copied) from syscache is
			 * for the life of CurrentResourceOwner. We may want to cache the
			 * thing for longer, if we can snag invalidation messages for it.
			 * So, call _searchSysCacheCopy, in the JavaMemoryContext, which is
			 * immortal; we'll arrange below to explicitly free our copy later.
			 */
			try ( Checked.AutoCloseable<RuntimeException> ac =
				allocatingIn(JavaMemoryContext()) )
			{
				heapTuple = _searchSysCacheCopy1(o.cacheId(), o.oid());
				if ( null == heapTuple )
					return null;
			}

			/*
			 * Because our copy is in an immortal memory context, we can
			 * pass null as the lifespan below. The DualState manager
			 * created for the TupleTableSlot will therefore not have
			 * any nativeStateReleased action; on javaStateUnreachable or
			 * javaStateReleased, it will free the tuple copy.
			 */
			return heapTupleGetLightSlot(o.cacheDescriptor(), heapTuple, null);
		}

		/**
		 * Find a tuple in the PostgreSQL {@code syscache}, returning a copy
		 * made in the current memory context.
		 *<p>
		 * The key(s) in PostgreSQL are really {@code Datum}; perhaps this
		 * should be refined to rely on {@link Datum.Accessor Datum.Accessor}
		 * somehow, once that implements store methods. For present purposes,
		 * we only need to support 32-bit integers, which will be zero-extended
		 * to {@code Datum} width.
		 */
		static native ByteBuffer _searchSysCacheCopy1(int cacheId, int key1);

		/**
		 * Find a tuple in the PostgreSQL {@code syscache}, returning a copy
		 * made in the current memory context.
		 *<p>
		 * The key(s) in PostgreSQL are really {@code Datum}; perhaps this
		 * should be refined to rely on {@link Datum.Accessor Datum.Accessor}
		 * somehow, once that implements store methods. For present purposes,
		 * we only need to support 32-bit integers, which will be zero-extended
		 * to {@code Datum} width.
		 */
		static native ByteBuffer _searchSysCacheCopy2(
			int cacheId, int key1, int key2);

		/**
		 * Search the table <var>classId</var> for at most one row with the Oid
		 * <var>objId</var> in column <var>oidCol</var>, using the index
		 * <var>indexOid</var> if it is not {@code InvalidOid}, returning null
		 * or a copy of the tuple in the current memory context.
		 *<p>
		 * The returned tuple should be like one obtained from {@code syscache}
		 * in having no external TOAST pointers. The tuple descriptor is passed
		 * so that {@code toast_flatten_tuple} can be called if necessary.
		 */
		static native ByteBuffer _sysTableGetByOid(
			int classId, int objId, int oidCol, int indexOid, long tupleDesc);

		/**
		 * Calls {@code lookup_rowtype_tupdesc_noerror} in the PostgreSQL
		 * {@code typcache}, returning a byte buffer over the result, or null
		 * if there isn't one (such as when called with a type oid that doesn't
		 * represent a composite type).
		 *<p>
		 * Beware that "noerror" does not prevent an ugly {@code ereport} if
		 * the oid doesn't represent an existing type at all.
		 *<p>
		 * Only to be called by {@code RegTypeImpl}. Declaring it here allows
		 * that class to be kept pure Java.
		 *<p>
		 * This is used when we know we will be caching the result, so
		 * the native code will already have further incremented
		 * the reference count (for a counted descriptor) and released the pin
		 * {@code lookup_rowtype_tupdesc} took, thereby waiving leaked-reference
		 * warnings. We will hold on to the result until an invalidation message
		 * tells us not to.
		 *<p>
		 * If the descriptor is not reference-counted, ordinarily it would be of
		 * dubious longevity, but when obtained from the {@code typcache},
		 * such a descriptor is good for the life of the process (clarified
		 * in upstream commit bbc227e).
		 */
		static native ByteBuffer _lookupRowtypeTupdesc(int oid, int typmod);

		/**
		 * Return a byte buffer mapping the tuple descriptor
		 * for {@code pg_class} itself, using only the PostgreSQL
		 * {@code relcache}.
		 *<p>
		 * Only to be called by {@code RegClassImpl}. Declaring it here allows
		 * that class to be kept pure Java.
		 *<p>
		 * Other descriptor lookups on a {@code RegClass} are done by handing
		 * off to its associated row {@code RegType}, which will look in
		 * the {@code typcache}. But finding the associated row {@code RegType}
		 * isn't something {@code RegClass} can do before it has obtained this
		 * crucial tuple descriptor for its own structure.
		 *<p>
		 * This method shall increment the reference count; the caller will pass
		 * the byte buffer directly to a {@code TupleDescImpl} constructor,
		 * which assumes that has already happened. The reference count shall be
		 * incremented without registering the descriptor for leak warnings.
		 */
		static native ByteBuffer _tupDescBootstrap();

		/* XXX private */ Addressed()
		{
			this(s_initializer.apply(new MethodHandle[NSLOTS]));
		}

		/**
		 * Constructor for use by a subclass that supplies a slots array
		 * (assumed to have length at least NSLOTS).
		 *<p>
		 * It is the responsibility of the subclass to initialize the slots
		 * (including the first NSLOTS ones defined here; s_initializer can be
		 * used for those, if the default global-switchpoint behavior it offers
		 * is appropriate).
		 *<p>
		 * Some subclasses may do oddball things, such as RegTypeImpl.Modified
		 * sharing the slots array of its base NoModifier instance.
		 *<p>
		 * Any class that will do such a thing must also hold a strong reference
		 * to whatever instance the slots array 'belongs' to; a reference to
		 * just the array can't be counted on to keep the other instance live.
		 */
		Addressed(MethodHandle[] slots)
		{
			if ( InvalidOid == oid() )
				makeInvalidInstance(slots);
			m_slots = slots;
		}

		/**
		 * Adjust cache slots when constructing an invalid instance.
		 *<p>
		 * This implementation stores a permanent null (insensitive to
		 * invalidation) in {@code SLOT_TUPLE}, which will cause {@code exists}
		 * to return false and other dependent methods to fail.
		 *<p>
		 * An instance method because {@code AttributeImpl.Transient} will
		 * have to override it; those things have the invalid Oid in real life.
		 */
		void makeInvalidInstance(MethodHandle[] slots)
		{
			setConstant(slots, SLOT_TUPLE, null);
		}

		@Override
		public RegClass.Known<T> classId()
		{
			return CatalogObjectImpl.Factory.staticFormClassId(
				classOid(), (Class<? extends T>)getClass());
		}

		@Override
		public boolean exists()
		{
			return null != cacheTuple();
		}

		TupleDescriptor cacheDescriptor()
		{
			return classId().tupleDescriptor();
		}

		TupleTableSlot cacheTuple()
		{
			try
			{
				MethodHandle h = m_slots[SLOT_TUPLE];
				return (TupleTableSlot)h.invokeExact(this, h);
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}

		/**
		 * Inheritable placeholder to throw
		 * {@code UnsupportedOperationException} during development.
		 */
		int cacheId()
		{
			throw notyet();
		}

		@Override
		public String toString()
		{
			String prefix = super.toString();
			if ( this instanceof CatalogObject.Named )
			{
				try
				{
					CatalogObject.Named named = (CatalogObject.Named)this;
					if ( ! exists() )
						return prefix;
					if ( this instanceof CatalogObject.Namespaced )
					{
						CatalogObject.Namespaced spaced =
							(CatalogObject.Namespaced)this;
						RegNamespace ns = spaced.namespace();
						if ( ns.exists() )
							return prefix + spaced.qualifiedName();
						return prefix + "(" + ns + ")." + named.name();
					}
					return prefix + named.name();
				}
				catch ( LinkageError e )
				{
					/*
					 * Do nothing; LinkageError is expected when testing in,
					 * for example, jshell, and not in a PostgreSQL backend.
					 */
				}
			}
			return prefix;
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
			try
			{
				MethodHandle h =
					((CatalogObjectImpl.Addressed)this).m_slots[SLOT_NAME];
				return (T)h.invokeExact(this, h);
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}
	}

	interface Namespaced<T extends Identifier.Unqualified<T>>
		extends Named<T>, CatalogObject.Namespaced<T>
	{
		@Override
		default RegNamespace namespace()
		{
			try
			{
				MethodHandle h =
					((CatalogObjectImpl.Addressed)this).m_slots[SLOT_NAMESPACE];
				return (RegNamespace)h.invokeExact(this, h);
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}
	}

	interface Owned extends CatalogObject.Owned
	{
		@Override
		default RegRole owner()
		{
			try
			{
				MethodHandle h =
					((CatalogObjectImpl.Addressed)this).m_slots[SLOT_OWNER];
				return (RegRole)h.invokeExact(this, h);
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}
	}

	interface AccessControlled<T extends Grant>
	extends CatalogObject.AccessControlled<T>
	{
		@Override
		default List<T> grants()
		{
			try
			{
				MethodHandle h =
					((CatalogObjectImpl.Addressed)this).m_slots[SLOT_ACL];
				/*
				 * The value stored in the slot comes from GrantAdapter, which
				 * returns undifferentiated List<Grant>, to be confidently
				 * narrowed here to List<T>.
				 */
				return (List<T>)h.invokeExact(this, h);
			}
			catch ( Throwable t )
			{
				throw unchecked(t);
			}
		}

		@Override
		default List<T> grants(RegRole grantee)
		{
			throw notyet();
		}
	}

	/**
	 * Instances of {@code ArrayAdapter} for types used in the catalogs.
	 *<p>
	 * A holder interface so these won't be instantiated unless wanted.
	 */
	public interface ArrayAdapters
	{
		ArrayAdapter<List<RegClass>,?> REGCLASS_LIST_INSTANCE =
			new ArrayAdapter<>(AsFlatList.of(AsFlatList::nullsIncludedCopy),
				REGCLASS_INSTANCE);

		ArrayAdapter<List<RegType>,?> REGTYPE_LIST_INSTANCE =
			new ArrayAdapter<>(AsFlatList.of(AsFlatList::nullsIncludedCopy),
				REGTYPE_INSTANCE);

		/**
		 * List of {@code Identifier.Simple} from an array of {@code TEXT}
		 * that represents SQL identifiers.
		 */
		ArrayAdapter<List<Identifier.Simple>,?> TEXT_NAME_LIST_INSTANCE =
			new ArrayAdapter<>(
				/*
				 * A custom array contract is an anonymous class, not just a
				 * lambda, so the compiler will record the actual type arguments
				 * with which it specializes the generic contract.
				 */
				new Adapter.Contract.Array<List<Identifier.Simple>,String>()
				{
					@Override
					public List<Identifier.Simple> construct(
						int nDims, int[] dimsAndBounds, As<String,?> adapter,
						TupleTableSlot.Indexed slot)
						throws SQLException
					{
						int n = slot.elements();
						Identifier.Simple[] names = new Identifier.Simple[n];
						for ( int i = 0; i < n; ++ i )
							names[i] =
								Identifier.Simple.fromCatalog(
									slot.get(i, adapter));
						return List.of(names);
					}
				},
				TextAdapter.INSTANCE);
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

	/**
	 * The Oid hash function used by the backend's Oid-based catalog caches
	 * to identify the entries affected by invalidation events.
	 *<p>
	 * From hashutils.h.
	 */
	static int murmurhash32(int h)
	{
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h;
	}
}
