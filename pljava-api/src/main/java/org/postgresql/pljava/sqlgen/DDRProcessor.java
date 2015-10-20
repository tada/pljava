/*
 * Copyright (c) 2004-2013 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 */
package org.postgresql.pljava.sqlgen;

import java.io.IOException;

import java.lang.annotation.Annotation;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

import java.math.BigDecimal;
import java.math.BigInteger;

import java.sql.ResultSet;
import java.sql.SQLData;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.sql.Time;
import java.sql.Timestamp;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;

import java.util.regex.Pattern;

import javax.annotation.processing.*;

import javax.lang.model.SourceVersion;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;

import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ExecutableType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;

import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static javax.lang.model.util.ElementFilter.constructorsIn;
import static javax.lang.model.util.ElementFilter.methodsIn;

import static javax.tools.Diagnostic.Kind;

import org.postgresql.pljava.ResultSetHandle;
import org.postgresql.pljava.ResultSetProvider;
import org.postgresql.pljava.TriggerData;

import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLActions;
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.Trigger;
import org.postgresql.pljava.annotation.BaseUDT;
import org.postgresql.pljava.annotation.MappedUDT;

/**
 * Annotation processor invoked by the annotations framework in javac for
 * annotations of type org.postgresql.pljava.annotation.*.
 *
 * Simply forwards to a DDRProcessorImpl instance that is not constructed
 * until the framework calls init (since there is nothing useful for the
 * constructor to do until then).
 *
 * @author Thomas Hallgren - pre-Java6 version
 * @author Chapman Flack (Purdue Mathematics) - update to Java6,
 * add SQLType/SQLAction, polishing
 */
@SupportedAnnotationTypes({"org.postgresql.pljava.annotation.*"})
@SupportedOptions
({
  "ddr.name.trusted",    // default "java"
  "ddr.name.untrusted",  // default "javaU"
  "ddr.implementor",     // implementor when not annotated, default "PostgreSQL"
  "ddr.output"           // name of ddr file to write
})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class DDRProcessor extends AbstractProcessor
{
	private DDRProcessorImpl impl;
	
	@Override
	public void init( ProcessingEnvironment processingEnv)
	{
		super.init( processingEnv);
		impl = new DDRProcessorImpl( processingEnv);
	}
	
	@Override
	public boolean process( Set<? extends TypeElement> tes, RoundEnvironment re)
	{
		if ( null == impl )
			throw new IllegalStateException(
				"The annotation processing framework has called process() " +
				"before init()");
		return impl.process( tes, re);
	}
}

/**
 * Where the work happens.
 */
class DDRProcessorImpl
{
	// Things supplied by the calling framework in ProcessingEnvironment,
	// used enough that it makes sense to break them out here with
	// short names that all nested classes below will inherit.
	//
	final Elements			  elmu;
	final Filer 			  filr;
	final Locale			  loca;
	final Messager			  msgr;
	final Map<String, String> opts;
	final SourceVersion       srcv;
	final Types               typu;

	// Similarly, the TypeMapper should be easily available to code below.
	//
	final TypeMapper          tmpr;

	// Options obtained from the invocation
	//	
	final String nameTrusted;
	final String nameUntrusted;
	final String output;
	final String defaultImplementor;
	
	// Certain known types that need to be recognized in the processed code
	//
	final DeclaredType TY_ITERATOR;
	final DeclaredType TY_OBJECT;
	final DeclaredType TY_RESULTSET;
	final DeclaredType TY_RESULTSETPROVIDER;
	final DeclaredType TY_RESULTSETHANDLE;
	final DeclaredType TY_SQLDATA;
	final DeclaredType TY_SQLINPUT;
	final DeclaredType TY_SQLOUTPUT;
	final DeclaredType TY_STRING;
	final DeclaredType TY_TRIGGERDATA;
	final       NoType TY_VOID;
	
	// Our own annotations
	//
	final TypeElement  AN_FUNCTION;
	final TypeElement  AN_SQLACTION;
	final TypeElement  AN_SQLACTIONS;
	final TypeElement  AN_SQLTYPE;
	final TypeElement  AN_TRIGGER;
	final TypeElement  AN_BASEUDT;
	final TypeElement  AN_MAPPEDUDT;
	
	DDRProcessorImpl( ProcessingEnvironment processingEnv)
	{
		elmu = processingEnv.getElementUtils();
		filr = processingEnv.getFiler();
		loca = processingEnv.getLocale();
		msgr = processingEnv.getMessager();
		opts = processingEnv.getOptions();
		srcv = processingEnv.getSourceVersion();
		typu = processingEnv.getTypeUtils();

		tmpr = new TypeMapper();
		
		String optv;
		
		optv = opts.get( "ddr.name.trusted");
		if ( null != optv )
			nameTrusted = optv;
		else
			nameTrusted = "java";
		
		optv = opts.get( "ddr.name.untrusted");
		if ( null != optv )
			nameUntrusted = optv;
		else
			nameUntrusted = "javaU";
		
		optv = opts.get( "ddr.implementor");
		if ( null != optv )
			defaultImplementor = "-".equals( optv) ? null : optv;
		else
			defaultImplementor = "PostgreSQL";

		optv = opts.get( "ddr.output");
		if ( null != optv )
			output = optv;
		else
			output = "pljava.ddr";
		
		TY_ITERATOR = typu.getDeclaredType(
			elmu.getTypeElement( java.util.Iterator.class.getName()));
		TY_OBJECT = typu.getDeclaredType(
			elmu.getTypeElement( Object.class.getName()));
		TY_RESULTSET = typu.getDeclaredType(
			elmu.getTypeElement( java.sql.ResultSet.class.getName()));
		TY_RESULTSETPROVIDER = typu.getDeclaredType(
			elmu.getTypeElement( ResultSetProvider.class.getName()));
		TY_RESULTSETHANDLE = typu.getDeclaredType(
			elmu.getTypeElement( ResultSetHandle.class.getName()));
		TY_SQLDATA = typu.getDeclaredType(
			elmu.getTypeElement( SQLData.class.getName()));
		TY_SQLINPUT = typu.getDeclaredType(
			elmu.getTypeElement( SQLInput.class.getName()));
		TY_SQLOUTPUT = typu.getDeclaredType(
			elmu.getTypeElement( SQLOutput.class.getName()));
		TY_STRING = typu.getDeclaredType(
			elmu.getTypeElement( String.class.getName()));
		TY_TRIGGERDATA = typu.getDeclaredType(
			elmu.getTypeElement( TriggerData.class.getName()));
		TY_VOID = typu.getNoType( TypeKind.VOID);

		AN_FUNCTION    = elmu.getTypeElement( Function.class.getName());
		AN_SQLACTION   = elmu.getTypeElement( SQLAction.class.getName());
		AN_SQLACTIONS  = elmu.getTypeElement( SQLActions.class.getName());
		AN_SQLTYPE     = elmu.getTypeElement( SQLType.class.getName());
		AN_TRIGGER     = elmu.getTypeElement( Trigger.class.getName());
		AN_BASEUDT     = elmu.getTypeElement( BaseUDT.class.getName());
		AN_MAPPEDUDT   = elmu.getTypeElement( MappedUDT.class.getName());
	}
	
	void msg( Kind kind, String fmt, Object... args)
	{
		msgr.printMessage( kind, String.format( fmt, args));
	}
	
	void msg( Kind kind, Element e, String fmt, Object... args)
	{
		msgr.printMessage( kind, String.format( fmt, args), e);
	}
	
	void msg( Kind kind, Element e, AnnotationMirror a,
		String fmt, Object... args)
	{
		msgr.printMessage( kind, String.format( fmt, args), e, a);
	}
	
	void msg( Kind kind, Element e, AnnotationMirror a, AnnotationValue v,
		String fmt, Object... args)
	{
		msgr.printMessage( kind, String.format( fmt, args), e, a, v);
	}

	/**
	 * Key usable in a mapping from (Object, Snippet-subtype) to Snippet.
	 * Because there's no telling in which order a Map implementation will
	 * compare two keys, the class matches if either one is assignable to
	 * the other. That's ok as long as the Snippet-subtype is never Snippet
	 * itself, no Object ever has two Snippets hung on it where one extends
	 * the other, and getSnippet is always called for the widest of any of
	 * the types it may retrieve.
	 */
	static final class SnippetsKey
	{
		final Object o;
		final Class<? extends Snippet> c;
		SnippetsKey(Object o, Class<? extends Snippet> c)
		{
			assert Snippet.class != c : "Snippet key must be a subtype";
			this.o = o;
			this.c = c;
		}
		public boolean equals(Object oth)
		{
			if ( ! (oth instanceof SnippetsKey) )
				return false;
			SnippetsKey osk = (SnippetsKey)oth;
			return o.equals( osk.o)
				&& ( c.isAssignableFrom( osk.c) || osk.c.isAssignableFrom( c) );
		}
		public int hashCode()
		{
			return o.hashCode(); // must not depend on c (subtypes will match)
		}
	}
	
