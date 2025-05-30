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
	 * that are needed. The native method in this class obtains a ByteBuffer
	 * windowing that static array.
	 *
	 * To detect fat-finger mistakes, the array will include alternating indices
	 * and values { IDX_SIZEOF_DATUM, SIZEOF_DATUM, ... }, so when windowed as
	 * an IntBuffer, get(2*IDX_FOO) should equal IDX_FOO and get(1 + 2*IDX_FOO)
	 * is then the value; this can be done without hairy preprocessor logic on
	 * the C side, and checked here (not statically, but still cheaply). C99's
	 * designated array initializers would offer a simpler, all-static approach,
	 * but PostgreSQL strives for C89 compatibility before PostgreSQL 12.
	 *
	 * As a practical matter, the sequence of IDX_... values is allowed to have
	 * gaps, so that new constants can be added as needed, coherently grouped,
	 * without requiring extensive renumbering of otherwise unaffected lines.
	 * The array cells remain consecutive, and this class simply tracks a gap
	 * between the IDX_... value and the physical position. This, of course,
	 * would complicate any move to C99 designated initializers.
	 *
	 * Starting with PostgreSQL 11, LLVM bitcode for the server might be found
	 * in $pkglibdir/bitcode/postgres, and that could one day pose opportunities
	 * for a PL/Java using an LLVM library, or depending on GraalVM, to access
	 * these values (and do much more) without this tedious hand coding. But for
	 * now, the goal is to support earlier versions and not require LLVM or
	 * GraalVM, and hope that the bootstrapping needed here does not become too
	 * burdensome.
	 */
	private static class Natives implements AutoCloseable
	{
		private IntBuffer b = _statics()
			.asReadOnlyBuffer().order(nativeOrder()).asIntBuffer();
		private int gap = 0;

		/**
		 * Returns the next constant from the windowed array.
		 *<p>
		 * The next constant is determined by the buffer's current position,
		 * not by <var>index</var>, which is only used for sanity checking:
		 * <ol>
		 * <li>adjusted by the current "gap", it should be half the buffer's
		 *     current position, and
		 * <li>unadjusted, it should equal the {@code int} read at
		 *     that position.
		 * </ol>
		 * The {@code int} read at the next consecutive position is returned.
		 * @param index the expected index of the next constant to be read
		 * @return the next constant
		 * @throws ConstantsError if any sanity check fails
		 */
		int get(int index)
		{
			try
			{
				if ( b.position() != (index - gap) << 1  ||  index != b.get() )
					throw new ConstantsError();
				return b.get();
			}
			catch ( Exception e )
			{
				throw (ConstantsError)new ConstantsError().initCause(e);
			}
		}

		/**
		 * Conforms the internal sanity checking to a gap in assigned indices.
		 *<p>
		 * The supplied <var>index</var> must be greater than that implied by
		 * the buffer's current position (and previously recorded gap, if any),
		 * and the difference is remembered as the new gap.
		 * @param index the assigned index of the next constant to be read
		 * @throws ConstantsError if the newly-computed gap is not larger than
		 * the remembered value
		 */
		void gap(int index)
		{
			int pos = b.position();
			assert 0 == (pos & 1); // expected as get() always advances by two
			index -= pos >>> 1;
			if ( index <= gap )
				throw new ConstantsError();
			gap = index;
		}

		@Override
		public void close()
		{
			if ( 0 < b.remaining() )
				throw new ConstantsError();
		}

		private static native ByteBuffer _statics();
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
	@Native public static final int SIZEOF_pg_attribute_atttypmod    = 4;
	@Native public static final int SIZEOF_pg_attribute_attbyval     = 1;
	@Native public static final int SIZEOF_pg_attribute_attalign     = 1;
	@Native public static final int SIZEOF_pg_attribute_attnotnull   = 1;
	@Native public static final int SIZEOF_pg_attribute_attisdropped = 1;

	@Native public static final int SIZEOF_CompactAttribute          = 16;

	@Native public static final int Anum_pg_extension_oid            = 1;
	@Native public static final int ExtensionOidIndexId              = 3080;

	@Native public static final int Anum_pg_trigger_oid              = 1;
	@Native public static final int TriggerOidIndexId                = 2702;

	@Native public static final int SIZEOF_ArrayType_ndim            = 4;
	@Native public static final int SIZEOF_ArrayType_dataoffset      = 4;
	@Native public static final int SIZEOF_ArrayType_elemtype        = 4;

	@Native public static final int OFFSET_ArrayType_ndim            = 0;
	@Native public static final int OFFSET_ArrayType_dataoffset      = 4;
	@Native public static final int OFFSET_ArrayType_elemtype        = 8;

	@Native public static final int OFFSET_ArrayType_DIMS            = 12;
	@Native public static final int SIZEOF_ArrayType_DIM             = 4;

	@Native public static final int SIZEOF_NodeTag                   = 4;
	@Native public static final int SIZEOF_Oid                       = 4;

	@Native public static final int SIZEOF_fcinfo_fncollation       = 4;
	@Native public static final int SIZEOF_fcinfo_isnull            = 1;
	@Native public static final int SIZEOF_fcinfo_nargs             = 2;

	/*
	 * These constants (which will be included in a generated header available
	 * to the C code) are (almost) indices into the 'statics' array where the
	 * various wanted values should be placed. Edits should keep them distinct
	 * consecutive small array indices within related groups; gaps are allowed
	 * (and encouraged) between groups, so additions can be made without mass
	 * renumbering. The get() method of Natives, used in the static initializer,
	 * will be checking for gaps or repeats; the gap() method must be called
	 * where each gap occurs, to advise what the next expected IDX_... value
	 * is to be.
	 */
	@Native private static final int IDX_PG_VERSION_NUM          = 0;

	@Native private static final int IDX_SIZEOF_DATUM            = 1;
	@Native private static final int IDX_SIZEOF_INT              = 2;
	@Native private static final int IDX_SIZEOF_LONG             = 3;
	@Native private static final int IDX_SIZEOF_SIZE             = 4;

	@Native private static final int IDX_ALIGNOF_SHORT           = 5;
	@Native private static final int IDX_ALIGNOF_INT             = 6;
	@Native private static final int IDX_ALIGNOF_DOUBLE          = 7;
	@Native private static final int IDX_MAXIMUM_ALIGNOF         = 8;

	@Native private static final int IDX_NAMEDATALEN             = 9;

	@Native private static final int IDX_SIZEOF_varatt_indirect  = 10;
	@Native private static final int IDX_SIZEOF_varatt_expanded  = 11;
	@Native private static final int IDX_SIZEOF_varatt_external  = 12;

	@Native private static final int IDX_OFFSET_Relation_rd_id   = 13;


	@Native private static final int IDX_HEAPTUPLESIZE           = 20;
	@Native private static final int IDX_OFFSET_TTS_NVALID       = 21;
	@Native private static final int IDX_SIZEOF_TTS_NVALID       = 22;

	@Native private static final int IDX_TTS_FLAG_EMPTY          = 23;
	@Native private static final int IDX_TTS_FLAG_FIXED          = 24;
	@Native private static final int IDX_OFFSET_TTS_FLAGS        = 25;

	/*
	 * Before PG 12, TTS had no flags field with bit flags, but instead
	 * distinct boolean (1-byte) fields.
	 */
	@Native private static final int IDX_OFFSET_TTS_EMPTY        = 26;
	@Native private static final int IDX_OFFSET_TTS_FIXED        = 27;
	@Native private static final int IDX_OFFSET_TTS_TABLEOID     = 28;

	@Native private static final int IDX_OFFSET_NullableDatum_isnull = 29;
	@Native private static final int IDX_SIZEOF_NullableDatum        = 30;

	@Native private static final int IDX_OFFSET_fcinfo_fncollation   = 31;
	@Native private static final int IDX_OFFSET_fcinfo_isnull        = 32;
	@Native private static final int IDX_OFFSET_fcinfo_nargs         = 33;
	@Native private static final int IDX_OFFSET_fcinfo_args          = 34;

	@Native private static final int IDX_OFFSET_Bitmapset_words      = 35;



	@Native private static final int IDX_OFFSET_TUPLEDESC_ATTRS      = 40;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDREFCOUNT = 41;
	@Native private static final int IDX_SIZEOF_TUPLEDESC_TDREFCOUNT = 42;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDTYPEID   = 43;
	@Native private static final int IDX_OFFSET_TUPLEDESC_TDTYPMOD   = 44;



	@Native private static final int IDX_SIZEOF_FORM_PG_ATTRIBUTE         = 50;
	@Native private static final int IDX_ATTRIBUTE_FIXED_PART_SIZE        = 51;
	@Native private static final int IDX_OFFSET_pg_attribute_atttypid     = 52;
	@Native private static final int IDX_OFFSET_pg_attribute_attlen       = 53;
	// available(1)
	@Native private static final int IDX_OFFSET_pg_attribute_atttypmod    = 55;
	@Native private static final int IDX_OFFSET_pg_attribute_attbyval     = 56;
	@Native private static final int IDX_OFFSET_pg_attribute_attalign     = 57;
	@Native private static final int IDX_OFFSET_pg_attribute_attnotnull   = 58;
	@Native private static final int IDX_OFFSET_pg_attribute_attisdropped = 59;



	@Native private static final int IDX_CLASS_TUPLE_SIZE          = 70;
	@Native private static final int IDX_Anum_pg_class_reltype     = 71;



	@Native private static final int IDX_SIZEOF_MCTX               = 80;
	@Native private static final int IDX_OFFSET_MCTX_isReset       = 81;
	@Native private static final int IDX_OFFSET_MCTX_mem_allocated = 82;
	@Native private static final int IDX_OFFSET_MCTX_parent        = 83;
	@Native private static final int IDX_OFFSET_MCTX_firstchild    = 84;
	@Native private static final int IDX_OFFSET_MCTX_prevchild     = 85;
	@Native private static final int IDX_OFFSET_MCTX_nextchild     = 86;
	@Native private static final int IDX_OFFSET_MCTX_name          = 87;
	@Native private static final int IDX_OFFSET_MCTX_ident         = 88;



	/*
	 * N_ACL_RIGHTS was stable for a long time, but changes in PG 15 and in 16
	 */
	@Native private static final int IDX_N_ACL_RIGHTS              = 100;
	@Native private static final int IDX_BITS_PER_BITMAPWORD       = 101;



	/*
	 * Tags of a select few PostgreSQL Node subtypes.
	 */
	@Native private static final int IDX_T_Invalid            = 110;
	@Native private static final int IDX_T_AggState           = 111;
	@Native private static final int IDX_T_CallContext        = 112;
	@Native private static final int IDX_T_EventTriggerData   = 113;
	@Native private static final int IDX_T_ReturnSetInfo      = 114;
	@Native private static final int IDX_T_TriggerData        = 115;
	@Native private static final int IDX_T_WindowAggState     = 116;
	@Native private static final int IDX_T_WindowObjectData   = 117;
	@Native private static final int IDX_T_Bitmapset          = 118;
	@Native private static final int IDX_T_ErrorSaveContext   = 119;



	/*
	 * Layout of the PostgreSQL Trigger structure that is supplied for a call
	 * on a trigger. The content comes from pg_trigger but the layout differs,
	 * so reading from it must be done without TupleTableSlot's help.
	 */
	@Native private static final int IDX_OFFSET_TRG_tgoid           = 150;
	@Native private static final int IDX_OFFSET_TRG_tgname          = 151;
	@Native private static final int IDX_OFFSET_TRG_tgfoid          = 152;
	@Native private static final int IDX_OFFSET_TRG_tgtype          = 153;
	@Native private static final int IDX_OFFSET_TRG_tgenabled       = 154;
	@Native private static final int IDX_OFFSET_TRG_tgisinternal    = 155;
	@Native private static final int IDX_OFFSET_TRG_tgisclone       = 156;
	@Native private static final int IDX_OFFSET_TRG_tgconstrrelid   = 157;
	@Native private static final int IDX_OFFSET_TRG_tgconstrindid   = 158;
	@Native private static final int IDX_OFFSET_TRG_tgconstraint    = 159;
	@Native private static final int IDX_OFFSET_TRG_tgdeferrable    = 160;
	@Native private static final int IDX_OFFSET_TRG_tginitdeferred  = 161;
	@Native private static final int IDX_OFFSET_TRG_tgnargs         = 162;
	@Native private static final int IDX_OFFSET_TRG_tgnattr         = 163;
	@Native private static final int IDX_OFFSET_TRG_tgattr          = 164;
	@Native private static final int IDX_OFFSET_TRG_tgargs          = 165;
	@Native private static final int IDX_OFFSET_TRG_tgqual          = 166;
	@Native private static final int IDX_OFFSET_TRG_tgoldtable      = 167;
	@Native private static final int IDX_OFFSET_TRG_tgnewtable      = 168;
	@Native private static final int IDX_SIZEOF_Trigger             = 169;



	/*
	 * Layouts of fcinfo->context / fcinfo->resultinfo structures.
	 */
	@Native private static final int IDX_OFFSET_TRGD_tg_event       = 180;
	@Native private static final int IDX_OFFSET_TRGD_tg_relation    = 181;
	@Native private static final int IDX_OFFSET_TRGD_tg_trigtuple   = 182;
	@Native private static final int IDX_OFFSET_TRGD_tg_newtuple    = 183;
	@Native private static final int IDX_OFFSET_TRGD_tg_trigger     = 184;
	@Native private static final int IDX_OFFSET_TRGD_tg_updatedcols = 185;

	@Native private static final int IDX_OFFSET_RSI_allowedModes    = 186;
	@Native private static final int IDX_OFFSET_RSI_isDone          = 187;
	@Native private static final int IDX_OFFSET_RSI_returnMode      = 188;
	@Native private static final int IDX_SIZEOF_RSI_isDone          = 189;
	@Native private static final int IDX_SIZEOF_RSI_returnMode      = 190;



	/*
	 * Identifiers of different caches in PG's syscache, utils/cache/syscache.c.
	 * As upstream adds new caches, the enum is kept in alphabetical order, so
	 * they belong in this section to have their effective values picked up.
	 */
	@Native private static final int IDX_ATTNUM         = 500;
	@Native private static final int IDX_AUTHMEMMEMROLE = 501;
	@Native private static final int IDX_AUTHMEMROLEMEM = 502;
	@Native private static final int IDX_AUTHOID        = 503;
	@Native private static final int IDX_COLLOID        = 504;
	@Native private static final int IDX_DATABASEOID    = 505;
	@Native private static final int IDX_LANGOID        = 506;
	@Native private static final int IDX_NAMESPACEOID   = 507;
	@Native private static final int IDX_OPEROID        = 508;
	@Native private static final int IDX_PROCOID        = 509;
	@Native private static final int IDX_RELOID         = 510;
	@Native private static final int IDX_TSCONFIGOID    = 511;
	@Native private static final int IDX_TSDICTOID      = 512;
	@Native private static final int IDX_TYPEOID        = 513;
	@Native private static final int IDX_CONSTROID      = 514;
	@Native private static final int IDX_TRFOID         = 515;
	@Native private static final int IDX_TRFTYPELANG    = 516;
	@Native private static final int IDX_AMOID          = 517;
	@Native private static final int IDX_TABLESPACEOID  = 518;

	@Native private static final int IDX_FOREIGNDATAWRAPPEROID = 519;
	@Native private static final int IDX_FOREIGNSERVEROID      = 520;
	@Native private static final int IDX_FOREIGNTABLEREL       = 521;



	/*
	 * These public statics are the values of interest, set at class
	 * initialization time by reading them from the buffer managed by Natives.
	 */

	/**
	 * Numeric PostgreSQL version compiled in at build time.
	 */
	public static final int PG_VERSION_NUM;

	public static final int SIZEOF_DATUM;
	/*
	 * In backporting, can be useful when the git history shows something was
	 * always of 'int' type, so it doesn't need a dedicated SIZEOF_FOO, but does
	 * need to notice if a platform has an unexpected 'int' width. Same for
	 * 'long', widely used in SPI and having an unexpected width on Windows.
	 */
	public static final int SIZEOF_INT;
	public static final int SIZEOF_LONG;
	public static final int SIZEOF_SIZE;

	public static final int ALIGNOF_SHORT;
	public static final int ALIGNOF_INT;
	public static final int ALIGNOF_DOUBLE;
	public static final int MAXIMUM_ALIGNOF;

	public static final short NAMEDATALEN;

	public static final int SIZEOF_varatt_indirect;
	public static final int SIZEOF_varatt_expanded;
	public static final int SIZEOF_varatt_external;

	public static final int OFFSET_Relation_rd_id;


	public static final int HEAPTUPLESIZE;
	public static final int OFFSET_TTS_NVALID;
	public static final int SIZEOF_TTS_NVALID; // int or int16 per pg version

	public static final int TTS_FLAG_EMPTY;
	public static final int TTS_FLAG_FIXED;
	public static final int OFFSET_TTS_FLAGS;

	public static final int OFFSET_TTS_EMPTY;
	public static final int OFFSET_TTS_FIXED;

	public static final int OFFSET_TTS_TABLEOID; // NOCONSTANT unless PG >= 12

	public static final int OFFSET_NullableDatum_isnull;
	public static final int SIZEOF_NullableDatum;

	public static final int OFFSET_fcinfo_fncollation;
	public static final int OFFSET_fcinfo_isnull;
	public static final int OFFSET_fcinfo_nargs;
	public static final int OFFSET_fcinfo_args;

	public static final int OFFSET_Bitmapset_words;



	/**
	 * Offset to where per-attribute data in a {@code TupleDesc} begins.
	 *<p>
	 * Prior to PostgreSQL 18, this was the offset of a member actually named
	 * {@code attrs}, and was exactly where the first {@code Form_pg_attribute}
	 * would be found. As of PostgreSQL 18, this will actually be the offset of
	 * a member named {@code compact_attrs}, and the first
	 * {@code Form_pg_attribute} must be found at this offset plus the number of
	 * attributes times {@code SIZEOF_CompactAttribute}.
	 */
	public static final int OFFSET_TUPLEDESC_ATTRS;
	public static final int OFFSET_TUPLEDESC_TDREFCOUNT;
	public static final int SIZEOF_TUPLEDESC_TDREFCOUNT;
	public static final int OFFSET_TUPLEDESC_TDTYPEID;
	public static final int OFFSET_TUPLEDESC_TDTYPMOD;



	public static final int SIZEOF_FORM_PG_ATTRIBUTE;
	public static final int ATTRIBUTE_FIXED_PART_SIZE;
	public static final int OFFSET_pg_attribute_atttypid;
	public static final int OFFSET_pg_attribute_attlen;
	// available(1)
	public static final int OFFSET_pg_attribute_atttypmod;
	public static final int OFFSET_pg_attribute_attbyval;
	public static final int OFFSET_pg_attribute_attalign;
	public static final int OFFSET_pg_attribute_attnotnull;
	public static final int OFFSET_pg_attribute_attisdropped;



	public static final int CLASS_TUPLE_SIZE;
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
	 * The number of meaningful rights bits in an ACL bitmask, imported by
	 * AclItem.
	 */
	public static final int N_ACL_RIGHTS;
	public static final int BITS_PER_BITMAPWORD; // DatumUtils



	/*
	 * Tags of a select few PostgreSQL Node subtypes.
	 */
	public static final int T_Invalid;
	public static final int T_AggState;
	public static final int T_CallContext;
	public static final int T_EventTriggerData;
	public static final int T_ReturnSetInfo;
	public static final int T_TriggerData;
	public static final int T_WindowAggState;
	public static final int T_WindowObjectData;
	public static final int T_Bitmapset;
	public static final int T_ErrorSaveContext;



	/*
	 * Layout of the PostgreSQL Trigger structure that is supplied for a call
	 * on a trigger.
	 */
	public static final int OFFSET_TRG_tgoid;
	public static final int OFFSET_TRG_tgname;
	public static final int OFFSET_TRG_tgfoid;
	public static final int OFFSET_TRG_tgtype;
	public static final int OFFSET_TRG_tgenabled;
	public static final int OFFSET_TRG_tgisinternal;
	public static final int OFFSET_TRG_tgisclone;
	public static final int OFFSET_TRG_tgconstrrelid;
	public static final int OFFSET_TRG_tgconstrindid;
	public static final int OFFSET_TRG_tgconstraint;
	public static final int OFFSET_TRG_tgdeferrable;
	public static final int OFFSET_TRG_tginitdeferred;
	public static final int OFFSET_TRG_tgnargs;
	public static final int OFFSET_TRG_tgnattr;
	public static final int OFFSET_TRG_tgattr;
	public static final int OFFSET_TRG_tgargs;
	public static final int OFFSET_TRG_tgqual;
	public static final int OFFSET_TRG_tgoldtable;
	public static final int OFFSET_TRG_tgnewtable;
	public static final int SIZEOF_Trigger;



	/*
	 * Layouts of fcinfo->context structures.
	 */
	public static final int OFFSET_TRGD_tg_event;
	public static final int OFFSET_TRGD_tg_relation;
	public static final int OFFSET_TRGD_tg_trigtuple;
	public static final int OFFSET_TRGD_tg_newtuple;
	public static final int OFFSET_TRGD_tg_trigger;
	public static final int OFFSET_TRGD_tg_updatedcols;

	public static final int OFFSET_RSI_allowedModes;
	public static final int OFFSET_RSI_isDone;
	public static final int OFFSET_RSI_returnMode;
	public static final int SIZEOF_RSI_isDone;
	public static final int SIZEOF_RSI_returnMode;



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
	public static final int CONSTROID;       // ConstraintImpl
	public static final int TRFOID;          // TransformImpl
	public static final int TRFTYPELANG;     // "
	public static final int AMOID;           // AccessMethodImpl
	public static final int TABLESPACEOID;   // TablespaceImpl

	public static final int FOREIGNDATAWRAPPEROID; // ForeignDataWrapperImpl
	public static final int FOREIGNSERVEROID;      // ForeignServerImpl
	public static final int FOREIGNTABLEREL;       // RegClassImpl



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
		try ( Natives n = new Natives() )
		{
			PG_VERSION_NUM    = n.get(IDX_PG_VERSION_NUM);

			SIZEOF_DATUM      = n.get(IDX_SIZEOF_DATUM);
			SIZEOF_INT        = n.get(IDX_SIZEOF_INT);
			SIZEOF_LONG       = n.get(IDX_SIZEOF_LONG);
			SIZEOF_SIZE       = n.get(IDX_SIZEOF_SIZE);

			ALIGNOF_SHORT     = n.get(IDX_ALIGNOF_SHORT);
			ALIGNOF_INT       = n.get(IDX_ALIGNOF_INT);
			ALIGNOF_DOUBLE    = n.get(IDX_ALIGNOF_DOUBLE);
			MAXIMUM_ALIGNOF   = n.get(IDX_MAXIMUM_ALIGNOF);

			int c             = n.get(IDX_NAMEDATALEN);
			NAMEDATALEN       = (short)c;
			assert c == NAMEDATALEN;

			SIZEOF_varatt_indirect = n.get(IDX_SIZEOF_varatt_indirect);
			SIZEOF_varatt_expanded = n.get(IDX_SIZEOF_varatt_expanded);
			SIZEOF_varatt_external = n.get(IDX_SIZEOF_varatt_external);

			OFFSET_Relation_rd_id  = n.get(IDX_OFFSET_Relation_rd_id);



			n.gap(IDX_HEAPTUPLESIZE);
			HEAPTUPLESIZE     = n.get(IDX_HEAPTUPLESIZE);
			OFFSET_TTS_NVALID = n.get(IDX_OFFSET_TTS_NVALID);
			SIZEOF_TTS_NVALID = n.get(IDX_SIZEOF_TTS_NVALID);

			TTS_FLAG_EMPTY    = n.get(IDX_TTS_FLAG_EMPTY);
			TTS_FLAG_FIXED    = n.get(IDX_TTS_FLAG_FIXED);
			OFFSET_TTS_FLAGS  = n.get(IDX_OFFSET_TTS_FLAGS);

			OFFSET_TTS_EMPTY  = n.get(IDX_OFFSET_TTS_EMPTY);
			OFFSET_TTS_FIXED  = n.get(IDX_OFFSET_TTS_FIXED);

			OFFSET_TTS_TABLEOID = n.get(IDX_OFFSET_TTS_TABLEOID);

			OFFSET_NullableDatum_isnull= n.get(IDX_OFFSET_NullableDatum_isnull);
			SIZEOF_NullableDatum       = n.get(IDX_SIZEOF_NullableDatum);

			OFFSET_fcinfo_fncollation = n.get(IDX_OFFSET_fcinfo_fncollation);
			OFFSET_fcinfo_isnull      = n.get(IDX_OFFSET_fcinfo_isnull);
			OFFSET_fcinfo_nargs       = n.get(IDX_OFFSET_fcinfo_nargs);
			OFFSET_fcinfo_args        = n.get(IDX_OFFSET_fcinfo_args);

			OFFSET_Bitmapset_words    = n.get(IDX_OFFSET_Bitmapset_words);



			n.gap(IDX_OFFSET_TUPLEDESC_ATTRS);
			OFFSET_TUPLEDESC_ATTRS     = n.get(IDX_OFFSET_TUPLEDESC_ATTRS);
			OFFSET_TUPLEDESC_TDREFCOUNT= n.get(IDX_OFFSET_TUPLEDESC_TDREFCOUNT);
			SIZEOF_TUPLEDESC_TDREFCOUNT= n.get(IDX_SIZEOF_TUPLEDESC_TDREFCOUNT);
			OFFSET_TUPLEDESC_TDTYPEID  = n.get(IDX_OFFSET_TUPLEDESC_TDTYPEID);
			OFFSET_TUPLEDESC_TDTYPMOD  = n.get(IDX_OFFSET_TUPLEDESC_TDTYPMOD);



			n.gap(IDX_SIZEOF_FORM_PG_ATTRIBUTE);
			SIZEOF_FORM_PG_ATTRIBUTE   = n.get(IDX_SIZEOF_FORM_PG_ATTRIBUTE);
			ATTRIBUTE_FIXED_PART_SIZE  = n.get(IDX_ATTRIBUTE_FIXED_PART_SIZE);
			OFFSET_pg_attribute_atttypid
				= n.get(IDX_OFFSET_pg_attribute_atttypid);
			OFFSET_pg_attribute_attlen
				= n.get(IDX_OFFSET_pg_attribute_attlen);
			n.gap(IDX_OFFSET_pg_attribute_atttypmod); // available(1)
			OFFSET_pg_attribute_atttypmod
				= n.get(IDX_OFFSET_pg_attribute_atttypmod);
			OFFSET_pg_attribute_attbyval
				= n.get(IDX_OFFSET_pg_attribute_attbyval);
			OFFSET_pg_attribute_attalign
				= n.get(IDX_OFFSET_pg_attribute_attalign);
			OFFSET_pg_attribute_attnotnull
				= n.get(IDX_OFFSET_pg_attribute_attnotnull);
			OFFSET_pg_attribute_attisdropped
				= n.get(IDX_OFFSET_pg_attribute_attisdropped);



			n.gap(IDX_CLASS_TUPLE_SIZE);
			CLASS_TUPLE_SIZE          = n.get(IDX_CLASS_TUPLE_SIZE);
			Anum_pg_class_reltype     = n.get(IDX_Anum_pg_class_reltype);



			n.gap(IDX_SIZEOF_MCTX);
			SIZEOF_MCTX               = n.get(IDX_SIZEOF_MCTX);
			OFFSET_MCTX_isReset       = n.get(IDX_OFFSET_MCTX_isReset);
			OFFSET_MCTX_mem_allocated = n.get(IDX_OFFSET_MCTX_mem_allocated);
			OFFSET_MCTX_parent        = n.get(IDX_OFFSET_MCTX_parent);
			OFFSET_MCTX_firstchild    = n.get(IDX_OFFSET_MCTX_firstchild);
			OFFSET_MCTX_prevchild     = n.get(IDX_OFFSET_MCTX_prevchild);
			OFFSET_MCTX_nextchild     = n.get(IDX_OFFSET_MCTX_nextchild);
			OFFSET_MCTX_name          = n.get(IDX_OFFSET_MCTX_name);
			OFFSET_MCTX_ident         = n.get(IDX_OFFSET_MCTX_ident);



			n.gap(IDX_N_ACL_RIGHTS);
			N_ACL_RIGHTS          = n.get(IDX_N_ACL_RIGHTS);
			BITS_PER_BITMAPWORD   = n.get(IDX_BITS_PER_BITMAPWORD);



			n.gap(IDX_T_Invalid);
			T_Invalid            = n.get(IDX_T_Invalid);
			T_AggState           = n.get(IDX_T_AggState);
			T_CallContext        = n.get(IDX_T_CallContext);
			T_EventTriggerData   = n.get(IDX_T_EventTriggerData);
			T_ReturnSetInfo      = n.get(IDX_T_ReturnSetInfo);
			T_TriggerData        = n.get(IDX_T_TriggerData);
			T_WindowAggState     = n.get(IDX_T_WindowAggState);
			T_WindowObjectData   = n.get(IDX_T_WindowObjectData);
			T_Bitmapset          = n.get(IDX_T_Bitmapset);
			T_ErrorSaveContext   = n.get(IDX_T_ErrorSaveContext);



			n.gap(IDX_OFFSET_TRG_tgoid);
			OFFSET_TRG_tgoid           = n.get(IDX_OFFSET_TRG_tgoid);
			OFFSET_TRG_tgname          = n.get(IDX_OFFSET_TRG_tgname);
			OFFSET_TRG_tgfoid          = n.get(IDX_OFFSET_TRG_tgfoid);
			OFFSET_TRG_tgtype          = n.get(IDX_OFFSET_TRG_tgtype);
			OFFSET_TRG_tgenabled       = n.get(IDX_OFFSET_TRG_tgenabled);
			OFFSET_TRG_tgisinternal    = n.get(IDX_OFFSET_TRG_tgisinternal);
			OFFSET_TRG_tgisclone       = n.get(IDX_OFFSET_TRG_tgisclone);
			OFFSET_TRG_tgconstrrelid   = n.get(IDX_OFFSET_TRG_tgconstrrelid);
			OFFSET_TRG_tgconstrindid   = n.get(IDX_OFFSET_TRG_tgconstrindid);
			OFFSET_TRG_tgconstraint    = n.get(IDX_OFFSET_TRG_tgconstraint);
			OFFSET_TRG_tgdeferrable    = n.get(IDX_OFFSET_TRG_tgdeferrable);
			OFFSET_TRG_tginitdeferred  = n.get(IDX_OFFSET_TRG_tginitdeferred);
			OFFSET_TRG_tgnargs         = n.get(IDX_OFFSET_TRG_tgnargs);
			OFFSET_TRG_tgnattr         = n.get(IDX_OFFSET_TRG_tgnattr);
			OFFSET_TRG_tgattr          = n.get(IDX_OFFSET_TRG_tgattr);
			OFFSET_TRG_tgargs          = n.get(IDX_OFFSET_TRG_tgargs);
			OFFSET_TRG_tgqual          = n.get(IDX_OFFSET_TRG_tgqual);
			OFFSET_TRG_tgoldtable      = n.get(IDX_OFFSET_TRG_tgoldtable);
			OFFSET_TRG_tgnewtable      = n.get(IDX_OFFSET_TRG_tgnewtable);
			SIZEOF_Trigger             = n.get(IDX_SIZEOF_Trigger);



			n.gap(IDX_OFFSET_TRGD_tg_event);
			OFFSET_TRGD_tg_event       = n.get(IDX_OFFSET_TRGD_tg_event);
			OFFSET_TRGD_tg_relation    = n.get(IDX_OFFSET_TRGD_tg_relation);
			OFFSET_TRGD_tg_trigtuple   = n.get(IDX_OFFSET_TRGD_tg_trigtuple);
			OFFSET_TRGD_tg_newtuple    = n.get(IDX_OFFSET_TRGD_tg_newtuple);
			OFFSET_TRGD_tg_trigger     = n.get(IDX_OFFSET_TRGD_tg_trigger);
			OFFSET_TRGD_tg_updatedcols = n.get(IDX_OFFSET_TRGD_tg_updatedcols);

			OFFSET_RSI_allowedModes    = n.get(IDX_OFFSET_RSI_allowedModes);
			OFFSET_RSI_isDone          = n.get(IDX_OFFSET_RSI_isDone);
			OFFSET_RSI_returnMode      = n.get(IDX_OFFSET_RSI_returnMode);
			SIZEOF_RSI_isDone          = n.get(IDX_SIZEOF_RSI_isDone);
			SIZEOF_RSI_returnMode      = n.get(IDX_SIZEOF_RSI_returnMode);



			n.gap(IDX_ATTNUM);
			ATTNUM         = n.get(IDX_ATTNUM);
			AUTHMEMMEMROLE = n.get(IDX_AUTHMEMMEMROLE);
			AUTHMEMROLEMEM = n.get(IDX_AUTHMEMROLEMEM);
			AUTHOID        = n.get(IDX_AUTHOID);
			COLLOID        = n.get(IDX_COLLOID);
			DATABASEOID    = n.get(IDX_DATABASEOID);
			LANGOID        = n.get(IDX_LANGOID);
			NAMESPACEOID   = n.get(IDX_NAMESPACEOID);
			OPEROID        = n.get(IDX_OPEROID);
			PROCOID        = n.get(IDX_PROCOID);
			RELOID         = n.get(IDX_RELOID);
			TSCONFIGOID    = n.get(IDX_TSCONFIGOID);
			TSDICTOID      = n.get(IDX_TSDICTOID);
			TYPEOID        = n.get(IDX_TYPEOID);
			CONSTROID      = n.get(IDX_CONSTROID);
			TRFOID         = n.get(IDX_TRFOID);
			TRFTYPELANG    = n.get(IDX_TRFTYPELANG);
			AMOID          = n.get(IDX_AMOID);
			TABLESPACEOID  = n.get(IDX_TABLESPACEOID);

			FOREIGNDATAWRAPPEROID = n.get(IDX_FOREIGNDATAWRAPPEROID);
			FOREIGNSERVEROID      = n.get(IDX_FOREIGNSERVEROID);
			FOREIGNTABLEREL       = n.get(IDX_FOREIGNTABLEREL);



		}
	}

	/**
	 * Distinguished subclass of {@link ExceptionInInitializerError} thrown
	 * if the build-time constants cannot be loaded.
	 */
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
