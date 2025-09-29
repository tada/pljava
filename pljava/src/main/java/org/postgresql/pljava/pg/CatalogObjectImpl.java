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
package org.postgresql.pljava.pg;

import org.postgresql.pljava.Adapter;
import org.postgresql.pljava.Adapter.As;
import org.postgresql.pljava.Adapter.AsByte;
import org.postgresql.pljava.TargetList.Projection;

import static org.postgresql.pljava.internal.Backend.threadMayEnterPG;
import org.postgresql.pljava.internal.CacheMap;
import org.postgresql.pljava.internal.Checked;
import org.postgresql.pljava.internal.DualState; // for javadoc
import org.postgresql.pljava.internal.Invocation;
import org.postgresql.pljava.internal.SwitchPointCache; // for javadoc
import org.postgresql.pljava.internal.SwitchPointCache.Builder;
import org.postgresql.pljava.internal.SwitchPointCache.SwitchPoint;
import static org.postgresql.pljava.internal.SwitchPointCache.setConstant;
import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import org.postgresql.pljava.adt.Array.AsFlatList;
import org.postgresql.pljava.adt.spi.Datum;

import org.postgresql.pljava.model.*;
import org.postgresql.pljava.model.RegType.Unresolved; // for javadoc
import static org.postgresql.pljava.model.MemoryContext.JavaMemoryContext;

import static org.postgresql.pljava.pg.MemoryContextImpl.allocatingIn;
import org.postgresql.pljava.pg.ModelConstants;
import static org.postgresql.pljava.pg.ModelConstants.PG_VERSION_NUM;
import static org.postgresql.pljava.pg.ModelConstants.LANGOID;
import static org.postgresql.pljava.pg.ModelConstants.PROCOID;
import static org.postgresql.pljava.pg.ModelConstants.TRFOID;
import static org.postgresql.pljava.pg.ModelConstants.TYPEOID;
import static org.postgresql.pljava.pg.ModelConstants.SIZEOF_LONG;
import static org.postgresql.pljava.pg.TupleTableSlotImpl.heapTupleGetLightSlot;

import org.postgresql.pljava.pg.adt.ArrayAdapter;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGCLASS_INSTANCE;
import static org.postgresql.pljava.pg.adt.OidAdapter.REGTYPE_INSTANCE;
import org.postgresql.pljava.pg.adt.Primitives;
import org.postgresql.pljava.pg.adt.TextAdapter;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

import java.io.IOException;

import java.lang.annotation.Native;

import java.lang.invoke.MethodHandle;
import static java.lang.invoke.MethodHandles.lookup;

import static java.lang.ref.Reference.reachabilityFence;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;

import java.sql.SQLException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Supplier;

