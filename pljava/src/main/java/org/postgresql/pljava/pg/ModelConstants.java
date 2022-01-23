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

import org.postgresql.pljava.annotation.BaseUDT.Alignment;
import org.postgresql.pljava.annotation.BaseUDT.Storage;

import static org.postgresql.pljava.internal.UncheckedException.unchecked;

import java.lang.annotation.Native;

import java.nio.ByteBuffer;
import static java.nio.ByteOrder.nativeOrder;
import java.nio.IntBuffer;

import java.sql.SQLException;

/**
 * Supply static values that can vary between PostgreSQL versions/builds.
 */
public abstract class ModelConstants
{
	/*
	 * C code will contain a static array of int initialized to the values
	 * that are needed. This native method will return a ByteBuffer windowing
	 * that static array.
	 *
	 * To detect fat-finger mistakes, the array will include alternating indices
	 * and values { IDX_SIZEOF_DATUM, SIZEOF_DATUM, ... }, so when windowed as
	 * an IntBuffer, get(2*IDX_FOO) should equal IDX_FOO and get(1 + 2*IDX_FOO)
	 * is then the value; this can be done without hairy preprocessor logic on
	 * the C side, and checked here (not statically, but still cheaply). C99's
	 * designated array initializers would offer a simpler, all-static approach,
	 * but PostgreSQL strives for C89 compatibility before PostgreSQL 12.
	 *
	 * Starting with PostgreSQL 11, LLVM bitcode for the server might be found
	 * in $pkglibdir/bitcode/postgres, and that could one day pose opportunities
	 * for a PL/Java using an LLVM library, or depending on GraalVM, to access
	 * these values (and do much more) without this tedious hand coding. But for
	 * now, the goal is to support earlier versions and not require LLVM or
	 * GraalVM, and hope that the bootstrapping needed here does not become too
	 * burdensome.
	 */
	private static class Natives
	{
		static native ByteBuffer _statics();
	}

	/*
	 * These constants (which will be included in a generated header available
	 * to the C code) have historically stable values that aren't expected to
	 * change. The C code simply asserts statically at build time that they
	 * are right. If a new PG version conflicts with the assertion, move the
	 * constant from here to the list further below of constants that get their
	 * values *from* the C code at class initialization time. (When doing that,
	 * also check uses of the constant for any assumptions that might no longer
	 * hold.)
	 */

	@Native public static final int PG_SQL_ASCII                     = 0;
	@Native public static final int PG_UTF8                          = 6;
	@Native public static final int PG_LATIN1                        = 8;
	@Native public static final int PG_ENCODING_BE_LAST              = 34;

	@Native public static final int VARHDRSZ                         = 4;
	@Native public static final int VARHDRSZ_EXTERNAL                = 2;
	@Native public static final byte VARTAG_INDIRECT                 = 1;
	@Native public static final byte VARTAG_EXPANDED_RO              = 2;
	@Native public static final byte VARTAG_EXPANDED_RW              = 3;
	@Native public static final byte VARTAG_ONDISK                   = 18;

	@Native public static final int   Anum_pg_attribute_attname      = 2;

	@Native public static final int SIZEOF_pg_attribute_atttypid     = 4;
	@Native public static final int SIZEOF_pg_attribute_attlen       = 2;
	@Native public static final int SIZEOF_pg_attribute_attcacheoff  = 4;
	@Native public static final int SIZEOF_pg_attribute_atttypmod    = 4;
	@Native public static final int SIZEOF_pg_attribute_attbyval     = 1;
	@Native public static final int SIZEOF_pg_attribute_attalign     = 1;
	@Native public static final int SIZEOF_pg_attribute_attnotnull   = 1;
	@Native public static final int SIZEOF_pg_attribute_attisdropped = 1;

	@Native public static final int Anum_pg_extension_oid            = 1;
	@Native public static final int ExtensionOidIndexId              = 3080;

	@Native public static final int SIZEOF_ArrayType_ndim            = 4;
	@Native public static final int SIZEOF_ArrayType_dataoffset      = 4;
	@Native public static final int SIZEOF_ArrayType_elemtype        = 4;

	@Native public static final int OFFSET_ArrayType_ndim            = 0;
	@Native public static final int OFFSET_ArrayType_dataoffset      = 4;
	@Native public static final int OFFSET_ArrayType_elemtype        = 8;

