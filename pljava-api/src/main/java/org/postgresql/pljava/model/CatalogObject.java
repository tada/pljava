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
package org.postgresql.pljava.model;

import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import java.util.function.IntPredicate;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

/**
 * Base interface representing some object in the PostgreSQL catalogs,
 * identified by its {@link #oid() oid}.
 *<p>
 * The {@link #oid() oid} by itself does not constitute an object address until
 * combined with a {@code classId} identifying the catalog to which it belongs.
 * This topmost interface, therefore, represents a catalog object when only
 * the {@code oid} is known, and the {@code classId} is: unknown, or simply
 * understood from context. An instance of this interface can be explicitly
 * combined with a {@code classId}, using the {@link #of of(classId)} method,
 * which will yield an instance of an interface that extends {@link Addressed}
 * and is specific to catalog objects of that class.
 *<p>
 * A {@code classId}, in turn, is simply an instance of
 * {@link RegClass RegClass} (the catalog of relations, whose name "class"
 * reflects PostgreSQL's object-relational origins). It identifies the specific
 * relation in the PostgreSQL catalogs where objects with that {@code classId}
 * can be looked up.
 *<p>
 * Every user relation, of course, is also represented by a {@code RegClass}
 * instance, but not one that can be used to form a catalog object address.
 * For that matter, not every class in the PostgreSQL catalogs is modeled by
 * a class in PL/Java. Therefore, not just any {@code RegClass} instance can be
 * passed to {@link #of of(classId)} as a {@code classId}. Those that can be
 * have the more-specific type {@code RegClass.Known<T>}, which also identifies
 * the Java model class T that will be returned.
 */
public interface CatalogObject
{
	/**
	 * The distinct integer value that {@link #oid oid()} will return when
	 * {@link #isValid isValid()} is false.
	 *<p>
	 * PostgreSQL catalogs typically use this value (rather than a nullable
	 * column and a null value) in cases where an object may or may not be
	 * specified and has not been.
	 */
	int InvalidOid = 0;

	/**
	 * This catalog object's object ID; the integer value that identifies the
	 * object to PostgreSQL when the containing catalog is known.
	 */
	int oid();

	/**
	 * Whether this catalog object has a valid {@code oid}
	 * (any value other than {@code InvalidOid}).
	 *<p>
	 * This is not the same as whether any corresponding catalog object actually
	 * exists. This question can be answered directly from the value of
	 * {@code oid()}. The existence question (which can be asked sensibly only
	 * of an {@link Addressed Addressed} instance with its
	 * {@link Addressed#exists exists()} method} can be answered only through
	 * a lookup attempt for the {@code oid} in the corresponding catalog.
	 *<p>
	 * There is not a unique singleton invalid catalog object instance. Rather,
	 * there can be distinct {@link Addressed Addressed} instances that have
	 * the invalid {@code oid} and distinct {@code classId}s, as well as one
	 * singleton {@code CatalogObject} that has the invalid {@code oid} and
	 * no valid {@code classId}.
	 *<p>
	 * When applied to a {@link RegRole.Grantee RegRole.Grantee}, this method
	 * simply returns the negation of {@link RegRole.Grantee#isPublic isPublic},
	 * which is the method that should be preferred for clarity in that case.
	 */
	boolean isValid();

	/**
	 * Return a catalog object as an {@code Addressed} instance in a known
	 * class.
	 *<p>
	 * For example, if a {@code CatalogObject o} is read from an {@code oid}
	 * column known to represent a namespace, {@code o.of(RegNamespace.CLASSID)}
	 * will return a {@code RegNamespace} instance.
	 *<p>
	 * An instance whose class id is already the desired one will return itself.
	 * On an instance that lacks a valid class id, {@code of} can apply any
	 * desired class id (a different instance will be returned). The invalid
	 * instance of any class can be converted to the (distinct) invalid instance
	 * of any other class. On an instance that is valid and already has a valid
	 * class id, {@code of} will throw an exception if the desired class id
	 * differs.
	 * @param classId A known class id, often from the CLASSID field of a known
	 * CatalogObject subclass.
	 * @param <T> Specific subtype of Addressed that represents catalog objects
	 * with the given class id.
	 * @return An instance with this instance's oid and the desired class id
	 * (this instance, if the class id matches).
	 */
	<T extends Addressed<T>> T of(RegClass.Known<T> classId);

