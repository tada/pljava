/*
 * Copyright (c) 2015-2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.sql.SQLData; // referred to in javadoc

/**
 * Annotation on a PL/Java class to form a User Defined Type that will
 * become a new PostgreSQL <a href=
'http://www.postgresql.org/docs/current/static/extend-type-system.html'
>base type</a>.
 *<p>
 * A class marked with this annotation must implement {@link SQLData}, and will
 * use the methods of that interface to map between its own state and an
 * internal form that PostgreSQL will store. For that to work, it must also have
 * a public, no-argument constructor.
 *<p>
 * It must also have a public, static
 * {@code parse(String value, String typeName)} method, and the ubiquitous Java
 * {@link Object#toString toString} method, which will be used to map between
 * its own state and an externally-usable, text representation. Note that, while
 * it may inherit some sort of {@code toString} method from a superclass, there
 * is a special requirement on {@code toString} for a class that will be a base
 * UDT: the method must produce exactly what the {@code parse} method can parse.
 *<p>
 * A type will be created in SQL with the {@link #name name} and, if specified,
 * {@link #schema schema} from this annotation. If not specified, the type will
 * be created with no explicit schema qualification (that is, it will go into
 * whatever schema heads the {@code search_path} at the time the commands are
 * executed), and the name will be the simple name of the annotated class.
 *<p>
 * Four functions will be declared in SQL, corresponding to the
 * {@code parse}, {@code toString}, {@link SQLData#readSQL readSQL}, and
 * {@link SQLData#writeSQL writeSQL} methods of the class. By default, the SQL
 * names given to them will use the schema, if any, given here for the class,
 * with the names based on the type name given or defaulted here, with
 * {@code _in}, {@code _out}, {@code _recv}, and {@code _send} suffixed,
 * respectively. All of those defaults (and other properties of the functions)
 * can be individually adjusted by adding {@link Function @Function} annotations
 * on a method's declaration in this class.
 *<p>
 * Other static methods in the class may be exported as SQL functions by
 * marking them with {@code @Function} in the usual way, and will not have any
 * special treatment on account of being in a UDT class. Those function
 * declarations will be correctly ordered before or after this type's, in common
 * cases such as when this type appears in their signatures, or the type must
 * refer to the functions (as it must for
 * {@link #typeModifierInput typeModifierInput} or
 * {@link #typeModifierOutput typeModifierOutput} functions, for example).
 * In a case that the automatic ordering does not handle correctly,
 * appropriate {@link #provides provides}/{@link #requires requires} labels can
 * be used in the {@code @Function} annotations and this annotation, to make
 * the order come out right.
 */
@Target(ElementType.TYPE) @Retention(RetentionPolicy.CLASS) @Documented
public @interface BaseUDT
{
	/**
	 * The supported alignment constraints for the type's internal
	 * representation.
	 */
	enum Alignment { CHAR, INT2, INT4, DOUBLE }

	/**
	 * The supported <a href=
'http://www.postgresql.org/docs/current/static/storage-toast.html'
>TOAST strategies</a> for the type's stored representation.
	 * Only {@code PLAIN} is applicable to fixed-length types.
	 */
	enum Storage
	{
		/**
		 * Never compressed or stored out-of-line.
		 */
		PLAIN,

		/**
		 * Can be moved out-of-line but not compressed.
		 */
		EXTERNAL,

		/**
		 * Can be compressed, and moved out-of-line if still too big.
		 */
		EXTENDED,

		/**
		 * Can be compressed, but moved out-of-line only if there is no other
		 * way to make the containing tuple fit a page.
		 */
		MAIN
	}

	/**
	 * The <a href=
'https://www.postgresql.org/docs/12/catalog-pg-type.html#CATALOG-TYPCATEGORY-TABLE'
>type categories</a> that are predefined in PostgreSQL.
	 *<p>
	 * This enumeration is not used as the type of the
	 * {@link #category category} element below, because PostgreSQL allows use
	 * of other single-ASCII-character category codes for custom purposes.
	 * Therefore, the annotation can be any such character. PostgreSQL reserves
	 * all of the upper-case ASCII letters to represent current or future
	 * predefined categories, and this enumeration allows mapping between those
	 * and their more readable names.
	 *<p>
	 * When one of the predefined categories is wanted for the
	 * {@link #category category} element, the corresponding character constant
	 * in {@link Code PredefinedCategory.Code} can be used in the annotation as
	 * a more readable alternative to the one-character code.
	 */
	enum PredefinedCategory
	{
		ARRAY      (Code.ARRAY),
		BOOLEAN    (Code.BOOLEAN),
		COMPOSITE  (Code.COMPOSITE),
		DATETIME   (Code.DATETIME),
		ENUM       (Code.ENUM),
		GEOMETRIC  (Code.GEOMETRIC),
		NETWORK    (Code.NETWORK),
		NUMERIC    (Code.NUMERIC),
		PSEUDOTYPE (Code.PSEUDOTYPE),
		RANGE      (Code.RANGE),
		STRING     (Code.STRING),
		TIMESPAN   (Code.TIMESPAN),
		USER       (Code.USER),
		BITSTRING  (Code.BITSTRING),
		UNKNOWN    (Code.UNKNOWN),
		INTERNAL   (Code.INTERNAL);