	@Native public static final int OFFSET_ArrayType_DIMS            = 12;
	@Native public static final int SIZEOF_ArrayType_DIM             = 4;

	/*
	 * These constants (which will be included in a generated header available
	 * to the C code) are the indices into the 'statics' array where the various
	 * wanted values should be placed. Edits should keep them consecutive
	 * distinct small array indices; the checked() function in the static
	 * initializer will be checking for gaps or repeats.
	 */
	@Native private static final int IDX_SIZEOF_DATUM            = 0;
	@Native private static final int IDX_SIZEOF_SIZE             = 1;

	@Native private static final int IDX_ALIGNOF_SHORT           = 2;
	@Native private static final int IDX_ALIGNOF_INT             = 3;
	@Native private static final int IDX_ALIGNOF_DOUBLE          = 4;
	@Native private static final int IDX_MAXIMUM_ALIGNOF         = 5;

	@Native private static final int IDX_NAMEDATALEN             = 6;

	@Native private static final int IDX_SIZEOF_varatt_indirect  = 7;
	@Native private static final int IDX_SIZEOF_varatt_expanded  = 8;
	@Native private static final int IDX_SIZEOF_varatt_external  = 9;

	@Native private static final int IDX_OFFSET_TTS_NVALID       = 10;
	@Native private static final int IDX_SIZEOF_TTS_NVALID       = 11;

	@Native private static final int IDX_TTS_FLAG_EMPTY          = 12;
	@Native private static final int IDX_TTS_FLAG_FIXED          = 13;
	@Native private static final int IDX_OFFSET_TTS_FLAGS        = 14;

	/*
	 * Before PG 12, TTS had no flags field with bit flags, but instead
	 * distinct boolean (1-byte) fields.
	 */
	@Native private static final int IDX_OFFSET_TTS_EMPTY     = 15;
	@Native private static final int IDX_OFFSET_TTS_FIXED     = 16;
	@Native private static final int IDX_OFFSET_TTS_TABLEOID  = 17;

	@Native private static final int IDX_SIZEOF_FORM_PG_ATTRIBUTE  = 18;
	@Native private static final int IDX_ATTRIBUTE_FIXED_PART_SIZE = 19;
	@Native private static final int IDX_CLASS_TUPLE_SIZE          = 20;
	@Native private static final int IDX_HEAPTUPLESIZE             = 21;

	@Native private static final int IDX_OFFSET_TUPLEDESC_ATTRS      = 22;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDREFCOUNT = 23;
	@Native private static final int IDX_SIZEOF_TUPLEDESC_TDREFCOUNT = 24;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDTYPEID   = 25;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDTYPMOD   = 26;

	@Native private static final int IDX_OFFSET_pg_attribute_atttypid     = 27;
	@Native private static final int IDX_OFFSET_pg_attribute_attlen       = 28;
	@Native private static final int IDX_OFFSET_pg_attribute_attcacheoff  = 29;
	@Native private static final int IDX_OFFSET_pg_attribute_atttypmod    = 30;
	@Native private static final int IDX_OFFSET_pg_attribute_attbyval     = 31;
	@Native private static final int IDX_OFFSET_pg_attribute_attalign     = 32;
	@Native private static final int IDX_OFFSET_pg_attribute_attnotnull   = 33;
	@Native private static final int IDX_OFFSET_pg_attribute_attisdropped = 34;

	@Native private static final int IDX_Anum_pg_class_reltype     = 35;

	@Native private static final int IDX_SIZEOF_MCTX               = 36;
	@Native private static final int IDX_OFFSET_MCTX_isReset       = 37;
	@Native private static final int IDX_OFFSET_MCTX_mem_allocated = 38;
	@Native private static final int IDX_OFFSET_MCTX_parent        = 39;
	@Native private static final int IDX_OFFSET_MCTX_firstchild    = 40;
	@Native private static final int IDX_OFFSET_MCTX_prevchild     = 41;
	@Native private static final int IDX_OFFSET_MCTX_nextchild     = 42;
	@Native private static final int IDX_OFFSET_MCTX_name          = 43;
	@Native private static final int IDX_OFFSET_MCTX_ident         = 44;