	/**
	 * A catalog object that has both {@code oid} and {@code classId} specified,
	 * and can be looked up in the PostgreSQL catalogs (where it may, or may
	 * not, be found).
	 * @param <T> Specific subtype of Addressed that represents catalog objects
	 * with the given class id.
	 */
	interface Addressed<T extends Addressed<T>> extends CatalogObject
	{
		/**
		 * Returns the {@code classId} (which is an instance of
		 * {@link RegClass.Known RegClass.Known} of this addressed catalog
		 * object.
		 */
		RegClass.Known<T> classId();

		/**
		 * Whether a catalog object with this address in fact exists in
		 * the PostgreSQL catalogs.
		 *<p>
		 * Unlike {@link #isValid isValid()}, which depends only on the value
		 * of {@code oid()}, this reflects the result of a catalog lookup.
		 */
		boolean exists();

		/**
		 * Whether this catalog object is shared across all databases in the
		 * cluster.
		 *<p>
		 * Contrast {@link RegClass#isShared() isShared()}, a method found only
		 * on {@code RegClass}, which indicates whether that {@code RegClass}
		 * instance represents a shared relation. Catalog objects formed with
		 * that {@code RegClass} instance as their {@code classId} will have
		 * {@code shared() == true}, though the {@code RegClass} instance itself
		 * will have {@code shared() == false} (because it models a row in
		 * {@code pg_class} itself, a catalog that isn't shared).
		 * @return classId().isShared()
		 */
		default boolean shared()
		{
			return classId().isShared();
		}
	}

	/**
	 * Interface for an object that is regarded as a component of some, other,
	 * addressed catalog object, and is identified by that other object's
	 * {@code classId} and {@code oid} along with an integer {@code subId}.
	 *<p>
	 * The chief (only?) example is an {@link Attribute Attribute}, which is
	 * identified by the {@code classId} and {@code oid} of its containing
	 * relation, plus a {@code subId}.
	 */
	interface Component
	{
		int subId();
	}

	/**
	 * Interface for any catalog object that has a name, which can be
	 * an {@link Identifier.Simple Identifier.Simple} or an
	 * {@link Identifier.Operator Identifier.Operator}.
	 */
	interface Named<T extends Identifier.Unqualified<T>>
	{
		T name();
	}

	/**
	 * Interface for any catalog object that has a name and also a namespace
	 * or schema (an associated instance of {@link RegNamespace RegNamespace}).
	 */
	interface Namespaced<T extends Identifier.Unqualified<T>>
	extends Named<T>
	{
		RegNamespace namespace();

		default Identifier.Qualified<T> qualifiedName()
		{
			return name().withQualifier(namespaceName());
		}

		default Identifier.Simple namespaceName()
		{
			return namespace().name();
		}
	}

	/**
	 * Interface for any catalog object that has an owner (an associated
	 * instance of {@link RegRole RegRole}.
	 */
	interface Owned
	{
		RegRole owner();
	}

	/**
	 * Interface for any catalog object with an access control list
	 * (a list of some type of {@code Grant}).
	 * @param <T> The subtype of {@link Grant Grant} that applies to catalog
	 * objects of this type.
	 */
	interface AccessControlled<T extends Grant>
	{
		/**
		 * Simple list of direct grants.
		 *<p>
		 * For any T except {@code Grant.OnRole}, simply returns the list of
		 * grants directly found in this catalog object's ACL. When T is
		 * {@code Grant.OnRole}, this catalog object is a {@code RegRole}, and
		 * the result contains a {@code Grant.OnRole} for every role R that is
		 * directly a member of the role this catalog object represents; each
		 * such grant has {@code maySetRole()} by definition, and
		 * {@code mayExercisePrivileges()} if and only if R has {@code inherit}.
		 */
		List<T> grants();