	/**
	 * Collection of code snippets being accumulated (possibly over more than
	 * one round), keyed by the object for which each snippet has been
	 * generated.
	 */
	Map<SnippetsKey, Snippet> snippets = new HashMap<SnippetsKey, Snippet>();

	<S extends Snippet> S getSnippet(Object o, Class<S> c)
	{
		return (S)snippets.get( new SnippetsKey( o, c));
	}

	void putSnippet( Object o, Snippet s)
	{
		snippets.put( new SnippetsKey( o, s.getClass()), s);
	}
	
	/**
	 * Find the elements in each round that carry any of the annotations of
	 * interest and generate code snippets accordingly. On the last round, with
	 * all processing complete, generate the deployment descriptor file.
	 */
	boolean process( Set<? extends TypeElement> tes, RoundEnvironment re)
	{
		boolean functionPresent = false;
		boolean sqlActionPresent = false;
		boolean sqlActionsPresent = false;
		boolean baseUDTPresent = false;
		boolean mappedUDTPresent = false;
		
		boolean willClaim = true;
		
		for ( TypeElement te : tes )
		{
			if ( AN_FUNCTION.equals( te) )
				functionPresent = true;
			else if ( AN_SQLACTION.equals( te) )
				sqlActionPresent = true;
			else if ( AN_SQLACTIONS.equals( te) )
				sqlActionsPresent = true;
			else if ( AN_BASEUDT.equals( te) )
				baseUDTPresent = true;
			else if ( AN_MAPPEDUDT.equals( te) )
				mappedUDTPresent = true;
			else if ( AN_SQLTYPE.equals( te) )
				; // these are handled within FunctionImpl
			else
			{
				msg( Kind.WARNING, te,
					"pljava annotation processor version may be older than " +
					"this annotation:\n%s", te.toString());
				willClaim = false;
			}
		}
		
		if ( baseUDTPresent )
			for ( Element e : re.getElementsAnnotatedWith( AN_BASEUDT) )
				processUDT( e, UDTKind.BASE);

		if ( mappedUDTPresent )
			for ( Element e : re.getElementsAnnotatedWith( AN_MAPPEDUDT) )
				processUDT( e, UDTKind.MAPPED);

		if ( functionPresent )
			for ( Element e : re.getElementsAnnotatedWith( AN_FUNCTION) )
				processFunction( e);
		
		if ( sqlActionPresent )
			for ( Element e : re.getElementsAnnotatedWith( AN_SQLACTION) )
				processSQLAction( e);
		
		if ( sqlActionsPresent )
			for ( Element e : re.getElementsAnnotatedWith( AN_SQLACTIONS) )
				processSQLActions( e);

		tmpr.workAroundJava7Breakage(); // perhaps it will be fixed in Java 9?

		if ( re.processingOver() && ! re.errorRaised() )
			generateDescriptor();

		return willClaim;
	}
	
	/**
	 * Arrange the collected snippets into a workable sequence (nothing with
	 * requires="X" can come before whatever has provides="X"), then create
	 * a deployment descriptor file in proper form.
	 */
	void generateDescriptor()
	{
		Queue<Vertex<Snippet>> vs =	new LinkedList<Vertex<Snippet>>();
		Map<String, Vertex<Snippet>> provider =
			new HashMap<String, Vertex<Snippet>>();
		Set<String> consumer = new HashSet<String>();
		boolean errorRaised = false;

		for ( Snippet snip : snippets.values() )
		{
			if ( ! snip.characterize() )
				continue;
			Vertex<Snippet> v = new Vertex<Snippet>( snip);
			vs.add( v);
			for ( String s : snip.provides() )
				if ( null != provider.put( s, v) )
				{
					msg( Kind.ERROR, "tag %s has more than one provider", s);
					errorRaised = true;
				}
			for ( String s : snip.requires() )
				consumer.add( s);
		}
		
		for ( Vertex<Snippet> v : vs )
			for ( String s : v.payload.requires() )
			{
				Vertex<Snippet> p = provider.get( s);
				if ( null != p )
					p.precede( v);
				else if ( s == v.payload.implementor() ) // yes == if from impl
				{
					/*
					 * It's the implicit requires(implementor()). Bump the
					 * indegree anyway so the snippet won't be emitted until
					 * the cycle breaker code (see below) sets it free after
					 * any others that can be handled first.
					 */
					if ( ! defaultImplementor.equals( s) )
						++ v.indegree;
				}
				else
				{
					msg( Kind.ERROR,
						"tag \"%s\" is required but nowhere provided", s);
					errorRaised = true;
				}
			}

		if ( errorRaised )
			return;

		Snippet[] snips = new Snippet [ vs.size() ];

		Queue<Vertex<Snippet>> q = new LinkedList<Vertex<Snippet>>();
		for ( Iterator<Vertex<Snippet>> it = vs.iterator() ; it.hasNext() ; )
		{
			Vertex<Snippet> v = it.next();
			if ( 0 == v.indegree )
			{
				q.add( v);
				it.remove();
			}
		}

queuerunning: for ( int i = 0 ; ; )
		{
			while ( ! q.isEmpty() )
			{
				Vertex<Snippet> v = q.remove();
				snips[i++] = v.payload;
				v.use( q, vs);
				for ( String p : v.payload.provides() )
					consumer.remove(p);
			}
			if ( vs.isEmpty() )
				break; // all done
			/*
			 * There are snippets remaining to output but they all have
			 * indegree > 0, normally a 'cycle' error. But somewhere there may
			 * be one with indegree exactly 1 and an implicit requirement of its
			 * own implementor tag, with no snippet on record to provide it.
			 * That's allowed (maybe the installing/removing environment will
			 * be "providing" that tag anyway), so set one such snippet free
			 * and see how much farther we get.
			 */
			for ( Iterator<Vertex<Snippet>> it = vs.iterator(); it.hasNext(); )
			{
				Vertex<Snippet> v = it.next();
				if ( 1 < v.indegree  ||  null == v.payload.implementor() )
					continue;
				if ( provider.containsKey( v.payload.implementor()) )
					continue;
				-- v.indegree;
				it.remove();
				q.add( v);
				continue queuerunning;
			}
			/*
			 * Got here? It's a real cycle ... nothing to be done.
			 */
			for ( String s : consumer )
				msg( Kind.ERROR, "requirement in a cycle: %s", s);
			return;
		}
		
		try
		{
			DDRWriter.emit( snips, this);
		}
		catch ( IOException ioe )
		{
			msg( Kind.ERROR, "while writing %s: %s", output, ioe.getMessage());
		}
	}
	
	/**
	 * Process a single element annotated with @SQLAction.
	 */
	void processSQLAction( Element e)
	{
		SQLActionImpl sa = getSnippet( e, SQLActionImpl.class);
		if ( null == sa )
		{
			sa = new SQLActionImpl();
			putSnippet( e, sa);
		}
		for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
		{
			if ( am.getAnnotationType().asElement().equals( AN_SQLACTION) )
				populateAnnotationImpl( sa, e, am);
		}
	}
	
	/**
	 * Process a single element annotated with @SQLActions (which simply takes
	 * an array of @SQLAction as a way to associate more than one SQLAction with
	 * a single program element)..
	 */
	void processSQLActions( Element e)
	{
		for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
		{
			if ( am.getAnnotationType().asElement().equals( AN_SQLACTIONS) )
			{
				SQLActionsImpl sas = new SQLActionsImpl();
				populateAnnotationImpl( sas, e, am);
				for ( SQLAction sa : sas.value() )
					putSnippet( sa, (Snippet)sa);
			}
		}
	}

	static enum UDTKind { BASE, MAPPED }

