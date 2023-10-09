/*
 * Copyright (c) 2022-2023 Tada AB and other contributors, as listed below.
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

import java.sql.SQLType;
import java.sql.SQLXML;

import org.postgresql.pljava.model.CatalogObject.*;

import static org.postgresql.pljava.model.CatalogObject.Factory.*;

import org.postgresql.pljava.model.RegProcedure.Memo;

import org.postgresql.pljava.annotation.BaseUDT.Alignment;
import org.postgresql.pljava.annotation.BaseUDT.PredefinedCategory; // javadoc
import org.postgresql.pljava.annotation.BaseUDT.Storage;

import org.postgresql.pljava.sqlgen.Lexicals.Identifier; // javadoc
import org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple;

/**
 * Model of a PostgreSQL data type, as defined in the system catalogs.
 *<p>
 * This class also has static final fields for a selection of commonly used
 * {@code RegType}s, such as those that correspond to types mentioned in JDBC,
 * and others that are just ubiquitous when working in PostgreSQL in general,
 * or are used in this model package.
 *<p>
 * An instance of {@code RegType} also implements the JDBC
 * {@link SQLType SQLType} interface, with the intention that it could be used
 * with a suitably-aware JDBC implementation to identify any type available
 * in PostgreSQL.
 *<p>
 * A type can have a 'modifier' (think {@code NUMERIC(4)} versus plain
 * {@code NUMERIC}). In PostgreSQL's C code, a type oid and modifier have to
 * be passed around in tandem. Here, you apply
 * {@link #modifier(int) modifier(int)} to the unmodified {@code RegType} and
 * obtain a distinct {@code RegType} instance incorporating the modifier.
 */