		/**
		 * Computed list of (possibly transitive) grants to <em>grantee</em>.
		 *<p>
		 * For any T except {@code Grant.OnRole}, a list of grants to
		 * <em>grantee</em> assembled from: direct grants in this object's ACL
		 * to {@code PUBLIC}, or to <em>grantee</em>, or to any role R for which
		 * {@code R.grants(grantee).mayExercisePrivileges()} is true.
		 *<p>
		 * When T is {@code Grant.OnRole}, this catalog object is a
		 * {@code RegRole}, and the result contains a {@code Grant.OnRole} for
		 * which {@code maySetRole()} is true if a membership path from
		 * <em>grantee</em> to this role exists, and
		 * {@code mayExercisePrivileges()} is true if such a path exists using
		 * only roles with {@code inherit()} true. (The {@code inherit()} status
		 * of this object itself is not considered.)
		 */
		List<T> grants(RegRole grantee); // transitive closure when on RegRole
		// aclitem[] acl();
		// { Oid grantee; Oid grantor; AclMode bits; } see nodes/parsenodes.h
	}

	/**
	 * Interface representing any single {@code Grant} (or ACL item), a grant
	 * of some set of possible privileges, to some role, granted by some role.
	 */
	interface Grant
	{
		/**
		 * Role to which the accompanying privileges are granted.
		 *<p>
		 * There is no actual role named {@code public}, but there is
		 * a distinguished instance {@link RegRole.Grantee#PUBLIC PUBLIC} of
		 * {@link RegRole.Grantee RegRole.Grantee}.
		 */
		RegRole.Grantee to();

		/**
		 * Role responsible for granting these privileges.
		 */
		RegRole by();

		/**
		 * Subtype of {@code Grant} representing the privileges that may be
		 * granted on an attribute (or column).
		 */
		interface OnAttribute extends SELECT, INSERT, UPDATE, REFERENCES { }

		/**
		 * Subtype of {@code Grant} representing the privileges that may be
		 * granted on a class (or relation, table, view).
		 */
		interface OnClass
		extends OnAttribute, DELETE, TRUNCATE, TRIGGER, MAINTAIN { }

		/**
		 * Subtype of {@code Grant} representing the privileges that may be
		 * granted on a database.
		 */
		interface OnDatabase extends CONNECT, CREATE, CREATE_TEMP { }

		/**
		 * Subtype of {@code Grant} representing the privileges that may be
		 * granted on a namespace (or schema).
		 */
		interface OnNamespace extends CREATE, USAGE { }

		/**
		 * Subtype of {@code Grant} representing the privileges that may be
		 * granted on a configuration setting.
		 */
		interface OnSetting extends SET, ALTER_SYSTEM { }

		/**
		 * Subtype of {@code Grant} representing the grants (of membership in,
		 * and/or privileges of, other roles) that may be made to a role.
		 */
		interface OnRole extends Grant
		{
			boolean mayExercisePrivileges();
			boolean maySetRole();
			boolean mayAdmin();
		}
	}

	/**
	 * @hidden
	 */
	interface INSERT       extends Grant
	{
		boolean insertGranted();
		boolean insertGrantable();
	}

	/**
	 * @hidden
	 */
	interface SELECT       extends Grant
	{
		boolean selectGranted();
		boolean selectGrantable();
	}

	/**
	 * @hidden
	 */
	interface UPDATE       extends Grant
	{
		boolean updateGranted();
		boolean updateGrantable();
	}

	/**
	 * @hidden
	 */
	interface DELETE       extends Grant
	{
		boolean deleteGranted();
		boolean deleteGrantable();
	}

	/**
	 * @hidden
	 */
	interface TRUNCATE     extends Grant
	{
		boolean truncateGranted();
		boolean truncateGrantable();
	}

	/**
	 * @hidden
	 */
	interface REFERENCES   extends Grant
	{
		boolean referencesGranted();
		boolean referencesGrantable();
	}

	/**
	 * @hidden
	 */
	interface TRIGGER      extends Grant
	{
		boolean triggerGranted();
		boolean triggerGrantable();
	}

	/**
	 * @hidden
	 */
	interface EXECUTE      extends Grant
	{
		boolean executeGranted();
		boolean executeGrantable();
	}

	/**
	 * @hidden
	 */
	interface USAGE        extends Grant
	{
		boolean usageGranted();
		boolean usageGrantable();
	}

	/**
	 * @hidden
	 */
	interface CREATE       extends Grant
	{
		boolean createGranted();
		boolean createGrantable();
	}

	/**
	 * @hidden
	 */
	interface CREATE_TEMP  extends Grant
	{
		boolean create_tempGranted();
		boolean create_tempGrantable();
	}