	/**
	 * Process a single element annotated with @BaseUDT or @MappedUDT, as
	 * indicated by the UDTKind k.
	 */
	void processUDT( Element e, UDTKind k)
	{
		if ( ! ElementKind.CLASS.equals( e.getKind()) )
		{
			msg( Kind.ERROR, e, "A pljava UDT must be a class");
		}
		Set<Modifier> mods = e.getModifiers();
		if ( ! mods.contains( Modifier.PUBLIC) )
		{
			msg( Kind.ERROR, e, "A pljava UDT must be public");
		}
		if ( mods.contains( Modifier.ABSTRACT) )
		{
			msg( Kind.ERROR, e, "A pljava UDT must not be abstract");
		}
		if ( ! ((TypeElement)e).getNestingKind().equals(
			NestingKind.TOP_LEVEL) )
		{
			if ( ! mods.contains( Modifier.STATIC) )
			{
				msg( Kind.ERROR, e,
					"When nested, a pljava UDT must be static (not inner)");
			}
			for ( Element ee = e; null != ( ee = ee.getEnclosingElement() ); )
			{
				if ( ! ee.getModifiers().contains( Modifier.PUBLIC) )
					msg( Kind.ERROR, ee,
						"A pljava UDT must not have a non-public " +
						"enclosing class");
				if ( ((TypeElement)ee).getNestingKind().equals(
					NestingKind.TOP_LEVEL) )
					break;
			}
		}

		switch ( k )
		{
		case BASE:
			BaseUDTImpl bu = getSnippet( e, BaseUDTImpl.class);
			if ( null == bu )
			{
				bu = new BaseUDTImpl( (TypeElement)e);
				putSnippet( e, bu);
			}
			for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
			{
				if ( am.getAnnotationType().asElement().equals( AN_BASEUDT) )
					populateAnnotationImpl( bu, e, am);
			}
			bu.registerFunctions();
			break;

		case MAPPED:
			MappedUDTImpl mu = getSnippet( e, MappedUDTImpl.class);
			if ( null == mu )
			{
				mu = new MappedUDTImpl( (TypeElement)e);
				putSnippet( e, mu);
			}
			for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
			{
				if ( am.getAnnotationType().asElement().equals( AN_MAPPEDUDT) )
					populateAnnotationImpl( mu, e, am);
			}
			break;
		}
	}

	ExecutableElement huntFor(List<ExecutableElement> ees, String name,
		boolean isStatic, TypeMirror retType, TypeMirror... paramTypes)
	{
		ExecutableElement quarry = null;
hunt:	for ( ExecutableElement ee : ees )
		{
			if ( null != name && ! ee.getSimpleName().contentEquals( name) )
				continue;
			if ( ee.isVarArgs() )
				continue;
			if ( null != retType
				&& ! typu.isSameType( ee.getReturnType(), retType) )
				continue;
			List<? extends TypeMirror> pts =
				((ExecutableType)ee.asType()).getParameterTypes();
			if ( pts.size() != paramTypes.length )
				continue;
			for ( int i = 0; i < paramTypes.length; ++i )
				if ( ! typu.isSameType( pts.get( i), paramTypes[i]) )
					continue hunt;
			Set<Modifier> mods = ee.getModifiers();
			if ( ! mods.contains( Modifier.PUBLIC) )
				continue;
			if ( isStatic && ! mods.contains( Modifier.STATIC) )
				continue;
			if ( null == quarry )
				quarry = ee;
			else
			{
				msg( Kind.ERROR, ee,
					"Found more than one candidate " +
					(null == name ? "constructor" : (name + " method")));
			}
		}
		return quarry;
	}

	/**
	 * Process a single element annotated with @Function. After checking that
	 * it has the right modifiers to be called via pljava, analyze its type
	 * information and annotations and register an appropriate SQL code snippet.
	 */
	void processFunction( Element e)
	{
		Set<Modifier> mods = e.getModifiers();
		if ( ! mods.contains( Modifier.PUBLIC) )
		{
			msg( Kind.ERROR, e, "A pljava function must be public");
		}

		for ( Element ee = e; null != ( ee = ee.getEnclosingElement() ); )
		{
			if ( ElementKind.CLASS.equals( ee.getKind()) )
			{
				if ( ! ee.getModifiers().contains( Modifier.PUBLIC) )
					msg( Kind.ERROR, ee,
						"A pljava function must not have a non-public " +
						"enclosing class");
				if ( ((TypeElement)ee).getNestingKind().equals(
					NestingKind.TOP_LEVEL) )
					break;
			}
		}

		FunctionImpl f = getSnippet( e, FunctionImpl.class);
		if ( null == f )
		{
			f = new FunctionImpl( (ExecutableElement)e);
			putSnippet( e, f);
		}
		for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
		{
			if ( am.getAnnotationType().asElement().equals( AN_FUNCTION) )
				populateAnnotationImpl( f, e, am);
		}
	}

	/**
	 * Populate an array of specified type from an annotation value
	 * representing an array.
	 *
	 * AnnotationValue's getValue() method returns Object, where the
	 * object is known to be an instance of one of a small set of classes.
	 * Populating an array when that value represents one is a common
	 * operation, so it is factored out here.
	 */
	static <T> T[] avToArray( Object o, Class<T> k)
	{
		boolean isEnum = k.isEnum();

		@SuppressWarnings({"unchecked"})
		List<? extends AnnotationValue> vs = (List<? extends AnnotationValue>)o;

		@SuppressWarnings({"unchecked"})
		T[] a = (T[])Array.newInstance( k, vs.size());

		int i = 0;
		for ( AnnotationValue av : vs )
		{
			Object v = av.getValue();
			if ( isEnum )
				v = Enum.valueOf( k.asSubclass( Enum.class),
					((VariableElement)v).getSimpleName().toString());
			a[i++] = k.cast( v);
		}
		return a;
	}

	/**
	 * Abstract superclass for synthetic implementations of annotation
	 * interfaces; these can be populated with element-value pairs from
	 * an AnnotationMirror and then used in the natural way for access to
	 * the values. Each subclass of this should implement the intended
	 * annotation interface, and should also have a
	 * setFoo(Object,boolean,Element) method for each foo() method in the
	 * interface. Rather than longwindedly using the type system to enforce
	 * that the needed setter methods are all there, they will be looked
	 * up using reflection.
	 */
	class AbstractAnnotationImpl implements Annotation
	{
		public Class<? extends Annotation> annotationType()
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Supply the required implementor() method for those subclasses
		 * that will implement {@link Snippet}.
		 */
		public String implementor() { return _implementor; }

		String _implementor = defaultImplementor;

		public void setImplementor( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_implementor = "".equals( o) ? null : (String)o;
		}

		/**
		 * Use from characterize() in any subclass implementing Snippet.
		 */
		protected String[] augmentRequires( String req[], String imp)
		{
			if ( null == imp )
				return req;
			String[] newreq = new String [ 1 + req.length ];
			System.arraycopy( req, 0, newreq, 0, req.length);
			newreq[req.length] = imp;
			return newreq;
		}
	}

	/**
	 * Populate an AbstractAnnotationImpl-derived Annotation implementation
	 * from the element-value pairs in an AnnotationMirror. For each element
	 * foo in the annotation interface, the implementation is assumed to have
	 * a method setFoo(Object o, boolean explicit, element e) where o is the
	 * element's value as obtained from AnnotationValue.getValue(), explicit
	 * indicates whether the element was explicitly present in the annotation
	 * or filled in from a default value, and e is the element carrying the
	 * annotation (chiefly for use as a location hint in diagnostic messages).
	 *
	 * Some of the annotation implementations below will leave certain elements
	 * null if they were not given explicit values, in order to have a clear
	 * indication that they were defaulted, even though that is not the way
	 * normal annotation objects behave.
	 *
	 * If a setFoo(Object o, boolean explicit, element e) method is not found
	 * but there is an accessible field _foo it will be set directly, but only
	 * if the value was explicitly present in the annotation or the field value
	 * is null. By this convention, an implementation can declare a field
	 * initially null and let its default value be filled in from what the
	 * annotation declares, or initially some non-null value distinct from
	 * possible annotation values, and be able to tell whether it was explicitly
	 * set. Note that a field of primitive type will never be seen as null.
	 */
	void populateAnnotationImpl(
		AbstractAnnotationImpl inst, Element e, AnnotationMirror am)
	{
		Map<? extends ExecutableElement, ? extends AnnotationValue> explicit =
			am.getElementValues();
		Map<? extends ExecutableElement, ? extends AnnotationValue> defaulted =
			elmu.getElementValuesWithDefaults( am);

		// Astonishingly, even though JLS3 9.7 clearly says "annotations must
		// contain an element-value pair for every element of the corresponding
		// annotation type, except for those elements with default values, or a
		// compile-time error occurs" - in Sun 1.6.0_39 javac never flags
		// the promised error, and instead allows us to NPE on something that
		// ought to be guaranteed to be there! >:[
		//
		// If you want something done right, you have to do it yourself....
		//
		
		Element anne = am.getAnnotationType().asElement();
		List<ExecutableElement> keys = methodsIn( anne.getEnclosedElements());
		for ( ExecutableElement k : keys )
			if ( ! defaulted.containsKey( k) )
				msg( Kind.ERROR, e, am,
					"annotation missing required element \"%s\"",
					k.getSimpleName());
		
		for (
			Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> me
			: defaulted.entrySet()
			)
		{
			ExecutableElement k = me.getKey();
			AnnotationValue  av = me.getValue();
			boolean isExplicit = explicit.containsKey( k);
			String name = k.getSimpleName().toString();
			Object v = av.getValue();
			Class<? extends Annotation> kl = inst.getClass();
			try
			{
				kl.getMethod( // let setter for foo() be setFoo()
					"set"+name.substring( 0, 1).toUpperCase() +
						name.substring( 1),
					Object.class, boolean.class, Element.class)
					.invoke(inst, v, isExplicit, e);
			}
			catch (NoSuchMethodException nsme)
			{
				try
				{
					Field f = kl.getField( "_"+name);
					Class<?> fkl = f.getType();
					if ( ! isExplicit  &&  null != f.get( inst) )
						continue;
					if ( fkl.isArray() )
						f.set( inst, avToArray( v, fkl.getComponentType()));
					else if ( fkl.isEnum() )
						f.set( inst, Enum.valueOf( fkl.asSubclass( Enum.class),
							((VariableElement)v).getSimpleName().toString()));
					else
						f.set( inst, v);
					nsme = null;
				}
				catch (NoSuchFieldException nsfe) { }
				catch (IllegalAccessException iae) { }
				if ( null != nsme )
					throw new RuntimeException(
						"Incomplete implementation in annotation processor",
						nsme);
			}
			catch (IllegalAccessException iae)
			{
				throw new RuntimeException(
					"Incorrect implementation of annotation processor", iae);
			}
			catch (InvocationTargetException ite)
			{
				String msg = ite.getCause().getMessage();
				msg( Kind.ERROR, e, am, av, "%s", msg);
			}
		}
	}
	