import static java.util.stream.Stream.iterate;

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

	protected CatalogObjectImpl() // only TriggerImpl outside this file
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
		if ( classOid() == ((CatalogObjectImpl)c).oid() )
			return (T) this;
		if ( classValid()  &&  isValid() )
			throw new IllegalStateException(String.format(
				"cannot make %s a CatalogObject of class %s", this, c));
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
		Class c = getClass();
		c = iterate(c, Objects::nonNull, Class::getSuperclass)
			.flatMap(c1 -> Arrays.stream(c1.getInterfaces()))
			.filter(CatalogObject.class::isAssignableFrom)
			.filter(i -> CatalogObject.class.getModule().equals(i.getModule()))
			.findFirst().get();
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
		T formObjectIdImpl(
			RegClass.Known<T> classId, int objId, IntPredicate versionTest)
		{
			return staticFormObjectId(classId, objId, versionTest);
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
		protected long fetchAll()
		{
			switch ( SIZEOF_LONG )
			{
				case Long.BYTES:    return Long.MAX_VALUE;
				case Integer.BYTES: return Integer.MAX_VALUE;
				default:
					throw new UnsupportedOperationException(
						"define FETCH_ALL on platform with SIZEOF_LONG="
						+ SIZEOF_LONG);
			}
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

		static <T extends CatalogObject.Addressed<T>>
		T staticFormObjectId(RegClass.Known<T> classId, int objId)
		{
			return staticFormObjectId(classId, objId, v -> true);
		}

		@SuppressWarnings("unchecked")
		static <T extends CatalogObject.Addressed<T>>
		T staticFormObjectId(
			RegClass.Known<T> classId, int objId, IntPredicate versionTest)
		{
			return (T)form(((CatalogObjectImpl)classId).oid(),
				versionTest.test(PG_VERSION_NUM) ? objId : InvalidOid, 0);
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
				case TableSpaceRelationId:
					fieldRead = Tablespace.CLASSID;
					return TablespaceImpl::new;
				case TypeRelationId:
					fieldRead = RegType.CLASSID;
					return typeConstructorFor(objId);
				case ProcedureRelationId:
					fieldRead = RegProcedure.CLASSID;
					return RegProcedureImpl::new;
				case AuthIdRelationId:
					fieldRead = RegRole.CLASSID;
					return RegRoleImpl::new;
				case DatabaseRelationId:
					fieldRead = Database.CLASSID;
					return DatabaseImpl::new;
				case ForeignServerRelationId:
					fieldRead = ForeignServer.CLASSID;
					return ForeignServerImpl::new;
				case ForeignDataWrapperRelationId:
					fieldRead = ForeignDataWrapper.CLASSID;
					return ForeignDataWrapperImpl::new;
				case AccessMethodRelationId:
					fieldRead = AccessMethod.CLASSID;
					return AccessMethodImpl::new;
				case ConstraintRelationId:
					fieldRead = Constraint.CLASSID;
					return ConstraintImpl::new;
				case LanguageRelationId:
					fieldRead = ProceduralLanguage.CLASSID;
					return ProceduralLanguageImpl::new;
				case NamespaceRelationId:
					fieldRead = RegNamespace.CLASSID;
					return RegNamespaceImpl::new;
				case OperatorRelationId:
					fieldRead = RegOperator.CLASSID;
					return RegOperatorImpl::new;
				case TriggerRelationId:
					fieldRead = Trigger.CLASSID;
					return TriggerImpl::new;
				case ExtensionRelationId:
					fieldRead = Extension.CLASSID;
					return ExtensionImpl::new;
				case CollationRelationId:
					fieldRead = RegCollation.CLASSID;
					return RegCollationImpl::new;
				case TransformRelationId:
					fieldRead = Transform.CLASSID;
					return TransformImpl::new;
				case TSDictionaryRelationId:
					fieldRead = RegDictionary.CLASSID;
					return RegDictionaryImpl::new;
				case TSConfigRelationId:
					fieldRead = RegConfig.CLASSID;
					return RegConfigImpl::new;
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
		private static void syscacheInvalidate(
			int cbIndex, int cacheId, int oidHash)
		{
			assert threadMayEnterPG() : "RegType invalidate thread";

			List<SwitchPoint> sps = new ArrayList<>();
			List<Runnable> postOps = new ArrayList<>();

			Class<? extends Addressed> targetClass = s_sysInvalClasses[cbIndex];

			forEachValue(o ->
			{
				if ( ! targetClass.isInstance(o) )
					return;
				if ( 0 == oidHash  ||  oidHash == murmurhash32(o.oid()) )
					((Addressed<?>)targetClass.cast(o))
						.invalidate(sps, postOps);
			});

			if ( sps.isEmpty() )
				return;

			SwitchPoint.invalidateAll(sps.stream().toArray(SwitchPoint[]::new));

			postOps.forEach(Runnable::run);
		}

		/**
		 * Returns a constructor for an ordinary {@code NoModifier}
		 * instance or an {@code Unresolved} instance, as determined by
		 * the PostgreSQL-version-specific set of PostgreSQL pseudotypes
		 * that require resolution to actual types used at a given call site.
		 *<p>
		 * At present, the same {@link Unresolved Unresolved} class is used for
		 * both families of polymorphic pseudotype as well as the truly
		 * anything-goes the {@code ANY} type.
		 */
		static Supplier<CatalogObjectImpl> typeConstructorFor(int oid)
		{
			switch ( oid )
			{
				// Polymorphic family 1
			case ANYARRAYOID:
			case ANYELEMENTOID:
			case ANYNONARRAYOID:
			case ANYENUMOID:
			case ANYRANGEOID:
				return RegTypeImpl.Unresolved::new;
			case ANYMULTIRANGEOID:
				if ( PG_VERSION_NUM >= 140000 )
					return RegTypeImpl.Unresolved::new;
				else
					return RegTypeImpl.NoModifier::new;

				// Polymorphic family 2
			case ANYCOMPATIBLEOID:
			case ANYCOMPATIBLEARRAYOID:
			case ANYCOMPATIBLENONARRAYOID:
			case ANYCOMPATIBLERANGEOID:
				if ( PG_VERSION_NUM >= 130000 )
					return RegTypeImpl.Unresolved::new;
				else
					return RegTypeImpl.NoModifier::new;
			case ANYCOMPATIBLEMULTIRANGEOID:
				if ( PG_VERSION_NUM >= 140000 )
					return RegTypeImpl.Unresolved::new;
				else
					return RegTypeImpl.NoModifier::new;

				// The wild-west wildcard "any" type
			case ANYOID:
				return RegTypeImpl.Unresolved::new;
			default:
				return RegTypeImpl.NoModifier::new;
			}
		}

		/*
		 * Oids of the polymorphic types. If there is ever a call to expose
		 * them in API like other type constants, these can be moved to
		 * CatalogObject.Factory with the rest of those, but for now it may be
		 * enough for the internal RegTypeImpl to know about them.
		 */
		@Native public static final int ANYOID                        = 2276;

		// ANYARRAYOID is inherited because API has RegType.ANYARRAY
		@Native public static final int ANYELEMENTOID                 = 2283;
		@Native public static final int ANYNONARRAYOID                = 2776;
		@Native public static final int ANYENUMOID                    = 3500;
		@Native public static final int ANYRANGEOID                   = 3831;
		@Native public static final int ANYMULTIRANGEOID              = 4537;

		@Native public static final int ANYCOMPATIBLEMULTIRANGEOID    = 4538;
		@Native public static final int ANYCOMPATIBLEOID              = 5077;
		@Native public static final int ANYCOMPATIBLEARRAYOID         = 5078;
		@Native public static final int ANYCOMPATIBLENONARRAYOID      = 5079;
		@Native public static final int ANYCOMPATIBLERANGEOID         = 5080;

		/*
		 * A relation ID that won't be used to construct a full-blown catalog
		 * object, but used in RegClassImpl.
		 */
		@Native public static final int ForeignTableRelationId = 3118;

		/*
		 * Indices into arrays used for syscache invalidation callbacks.
		 * One of these is a boolean native array the C callback can check
		 * and return quickly if there is nothing from that cache to invalidate.
		 * At least one more array indexed the same way is in Java and used
		 * in syscacheInvalidate.
		 */
		@Native public static final int LANGOID_CB = 0;
		@Native public static final int PROCOID_CB = 1;
		@Native public static final int  TRFOID_CB = 2;
		@Native public static final int TYPEOID_CB = 3;
		@Native public static final int SYSCACHE_CBS = 4;

		/**
		 * An array mapping a syscache invalidation callback index to the Java
		 * class used for instances of the corresponding catalog class.
		 */
		private static final Class<? extends Addressed>[] s_sysInvalClasses;

		static
		{
			@SuppressWarnings("unchecked")
			Class<? extends Addressed>[] cs =
				(Class<? extends Addressed>[])new Class<?> [ SYSCACHE_CBS ];
			cs [ LANGOID_CB ] = ProceduralLanguageImpl.class;
			cs [ PROCOID_CB ] = RegProcedureImpl.class;
			cs [  TRFOID_CB ] = TransformImpl.class;
			cs [ TYPEOID_CB ] = RegTypeImpl.class;
			s_sysInvalClasses = cs;
		}

		/**
		 * A {@code ByteBuffer} that windows a C boolean array, one for each
		 * registered syscache invalidation callback, updated (see the {@link
		 * CatalogObjectImpl.Addressed#sysCacheInvalArmed sysCacheInvalArmed}
		 * method below) to reflect whether the Java {@code CacheMap} contains
		 * any instances of the corresponding class that could need
		 * invalidation.
		 */
		private static final ByteBuffer s_sysCacheInvalArmed =
			CatalogObjectImpl.Addressed._windowSysCacheInvalArmed()
				.order(nativeOrder());
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

	/**
	 * Base class for every catalog object that has an {@link #oid oid}
	 * identifying a row in a catalog table identified by
	 * a {@link #classId classId}.
	 */
	static class Addressed<T extends CatalogObject.Addressed<T>>
	extends CatalogObjectImpl implements CatalogObject.Addressed<T>
	{
		/**
		 * Copy this constant here so it can be inherited without ceremony
		 * by subclasses of Addressed, which may need it when initializing
		 * attribute projections. Putting the copy up in CatalogObject itself
		 * is a problem if another compilation unit does import static of both
		 * CatalogObjectImpl.* and ModelConstants.* but there is little reason
		 * anyone would import CatalogObjectImpl.Addressed.*.
		 */
		static final int PG_VERSION_NUM = ModelConstants.PG_VERSION_NUM;

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

		/**
		 * Obtains a {@code ByteBuffer} that windows the C boolean array
		 * indicating which syscache callbacks are currently 'armed'.
		 */
		private static native ByteBuffer _windowSysCacheInvalArmed();

		/**
		 * Initializer for only the {@code SLOT_TUPLE} slot of a
		 * {@code CatalogObjectImpl.Addressed} or subclass.
		 *<p>
		 * This initializer uses
		 * {@link #cacheTuple(CatalogObjectImpl.Addressed) cacheTuple} to
		 * populate the slot, which is appropriate for the common case where the
		 * subclass overrides {@link #cacheId() cacheId} to return the
		 * identifier of a syscache to be searched by a single oid.
		 */
		static final Function<MethodHandle[],MethodHandle[]> s_initializer;

		/**
		 * {@link SwitchPointCache SwitchPointCache}-managed slots, the
		 * foundation for cached values returned by API methods of
		 * {@code CatalogObjectImpl.Addressed} subclasses.
		 *<p>
		 * The assignment of this field happens in this class's constructor, but
		 * when a subclass is being instantiated, the subclass constructor
		 * supplies the array. The array length is determined by the number of
		 * slots needed by the subclass, whose own slots begin after the
		 * {@link #NSLOTS NSLOTS} initial ones reserved above.
		 *<p>
		 * Each array element is a "slot", and contains a {@link MethodHandle}
		 * of two arguments and a return type specialized to the type of the
		 * value to be cached there. API methods for returning cached values
		 * do so by invoking the method handle with two arguments, the receiver
		 * object and the handle itself, and returning its result.
		 *<p>
		 * On the first call, or the first again after invalidation caused by
		 * DDL changes, the method handle will invoke a "computation method".
		 * By convention, the computation method has the same name as the API
		 * method, but is static, taking the object instance as its only
		 * parameter rather than as an instance method's receiver. Such naming
		 * is merely a convention; the association between each slot and its
		 * computation method is determined by a
		 * {@link SwitchPointCache.Builder#withDependent withDependent} call as
		 * the initializer for the slots array is being built. In each subclass,
		 * a static initializer uses {@link SwitchPointCache.Builder} to
		 * construct an initializer
		 * ({@code Function<MethodHandle[],MethodHandle[]>}) that will be saved
		 * in a static, and applied in the instance constructor to
		 * a freshly-allocated array, installing the initial method handles in
		 * its slots.
		 *<p>
		 * On subsequent uses of a slot, until invalidation is triggered, the
		 * method handle found there will typically disregard its arguments and
		 * return, as a constant, the value the computation method returned.
		 *<p>
		 * When a computation method runs, it runs on "the PG thread" (see
		 * {@link DualState DualState} for more on what "the PG thread" means
		 * under the different settings of {@code pljava.java_thread_pg_entry}),
		 * so without further ceremony it may assume it is serialized with
		 * respect to other computation methods, and perform actions, such as
		 * JNI calls, for which that thread must be used.
		 */
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

		/**
		 * Writes the 'armed' status for a specific C syscache invalidation
		 * callback, specified by the index defined for it in
		 * {@link CatalogObjectImpl.Factory Factory}.
		 *<p>
		 * When the status for a given syscache is not armed, its C callback
		 * returns immediately with no Java upcall.
		 */
		protected static void sysCacheInvalArmed(int index, boolean inUse)
		{
			CatalogObjectImpl.Factory.s_sysCacheInvalArmed.put(
				index, (byte)(inUse ? 1 : 0));
		}

		/**
		 * A computation method for retrieving the "cache tuple", suited to the
		 * common case where a subclass overrides {@link #cacheId cacheId} to
		 * return the ID of a syscache searchable with a single {@code oid} key.
		 */
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
		 * for <var>relid</var>, using only the PostgreSQL
		 * {@code relcache}.
		 *<p>
		 * Only to be called by {@code RegClassImpl}. Declaring it here allows
		 * that class to be kept pure Java.
		 *<p>
		 * Other descriptor lookups on a {@code RegClass} are done by handing
		 * off to an associated row {@code RegType}, when there is one, which
		 * will look in
		 * the {@code typcache}. But finding the associated row {@code RegType}
		 * isn't something {@code RegClass} can do before it has obtained this
		 * crucial tuple descriptor for its own structure, and also there are
		 * relation kinds (index and toast, anyway) which have no type entry.
		 *<p>
		 * This method shall increment the reference count; the caller will pass
		 * the byte buffer directly to a {@code TupleDescImpl} constructor,
		 * which assumes that has already happened. The reference count shall be
		 * incremented without registering the descriptor for leak warnings.
		 */
		static native ByteBuffer _tupDescBootstrap(int relid);

		private Addressed()
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
		 * Called from an invalidation callback in {@code Factory} to set up
		 * the invalidation of this catalog object's metadata.
		 *<p>
		 * Adds this object's {@code SwitchPoint} to the caller's list so that,
		 * if more than one is to be invalidated, that can be done in bulk. Adds
		 * to <var>postOps</var> any operations the caller should conclude with
		 * after invalidating the {@code SwitchPoint}.
		 *<p>
		 * This implementation does nothing (other than to assert false, when
		 * assertions are enabled). It should be overridden in those subclasses
		 * that do more fine-grained invalidation than simply relying on
		 * {@code s_globalPoint}.
		 */
		void invalidate(List<SwitchPoint> sps, List<Runnable> postOps)
		{
			assert false : "unhandled invalidation of " + toString();
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
			/*
			 * By design, this class must be an implementation of
			 * T (extends CatalogObject.Addressed<T>) for the same T that is
			 * the parameter of its classId.
			 */
			@SuppressWarnings("unchecked")
			Class<? extends T> thisClass = (Class<? extends T>)getClass();

			return CatalogObjectImpl.Factory.staticFormClassId(
				classOid(), thisClass);
		}

		@Override
		public boolean exists()
		{
			return null != cacheTuple();
		}

		/**
		 * Returns the {@link TupleDescriptor} for the catalog table whose rows
		 * define instances of this class.
		 *<p>
		 * This implementation calls {@link #classId classId} and then
		 * {@link RegClass#tupleDescriptor tupleDescriptor} on that. A subclass
		 * may override when it can supply the decriptor more efficiently, and
		 * must override in the few cases ({@link AttributeImpl AttributeImpl},
		 * for example) where that isn't the right way to get it.
		 */
		TupleDescriptor cacheDescriptor()
		{
			return classId().tupleDescriptor();
		}

		/**
		 * Returns, from the proper catalog table, the cached tuple that defines
		 * this instance of this class.
		 */
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
		 * Returns the ID of a syscache that is searchable with a single oid key
		 * to retrieve the {@link #cacheTuple cacheTuple} defining this instance
		 * of this class.
		 *<p>
		 * This implementation throws {@code UnsupportedOperationException} and
		 * must be overridden in every subclass, unless a subclass supplies its
		 * own computation method for {@code cacheTuple} that does not use this.
		 */
		int cacheId()
		{
			throw notyet();
		}

		/**
		 * Default {@code toString} method for {@code Addressed} and subclasses.
		 *<p>
		 * Extends {@link CatalogObjectImpl#toString CatalogObjectImpl.toString}
		 * by adding the name (if this is an instance of
		 * {@link CatalogObject.Named Named}) or qualified name (if an
		 * instance of {@link CatalogObject.Namespaced Namespaced}), if
		 * available.
		 */
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

		/**
		 * Utility class to create a {@link Projection Projection} using
		 * attribute names that may be conditional (on something like
		 * {@link #PG_VERSION_NUM PG_VERSION_NUM}).
		 *<p>
		 * {@code alsoIf} adds strings to the list, if the condition is true, or
		 * the same number of nulls if the condition is false.
		 *<p>
		 * {@code project} filters the list to only the non-null values, using
		 * those to form a {@code Projection} and obtain its iterator of
		 * attributes.
		 *<p>
		 * This class then implements its own iterator of attributes, iterating
		 * for the length of the original name list, drawing from the
		 * Projection's iterator where a non-null name was saved, or producing
		 * null (and not incrementing the Projection's iterator) where a null
		 * was saved.
		 *<p>
		 * The iterator can be used in a sequence of static final initializers,
		 * such that the final fields will end up containing the wanted
		 * Attribute instances where applicable, or null where not.
		 */
		static class AttNames implements Iterator<Attribute>
		{
			private ArrayList<String> strings = new ArrayList<>();

			private Iterator<String> myItr;
			private Projection it;
			private Iterator<Attribute> itsItr;

			AttNames alsoIf(boolean p, String... toAdd)
			{
				if ( p )
					for ( String s : toAdd )
						strings.add(s);
				else
					for ( String s : toAdd )
						strings.add(null);
				return this;
			}

			AttNames project(Projection p)
			{
				String[] filtered = strings
					.stream().filter(Objects::nonNull).toArray(String[]::new);
				it = p.project(filtered);
				itsItr = it.iterator();
				myItr = strings.iterator();
				return this;
			}

			/**
			 * Returns a further projection of the one derived from the names.
			 *<p>
			 * Caters to cases (so far only one in RegTypeImpl) where a
			 * computation method will want a projection of multiple attributes,
			 * instead of a single attribute.
			 *<p>
			 * In the expected usage, the attribute arguments will have been
			 * supplied from the iterator, and will be null where the expected
			 * attributes do not exist. In that case, null must be returned for
			 * the projection.
			 */
			Projection project(Attribute... atts)
			{
				if ( Arrays.stream(atts).anyMatch(Objects::isNull) )
					return null;
				return it.project(atts);
			}

			@Override
			public boolean hasNext()
			{
				return myItr.hasNext();
			}

			@Override
			public Attribute next()
			{
				String myNext = myItr.next();
				if ( null == myNext )
					return null;
				return itsItr.next();
			}
		}

		/**
		 * Constructs a new {@link AttNames AttNames} instance and begins
		 * populating it, adding <var>names</var> unconditionally.
		 */
		static AttNames attNames(String... names)
		{
			return new AttNames().alsoIf(true, names);
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
	/**
	 * Mixin that supplies the implementation of
	 * {@link CatalogObject.Named CatalogObject.Named}.
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

	/**
	 * Mixin that supplies the implementation of
	 * {@link CatalogObject.Namespaced CatalogObject.Namespaced}.
	 */
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

	/**
	 * Mixin that supplies the implementation of
	 * {@link CatalogObject.Owned CatalogObject.Owned}.
	 */
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

	/**
	 * Mixin that supplies the implementation of
	 * {@link CatalogObject.AccessControlled CatalogObject.AccessControlled}.
	 */
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
	 * Instances of {@link ArrayAdapter ArrayAdapter} for types used
	 * in the catalogs.
	 *<p>
	 * A holder interface so these won't be instantiated unless wanted.
	 */
	public interface ArrayAdapters
	{
		ArrayAdapter<List<RegClass>> REGCLASS_LIST_INSTANCE =
			new ArrayAdapter<>(REGCLASS_INSTANCE,
				AsFlatList.of(AsFlatList::nullsIncludedCopy));

		ArrayAdapter<List<RegType>> REGTYPE_LIST_INSTANCE =
			new ArrayAdapter<>(REGTYPE_INSTANCE,
				AsFlatList.of(AsFlatList::nullsIncludedCopy));

		/**
		 * List of {@code Identifier.Simple} from an array of {@code TEXT}
		 * that represents SQL identifiers.
		 */
		ArrayAdapter<List<Identifier.Simple>> TEXT_NAME_LIST_INSTANCE =
			new ArrayAdapter<>(TextAdapter.INSTANCE,
				/*
				 * A custom array contract is an anonymous class, not just a
				 * lambda, so the compiler will record the actual type arguments
				 * with which it specializes the generic contract.
				 */
				new Adapter.Contract.Array<>()
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
				});

		/**
		 * List of {@code RegProcedure.ArgMode} from an array of {@code "char"}.
		 */
		ArrayAdapter<List<RegProcedure.ArgMode>> ARGMODE_LIST_INSTANCE =
			new ArrayAdapter<>(Primitives.INT1_INSTANCE,
				new Adapter.Contract.Array<>()
				{
					@Override
					public List<RegProcedure.ArgMode> construct(
						int nDims, int[] dimsAndBounds, AsByte<?> adapter,
						TupleTableSlot.Indexed slot)
						throws SQLException
					{
						int n = slot.elements();
						RegProcedure.ArgMode[] modes =
							new RegProcedure.ArgMode[n];
						for ( int i = 0; i < n; ++ i )
						{
							byte in = slot.get(i, adapter);
							switch ( in )
							{
							case (byte)'i':
								modes[i] = RegProcedure.ArgMode.IN;
								break;
							case (byte)'o':
								modes[i] = RegProcedure.ArgMode.OUT;
								break;
							case (byte)'b':
								modes[i] = RegProcedure.ArgMode.INOUT;
								break;
							case (byte)'v':
								modes[i] = RegProcedure.ArgMode.VARIADIC;
								break;
							case (byte)'t':
								modes[i] = RegProcedure.ArgMode.TABLE;
								break;
							default:
								throw new UnsupportedOperationException(
									String.format("Unrecognized " +
										"procedure/function argument mode " +
										"value %#x", in));
							}
						}
						return List.of(modes);
					}
				});

		/**
		 * {@code Map<Identifier.Simple,String>} from an array of {@code TEXT}
		 * that represents 'reloptions' (as used on relations, attributes, and
		 * foreign wrappers / servers / tables, at least).
		 *<p>
		 * The {@code String} value is never expected to be null (PostgreSQL's
		 * {@code transformRelOptions} will have substituted {@code true} where
		 * an option with no value was parsed), and this adapter will
		 * <em>assume</em> the first {@code '='} in each element delimits the
		 * key from the value (that is, that no key can be an SQL delimited
		 * identifier containing {@code '='}, though PostgreSQL as of 17 does
		 * not enforce that).
		 */
		ArrayAdapter<Map<Identifier.Simple,String>> RELOPTIONS_INSTANCE =
			new ArrayAdapter<>(TextAdapter.INSTANCE,
				new Adapter.Contract.Array<>()
				{
					@Override
					public Map<Identifier.Simple,String> construct(
						int nDims, int[] dimsAndBounds, As<String,?> adapter,
						TupleTableSlot.Indexed slot)
						throws SQLException
					{
						int n = slot.elements();
						@SuppressWarnings("unchecked")
						Map.Entry<Identifier.Simple,String>[] entries =
							new Map.Entry[n];
						for ( int i = 0; i < n; ++ i )
						{
							String s = slot.get(i, adapter);
							int pos = s.indexOf('=');
							try
							{
								entries[i] = Map.entry(
									Identifier.Simple.fromCatalog(
										s.substring(0, pos)),
									s.substring(1 + pos));
							}
							catch ( StringIndexOutOfBoundsException e )
							{
								throw new AssertionError(
									"transformed reloption with no =", e);
							}
						}
						return Map.ofEntries(entries);
					}
				});
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