		private final char code;

		PredefinedCategory(char code)
		{
			this.code = code;
		}

		/**
		 * Return this category's single-character code.
		 */
		public char code()
		{
			return code;
		}

		/**
		 * Return the {@code PredefinedCategory} corresponding to a
		 * single-character code as found in the system catalogs, or null
		 * if the character represents a custom category (or a predefined one in
		 * a PostgreSQL version newer than this class).
		 */
		public static PredefinedCategory valueOf(char code)
		{
			for ( PredefinedCategory c : values() )
				if ( c.code == code )
					return c;
			return null;
		}

		/**
		 * Character constants corresponding to the predefined categories,
		 * for use in the {@link #category} annotation element.
		 */
		public interface Code
		{
			char ARRAY      = 'A';
			char BOOLEAN    = 'B';
			char COMPOSITE  = 'C';
			char DATETIME   = 'D';
			char ENUM       = 'E';
			char GEOMETRIC  = 'G';
			char NETWORK    = 'I';
			char NUMERIC    = 'N';
			char PSEUDOTYPE = 'P';
			char RANGE      = 'R';
			char STRING     = 'S';
			char TIMESPAN   = 'T';
			char USER       = 'U';
			char BITSTRING  = 'V';
			char UNKNOWN    = 'X';
			char INTERNAL   = 'Z';
		}
	}

	/**
	 * Name of the new type in SQL, if it is not to be the simple name of
	 * the class. By default, the class name will be used, subject to
	 * PostgreSQL's normal case-folding and case-insensitive matching.
	 */
	String name() default "";

	/**
	 * Schema in which the new type (and, by default, its
	 * input/output/send/receive functions) should be declared.
	 * If not given, the names will not be schema qualified, and will be
	 * created in the schema at the head of the {@code search_path} at
	 * creation time.
	 */
	String schema() default "";

	/**
	 * One or more arbitrary labels that will be considered 'provided' by the
	 * object carrying this annotation. The deployment descriptor will be
	 * generated in such an order that other objects that 'require' labels
	 * 'provided' by this come later in the output for install actions, and
	 * earlier for remove actions.
	 */
	String[] provides() default {};

	/**
	 * One or more arbitrary labels that will be considered 'required' by the
	 * object carrying this annotation. The deployment descriptor will be
	 * generated in such an order that other objects that 'provide' labels
	 * 'required' by this come earlier in the output for install actions, and
	 * later for remove actions.
	 */
	String[] requires() default {};

	/**
	 * The {@code <implementor name>} to be used around SQL code generated
	 * for this type (and for its in/out/recv/send functions, if not
	 * overridden for them). Defaults to {@code PostgreSQL}. Set explicitly
	 * to {@code ""} to emit code not wrapped in an
	 * {@code <implementor block>}.
	 */
	String implementor() default "";

	/**
	 * A comment to be associated with the type. If left to default,
	 * and the Java class has a doc comment, its first sentence will be used.
	 * If an empty string is explicitly given, no comment will be set.
	 */
	String comment() default "";

	/**
	 * Name, possibly schema-qualified, of a function to parse type-modifier
	 * strings for this type. Such a function may be implemented in any language
	 * as long as it can accept a single {@code cstring[]} parameter and return
	 * a single non-negative {@code int4}. A static Java method may be used,
	 * following the conventions for a normal PL/Java function.
	 *<p>
	 * Even if the method is defined on the UDT class marked by this annotation,
	 * it is not automatically found or used. It will need its own
	 * {@link Function} annotation giving it a name, and this annotation must
	 * refer to it by that name to ensure the SQL is generated in the right
	 * order.
	 */
	String typeModifierInput() default "";