	/**
	 * @hidden
	 */
	interface CONNECT      extends Grant
	{
		boolean connectGranted();
		boolean connectGrantable();
	}

	/**
	 * @hidden
	 */
	interface SET          extends Grant
	{
		boolean setGranted();
		boolean setGrantable();
	}

	/**
	 * @hidden
	 */
	interface ALTER_SYSTEM extends Grant
	{
		boolean alterSystemGranted();
		boolean alterSystemGrantable();
	}

	/**
	 * @hidden
	 */
	interface MAINTAIN     extends Grant
	{
		boolean maintainGranted();
		boolean maintainGrantable();
	}

	/**
	 * @hidden
	 */
	abstract class Factory
	{
		static final Factory INSTANCE;

		static
		{
			INSTANCE = ServiceLoader
				.load(Factory.class.getModule().getLayer(), Factory.class)
				.findFirst().orElseThrow(() -> new ServiceConfigurationError(
					"could not load PL/Java CatalogObject.Factory"));
		}

		static <T extends Addressed<T>>
			RegClass.Known<T> formClassId(int classId, Class<? extends T> clazz)
		{
			return INSTANCE.formClassIdImpl(classId, clazz);
		}

		static <T extends Addressed<T>>
			T formObjectId(RegClass.Known<T> classId, int objId)
		{
			return INSTANCE.formObjectIdImpl(classId, objId, v -> true);
		}

		static <T extends Addressed<T>>
			T formObjectId(
				RegClass.Known<T> classId, int objId, IntPredicate versionTest)
		{
			return INSTANCE.formObjectIdImpl(classId, objId, versionTest);
		}

		static Database currentDatabase(RegClass.Known<Database> classId)
		{
			return INSTANCE.currentDatabaseImpl(classId);
		}

		static RegRole.Grantee publicGrantee()
		{
			return INSTANCE.publicGranteeImpl();
		}

		protected abstract <T extends Addressed<T>>
			RegClass.Known<T> formClassIdImpl(
				int classId, Class<? extends T> clazz);

		protected abstract <T extends Addressed<T>>
			T formObjectIdImpl(
				RegClass.Known<T> classId, int objId, IntPredicate versionTest);

		protected abstract Database
			currentDatabaseImpl(RegClass.Known<Database> classId);

		protected abstract RegRole.Grantee publicGranteeImpl();

		protected abstract CharsetEncoding serverEncoding();
		protected abstract CharsetEncoding clientEncoding();
		protected abstract CharsetEncoding encodingFromOrdinal(int ordinal);
		protected abstract CharsetEncoding encodingFromName(String name);

		/*
		 * These magic numbers are hardcoded here inside the pljava-api project
		 * so they can be used in static initializers in API interfaces. The
		 * verification that they are the right magic numbers takes place in
		 * compilation of the pljava and pljava-so projects, where they are
		 * included from here, exported in JNI .h files, and compared using
		 * StaticAssertStmt to the corresponding values from PostgreSQL headers.
		 *
		 * Within groups here, numerical order is as good as any. When adding a
		 * constant here, add a corresponding CONFIRMCONST in ModelConstants.c.
		 */
		protected static final int         TypeRelationId = 1247;
		protected static final int    AttributeRelationId = 1249;
		protected static final int    ProcedureRelationId = 1255;
		protected static final int     RelationRelationId = 1259;
		protected static final int       AuthIdRelationId = 1260;
		protected static final int     DatabaseRelationId = 1262;
		protected static final int     LanguageRelationId = 2612;
		protected static final int    NamespaceRelationId = 2615;
		protected static final int     OperatorRelationId = 2617;
		protected static final int    ExtensionRelationId = 3079;
		protected static final int    CollationRelationId = 3456;
		protected static final int TSDictionaryRelationId = 3600;
		protected static final int     TSConfigRelationId = 3602;