	// It could be nice to have another annotation-driven tool that could just
	// generate these implementations of some annotation types....
	
	class SQLTypeImpl extends AbstractAnnotationImpl implements SQLType
	{
		public String value() { return _value; }
		public String[] defaultValue() { return _defaultValue; }
		
		String _value;
		String[] _defaultValue;
		
		public void setValue( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_value = (String)o;
		}
		
		public void setDefaultValue( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_defaultValue = avToArray( o, String.class);
		}
	}

	class SQLActionsImpl extends AbstractAnnotationImpl	implements SQLActions
	{
		public SQLAction[] value() { return _value; }
		
		SQLAction[] _value;
		
		public void setValue( Object o, boolean explicit, Element e)
		{
			AnnotationMirror[] ams = avToArray( o, AnnotationMirror.class);
			_value = new SQLAction [ ams.length ];
			int i = 0;
			for ( AnnotationMirror am : ams )
			{
			  SQLActionImpl a = new SQLActionImpl();
			  populateAnnotationImpl( a, e, am);
			  _value [ i++ ] = a;
			}
		}
	}

	class SQLActionImpl
	extends AbstractAnnotationImpl
	implements SQLAction, Snippet
	{
		public String[]  install() { return _install;  }
		public String[]   remove() { return _remove;   }
		public String[] provides() { return _provides; }
		public String[] requires() { return _requires; }

		public String[]   deployStrings() { return _install; }
		public String[] undeployStrings() { return _remove; }
		
		public String[] _install;
		public String[] _remove;
		public String[] _provides;
		public String[] _requires;

		public boolean characterize()
		{
			_requires = augmentRequires( _requires, implementor());
			return true;
		}
	}
	
	class TriggerImpl
	extends AbstractAnnotationImpl
	implements Trigger, Snippet
	{
		public String[] arguments() { return _arguments; }
		public Event[]     events() { return _events; }
		public String        name() { return _name; }
		public String      schema() { return _schema; }
		public String       table() { return _table; }
		public Scope        scope() { return _scope; }
		public When          when() { return _when; }
		
		public String[] provides() { return new String[0]; }
		public String[] requires() { return new String[0]; }
		/* Trigger is a Snippet but doesn't directly participate in tsort */

		public String[] _arguments;
		public Event[]  _events;
		public String   _name;
		public String   _schema;
		public String  	_table;
		public Scope    _scope;
		public When     _when;
		
		FunctionImpl func;
		AnnotationMirror origin;
		
		TriggerImpl( FunctionImpl f, AnnotationMirror am)
		{
			func = f;
			origin = am;
		}

		public boolean characterize()
		{
			if ( Scope.ROW.equals( _scope) )
				for ( Event e : _events )
					if ( Event.TRUNCATE.equals( e) )
						msg( Kind.ERROR, func.func, origin,
							"TRUNCATE trigger cannot be FOR EACH ROW");

			if ( "".equals( _name) )
				_name = TriggerNamer.synthesizeName( this);
			return false;
		}
		
		public String[] deployStrings()
		{
			StringBuilder sb = new StringBuilder();
			sb.append( "CREATE TRIGGER ").append( name()).append( "\n\t");
			sb.append( when().toString()).append( ' ');
			int s = _events.length;
			for ( Event e : _events )
			{
				sb.append( e.toString());
				if ( 0 < -- s )
					sb.append( " OR ");
			}
			sb.append( "\n\tON ");
			if ( ! "".equals( schema()) )
				sb.append( schema()).append( '.');
			sb.append( table()).append( "\n\tFOR EACH ");
			sb.append( scope().toString()).append( "\n\tEXECUTE PROCEDURE ");
			func.appendNameAndParams( sb, false);
			sb.setLength( sb.length() - 1); // drop closing )
			s = _arguments.length;
			for ( String a : _arguments )
			{
				sb.append( "\n\t").append( DDRWriter.eQuote( a));
				if ( 0 < -- s )
					sb.append( ',');
			}
			sb.append( ')');
			return new String[] { sb.toString() };
		}
		
		public String[] undeployStrings()
		{
			StringBuilder sb = new StringBuilder();
			sb.append( "DROP TRIGGER ").append( name()).append( "\n\tON ");
			if ( ! "".equals( schema()) )
				sb.append( schema()).append( '.');
			sb.append( table());
			return new String[] { sb.toString() };
		}
	}