	/*
	 * Identifiers of different caches in PG's syscache, utils/cache/syscache.c.
	 * As upstream adds new caches, the enum is kept in alphabetical order, so
	 * they belong in this section to have their effective values picked up.
	 */
	@Native private static final int IDX_ATTNUM         = 45;
	@Native private static final int IDX_AUTHMEMMEMROLE = 46;
	@Native private static final int IDX_AUTHMEMROLEMEM = 47;
	@Native private static final int IDX_AUTHOID        = 48;
	@Native private static final int IDX_COLLOID        = 49;
	@Native private static final int IDX_DATABASEOID    = 50;
	@Native private static final int IDX_LANGOID        = 51;
	@Native private static final int IDX_NAMESPACEOID   = 52;
	@Native private static final int IDX_OPEROID        = 53;
	@Native private static final int IDX_PROCOID        = 54;
	@Native private static final int IDX_RELOID         = 55;
	@Native private static final int IDX_TSCONFIGOID    = 56;
	@Native private static final int IDX_TSDICTOID      = 57;
	@Native private static final int IDX_TYPEOID        = 58;

	/*
	 * These public statics are the values of interest, set at class
	 * initialization time by reading them from the buffer returned by _statics.
	 */

	public static final int SIZEOF_DATUM;
	public static final int SIZEOF_SIZE;

	public static final int ALIGNOF_SHORT;
	public static final int ALIGNOF_INT;
	public static final int ALIGNOF_DOUBLE;
	public static final int MAXIMUM_ALIGNOF;

	public static final short NAMEDATALEN;

	public static final int SIZEOF_varatt_indirect;
	public static final int SIZEOF_varatt_expanded;
	public static final int SIZEOF_varatt_external;

	public static final int OFFSET_TTS_NVALID;
	public static final int SIZEOF_TTS_NVALID; // int or int16 per pg version

	public static final int TTS_FLAG_EMPTY;
	public static final int TTS_FLAG_FIXED;
	public static final int OFFSET_TTS_FLAGS;

	public static final int OFFSET_TTS_EMPTY;
	public static final int OFFSET_TTS_FIXED;

	public static final int OFFSET_TTS_TABLEOID; // NOCONSTANT unless PG >= 12

	public static final int SIZEOF_FORM_PG_ATTRIBUTE;
	public static final int ATTRIBUTE_FIXED_PART_SIZE;
	public static final int CLASS_TUPLE_SIZE;
	public static final int HEAPTUPLESIZE;

	public static final int OFFSET_TUPLEDESC_ATTRS;
	public static final int OFFSET_TUPLEDESC_TDREFCOUNT;
	public static final int SIZEOF_TUPLEDESC_TDREFCOUNT;
	public static final int OFFSET_TUPLEDESC_TDTYPEID;
	public static final int OFFSET_TUPLEDESC_TDTYPMOD;

	public static final int OFFSET_pg_attribute_atttypid;
	public static final int OFFSET_pg_attribute_attlen;
	public static final int OFFSET_pg_attribute_attcacheoff;
	public static final int OFFSET_pg_attribute_atttypmod;
	public static final int OFFSET_pg_attribute_attbyval;
	public static final int OFFSET_pg_attribute_attalign;
	public static final int OFFSET_pg_attribute_attnotnull;
	public static final int OFFSET_pg_attribute_attisdropped;

	public static final int Anum_pg_class_reltype;

	public static final int SIZEOF_MCTX;
	public static final int OFFSET_MCTX_isReset;
	public static final int OFFSET_MCTX_mem_allocated; // since PG 13
	public static final int OFFSET_MCTX_parent;
	public static final int OFFSET_MCTX_firstchild;
	public static final int OFFSET_MCTX_prevchild;     // since PG 9.6
	public static final int OFFSET_MCTX_nextchild;
	public static final int OFFSET_MCTX_name;
	public static final int OFFSET_MCTX_ident;         // since PG 11

	/*
	 * These identify different caches in the PostgreSQL syscache.
	 * The indicated classes import them.
	 */
	public static final int ATTNUM;          // AttributeImpl
	public static final int AUTHMEMMEMROLE;  // RegRoleImpl
	public static final int AUTHMEMROLEMEM;  // "
	public static final int AUTHOID;         // "
	public static final int COLLOID;         // RegCollationImpl
	public static final int DATABASEOID;     // DatabaseImpl
	public static final int LANGOID;         // ProceduralLanguageImpl
	public static final int NAMESPACEOID;    // RegNamespaceImpl
	public static final int OPEROID;         // RegOperatorImpl
	public static final int PROCOID;         // RegProcedureImpl
	public static final int RELOID;          // RegClassImpl
	public static final int TSCONFIGOID;     // RegConfigImpl
	public static final int TSDICTOID;       // RegDictionaryImpl
	public static final int TYPEOID;         // RegTypeImpl