	/**
	 * Name, possibly schema-qualified, of a function to format internal
	 * type-modifier codes for display. If it is not provided, PostgreSQL will
	 * simply display numeric modifier codes in parentheses following a type
	 * name. Such a function may be implemented in any language
	 * as long as it can accept a single {@code int4} parameter and return
	 * a {@code cstring} (<em>not</em> a {@code cstring[]}, as you might expect
	 * by analogy to {@link #typeModifierInput typeModifierInput}). A static
	 * Java method may be used, following the conventions for a normal PL/Java
	 * function.
	 *<p>
	 * Even if the method is defined on the UDT class marked by this annotation,
	 * it is not automatically found or used. It will need its own
	 * {@link Function} annotation giving it a name, and this annotation must
	 * refer to it by that name to ensure the SQL is generated in the right
	 * order.
	 */
	String typeModifierOutput() default "";

	/**
	 * Name, possibly schema-qualified, of a function to gather custom
	 * statistics for this type. It must be implemented in a language allowing
	 * a parameter of type {@code internal}, and return a {@code boolean}.
	 * The details of the necessary API are in <a href=
'http://git.postgresql.org/gitweb/?p=postgresql.git;a=blob;f=src/include/commands/vacuum.h'
>{@code vacuum.h}</a>.
	 *<p>
	 * Even if the method is defined on the UDT class marked by this annotation,
	 * it is not automatically found or used. It will need its own
	 * {@link Function} annotation giving it a name, and this annotation must
	 * refer to it by that name to ensure the SQL is generated in the right
	 * order.
	 */
	String analyze() default "";

	/**
	 * Length of the type's internal representation, positive for a fixed
	 * length. {@code VARIABLE} is assumed if this is omitted.
	 */
	int internalLength() default -1;

	/**
	 * If true, indicates that the contents are passed by value rather than by
	 * reference, only possible for fixed-length types no wider than the
	 * PostgreSQL {@code Datum}.
	 */
	boolean passedByValue() default false;

	/**
	 * Alignment constraint for the type in memory.
	 * {@code INT4} is the default and, for variable-length types, also the
	 * minimum (variable-length types begin with an {@code INT4} header).
	 */
	Alignment alignment() default Alignment.INT4;

	/**
	 * The <a href=
'http://www.postgresql.org/docs/current/static/storage-toast.html'
>TOAST strategy</a> for the type's stored representation.
	 * Only {@code PLAIN} is applicable to fixed-length types.
	 */
	Storage storage() default Storage.PLAIN;

	/**
	 * Name, possibly schema-qualified, of another SQL type whose
	 * {@code internalLength}, {@code passedByValue}, {@code alignment}, and
	 * {@code storage} properties are used for this type, as a shorthand
	 * alternative to spelling them all out.
	 */
	String like() default "";

	/**
	 * A category for this type, influencing PostgreSQL's choices for implicit
	 * <a href=
'http://www.postgresql.org/docs/current/static/typeconv-overview.html'
>type conversion</a>.
	 * This must be a single character, which PostgreSQL calls "simple ASCII"
	 * and really forces to be in {@code [ -~]}, that is, space to tilde,
	 * inclusive.
	 *<p>
	 * The upper-case ASCII letters are reserved for PostgreSQL's predefined
	 * categories, which can be found in the
	 * {@link PredefinedCategory PredefinedCategory} enumeration. The default is
	 * {@link PredefinedCategory.Code#USER PredefinedCategory.Code.USER}.
	 */
	char category() default PredefinedCategory.Code.USER;

	/**
	 * Whether this type is to be "preferred" in its {@link #category},
	 * for implicit <a href=
'http://www.postgresql.org/docs/current/static/typeconv-overview.html'
>type conversion</a>
	 * purposes. Setting true without careful thought can cause perplexing
	 * behaviors.
	 */
	boolean preferred() default false;

	/**
	 * A default value for attributes declared to be of this type.
	 * The value given here will be placed in the {@code CREATE TYPE} command
	 * as a properly-quoted string literal, which must be convertible to a value
	 * of the type (using the {@code parse} function of the class this
	 * annotation marks).
	 */
	String defaultValue() default "";

	/**
	 * Name, possibly schema-qualified, of an existing type to be accessible by
	 * subscripting this new type as if it were an array (only with zero-based
	 * indexing instead of one-based). Reports from anyone who has seen this
	 * functionality actually work are welcome.
	 */
	String element() default "";

	/**
	 * Delimiter character to be used between elements when the generic array
	 * routines output an array with this new type as its element type.
	 */
	char delimiter() default ',';

	/**
	 * If true, indicates that column definitions or expressions of this type
	 * may carry a <a href=
'http://www.postgresql.org/docs/current/static/sql-expressions.html#SQL-SYNTAX-COLLATE-EXPRS'
>{@code COLLATE} clause</a>.
	 * What is actually done with that information is up to functions that
	 * operate on the new type; nothing happens automatically.
	 */
	boolean collatable() default false;
}