	class FunctionImpl
	extends AbstractAnnotationImpl
	implements Function, Snippet
	{
		public String      complexType() { return _complexType; }
		public String             name() { return _name; }
		public String           schema() { return _schema; }
		public OnNullInput onNullInput() { return _onNullInput; }
		public Security       security() { return _security; }
		public Type               type() { return _type; }
		public Trust             trust() { return _trust; }
		public boolean       leakproof() { return _leakproof; }
		public int                cost() { return _cost; }
		public int                rows() { return _rows; }
		public String[]       settings() { return _settings; }
		public String[]       provides() { return _provides; }
		public String[]       requires() { return _requires; }
		public Trigger[]      triggers() { return _triggers; }

		ExecutableElement func;

		public String      _complexType;
		public String      _name;
		public String      _schema;
		public OnNullInput _onNullInput;
		public Security    _security;
		public Type        _type;
		public Trust       _trust;
		public Boolean     _leakproof;
		int                _cost;
		int                _rows;
		public String[]    _settings;
		public String[]    _provides;
		public String[]    _requires;
		Trigger[]          _triggers;

		boolean complexViaInOut = false;
		boolean setof = false;
		TypeMirror setofComponent = null;
		boolean trigger = false;

		FunctionImpl(ExecutableElement e)
		{
			func = e;
		}

		public void setCost( Object o, boolean explicit, Element e)
		{
			_cost = ((Integer)o).intValue();
			if ( _cost < 0 && explicit )
				throw new IllegalArgumentException( "cost must be nonnegative");
		}

		public void setRows( Object o, boolean explicit, Element e)
		{
			_rows = ((Integer)o).intValue();
			if ( _rows < 0 && explicit )
				throw new IllegalArgumentException( "rows must be nonnegative");
		}

		public void setTriggers( Object o, boolean explicit, Element e)
		{
			AnnotationMirror[] ams = avToArray( o, AnnotationMirror.class);
			_triggers = new Trigger [ ams.length ];
			int i = 0;
			for ( AnnotationMirror am : ams )
			{
				TriggerImpl ti = new TriggerImpl( this, am);
				populateAnnotationImpl( ti, e, am);
				_triggers [ i++ ] = ti;
			}
		}

		public boolean characterize()
		{
			if ( "".equals( _name) )
				_name = func.getSimpleName().toString();

			Set<Modifier> mods = func.getModifiers();
			if ( ! mods.contains( Modifier.STATIC) )
			{
				msg( Kind.ERROR, func, "A pljava function must be static");
			}

			TypeMirror ret = func.getReturnType();
			if ( ret.getKind().equals( TypeKind.ERROR) )
			{
				msg( Kind.ERROR, func,
					"Unable to resolve return type of function");
				return false;
			}

			ExecutableType et = (ExecutableType)func.asType();
			List<? extends TypeMirror> ptms = et.getParameterTypes();
			int arity = ptms.size();

			if ( ! "".equals( complexType())
				&& ret.getKind().equals( TypeKind.BOOLEAN) )
			{
				complexViaInOut = true;
				TypeMirror tm = ptms.get( arity - 1);
				if ( tm.getKind().equals( TypeKind.ERROR)
					// unresolved things seem assignable to anything
					|| ! typu.isSameType( tm, TY_RESULTSET) )
				{
					msg( Kind.ERROR, func.getParameters().get( arity - 1),
						"Last parameter of complex-type-returning function " +
						"must be ResultSet");
					return false;
				}
			}
			else if ( typu.isAssignable( typu.erasure( ret), TY_ITERATOR) )
			{
				setof = true;
				List<TypeMirror> pending = new LinkedList<TypeMirror>();
				pending.add( ret);
				while ( ! pending.isEmpty() )
				{
					TypeMirror tm = pending.remove( 0);
					if ( typu.isSameType( typu.erasure( tm), TY_ITERATOR) )
					{
						DeclaredType dt = (DeclaredType)tm;
						List<? extends TypeMirror> typeArgs =
							dt.getTypeArguments();
						if ( 1 != typeArgs.size() )
						{
							msg( Kind.ERROR, func,
								"Need one type argument for Iterator " +
								"return type");
							return false;
						}
						setofComponent = typeArgs.get( 0);
						break;
					}
					else
					{
						pending.addAll( typu.directSupertypes( tm));
					}
				}
				if ( null == setofComponent )
				{
					msg( Kind.ERROR, func,
						"Failed to find setof component type");
					return false;
				}
			}
			else if ( typu.isAssignable( ret, TY_RESULTSETPROVIDER)
				|| typu.isAssignable( ret, TY_RESULTSETHANDLE) )
			{
				setof = true;
			}
			else if ( ret.getKind().equals( TypeKind.VOID) && 1 == arity )
			{
				TypeMirror tm = ptms.get( 0);
				if ( ! tm.getKind().equals( TypeKind.ERROR)
					// unresolved things seem assignable to anything
					&& typu.isSameType( tm, TY_TRIGGERDATA) )
				{
					trigger = true;
				}
			}
				
			if ( ! setof && -1 != rows() )
				msg( Kind.ERROR, func,
					"ROWS specified on a function not returning SETOF");

			if ( ! trigger && 0 != _triggers.length )
				msg( Kind.ERROR, func,
					"a function with triggers needs void return and " +
					"one TriggerData parameter");

			_requires = augmentRequires( _requires, implementor());

			for ( Trigger t : triggers() )
				((TriggerImpl)t).characterize();
			return true;
		}

		/**
		 * Append SQL syntax for the function's name (schema-qualified if
		 * appropriate) and parameters, either with any defaults indicated
		 * (for use in CREATE FUNCTION) or without (for use in DROP FUNCTION).
		 *
		 * @param dflts Whether to include the defaults, if any.
		 */
		void appendNameAndParams( StringBuilder sb, boolean dflts)
		{
			if ( ! "".equals( schema()) )
				sb.append( schema()).append( '.');
			sb.append( name()).append( '(');
			appendParams( sb, dflts);
			// TriggerImpl relies on ) being the very last character
			sb.append( ')');
		}

		void appendParams( StringBuilder sb, boolean dflts)
		{
			if ( ! trigger )
			{
				ExecutableType et = (ExecutableType)func.asType();
				List<? extends TypeMirror> tms = et.getParameterTypes();
				Iterator<? extends VariableElement> ves =
					func.getParameters().iterator();
				if ( complexViaInOut )
					tms = tms.subList( 0, tms.size() - 1);
				int s = tms.size();
				for ( TypeMirror tm : tms )
				{
					VariableElement ve = ves.next();
					sb.append( "\n\t").append( ve.getSimpleName().toString());
					sb.append( ' ');
					sb.append( tmpr.getSQLType( tm, ve, true, dflts));
					if ( 0 < -- s )
						sb.append( ',');
				}
			}
		}

		void appendAS( StringBuilder sb)
		{
			Element e = func.getEnclosingElement();
			if ( ! e.getKind().equals( ElementKind.CLASS) )
				msg( Kind.ERROR, func,
					"Somehow this method got enclosed by something other " +
					"than a class");
			sb.append( e.toString()).append( '.');
			sb.append( func.toString());
		}

		public String[] deployStrings()
		{
			String[] rslt = new String [ 1 + triggers().length ];
			// relies on each trigger's deployStrings() having length == 1
			StringBuilder sb = new StringBuilder();
			sb.append( "CREATE OR REPLACE FUNCTION ");
			appendNameAndParams( sb, true);
			sb.append( "\n\tRETURNS ");
			if ( trigger )
				sb.append( "trigger");
			else
			{
				if ( setof )
					sb.append( "SETOF ");
				if ( ! "".equals( complexType()) )
					sb.append( complexType());
				else if ( null != setofComponent )
					sb.append( tmpr.getSQLType( setofComponent, func));
				else if ( setof )
					sb.append( "RECORD");
				else
					sb.append( tmpr.getSQLType( func.getReturnType(), func));
			}
			sb.append( "\n\tLANGUAGE ");
			if ( Trust.RESTRICTED.equals( trust()) )
				sb.append( nameTrusted);
			else
				sb.append( nameUntrusted);
			sb.append( ' ').append( type());
			if ( leakproof() )
				sb.append( " LEAKPROOF");
			sb.append( '\n');
			if ( OnNullInput.RETURNS_NULL.equals( onNullInput()) )
				sb.append( "\tRETURNS NULL ON NULL INPUT\n");
			if ( Security.DEFINER.equals( security()) )
				sb.append( "\tSECURITY DEFINER\n");
			if ( -1 != cost() )
				sb.append( "\tCOST ").append( cost()).append( '\n');
			if ( -1 != rows() )
				sb.append( "\tROWS ").append( rows()).append( '\n');
			for ( String s : settings() )
				sb.append( "\tSET ").append( s).append( '\n');
			sb.append( "\tAS '");
			appendAS( sb);
			sb.append( '\'');
			rslt [ 0 ] = sb.toString();
			
			int i = 1;
			for ( Trigger t : triggers() )
				for ( String s : ((TriggerImpl)t).deployStrings() )
					rslt [ i++ ] = s;
			return rslt;
		}
		
		public String[] undeployStrings()
		{
			String[] rslt = new String [ 1 + triggers().length ];
			int i = rslt.length - 1;
			for ( Trigger t : triggers() )
				for ( String s : ((TriggerImpl)t).undeployStrings() )
					rslt [ --i ] = s;

			StringBuilder sb = new StringBuilder();
			sb.append( "DROP FUNCTION ");
			appendNameAndParams( sb, false);
			rslt [ rslt.length - 1 ] = sb.toString();
			return rslt;
		}
	}

	static enum BaseUDTFunctionID
	{
		INPUT( "in", "cstring", null),
		OUTPUT( "out", null, "cstring"),
		RECEIVE( "recv", "internal", null),
		SEND( "send", null, "bytea");
		BaseUDTFunctionID( String suffix, String param, String ret)
		{
			this.suffix = suffix;
			this.param = param;
			this.ret = ret;
		}
		private String suffix;
		private String param;
		private String ret;
		String getSuffix() { return suffix; }
		String getParam( BaseUDTImpl u)
		{
			if ( null != param )
				return param;
			return u.qname;
		}
		String getRet( BaseUDTImpl u)
		{
			if ( null != ret )
				return ret;
			return u.qname;
		}
	}

	class BaseUDTFunctionImpl extends FunctionImpl
	{
		BaseUDTFunctionImpl(
			BaseUDTImpl ui, TypeElement te, BaseUDTFunctionID id)
		{
			super( null);
			this.ui = ui;
			this.te = te;
			this.id = id;

			_complexType = id.getRet( ui);
			_name = ui.name() + '_' + id.getSuffix();
			_schema = ui.schema();
			_cost = -1;
			_rows = -1;
			_onNullInput = OnNullInput.CALLED;
			_security = Security.INVOKER;
			_type = Type.VOLATILE;
			_trust = Trust.RESTRICTED;
			_leakproof = false;
			_settings = new String[0];
			_triggers = new Trigger[0];
			_provides = _settings;
			_requires = _settings;
		}