	/**
	 * Value supplied for one of these constants when built in a version of PG
	 * that does not define it.
	 *<p>
	 * Clearly not useful if the value could be valid for the constant
	 * in question.
	 */
	@Native public static final int NOCONSTANT = -1;

	static
	{
		IntBuffer b =
			Natives._statics()
				.asReadOnlyBuffer().order(nativeOrder()).asIntBuffer();

		SIZEOF_DATUM      = checked(b, IDX_SIZEOF_DATUM);
		SIZEOF_SIZE       = checked(b, IDX_SIZEOF_SIZE);

		ALIGNOF_SHORT     = checked(b, IDX_ALIGNOF_SHORT);
		ALIGNOF_INT       = checked(b, IDX_ALIGNOF_INT);
		ALIGNOF_DOUBLE    = checked(b, IDX_ALIGNOF_DOUBLE);
		MAXIMUM_ALIGNOF   = checked(b, IDX_MAXIMUM_ALIGNOF);

		int n             = checked(b, IDX_NAMEDATALEN);
		NAMEDATALEN       = (short)n;
		assert n == NAMEDATALEN;

		SIZEOF_varatt_indirect = checked(b, IDX_SIZEOF_varatt_indirect);
		SIZEOF_varatt_expanded = checked(b, IDX_SIZEOF_varatt_expanded);
		SIZEOF_varatt_external = checked(b, IDX_SIZEOF_varatt_external);

		OFFSET_TTS_NVALID = checked(b, IDX_OFFSET_TTS_NVALID);
		SIZEOF_TTS_NVALID = checked(b, IDX_SIZEOF_TTS_NVALID);

		TTS_FLAG_EMPTY    = checked(b, IDX_TTS_FLAG_EMPTY);
		TTS_FLAG_FIXED    = checked(b, IDX_TTS_FLAG_FIXED);
		OFFSET_TTS_FLAGS  = checked(b, IDX_OFFSET_TTS_FLAGS);

		OFFSET_TTS_EMPTY  = checked(b, IDX_OFFSET_TTS_EMPTY);
		OFFSET_TTS_FIXED  = checked(b, IDX_OFFSET_TTS_FIXED);

		OFFSET_TTS_TABLEOID = checked(b, IDX_OFFSET_TTS_TABLEOID);

		SIZEOF_FORM_PG_ATTRIBUTE   = checked(b, IDX_SIZEOF_FORM_PG_ATTRIBUTE);
		ATTRIBUTE_FIXED_PART_SIZE  = checked(b, IDX_ATTRIBUTE_FIXED_PART_SIZE);
		CLASS_TUPLE_SIZE           = checked(b, IDX_CLASS_TUPLE_SIZE);
		HEAPTUPLESIZE              = checked(b, IDX_HEAPTUPLESIZE);

		OFFSET_TUPLEDESC_ATTRS     = checked(b, IDX_OFFSET_TUPLEDESC_ATTRS);
		OFFSET_TUPLEDESC_TDREFCOUNT= checked(b,IDX_OFFSET_TUPLEDESC_TDREFCOUNT);
		SIZEOF_TUPLEDESC_TDREFCOUNT= checked(b,IDX_SIZEOF_TUPLEDESC_TDREFCOUNT);
		OFFSET_TUPLEDESC_TDTYPEID  = checked(b, IDX_OFFSET_TUPLEDESC_TDTYPEID);
		OFFSET_TUPLEDESC_TDTYPMOD  = checked(b, IDX_OFFSET_TUPLEDESC_TDTYPMOD);

		OFFSET_pg_attribute_atttypid
			= checked(b, IDX_OFFSET_pg_attribute_atttypid);
		OFFSET_pg_attribute_attlen
			= checked(b, IDX_OFFSET_pg_attribute_attlen);
		OFFSET_pg_attribute_attcacheoff
			= checked(b, IDX_OFFSET_pg_attribute_attcacheoff);
		OFFSET_pg_attribute_atttypmod
			= checked(b, IDX_OFFSET_pg_attribute_atttypmod);
		OFFSET_pg_attribute_attbyval
			= checked(b, IDX_OFFSET_pg_attribute_attbyval);
		OFFSET_pg_attribute_attalign
			= checked(b, IDX_OFFSET_pg_attribute_attalign);
		OFFSET_pg_attribute_attnotnull
			= checked(b, IDX_OFFSET_pg_attribute_attnotnull);
		OFFSET_pg_attribute_attisdropped
			= checked(b, IDX_OFFSET_pg_attribute_attisdropped);

		Anum_pg_class_reltype     = checked(b, IDX_Anum_pg_class_reltype);

		SIZEOF_MCTX               = checked(b, IDX_SIZEOF_MCTX);
		OFFSET_MCTX_isReset       = checked(b, IDX_OFFSET_MCTX_isReset);
		OFFSET_MCTX_mem_allocated = checked(b, IDX_OFFSET_MCTX_mem_allocated);
		OFFSET_MCTX_parent        = checked(b, IDX_OFFSET_MCTX_parent);
		OFFSET_MCTX_firstchild    = checked(b, IDX_OFFSET_MCTX_firstchild);
		OFFSET_MCTX_prevchild     = checked(b, IDX_OFFSET_MCTX_prevchild);
		OFFSET_MCTX_nextchild     = checked(b, IDX_OFFSET_MCTX_nextchild);
		OFFSET_MCTX_name          = checked(b, IDX_OFFSET_MCTX_name);
		OFFSET_MCTX_ident         = checked(b, IDX_OFFSET_MCTX_ident);

		ATTNUM         = checked(b, IDX_ATTNUM);
		AUTHMEMMEMROLE = checked(b, IDX_AUTHMEMMEMROLE);
		AUTHMEMROLEMEM = checked(b, IDX_AUTHMEMROLEMEM);
		AUTHOID        = checked(b, IDX_AUTHOID);
		COLLOID        = checked(b, IDX_COLLOID);
		DATABASEOID    = checked(b, IDX_DATABASEOID);
		LANGOID        = checked(b, IDX_LANGOID);
		NAMESPACEOID   = checked(b, IDX_NAMESPACEOID);
		OPEROID        = checked(b, IDX_OPEROID);
		PROCOID        = checked(b, IDX_PROCOID);
		RELOID         = checked(b, IDX_RELOID);
		TSCONFIGOID    = checked(b, IDX_TSCONFIGOID);
		TSDICTOID      = checked(b, IDX_TSDICTOID);
		TYPEOID        = checked(b, IDX_TYPEOID);

		if ( 0 != b.remaining() )
			throw new ConstantsError();
	}