public interface RegType
extends
	Addressed<RegType>, Namespaced<Simple>, Owned, AccessControlled<USAGE>,
	SQLType
{
	RegClass.Known<RegType> CLASSID =
		formClassId(TypeRelationId, RegType.class);

	/*
	 * PG types good to have around because of corresponding JDBC types.
	 */
	RegType        BOOL = formObjectId(CLASSID,        BOOLOID);
	RegType       BYTEA = formObjectId(CLASSID,       BYTEAOID);
	/**
	 * The PostgreSQL type {@code "char"} (the quotes are needed to distinguish
	 * it from the different SQL type named {@code CHAR}), which is an eight-bit
	 * signed value with no associated character encoding (though it is often
	 * used in the catalogs with ASCII-letter values as an ersatz enum).
	 *<p>
	 * It can be mapped to the JDBC type {@code TINYINT}, or Java {@code byte}.
	 */
	RegType        CHAR = formObjectId(CLASSID,        CHAROID);
	RegType        INT8 = formObjectId(CLASSID,        INT8OID);
	RegType        INT2 = formObjectId(CLASSID,        INT2OID);
	RegType        INT4 = formObjectId(CLASSID,        INT4OID);
	RegType         XML = formObjectId(CLASSID,         XMLOID);
	RegType      FLOAT4 = formObjectId(CLASSID,      FLOAT4OID);
	RegType      FLOAT8 = formObjectId(CLASSID,      FLOAT8OID);
	/**
	 * "Blank-padded CHAR", the PostgreSQL type that corresponds to the SQL
	 * standard {@code CHAR} (spelled without quotes) type.
	 */
	RegType      BPCHAR = formObjectId(CLASSID,      BPCHAROID);
	RegType     VARCHAR = formObjectId(CLASSID,     VARCHAROID);
	RegType        DATE = formObjectId(CLASSID,        DATEOID);
	RegType        TIME = formObjectId(CLASSID,        TIMEOID);
	RegType   TIMESTAMP = formObjectId(CLASSID,   TIMESTAMPOID);
	RegType TIMESTAMPTZ = formObjectId(CLASSID, TIMESTAMPTZOID);
	RegType      TIMETZ = formObjectId(CLASSID,      TIMETZOID);
	RegType         BIT = formObjectId(CLASSID,         BITOID);
	RegType      VARBIT = formObjectId(CLASSID,      VARBITOID);
	RegType     NUMERIC = formObjectId(CLASSID,     NUMERICOID);

	/*
	 * PG types not mentioned in JDBC but bread-and-butter to PG devs.
	 */
	RegType        TEXT = formObjectId(CLASSID,    TEXTOID);
	RegType     UNKNOWN = formObjectId(CLASSID, UNKNOWNOID);
	RegType      RECORD = formObjectId(CLASSID,  RECORDOID);
	RegType     CSTRING = formObjectId(CLASSID, CSTRINGOID);
	RegType        VOID = formObjectId(CLASSID,    VOIDOID);
	RegType     TRIGGER = formObjectId(CLASSID, TRIGGEROID);

	/*
	 * PG types used in modeling PG types themselves.
	 */
	RegType          NAME = formObjectId(CLASSID,          NAMEOID);
	RegType       REGPROC = formObjectId(CLASSID,       REGPROCOID);
	RegType           OID = formObjectId(CLASSID,           OIDOID);
	RegType  PG_NODE_TREE = formObjectId(CLASSID,  PG_NODE_TREEOID);
	RegType       ACLITEM = formObjectId(CLASSID,       ACLITEMOID);
	RegType  REGPROCEDURE = formObjectId(CLASSID,  REGPROCEDUREOID);
	RegType       REGOPER = formObjectId(CLASSID,       REGOPEROID);
	RegType   REGOPERATOR = formObjectId(CLASSID,   REGOPERATOROID);
	RegType      REGCLASS = formObjectId(CLASSID,      REGCLASSOID);
	RegType       REGTYPE = formObjectId(CLASSID,       REGTYPEOID);
	RegType     REGCONFIG = formObjectId(CLASSID,     REGCONFIGOID);
	RegType REGDICTIONARY = formObjectId(CLASSID, REGDICTIONARYOID);
	RegType  REGNAMESPACE = formObjectId(CLASSID,  REGNAMESPACEOID);
	RegType       REGROLE = formObjectId(CLASSID,       REGROLEOID);
	RegType  REGCOLLATION = formObjectId(CLASSID,  REGCOLLATIONOID);

	enum Type { BASE, COMPOSITE, DOMAIN, ENUM, PSEUDO, RANGE, MULTIRANGE }

	interface TypeInput extends Memo<TypeInput> { }
	interface TypeOutput extends Memo<TypeOutput> { }
	interface TypeReceive extends Memo<TypeReceive> { }
	interface TypeSend extends Memo<TypeSend> { }
	interface TypeModifierInput extends Memo<TypeModifierInput> { }
	interface TypeModifierOutput extends Memo<TypeModifierOutput> { }
	interface TypeAnalyze extends Memo<TypeAnalyze> { }
	interface TypeSubscript extends Memo<TypeSubscript> { }

	/**
	 * Interface additionally implemented by an instance that represents a type
	 * (such as the PostgreSQL polymorphic pseudotypes or the even wilder "any"
	 * type) needing resolution to an actual type used at a given call site.
	 */
	interface Unresolved extends RegType
	{
		/**
		 * Returns true, indicating resolution to an actual type is needed.
		 */
		@Override
		default boolean needsResolution()
		{
			return true;
		}
	}

	/**
	 * Whether this instance represents a type (such as the PostgreSQL
	 * polymorphic pseudotypes or the even wilder "any" type) needing resolution
	 * to an actual type used at a given call site.
	 *<p>
	 * This information does not come from the {@code pg_type} catalog, but
	 * simply reflects PostgreSQL-version-specific knowledge of which types
	 * require such treatment.
	 *<p>
	 * This default implementation returns false.
	 * @see Unresolved#needsResolution
	 */
	default boolean needsResolution()
	{
		return false;
	}

	short length();
	boolean byValue();
	Type type();
	/**
	 * A one-character code representing the type's 'category'.
	 *<p>
	 * Custom categories are possible, so not every value here need correspond
	 * to a {@link PredefinedCategory PredefinedCategory}, but common ones will,
	 * and can be 'decoded' with {@link PredefinedCategory#valueOf(char)}.
	 */
	char category();
	boolean preferred();
	boolean defined();
	byte delimiter();
	RegClass relation();
	RegType element();
	RegType array();
	RegProcedure<TypeInput> input();
	RegProcedure<TypeOutput> output();
	RegProcedure<TypeReceive> receive();
	RegProcedure<TypeSend> send();
	RegProcedure<TypeModifierInput> modifierInput();
	RegProcedure<TypeModifierOutput> modifierOutput();
	RegProcedure<TypeAnalyze> analyze();
	RegProcedure<TypeSubscript> subscript();
	Alignment alignment();
	Storage storage();
	boolean notNull();
	RegType baseType();
	int dimensions();
	RegCollation collation();
	SQLXML defaultBin();
	String defaultText();
	RegType modifier(int typmod);

	/**
	 * Returns the {@code RegType} for this type with no modifier, if this
	 * instance has one.
	 *<p>
	 * If not, simply returns {@code this}.
	 */
	RegType withoutModifier();

	/**
	 * Returns the modifier if this instance has one, else -1.
	 */
	int modifier();

	/**
	 * The corresponding {@link TupleDescriptor TupleDescriptor}, non-null only
	 * for composite types.
	 */
	TupleDescriptor.Interned tupleDescriptor();

	/**
	 * The name of this type as a {@code String}, as the JDBC
	 * {@link SQLType SQLType} interface requires.
	 *<p>
	 * The string produced here is as would be produced by
	 * {@link Identifier#deparse deparse(StandardCharsets.UTF_8)} applied to
	 * the result of {@link #qualifiedName qualifiedName()}.
	 * The returned string may include double-quote marks, which affect its case
	 * sensitivity and the characters permitted within it. If an application is
	 * not required to use this method for JDBC compatibility, it can avoid
	 * needing to fuss with those details by using {@code qualifiedName}
	 * instead.
	 */
	@Override
	default String getName()
	{
		return qualifiedName().toString();
	}

	/**
	 * A string identifying the "vendor" for which the type name and number here
	 * are meaningful, as the JDBC {@link SQLType SQLType} interface requires.
	 *<p>
	 * The JDBC API provides that the result "typically is the package name for
	 * this vendor", and this method returns {@code org.postgresql} as
	 * a constant string.
	 *<p>
	 * Note, however, that every type that is defined in the current PostgreSQL
	 * database can be represented by an instance of this interface, whether
	 * built in to PostgreSQL, installed with an extension, or user-defined.
	 * Therefore, not every instance with this "vendor" string can be assumed
	 * to be a type known to all PostgreSQL databases. Moreover, even if
	 * the same extension-provided or user-defined type is present in different
	 * PostgreSQL databases, it need not be installed with the same
	 * {@link #qualifiedName qualifiedName} in each, and will almost certainly
	 * have different object IDs, so {@link #getName getName} and
	 * {@link #getVendorTypeNumber getVendorTypeNumber} may not in general
	 * identify the same type across unrelated PostgreSQL databases.
	 */
	@Override
	default String getVendor()
	{
		return "org.postgresql";
	}

	/**
	 * A vendor-specific type number identifying this type, as the JDBC
	 * {@link SQLType SQLType} interface requires.
	 *<p>
	 * This implementation returns the {@link #oid oid} of the type in
	 * the current database. However, except for the subset of types that are
	 * built in to PostgreSQL with oid values that are fixed, the result of this
	 * method should only be relied on to identify a type within the current
	 * database.
	 */
	@Override
	default Integer getVendorTypeNumber()
	{
		return oid();
	}
}