		BaseUDTImpl ui;
		TypeElement te;
		BaseUDTFunctionID id;

		@Override
		void appendParams( StringBuilder sb, boolean dflts)
		{
			sb.append( id.getParam( ui));
		}

		@Override
		void appendAS( StringBuilder sb)
		{
			sb.append( "UDT[").append( te.toString()).append( "] ");
			sb.append( id.name());
		}

		StringBuilder appendTypeOp( StringBuilder sb)
		{
			sb.append( id.name()).append( " = ");
			if ( ! "".equals( schema()) )
				sb.append( schema()).append( '.');
			return sb.append( name());
		}

		@Override
		public boolean characterize()
		{
			return false;
		}

		public void setComplexType( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"The complexType of a UDT function may not be changed");
		}

		public void setRows( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"The rows attribute of a UDT function may not be set");
		}

		public void setProvides( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"A UDT function does not have its own provides/requires");
		}

		public void setRequires( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"A UDT function does not have its own provides/requires");
		}

		public void setTriggers( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"A UDT function may not have associated triggers");
		}

		public void setImplementor( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"A UDT function does not have its own implementor");
		}

		public String implementor()
		{
			return ui.implementor();
		}
	}

	abstract class AbstractUDTImpl
	extends AbstractAnnotationImpl
	implements Snippet
	{
		public String       name() { return _name; }
		public String     schema() { return _schema; }
		public String[] provides() { return _provides; }
		public String[] requires() { return _requires; }

		public String[] _provides;
		public String[] _requires;
		public String   _name;
		public String   _schema;

		TypeElement tclass;

		String qname;

		AbstractUDTImpl(TypeElement e)
		{
			tclass = e;

			if ( ! typu.isAssignable( e.asType(), TY_SQLDATA) )
			{
				msg( Kind.ERROR, e,	"A pljava UDT must implement %s",
					TY_SQLDATA);
			}

			ExecutableElement niladicCtor =	huntFor(
				constructorsIn( tclass.getEnclosedElements()), null, false,
					null);

			if ( null == niladicCtor )
			{
				msg( Kind.ERROR, tclass,
					"A pljava UDT must have a public no-arg constructor");
			}
		}

		protected void setQname()
		{
			if ( "".equals( _name) )
				_name = tclass.getSimpleName().toString();

			if ( "".equals( _schema) )
				qname = _name;
			else
				qname = _schema + "." + _name;
		}
	}

	class MappedUDTImpl
	extends AbstractUDTImpl
	implements MappedUDT
	{
		public String[]    structure() { return _structure; }

		String[] _structure;

		public void setStructure( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_structure = avToArray( o, String.class);
		}

		MappedUDTImpl(TypeElement e)
		{
			super( e);
		}

		public boolean characterize()
		{
			setQname();

			_requires = augmentRequires( _requires, implementor());

			return true;
		}

		public String[] deployStrings()
		{
			ArrayList<String> al = new ArrayList<String>();
			if ( null != structure() )
			{
				StringBuilder sb = new StringBuilder();
				sb.append( "CREATE TYPE ").append( qname).append( " AS (");
				int i = structure().length;
				for ( String s : structure() )
					sb.append( "\n\t").append( s).append(
						( 0 < -- i ) ? ',' : '\n');
				sb.append( ')');
				al.add( sb.toString());
			}
			al.add( "SELECT sqlj.add_type_mapping(" +
				DDRWriter.eQuote( qname) + ", " +
				DDRWriter.eQuote( tclass.toString()) + ')');
			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			ArrayList<String> al = new ArrayList<String>();
			al.add( "SELECT sqlj.drop_type_mapping(" +
				DDRWriter.eQuote( qname) + ')');
			if ( null != structure() )
				al.add( "DROP TYPE " + qname);
			return al.toArray( new String [ al.size() ]);
		}
	}

	class BaseUDTImpl
	extends AbstractUDTImpl
	implements BaseUDT
	{
		public String    typeModifierInput() { return _typeModifierInput; }
		public String   typeModifierOutput() { return _typeModifierOutput; }
		public String              analyze() { return _analyze; }
		public int          internalLength() { return _internalLength; }
		public boolean       passedByValue() { return _passedByValue; }
		public Alignment         alignment() { return _alignment; }
		public Storage             storage() { return _storage; }
		public String                 like() { return _like; }
		public char               category() { return _category; }
		public boolean           preferred() { return _preferred; }
		public String         defaultValue() { return _defaultValue; }
		public String              element() { return _element; }
		public char              delimiter() { return _delimiter; }
		public boolean          collatable() { return _collatable; }

		BaseUDTFunctionImpl in, out, recv, send;

		public String            _typeModifierInput;
		public String            _typeModifierOutput;
		public String            _analyze;
		int                      _internalLength;
		public Boolean           _passedByValue;
		Alignment                _alignment;
		Storage                  _storage;
		public String            _like;
		char                     _category;
		public Boolean           _preferred;
		String                   _defaultValue;
		public String            _element;
		char                     _delimiter;
		public Boolean           _collatable;

		boolean lengthExplicit;
		boolean alignmentExplicit;
		boolean storageExplicit;
		boolean categoryExplicit;
		boolean delimiterExplicit;

		public void setInternalLength( Object o, boolean explicit, Element e)
		{
			_internalLength = (Integer)o;
			lengthExplicit = explicit;
		}

		public void setAlignment( Object o, boolean explicit, Element e)
		{
			_alignment = Alignment.valueOf(
				((VariableElement)o).getSimpleName().toString());
			alignmentExplicit = explicit;
		}

		public void setStorage( Object o, boolean explicit, Element e)
		{
			_storage = Storage.valueOf(
				((VariableElement)o).getSimpleName().toString());
			categoryExplicit = explicit;
		}

		public void setDefaultValue( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_defaultValue = (String)o; // "" could be a real default value
		}

		public void setCategory( Object o, boolean explicit, Element e)
		{
			_category = (Character)o;
			categoryExplicit = explicit;
		}

		public void setDelimiter( Object o, boolean explicit, Element e)
		{
			_delimiter = (Character)o;
			delimiterExplicit = explicit;
		}

		BaseUDTImpl(TypeElement e)
		{
			super( e);
		}

		void registerFunctions()
		{
			setQname();

			ExecutableElement instanceReadSQL = huntFor(
				methodsIn( tclass.getEnclosedElements()), "readSQL", false,
					TY_VOID, TY_SQLINPUT, TY_STRING);

			ExecutableElement instanceWriteSQL = huntFor(
				methodsIn( tclass.getEnclosedElements()), "writeSQL", false,
					TY_VOID, TY_SQLOUTPUT);

			ExecutableElement instanceToString = huntFor(
				methodsIn( tclass.getEnclosedElements()), "toString", false,
					TY_STRING);

			ExecutableElement staticParse = huntFor(
				methodsIn( tclass.getEnclosedElements()), "parse", true,
					tclass.asType(), TY_STRING, TY_STRING);

			if ( null == staticParse )
			{
				msg( Kind.ERROR, tclass,
					"A pljava UDT must have a public static " +
					"parse(String,String) method that returns the UDT");
			}

			in = new BaseUDTFunctionImpl(
				this, tclass, BaseUDTFunctionID.INPUT);
			putSnippet( staticParse, in);

			out = new BaseUDTFunctionImpl(
				this, tclass, BaseUDTFunctionID.OUTPUT);
			putSnippet( null != instanceToString ? instanceToString : out, out);

			recv = new BaseUDTFunctionImpl(
				this, tclass, BaseUDTFunctionID.RECEIVE);
			putSnippet( null != instanceReadSQL ? instanceReadSQL : recv, recv);

			send = new BaseUDTFunctionImpl(
				this, tclass, BaseUDTFunctionID.SEND);
			putSnippet( null != instanceWriteSQL ? instanceWriteSQL : send,
				send);
		}

		public boolean characterize()
		{
			if ( "".equals( typeModifierInput())
				&& ! "".equals( typeModifierOutput()) )
				msg( Kind.ERROR, tclass,
					"UDT typeModifierOutput useless without typeModifierInput");

			if ( 1 > internalLength() && -1 != internalLength() )
				msg( Kind.ERROR, tclass,
					"UDT internalLength must be positive, or -1 for varying");

			if ( passedByValue() &&
				( 8 < internalLength() || -1 == internalLength() ) )
				msg( Kind.ERROR, tclass,
					"Only a UDT of fixed length <= 8 can be passed by value");

			if ( -1 == internalLength() &&
				-1 == alignment().compareTo( Alignment.INT4) )
				msg( Kind.ERROR, tclass,
					"A variable-length UDT must have alignment at least INT4");

			if ( -1 != internalLength() && Storage.PLAIN != storage() )
				msg( Kind.ERROR, tclass,
					"Storage for a fixed-length UDT must be PLAIN");

			// see PostgreSQL backend/commands/typecmds.c "must be simple ASCII"
			if ( 32 > category() || category() > 126 )
				msg( Kind.ERROR, tclass,
					"UDT category must be a printable ASCII character");

			_requires = augmentRequires( _requires, implementor());

			return true;
		}

		public String[] deployStrings()
		{
			ArrayList<String> al = new ArrayList<String>();
			al.add( "CREATE TYPE " + qname);

			al.addAll( Arrays.asList( in.deployStrings()));
			al.addAll( Arrays.asList( out.deployStrings()));
			al.addAll( Arrays.asList( recv.deployStrings()));
			al.addAll( Arrays.asList( send.deployStrings()));

			StringBuilder sb = new StringBuilder();
			sb.append( "CREATE TYPE ").append( qname).append( " (\n\t");
			in.appendTypeOp( sb).append( ",\n\t");
			out.appendTypeOp( sb).append( ",\n\t");
			recv.appendTypeOp( sb).append( ",\n\t");
			send.appendTypeOp( sb);

			if ( ! "".equals( typeModifierInput()) )
				sb.append( ",\n\tTYPMOD_IN = ").append( typeModifierInput());

			if ( ! "".equals( typeModifierOutput()) )
				sb.append( ",\n\tTYPMOD_OUT = ").append( typeModifierOutput());

			if ( ! "".equals( analyze()) )
				sb.append( ",\n\tANALYZE = ").append( typeModifierOutput());

			if ( lengthExplicit  ||  "".equals( like()) )
				sb.append( ",\n\tINTERNALLENGTH = ").append(
					-1 == internalLength() ? "VARIABLE"
					: String.valueOf( internalLength()));

			if ( passedByValue() )
				sb.append( ",\n\tPASSEDBYVALUE");

			if ( alignmentExplicit  ||  "".equals( like()) )
				sb.append( ",\n\tALIGNMENT = ").append( alignment().name());

			if ( storageExplicit  ||  "".equals( like()) )
				sb.append( ",\n\tSTORAGE = ").append( storage().name());

			if ( ! "".equals( like()) )
				sb.append( ",\n\tLIKE = ").append( like());

			if ( categoryExplicit )
				sb.append( ",\n\tCATEGORY = '").append(
					DDRWriter.eQuote( String.valueOf( category())));

			if ( preferred() )
				sb.append( ",\n\tPREFERRED = true");

			if ( null != defaultValue() )
				sb.append( ",\n\tDEFAULT = ").append(
					DDRWriter.eQuote( defaultValue()));

			if ( ! "".equals( element()) )
				sb.append( ",\n\tELEMENT = ").append( element());

			if ( delimiterExplicit )
				sb.append( ",\n\tDELIMITER = '").append(
					DDRWriter.eQuote( String.valueOf( delimiter())));

			if ( collatable() )
				sb.append( ",\n\tCOLLATABLE = true");

			al.add( sb.append( "\n)").toString());
			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			return new String[]
			{
				"DROP TYPE " + qname + " CASCADE"
			};
		}
	}

	/**
	 * Provides the default mappings from Java types to SQL types.
	 */
	class TypeMapper
	{
		ArrayList<Map.Entry<Class<?>, String>> protoMappings;
		ArrayList<Map.Entry<TypeMirror, String>> finalMappings;

		TypeMapper()
		{
			protoMappings = new ArrayList<Map.Entry<Class<?>, String>>();

			// Primitives
			//
			this.addMap(boolean.class, "boolean");
			this.addMap(Boolean.class, "boolean");
			this.addMap(byte.class, "smallint");
			this.addMap(Byte.class, "smallint");
			this.addMap(char.class, "smallint");
			this.addMap(Character.class, "smallint");
			this.addMap(double.class, "double precision");
			this.addMap(Double.class, "double precision");
			this.addMap(float.class, "real");
			this.addMap(Float.class, "real");
			this.addMap(int.class, "integer");
			this.addMap(Integer.class, "integer");
			this.addMap(long.class, "bigint");
			this.addMap(Long.class, "bigint");
			this.addMap(short.class, "smallint");
			this.addMap(Short.class, "smallint");

			// Known common mappings
			//
			this.addMap(Number.class, "numeric");
			this.addMap(String.class, "varchar");
			this.addMap(java.util.Date.class, "timestamp");
			this.addMap(Timestamp.class, "timestamp");
			this.addMap(Time.class, "time");
			this.addMap(java.sql.Date.class, "date");
			this.addMap(BigInteger.class, "numeric");
			this.addMap(BigDecimal.class, "numeric");
			this.addMap(ResultSet.class, "record");
			this.addMap(Object.class, "\"any\"");

			this.addMap(byte[].class, "bytea");
		}

		/*
		 * What worked in Java 6 was to keep a list of Class<?> -> sqltype
		 * mappings, and get TypeMirrors from the Classes at the time of trying
		 * to identify types (in the final, after-all-sources-processed round).
		 * Starting in Java 7, you get different TypeMirror instances in
		 * different rounds for the same types, so you can't match something
		 * seen in round 1 to something looked up in the final round. (However,
		 * you can match things seen in round 1 to things looked up prior to
		 * the first round, when init() is called and constructs the processor.)
		 *
		 * So, this method needs to be called at the end of round 1 (or at the
		 * end of every round, it just won't do anything but once), and at that
		 * point it will compute the list order and freeze a list of TypeMirrors
		 * to avoid looking up the Class<?>es later and getting different
		 * mirrors.
		 *
		 * This should work as long as all the sources containg pljava
		 * annotations will be found in round 1. That would only not be the case
		 * if some other annotation processor is in use that could generate new
		 * sources with pljava annotations in them, requiring additional rounds.
		 * In the present state of things, that simply won't work. Java bug
		 * http://bugs.java.com/bugdatabase/view_bug.do?bug_id=8038455 might
		 * cover this, and promises a fix in Java 9, but who knows?
		 */
		private void workAroundJava7Breakage()
		{
			if ( null != finalMappings )
				return; // after the first round, it's too late!

			// Need to check more specific types before those they are
			// assignable to by widening reference conversions, so a
			// topological sort is in order.
			//
			List<Vertex<Map.Entry<Class<?>, String>>> vs =
				new ArrayList<Vertex<Map.Entry<Class<?>, String>>>(
					protoMappings.size());

			for ( Map.Entry<Class<?>, String> me : protoMappings )
				vs.add( new Vertex<Map.Entry<Class<?>, String>>( me));

			for ( int i = vs.size(); i --> 1; )
			{
				Vertex<Map.Entry<Class<?>, String>> vi = vs.get( i);
				Class<?> ci = vi.payload.getKey();
				for ( int j = i; j --> 0; )
				{
					Vertex<Map.Entry<Class<?>, String>> vj = vs.get( j);
					Class<?> cj = vj.payload.getKey();
					boolean oij = ci.isAssignableFrom( cj);
					boolean oji = cj.isAssignableFrom( ci);
					if ( oji == oij )
						continue; // no precedence constraint between these two
					if ( oij )
						vj.precede( vi);
					else
						vi.precede( vj);
				}
			}

			Queue<Vertex<Map.Entry<Class<?>, String>>> q =
				new LinkedList<Vertex<Map.Entry<Class<?>, String>>>();
			for ( Vertex<Map.Entry<Class<?>, String>> v : vs )
				if ( 0 == v.indegree )
					q.add( v);

			finalMappings = new ArrayList<Map.Entry<TypeMirror, String>>(
				protoMappings.size());
			protoMappings.clear();

			while ( ! q.isEmpty() )
			{
				Vertex<Map.Entry<Class<?>, String>> v = q.remove();
				v.use( q);
				Class<?> k = v.payload.getKey();
				TypeMirror ktm;
				if ( k.isPrimitive() )
				{
					TypeKind tk = 
						TypeKind.valueOf( k.getName().toUpperCase());
					ktm = typu.getPrimitiveType( tk);
				}
				else
				{
					TypeElement te =
						elmu.getTypeElement( k.getName());
					if ( null == te ) // can't find it -> not used in code?
					{
						msg( Kind.WARNING,
							"Found no TypeElement for %s", k.getName());
						continue; // hope it wasn't one we'll need!
					}
					ktm = te.asType();
				}
				finalMappings.add(
					new AbstractMap.SimpleImmutableEntry<TypeMirror, String>(
						ktm, v.payload.getValue()));
			}
		}

		/**
		 * Add a custom mapping from a Java class to an SQL type.
		 *
		 * @param k Class representing the Java type
		 * @param v String representing the SQL type to be used
		 */
		void addMap(Class<?> k, String v)
		{
			if ( null != finalMappings )
			{
				msg( Kind.ERROR,
					"addMap(%s, %s)\n" +
					"called after workAroundJava7Breakage", k.getName(), v);
				return;
			}
			protoMappings.add(
				new AbstractMap.SimpleImmutableEntry<Class<?>, String>( k, v));
		}

		/**
		 * Return the SQL type for the Java type represented by a TypeMirror,
		 * from an explicit annotation if present, otherwise by applying the
		 * default mappings. No default-value information is included in the
		 * string returned. It is assumed that a function return is being typed
		 * rather than a function parameter.
		 *
		 * @param tm Represents the type whose corresponding SQL type is wanted.
		 * @param e Annotated element (chiefly for use as a location hint in
		 * diagnostic messages).
		 */
		String getSQLType(TypeMirror tm, Element e)
		{
			return getSQLType( tm, e, false, false);
		}


		/**
		 * Return the SQL type for the Java type represented by a TypeMirror,
		 * from an explicit annotation if present, otherwise by applying the
		 * default mappings.
		 *
		 * @param tm Represents the type whose corresponding SQL type is wanted.
		 * @param e Annotated element (chiefly for use as a location hint in
		 * diagnostic messages).
		 * @param contravariant Indicates that the element whose type is wanted
		 * is a function parameter and should be given the widest type that can
		 * be assigned to it. If false, find the narrowest type that a function
		 * return can be assigned to.
		 * @param withDefault Indicates whether any specified default value
		 * information should also be included in the "type" string returned.
		 */
		String getSQLType(TypeMirror tm, Element e,
			boolean contravariant, boolean withDefault)
		{
			boolean array = false;
			String rslt = null;
			
			String[] defaults = null;
			
			for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
			{
				if ( am.getAnnotationType().asElement().equals( AN_SQLTYPE) )
				{
					SQLTypeImpl sti = new SQLTypeImpl();
					populateAnnotationImpl( sti, e, am);
					rslt = sti.value();
					defaults = sti.defaultValue();
				}
			}

			if ( tm.getKind().equals( TypeKind.ARRAY) )
			{
				ArrayType at = ((ArrayType)tm);
				if ( ! at.getComponentType().getKind().equals( TypeKind.BYTE) )
				{
					array = true;
					tm = at.getComponentType();
					// only for bytea[] should this ever still be an array
				}
			}
			
			if ( null != rslt )
				return typeWithDefault( rslt, array, defaults, withDefault);

			if ( tm.getKind().equals( TypeKind.VOID) )
				return "void"; // can't be a parameter type so no defaults apply

			if ( tm.getKind().equals( TypeKind.ERROR) )
			{
				msg ( Kind.ERROR, e,
					"Cannot determine mapping to SQL type for unresolved type");
				rslt = tm.toString();
			}
			else
			{    
				ArrayList<Map.Entry<TypeMirror, String>> ms = finalMappings;
				if ( contravariant )
				{
					ms = (ArrayList<Map.Entry<TypeMirror, String>>)ms.clone();
					Collections.reverse( ms);
				}
				for ( Map.Entry<TypeMirror, String> me : ms )
				{
					TypeMirror ktm = me.getKey();
					if ( ktm instanceof PrimitiveType )
					{
						if ( typu.isSameType( tm, ktm) )
						{
							rslt = me.getValue();
							break;
						}
					}
					else
					{
						boolean accept;
						if ( contravariant )
							accept = typu.isAssignable( ktm, tm);
						else
							accept = typu.isAssignable( tm, ktm);
						if ( accept )
						{
							// don't compute a type of Object/"any" for
							// a function return (just admit defeat instead)
							if ( contravariant
								|| ! typu.isSameType( ktm, TY_OBJECT) )
								rslt = me.getValue();
							break;
						}
					}
				}
			}

			if ( null == rslt )
			{
				msg( Kind.ERROR, e,
					"No known mapping to an SQL type");
				rslt = tm.toString();
			}

			if ( array )
				rslt += "[]";
			
			return typeWithDefault( rslt, array, defaults, withDefault);
		}
		
		/**
		 * Given the matching SQL type already determined, return it with or
		 * without default-value information appended, as the caller desires.
		 * To ensure that the generated descriptor will be in proper form, the
		 * default values are emitted as properly-escaped string literals and
		 * then cast to the appropriate type. This approach will not work for
		 * defaults given as arbitrary SQL expressions, but covers the typical
		 * cases of simple literals and even anything that can be computed as
		 * a Java String constant expression (e.g. ""+Math.PI).
		 *
		 * @param rslt The bare SQL type string already determined
		 * @param array Whether the Java type was determined to be an array
		 * @param defaults Array (null if not present) of default value strings
		 * @param withDefault Whether to append the default information to the
		 * type.
		 */
		String typeWithDefault(
			String rslt, boolean array, String[] defaults, boolean withDefault)
		{
			if ( null == defaults || ! withDefault )
				return rslt;
			
			int n = defaults.length;
			if ( n != 1 )
				array = true;
			else if ( ! array )
				array = arrayish.matcher( rslt).matches();
			
			StringBuilder sb = new StringBuilder( rslt);
			sb.append( " DEFAULT CAST(");
			if ( array )
				sb.append( "ARRAY[");
			if ( n != 1 )
				sb.append( "\n\t");
			for ( String s : defaults )
			{
				sb.append( DDRWriter.eQuote( s));
				if ( 0 < -- n )
					sb.append( ",\n\t");
			}
			if ( array )
				sb.append( ']');
			sb.append( " AS ").append( rslt).append( ')');
			return sb.toString();
		}
	}

	// expression intended to match SQL types that are arrays
	static final Pattern arrayish =
		Pattern.compile( "(?si:(?:\\[\\s*\\d*\\s*\\]|ARRAY)\\s*)$");
}