		/*
		 * PG types good to have around because of corresponding JDBC types.
		 */
		protected static final int        BOOLOID =   16;
		protected static final int       BYTEAOID =   17;
		protected static final int        CHAROID =   18;
		protected static final int        INT8OID =   20;
		protected static final int        INT2OID =   21;
		protected static final int        INT4OID =   23;
		protected static final int         XMLOID =  142;
		protected static final int      FLOAT4OID =  700;
		protected static final int      FLOAT8OID =  701;
		protected static final int      BPCHAROID = 1042;
		protected static final int     VARCHAROID = 1043;
		protected static final int        DATEOID = 1082;
		protected static final int        TIMEOID = 1083;
		protected static final int   TIMESTAMPOID = 1114;
		protected static final int TIMESTAMPTZOID = 1184;
		protected static final int      TIMETZOID = 1266;
		protected static final int         BITOID = 1560;
		protected static final int      VARBITOID = 1562;
		protected static final int     NUMERICOID = 1700;

		/*
		 * PG types not mentioned in JDBC but bread-and-butter to PG devs.
		 */
		protected static final int        TEXTOID =   25;
		protected static final int     UNKNOWNOID =  705;
		protected static final int      RECORDOID = 2249;
		protected static final int     CSTRINGOID = 2275;
		protected static final int        VOIDOID = 2278;
		protected static final int     TRIGGEROID = 2279;

		/*
		 * PG types used in modeling PG types themselves.
		 */
		protected static final int          NAMEOID =   19;
		protected static final int       REGPROCOID =   24;
		protected static final int           OIDOID =   26;
		protected static final int  PG_NODE_TREEOID =  194;
		protected static final int       ACLITEMOID = 1033;
		protected static final int  REGPROCEDUREOID = 2202;
		protected static final int       REGOPEROID = 2203;
		protected static final int   REGOPERATOROID = 2204;
		protected static final int      REGCLASSOID = 2205;
		protected static final int       REGTYPEOID = 2206;
		protected static final int     REGCONFIGOID = 3734;
		protected static final int REGDICTIONARYOID = 3769;
		protected static final int  REGNAMESPACEOID = 4089;
		protected static final int       REGROLEOID = 4096;
		protected static final int  REGCOLLATIONOID = 4191;

		/*
		 * The well-known, pinned procedural languages.
		 */
		protected static final int INTERNALlanguageId = 12;
		protected static final int        ClanguageId = 13;
		protected static final int      SQLlanguageId = 14;

		/*
		 * The well-known, pinned namespaces.
		 */
		protected static final int PG_CATALOG_NAMESPACE = 11;
		protected static final int   PG_TOAST_NAMESPACE = 99;

		/*
		 * The well-known, pinned collations.
		 */
		protected static final int DEFAULT_COLLATION_OID = 100;
		protected static final int       C_COLLATION_OID = 950;
		protected static final int   POSIX_COLLATION_OID = 951;

		/*
		 * These magic numbers are assigned here to allow the various well-known
		 * PostgreSQL ResourceOwners to be retrieved without a proliferation of
		 * methods on the factory interface. These are arbitrary array indices,
		 * visible also to JNI code through the generated headers just as
		 * described above. The native initialization method may create,
		 * for example, an array of ByteBuffers that window the corresponding
		 * PostgreSQL globals, ordered according to these indices. The Java code
		 * implementing resourceOwner() can be ignorant of these specific values
		 * and simply use them to index the array. HOWEVER, it does know that
		 * the first one, index 0, refers to the current resource owner.
		 */
		protected static final int RSO_Current        = 0; // must be index 0
		protected static final int RSO_CurTransaction = 1;
		protected static final int RSO_TopTransaction = 2;
		protected static final int RSO_AuxProcess     = 3;

		protected abstract ResourceOwner resourceOwner(int which);

		/*
		 * Same as above but for the well-known PostgreSQL MemoryContexts.
		 * Again, the implementing code knows index 0 is for the current one.
		 */
		protected static final int MCX_CurrentMemory  = 0; // must be index 0
		protected static final int MCX_TopMemory      = 1;
		protected static final int MCX_Error          = 2;
		protected static final int MCX_Postmaster     = 3;
		protected static final int MCX_CacheMemory    = 4;
		protected static final int MCX_Message        = 5;
		protected static final int MCX_TopTransaction = 6;
		protected static final int MCX_CurTransaction = 7;
		protected static final int MCX_Portal         = 8;
		/*
		 * A long-lived, never-reset context created by PL/Java as a child of
		 * TopMemoryContext.
		 */
		protected static final int MCX_JavaMemory     = 9;

		protected abstract MemoryContext memoryContext(int which);

		protected abstract MemoryContext upperMemoryContext();
	}
}