	private static int checked(IntBuffer b, int index)
	{
		try
		{
			if ( b.position() != index << 1  ||  index != b.get() )
				throw new ConstantsError();
			return b.get();
		}
		catch ( Exception e )
		{
			throw (ConstantsError)new ConstantsError().initCause(e);
		}
	}

	static class ConstantsError extends ExceptionInInitializerError
	{
		ConstantsError()
		{
			super("PL/Java native constants jumbled; " +
				"are jar and shared object same version?");
		}
	}

	/*
	 * Some static methods used by more than one model class, here because they
	 * are sort of related to constants. For example, Alignment appears both in
	 * RegType and in Attribute.
	 */

	static Alignment alignmentFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'c': return Alignment.CHAR;
		case (byte)'s': return Alignment.INT2;
		case (byte)'i': return Alignment.INT4;
		case (byte)'d': return Alignment.DOUBLE;
		}
		throw unchecked(new SQLException(
			"unrecognized alignment '" + (char)b + "' in catalog", "XX000"));
	}

	static int alignmentModulus(Alignment a)
	{
		switch ( a )
		{
		case   CHAR: return 1;
		case   INT2: return ALIGNOF_SHORT;
		case   INT4: return ALIGNOF_INT;
		case DOUBLE: return ALIGNOF_DOUBLE;
		}
		throw unchecked(new SQLException(
			"expected alignment, got " + a, "XX000"));
	}

	static Storage storageFromCatalog(byte b)
	{
		switch ( b )
		{
		case (byte)'x': return Storage.EXTENDED;
		case (byte)'e': return Storage.EXTERNAL;
		case (byte)'m': return Storage.MAIN;
		case (byte)'p': return Storage.PLAIN;
		}
		throw unchecked(new SQLException(
			"unrecognized storage '" + (char)b + "' in catalog", "XX000"));
	}
}