/**
 * A code snippet. May contain zero, one, or more complete SQL commands for
 * each of deploying and undeploying. The commands contained in one Snippet
 * will always be emitted in a fixed order. A collection of Snippets will be
 * output in an order constrained by their provides and requires methods.
 */
interface Snippet
{
	/**
	 * An {@code <implementor name>} that will be used to wrap each command
	 * from this Snippet as an {@code <implementor block>}. If null, the
	 * commands will be emitted as plain {@code <SQL statement>}s.
	 */
	public String implementor();
	/**
	 * Return an array of SQL commands (one complete command to a string) to
	 * be executed in order during deployment.
	 */
	public String[] deployStrings();
	/**
	 * Return an array of SQL commands (one complete command to a string) to
	 * be executed in order during undeployment.
	 */
	public String[] undeployStrings();
	/**
	 * Return an array of arbitrary labels considered "provided" by this
	 * Snippet. In generating the final order of the deployment descriptor file,
	 * this Snippet will come before any whose requires method returns any of
	 * the same labels.
	 */
	public String[] provides();
	/**
	 * Return an array of arbitrary labels considered "required" by this
	 * Snippet. In generating the final order of the deployment descriptor file,
	 * this Snippet will come after those whose provides method returns any of
	 * the same labels.
	 */
	public String[] requires();
	/**
	 * Method to be called on the final round, after all annotations'
	 * element/value pairs have been filled in, to compute any additional
	 * information derived from those values before deployStrings() or
	 * undeployStrings() can be called.
	 * @return true if this Snippet is standalone and should be scheduled and
	 * emitted based on provides/requires; false if something else will emit it.
	 */
	public boolean characterize();
}

/**
 * Vertex in a DAG, as used to put things in workable topological order
 */
class Vertex<P>
{
	P payload;
	int indegree;
	List<Vertex<P>> adj;
	
	Vertex( P payload)
	{
		this.payload = payload;
		indegree = 0;
		adj = new ArrayList<Vertex<P>>();
	}
	
	void precede( Vertex<P> v)
	{
		++ v.indegree;
		adj.add( v);
	}
	
	void use( Collection<Vertex<P>> q)
	{
		for ( Vertex<P> v : adj )
			if ( 0 == -- v.indegree )
				q.add( v);
	}

	void use( Collection<Vertex<P>> q, Collection<Vertex<P>> vs)
	{
		for ( Vertex<P> v : adj )
			if ( 0 == -- v.indegree )
			{
				vs.remove( v);
				q.add( v);
			}
	}
}
