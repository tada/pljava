/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
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
package org.postgresql.pljava.annotation.processing;

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

import java.text.BreakIterator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import static java.util.Collections.unmodifiableSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import static java.util.Objects.hash;
import static java.util.Objects.requireNonNull;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import java.util.function.Supplier;

import java.util.stream.Stream;
import static java.util.stream.Collectors.joining;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static java.util.regex.Pattern.compile;

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

import org.postgresql.pljava.sqlgen.Lexicals;
import static org.postgresql.pljava.sqlgen.Lexicals
	.ISO_AND_PG_IDENTIFIER_CAPTURING;
import static org.postgresql.pljava.sqlgen.Lexicals.ISO_REGULAR_IDENTIFIER_PART;
import static org.postgresql.pljava.sqlgen.Lexicals.PG_REGULAR_IDENTIFIER_PART;
import static org.postgresql.pljava.sqlgen.Lexicals.SEPARATOR;
import static org.postgresql.pljava.sqlgen.Lexicals.identifierFrom;
import static org.postgresql.pljava.sqlgen.Lexicals.separator;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier;
import static org.postgresql.pljava.sqlgen.Lexicals.Identifier.Simple.pgFold;

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
  "ddr.reproducible",    // default true
  "ddr.name.trusted",    // default "java"
  "ddr.name.untrusted",  // default "javaU"
  "ddr.implementor",     // implementor when not annotated, default "PostgreSQL"
  "ddr.output"           // name of ddr file to write
})
@SupportedSourceVersion(SourceVersion.RELEASE_9)
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
	final SnippetTiebreaker   snippetTiebreaker;

	// Options obtained from the invocation
	//	
	final String nameTrusted;
	final String nameUntrusted;
	final String output;
	final Identifier.Simple defaultImplementor;
	final boolean reproducible;
	
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

	// Certain familiar DBTypes (capitalized as this file historically has)
	//
	final DBType DT_RECORD  = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.RECORD"));
	final DBType DT_TRIGGER = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.trigger"));
	final DBType DT_VOID = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.void"));

	// Function signatures for certain known functions
	//
	final DBType[] SIG_TYPMODIN =
		{ DBType.fromSQLTypeAnnotation("pg_catalog.cstring[]") };
	final DBType[] SIG_TYPMODOUT =
		{ DBType.fromSQLTypeAnnotation("integer") };
	final DBType[] SIG_ANALYZE =
		{ DBType.fromSQLTypeAnnotation("pg_catalog.internal") };
	
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
			defaultImplementor = "-".equals( optv) ? null :
				Identifier.Simple.fromJava(optv);
		else
			defaultImplementor = Identifier.Simple.fromJava("PostgreSQL");

		optv = opts.get( "ddr.output");
		if ( null != optv )
			output = optv;
		else
			output = "pljava.ddr";

		optv = opts.get( "ddr.reproducible");
		if ( null != optv )
			reproducible = Boolean.parseBoolean( optv);
		else
			reproducible = true;

		snippetTiebreaker = reproducible ? new SnippetTiebreaker() : null;
		
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
	Map<SnippetsKey, Snippet> snippets = new HashMap<>();

	<S extends Snippet> S getSnippet(Object o, Class<S> c, Supplier<S> ctor)
	{
		return
			c.cast(snippets
				.computeIfAbsent(new SnippetsKey( o, c), k -> ctor.get()));
	}

	void putSnippet( Object o, Snippet s)
	{
		snippets.put( new SnippetsKey( o, s.getClass()), s);
	}

	/**
	 * Queue on which snippets are entered in preparation for topological
	 * ordering. Has to be an instance field because populating the queue
	 * (which involves invoking the snippets' characterize methods) cannot
	 * be left to generateDescriptor, which runs in the final round. This is
	 * (AFAICT) another workaround for javac 7's behavior of throwing away
	 * symbol tables between rounds; when characterize was invoked in
	 * generateDescriptor, any errors reported were being shown with no source
	 * location info, because it had been thrown away.
	 */
	List<VertexPair<Snippet>> snippetVPairs = new ArrayList<>();

	/**
	 * Map from each arbitrary provides/requires label to the snippet
	 * that 'provides' it. Has to be out here as an instance field for the
	 * same reason {@code snippetVPairs} does.
	 */
	Map<DependTag, VertexPair<Snippet>> provider = new HashMap<>();
	
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

		if ( ! re.processingOver() )
			defensiveEarlyCharacterize();
		else if ( ! re.errorRaised() )
			generateDescriptor();

		return willClaim;
	}

	/**
	 * Iterate over collected snippets, characterize them, and enter them
	 * (if no error) in the data structures for topological ordering. Was
	 * originally the first part of {@code generateDescriptor}, but that is
	 * run in the final round, which is too late for javac 7 anyway, which
	 * throws symbol tables away between rounds. Any errors reported from
	 * characterize were being shown without source locations, because the
	 * information was gone. This may now be run more than once, so the
	 * {@code snippets} map is cleared before returning.
	 */
	void defensiveEarlyCharacterize()
	{
		for ( Snippet snip : snippets.values() )
		{
			if ( ! snip.characterize() )
				continue;
			VertexPair<Snippet> v = new VertexPair<>( snip);
			snippetVPairs.add( v);
			for ( DependTag s : snip.provideTags() )
				if ( null != provider.put( s, v) )
					msg( Kind.ERROR, "tag %s has more than one provider", s);
		}
		snippets.clear();
	}

	/**
	 * Arrange the collected snippets into a workable sequence (nothing with
	 * requires="X" can come before whatever has provides="X"), then create
	 * a deployment descriptor file in proper form.
	 */
	void generateDescriptor()
	{
		boolean errorRaised = false;
		Set<DependTag> fwdConsumers = new HashSet<>();
		Set<DependTag> revConsumers = new HashSet<>();

		for ( VertexPair<Snippet> v : snippetVPairs )
		{
			VertexPair<Snippet> p;

			/*
			 * First handle the implicit requires(implementor()). This is unlike
			 * the typical provides/requires relationship, in that it does not
			 * reverse when generating the 'remove' actions. Conditions that
			 * determined what got installed must also be evaluated early and
			 * determine what gets removed.
			 */
			Identifier.Simple impName = v.payload().implementorName();
			DependTag imp = v.payload().implementorTag();
			if ( null != imp )
			{
				p = provider.get( imp);
				if ( null != p )
				{
					fwdConsumers.add( imp);
					revConsumers.add( imp);

					p.fwd.precede( v.fwd);
					p.rev.precede( v.rev);

					/*
					 * A snippet providing an implementor tag probably has no
					 * undeployStrings, because its deployStrings should be used
					 * on both occasions; if so, replace it with a proxy that
					 * returns deployStrings for undeployStrings.
					 */
					if ( 0 == p.rev.payload.undeployStrings().length )
						p.rev.payload = new ImpProvider( p.rev.payload);
				}
				else if ( ! defaultImplementor.equals( impName, msgr) )
				{
					/*
					 * Don't insist that every implementor tag have a provider
					 * somewhere in the code. Perhaps the environment will
					 * provide it at load time. If this is not the default
					 * implementor, bump the relying vertices' indegree anyway
					 * so the snippet won't be emitted until the cycle-breaker
					 * code (see below) sets it free after any others that
					 * can be handled first.
					 */
					++ v.fwd.indegree;
					++ v.rev.indegree;
				}
			}
			for ( DependTag s : v.payload().requireTags() )
			{
				p = provider.get( s);
				if ( null != p )
				{
					fwdConsumers.add( s);
					revConsumers.add( s);
					p.fwd.precede( v.fwd);
					v.rev.precede( p.rev); // these relationships do reverse
				}
				else if ( s instanceof DependTag.Explicit )
				{
					msg( Kind.ERROR,
						"tag \"%s\" is required but nowhere provided", s);
					errorRaised = true;
				}
			}
		}

		if ( errorRaised )
			return;

		Queue<Vertex<Snippet>> fwdBlocked = new LinkedList<>();
		Queue<Vertex<Snippet>> revBlocked = new LinkedList<>();

		Queue<Vertex<Snippet>> fwdReady;
		Queue<Vertex<Snippet>> revReady;
		if ( reproducible )
		{
			fwdReady = new PriorityQueue<>( 11, snippetTiebreaker);
			revReady = new PriorityQueue<>( 11, snippetTiebreaker);
		}
		else
		{
			fwdReady = new LinkedList<>();
			revReady = new LinkedList<>();
		}

		for ( VertexPair<Snippet> vp : snippetVPairs )
		{
			Vertex<Snippet> v = vp.fwd;
			if ( 0 == v.indegree )
				fwdReady.add( v);
			else
				fwdBlocked.add( v);
			v = vp.rev;
			if ( 0 == v.indegree )
				revReady.add( v);
			else
				revBlocked.add( v);
		}

		Snippet[] fwdSnips = order( fwdReady, fwdBlocked, fwdConsumers, true);
		Snippet[] revSnips = order( revReady, revBlocked, revConsumers, false);

		if ( null == fwdSnips  ||  null == revSnips )
			return; // error already reported

		try
		{
			DDRWriter.emit( fwdSnips, revSnips, this);
		}
		catch ( IOException ioe )
		{
			msg( Kind.ERROR, "while writing %s: %s", output, ioe.getMessage());
		}
	}

	/**
	 * Given a Snippet DAG, either the forward or reverse one, return the
	 * snippets in a workable order.
	 * @return Array of snippets in order, or null if no suitable order could
	 * be found.
	 */
	Snippet[] order(
		Queue<Vertex<Snippet>> ready, Queue<Vertex<Snippet>> blocked,
		Set<DependTag> consumer, boolean deploying)
	{
		ArrayList<Snippet> snips = new ArrayList<>(ready.size()+blocked.size());
		Vertex<Snippet> cycleBreaker = null;

queuerunning:
		for ( ; ; )
		{
			while ( ! ready.isEmpty() )
			{
				Vertex<Snippet> v = ready.remove();
				snips.add(v.payload);
				v.use(ready, blocked);
				for ( DependTag p : v.payload.provideTags() )
					consumer.remove(p);
			}
			if ( blocked.isEmpty() )
				break; // all done

			/*
			 * There are snippets remaining to output but they all have
			 * indegree > 0, normally a 'cycle' error. But some may have
			 * breakCycle methods that can help. Add any vertices they return
			 * onto the ready queue (all at once, so that for reproducible
			 * builds, the ready queue's ordering constraints will take effect).
			 */
			boolean cycleBroken = false;
			for ( Iterator<Vertex<Snippet>> it = blocked.iterator();
					it.hasNext(); )
			{
				Vertex<Snippet> v = it.next();
				cycleBreaker = v.payload.breakCycle(v, deploying);
				if ( null == cycleBreaker )
					continue;
				/*
				 * If v supplied another vertex to go on the ready queue, leave
				 * v on the blocked queue; it should become ready in due course.
				 * If v nominated itself as cycle breaker, remove from blocked.
				 */
				if ( cycleBreaker == v )
					it.remove();
				ready.add(cycleBreaker);
				cycleBroken = true;
			}
			if ( cycleBroken )
				continue;

			/*
			 * A cycle was detected and no snippet's breakCycle method broke it,
			 * but there may yet be a way. Somewhere there may be a vertex
			 * with indegree exactly 1 and an implicit requirement of its
			 * own implementor tag, with no snippet on record to provide it.
			 * That's allowed (maybe the installing/removing environment will
			 * be "providing" that tag anyway), so set one such snippet free
			 * and see how much farther we get.
			 */
			for ( Iterator<Vertex<Snippet>> it = blocked.iterator();
					it.hasNext(); )
			{
				Vertex<Snippet> v = it.next();
				if ( 1 < v.indegree )
					continue;
				Identifier.Simple impName = v.payload.implementorName();
				if ( null == impName
					|| defaultImplementor.equals( impName, msgr) )
					continue;
				if ( provider.containsKey( v.payload.implementorTag()) )
					continue;
				if ( reproducible )
				{
					if (null == cycleBreaker ||
						0 < snippetTiebreaker.compare(cycleBreaker, v))
						cycleBreaker = v;
				}
				else
				{
					-- v.indegree;
					it.remove();
					ready.add( v);
					continue queuerunning;
				}
			}
			if ( null != cycleBreaker )
			{
				blocked.remove( cycleBreaker);
				-- cycleBreaker.indegree;
				ready.add( cycleBreaker);
				cycleBreaker = null;
				continue;
			}
			/*
			 * Got here? It's a real cycle ... nothing to be done.
			 */
			for ( DependTag s : consumer )
				msg( Kind.ERROR, "requirement in a cycle: %s", s);
			return null;
		}
		return snips.toArray(new Snippet[snips.size()]);
	}
	
	/**
	 * Process a single element annotated with @SQLAction.
	 */
	void processSQLAction( Element e)
	{
		SQLActionImpl sa =
			getSnippet( e, SQLActionImpl.class, SQLActionImpl::new);
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
		/*
		 * The allowed target type for the UDT annotations is TYPE, which can
		 * be a class, interface (including annotation type) or enum, of which
		 * only CLASS is valid here. If it is anything else, just return, as
		 * that can only mean a source error prevented the compiler making sense
		 * of it, and the compiler will have its own messages about that.
		 */
		switch ( e.getKind() )
		{
			case CLASS:
				break;
			case ANNOTATION_TYPE:
			case ENUM:
			case INTERFACE:
				msg( Kind.ERROR, e, "A pljava UDT must be a class");
			default:
				return;
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
			BaseUDTImpl bu = getSnippet( e, BaseUDTImpl.class, () ->
				new BaseUDTImpl( (TypeElement)e));
			for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
			{
				if ( am.getAnnotationType().asElement().equals( AN_BASEUDT) )
					populateAnnotationImpl( bu, e, am);
			}
			bu.registerFunctions();
			break;

		case MAPPED:
			MappedUDTImpl mu = getSnippet( e, MappedUDTImpl.class, () ->
				new MappedUDTImpl( (TypeElement)e));
			for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
			{
				if ( am.getAnnotationType().asElement().equals( AN_MAPPEDUDT) )
					populateAnnotationImpl( mu, e, am);
			}
			mu.registerMapping();
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
		/*
		 * METHOD is the only target type allowed for the Function annotation,
		 * so the only way for e to be anything else is if some source error has
		 * prevented the compiler making sense of it. In that case just return
		 * silently on the assumption that the compiler will have its own
		 * message about the true problem.
		 */
		if ( ! ElementKind.METHOD.equals( e.getKind()) )
			return;

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

		FunctionImpl f = getSnippet( e, FunctionImpl.class, () ->
			new FunctionImpl( (ExecutableElement)e));
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
			Object v = getValue( av);
			if ( isEnum )
			{
				@SuppressWarnings({"unchecked"})
				T t = (T)Enum.valueOf( k.asSubclass( Enum.class),
					((VariableElement)v).getSimpleName().toString());
				a[i++] = t;
			}
			else
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
		private Set<DependTag> m_provideTags = new HashSet<>();
		private Set<DependTag> m_requireTags = new HashSet<>();

		@Override
		public Class<? extends Annotation> annotationType()
		{
			throw new UnsupportedOperationException();
		}

		/**
		 * Supply the required implementor() method for those subclasses
		 * that will implement {@link Snippet}.
		 */
		public String implementor()
		{
			return null == _implementor ? null : _implementor.pgFolded();
		}

		/**
		 * Supply the required implementor() method for those subclasses
		 * that will implement {@link Snippet}.
		 */
		public Identifier.Simple implementorName()
		{
			return _implementor;
		}

		Identifier.Simple _implementor = defaultImplementor;
		String _comment;

		public void setImplementor( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_implementor = "".equals( o) ? null :
					Identifier.Simple.fromJava((String)o, msgr);
		}

		@Override
		public String toString()
		{
			return String.format(
				"(%s)%s", getClass().getSimpleName(), _comment);
		}

		public String comment() { return _comment; }

		public void setComment( Object o, boolean explicit, Element e)
		{
			if ( explicit )
			{
				_comment = (String)o;
				if ( "".equals( _comment) )
					_comment = null;
			}
			else
				_comment = ((Commentable)this).derivedComment( e);
		}

		public String derivedComment( Element e)
		{
			String dc = elmu.getDocComment( e);
			if ( null == dc )
				return null;
			return firstSentence( dc);
		}

		public String firstSentence( String s)
		{
			BreakIterator bi = BreakIterator.getSentenceInstance( loca);
			bi.setText( s);
			int start = bi.first();
			int end = bi.next();
			if ( BreakIterator.DONE == end )
				return null;
			return s.substring( start, end).trim();
		}

		/**
		 * Called by a snippet's {@code characterize} method to install its
		 * explicit, annotation-supplied 'provides' / 'requires' strings, if
		 * any, into the {@code provideTags} and {@code requireTags} sets, then
		 * making those sets immutable.
		 */
		protected void recordExplicitTags(String[] provides, String[] requires)
		{
			if ( null != provides )
				for ( String s : provides )
					m_provideTags.add(new DependTag.Explicit(s));
			if ( null != requires )
				for ( String s : requires )
					m_requireTags.add(new DependTag.Explicit(s));
			m_provideTags = unmodifiableSet(m_provideTags);
			m_requireTags = unmodifiableSet(m_requireTags);
		}

		/**
		 * Return the set of 'provide' tags, mutable before
		 * {@code recordExplicitTags} has been called, immutable thereafter.
		 */
		public Set<DependTag> provideTags()
		{
			return m_provideTags;
		}

		/**
		 * Return the set of 'require' tags, mutable before
		 * {@code recordExplicitTags} has been called, immutable thereafter.
		 */
		public Set<DependTag> requireTags()
		{
			return m_requireTags;
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
			Class<? extends Annotation> kl = inst.getClass();
			try
			{
				Object v = getValue( av);
				kl.getMethod( // let setter for foo() be setFoo()
					"set"+name.substring( 0, 1).toUpperCase() +
						name.substring( 1),
					Object.class, boolean.class, Element.class)
					.invoke(inst, v, isExplicit, e);
			}
			catch (AnnotationValueException ave)
			{
				msg( Kind.ERROR, e, am,
					"unresolved value for annotation member \"%s\"" +
					" (check for missing/misspelled import, etc.)",
					name);
			}
			catch (NoSuchMethodException nsme)
			{
				Object v = getValue( av);
				try
				{
					Field f = kl.getField( "_"+name);
					Class<?> fkl = f.getType();
					if ( ! isExplicit  &&  null != f.get( inst) )
						continue;
					if ( fkl.isArray() )
					{
						try {
							f.set( inst, avToArray( v, fkl.getComponentType()));
						}
						catch (AnnotationValueException ave)
						{
							msg( Kind.ERROR, e, am,
							"unresolved value for an element of annotation" +
							" member \"%s\" (check for missing/misspelled" +
							" import, etc.)",
							name);
						}
					}
					else if ( fkl.isEnum() )
					{
						@SuppressWarnings("unchecked")
						Object t = Enum.valueOf( fkl.asSubclass( Enum.class),
							((VariableElement)v).getSimpleName().toString());
						f.set( inst, t);
					}
					else
						f.set( inst, v);
					nsme = null;
				}
				catch (NoSuchFieldException | IllegalAccessException ex) { }
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
		public String name() { return _name; }
		
		String _value;
		String[] _defaultValue;
		String _name;
		
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

		public void setName( Object o, boolean explicit, Element e)
		{
			if ( ! explicit )
				return;

			_name = (String)o;
			if ( _name.startsWith( "\"")
				&& ! Lexicals.ISO_DELIMITED_IDENTIFIER.matcher( _name).matches()
				)
				msg( Kind.WARNING, e, "malformed parameter name: %s", _name);
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
			recordExplicitTags(_provides, _requires);
			return true;
		}
	}
	
	class TriggerImpl
	extends AbstractAnnotationImpl
	implements Trigger, Snippet, Commentable
	{
		public String[]    arguments() { return _arguments; }
		public Constraint constraint() { return _constraint; }
		public Event[]        events() { return _events; }
		public String     fromSchema() { return _fromSchema; }
		public String           from() { return _from; }
		public String           name() { return _name; }
		public String         schema() { return _schema; }
		public String          table() { return _table; }
		public Scope           scope() { return _scope; }
		public Called         called() { return _called; }
		public String           when() { return _when; }
		public String[]      columns() { return _columns; }
		public String       tableOld() { return _tableOld; }
		public String       tableNew() { return _tableNew; }
		
		public String[] provides() { return new String[0]; }
		public String[] requires() { return new String[0]; }
		/* Trigger is a Snippet but doesn't directly participate in tsort */

		public String[]   _arguments;
		public Constraint _constraint;
		public Event[]    _events;
		public String     _fromSchema;
		public String     _from;
		public String     _name;
		public String     _schema;
		public String  	  _table;
		public Scope      _scope;
		public Called     _called;
		public String     _when;
		public String[]   _columns;
		public String     _tableOld;
		public String     _tableNew;
		
		FunctionImpl func;
		AnnotationMirror origin;

		boolean refOld;
		boolean refNew;
		boolean isConstraint = false;

		/* The only values of the Constraint enum are those applicable to
		 * constraint triggers. To determine whether this IS a constraint
		 * trigger or not, use the 'explicit' parameter to distinguish whether
		 * the 'constraint' attribute was or wasn't seen in the annotation.
		 */
		public void setConstraint( Object o, boolean explicit, Element e)
		{
			if ( explicit )
			{
				isConstraint = true;
				_constraint = Constraint.valueOf(
					((VariableElement)o).getSimpleName().toString());
			}
		}
		
		TriggerImpl( FunctionImpl f, AnnotationMirror am)
		{
			func = f;
			origin = am;
		}

		public boolean characterize()
		{
			if ( Scope.ROW.equals( _scope) )
			{
				for ( Event e : _events )
					if ( Event.TRUNCATE.equals( e) )
						msg( Kind.ERROR, func.func, origin,
							"TRUNCATE trigger cannot be FOR EACH ROW");
			}
			else if ( Called.INSTEAD_OF.equals( _called) )
				msg( Kind.ERROR, func.func, origin,
					"INSTEAD OF trigger cannot be FOR EACH STATEMENT");

			if ( ! "".equals( _when) && Called.INSTEAD_OF.equals( _called) )
				msg( Kind.ERROR, func.func, origin,
					"INSTEAD OF triggers do not support WHEN conditions");

			if ( 0 < _columns.length )
			{
				if ( Called.INSTEAD_OF.equals( _called) )
					msg( Kind.ERROR, func.func, origin,
						"INSTEAD OF triggers do not support lists of columns");
				boolean seen = false;
				for ( Event e : _events )
					if ( Event.UPDATE.equals( e) )
						seen = true;
				if ( ! seen )
					msg( Kind.ERROR, func.func, origin,
				"Column list is meaningless unless UPDATE is a trigger event");
			}

			refOld = ! "".equals( _tableOld);
			refNew = ! "".equals( _tableNew);

			if ( refOld || refNew )
			{
				if ( ! Called.AFTER.equals( _called) )
					msg( Kind.ERROR, func.func, origin,
					"Only AFTER triggers can reference OLD TABLE or NEW TABLE");
				boolean badOld = refOld;
				boolean badNew = refNew;
				for ( Event e : _events )
				{
					switch ( e )
					{
						case INSERT:          badNew = false; break;
						case UPDATE: badOld = badNew = false; break;
						case DELETE: badOld =          false; break;
					}
				}
				if ( badOld )
					msg( Kind.ERROR, func.func, origin,
		 "Trigger must be callable on UPDATE or DELETE to reference OLD TABLE");
				if ( badNew )
					msg( Kind.ERROR, func.func, origin,
		 "Trigger must be callable on UPDATE or INSERT to reference NEW TABLE");
			}

			if ( isConstraint )
			{
				if ( ! Called.AFTER.equals( _called) )
					msg( Kind.ERROR, func.func, origin,
						"A constraint trigger must be an AFTER trigger");
				if ( ! Scope.ROW.equals( _scope) )
					msg( Kind.ERROR, func.func, origin,
						"A constraint trigger must be FOR EACH ROW");
				if ( "".equals( _from) && ! "".equals( _fromSchema) )
					msg( Kind.ERROR, func.func, origin,
						"To use fromSchema, specify a table name with from");
			}
			else
			{
				if ( ! "".equals( _from) )
					msg( Kind.ERROR, func.func, origin,
						"Only a constraint trigger can use 'from'");
				if ( ! "".equals( _fromSchema) )
					msg( Kind.ERROR, func.func, origin,
						"Only a constraint trigger can use 'fromSchema'");
			}

			if ( "".equals( _name) )
				_name = TriggerNamer.synthesizeName( this);
			return false;
		}
		
		public String[] deployStrings()
		{
			StringBuilder sb = new StringBuilder();
            sb.append("CREATE ");
			if ( isConstraint )
			{
				sb.append("CONSTRAINT ");
			}
            sb.append("TRIGGER ").append(name()).append("\n\t");
			switch ( called() )
			{
				case BEFORE:	 sb.append( "BEFORE "	 ); break;
				case AFTER: 	 sb.append( "AFTER "	 ); break;
				case INSTEAD_OF: sb.append( "INSTEAD OF "); break;
			}
			int s = _events.length;
			for ( Event e : _events )
			{
				sb.append( e.toString());
				if ( Event.UPDATE.equals( e) && 0 < _columns.length )
				{
					sb.append( " OF ");
					int cs = _columns.length;
					for ( String c : _columns )
					{
						sb.append( c);
						if ( 0 < -- cs )
							sb.append( ", ");
					}
				}
				if ( 0 < -- s )
					sb.append( " OR ");
			}
			sb.append( "\n\tON ");
			sb.append(qnameFrom(table(), schema()));
			if ( ! "".equals( from()) )
			{
				sb.append("\n\tFROM ");
				sb.append(qnameFrom(from(), fromSchema()));
			}
			if ( isConstraint ) {
				sb.append("\n\t");
				switch ( _constraint )
				{
					case NOT_DEFERRABLE:
						sb.append("NOT DEFERRABLE");
						break;
					case INITIALLY_IMMEDIATE:
						sb.append("DEFERRABLE INITIALLY IMMEDIATE");
						break;
					case INITIALLY_DEFERRED:
						sb.append("DEFERRABLE INITIALLY DEFERRED");
						break;
				}
			}
            if ( refOld || refNew )
			{
				sb.append( "\n\tREFERENCING");
				if ( refOld )
					sb.append( " OLD TABLE AS ").append( _tableOld);
				if ( refNew )
					sb.append( " NEW TABLE AS ").append( _tableNew);
			}
			sb.append( "\n\tFOR EACH ");
			sb.append( scope().toString());
			if ( ! "".equals( _when) )
				sb.append( "\n\tWHEN ").append( _when); 
			sb.append( "\n\tEXECUTE PROCEDURE ");
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

			String comm = comment();
			if ( null == comm )
				return new String[] { sb.toString() };

			return new String[] {
				sb.toString(),
				"COMMENT ON TRIGGER " + name() + " ON " +
				qnameFrom(table(), schema()) +
				"\nIS " +
				DDRWriter.eQuote( comm)
			};
		}
		
		public String[] undeployStrings()
		{
			StringBuilder sb = new StringBuilder();
			sb.append( "DROP TRIGGER ").append( name()).append( "\n\tON ");
			sb.append(qnameFrom(table(), schema()));
			return new String[] { sb.toString() };
		}
	}

	class FunctionImpl
	extends AbstractAnnotationImpl
	implements Function, Snippet, Commentable
	{
		public String             type() { return _type; }
		public String             name() { return _name; }
		public String           schema() { return _schema; }
		public OnNullInput onNullInput() { return _onNullInput; }
		public Security       security() { return _security; }
		public Effects         effects() { return _effects; }
		public Trust             trust() { return _trust; }
		public Parallel       parallel() { return _parallel; }
		public boolean       leakproof() { return _leakproof; }
		public int                cost() { return _cost; }
		public int                rows() { return _rows; }
		public String[]       settings() { return _settings; }
		public String[]       provides() { return _provides; }
		public String[]       requires() { return _requires; }
		public Trigger[]      triggers() { return _triggers; }

		ExecutableElement func;

		public String      _type;
		public String      _name;
		public String      _schema;
		public OnNullInput _onNullInput;
		public Security    _security;
		public Effects     _effects;
		public Trust       _trust;
		public Parallel    _parallel;
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
		TypeMirror returnTypeMapKey = null;
		SQLType[] paramTypeAnnotations;

		DBType returnType;
		DBType[] parameterTypes;

		boolean subsumed = false;

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
			List<? extends TypeMirror> typeArgs;
			int arity = ptms.size();

			if ( ! "".equals( type())
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
			else if ( null != (typeArgs = specialization( ret, TY_ITERATOR)) )
			{
				setof = true;
				if ( 1 != typeArgs.size() )
				{
					msg( Kind.ERROR, func,
						"Need one type argument for Iterator return type");
					return false;
				}
				setofComponent = typeArgs.get( 0);
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

			returnTypeMapKey = ret;
				
			if ( ! setof && -1 != rows() )
				msg( Kind.ERROR, func,
					"ROWS specified on a function not returning SETOF");

			if ( ! trigger && 0 != _triggers.length )
				msg( Kind.ERROR, func,
					"a function with triggers needs void return and " +
					"one TriggerData parameter");

			collectParameterTypeAnnotations();

			/*
			 * Report any unmappable types now that could appear in
			 * deployStrings (return type or parameter types) ... so that the
			 * error messages won't be missing the source location, as they can
			 * with javac 7 throwing away symbol tables between rounds.
			 */
			resolveParameterAndReturnTypes();

			recordImplicitTags();

			recordExplicitTags(_provides, _requires);

			for ( Trigger t : triggers() )
				((TriggerImpl)t).characterize();
			return true;
		}

		/*
		 * Factored out of characterize() so it could be called if needed by
		 * BaseUDTFunctionImpl.characterize(), which does not need anything else
		 * from its super.characterize(). But for now it doesn't need this
		 * either; it knows what parameters the base UDT functions take, and it
		 * takes no heed of @SQLType annotations. Perhaps it should warn if such
		 * annotations are used, but that's for another day.
		 */
		void collectParameterTypeAnnotations()
		{
			List<? extends VariableElement> ves = func.getParameters();
			paramTypeAnnotations = new SQLType [ ves.size() ];
			int i = 0;
			for ( VariableElement ve : ves )
			{
				for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( ve) )
				{
					if ( am.getAnnotationType().asElement().equals(AN_SQLTYPE) )
					{
						SQLTypeImpl sti = new SQLTypeImpl();
						populateAnnotationImpl( sti, ve, am);
						paramTypeAnnotations[i] = sti;
					}
				}
				++ i;
			}
		}

		/**
		 * Return a stream of {@code ParameterInfo} 'records' for the function's
		 * parameters in order.
		 *<p>
		 * If {@code paramTypeAnnotations} has not been set, every element in
		 * the stream will have null for {@code st}.
		 *<p>
		 * If {@code parameterTypes} has not been set, every element in
		 * the stream will have null for {@code dt}.
		 */
		Stream<ParameterInfo> parameterInfo()
		{
			if ( trigger )
				return Stream.empty();

			ExecutableType et = (ExecutableType)func.asType();
			List<? extends TypeMirror> tms = et.getParameterTypes();
			if ( complexViaInOut )
				tms = tms.subList( 0, tms.size() - 1);

			Iterator<? extends VariableElement> ves =
				func.getParameters().iterator();

			Supplier<SQLType> sts =
				null == paramTypeAnnotations
				? () -> null
				: Arrays.asList(paramTypeAnnotations).iterator()::next;

			Supplier<DBType> dts =
				null == parameterTypes
				? () -> null
				: Arrays.asList(parameterTypes).iterator()::next;

			return tms.stream().map(tm ->
				new ParameterInfo(tm, ves.next(), sts.get(), dts.get()));
		}

		/**
		 * Create the {@code DBType}s to populate {@code returnType} and
		 * {@code parameterTypes}.
		 */
		void resolveParameterAndReturnTypes()
		{
			if ( ! "".equals( type()) )
				returnType = DBType.fromSQLTypeAnnotation( type());
			else if ( null != setofComponent )
				returnType = tmpr.getSQLType( setofComponent, func);
			else if ( setof )
				returnType = DT_RECORD;
			else
				returnType = tmpr.getSQLType( returnTypeMapKey, func);

			parameterTypes = parameterInfo()
				.map(i -> tmpr.getSQLType(i.tm, i.ve, i.st, true, true))
				.toArray(DBType[]::new);
		}

		/**
		 * Record that this function provides itself, and requires its
		 * parameter and return types.
		 *<p>
		 * Must be called before {@code recordExplicitTags}, which makes the
		 * provides and requires sets immutable.
		 */
		void recordImplicitTags()
		{
			Set<DependTag> provides = provideTags();
			Set<DependTag> requires = requireTags();

			provides.add(new DependTag.Function(
				qnameFrom(_name, _schema), parameterTypes));

			DependTag t = returnType.dependTag();
			if ( null != t )
				requires.add(t);

			for ( DBType dbt : parameterTypes )
			{
				t = dbt.dependTag();
				if ( null != t )
					requires.add(t);
			}
		}

		@Override
		public void subsume()
		{
			subsumed = true;
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
			sb.append(qnameFrom(name(), schema())).append( '(');
			appendParams( sb, dflts);
			// TriggerImpl relies on ) being the very last character
			sb.append( ')');
		}

		void appendParams( StringBuilder sb, boolean dflts)
		{
			sb.append(parameterInfo()
				.map(
					i ->
					{
						String name = null == i.st ? null : i.st.name();
						if ( null == name )
							name = i.ve.getSimpleName().toString();
						return "\n\t" + name + " " + i.dt.toString(dflts);
					})
				.collect(joining(","))
			);
		}

		void appendAS( StringBuilder sb)
		{
			if ( ! ( complexViaInOut || setof || trigger ) )
				sb.append( typu.erasure( func.getReturnType())).append( '=');
			Element e = func.getEnclosingElement();
			if ( ! e.getKind().equals( ElementKind.CLASS) )
				msg( Kind.ERROR, func,
					"Somehow this method got enclosed by something other " +
					"than a class");
			sb.append( e.toString()).append( '.');
			sb.append( trigger ? func.getSimpleName() : func.toString());
		}

		public String[] deployStrings()
		{
			ArrayList<String> al = new ArrayList<>();
			StringBuilder sb = new StringBuilder();
			sb.append( "CREATE OR REPLACE FUNCTION ");
			appendNameAndParams( sb, true);
			sb.append( "\n\tRETURNS ");
			if ( trigger )
				sb.append( DT_TRIGGER.toString());
			else
			{
				if ( setof )
					sb.append( "SETOF ");
				sb.append( returnType);
			}
			sb.append( "\n\tLANGUAGE ");
			if ( Trust.SANDBOXED.equals( trust()) )
				sb.append( nameTrusted);
			else
				sb.append( nameUntrusted);
			sb.append( ' ').append( effects());
			if ( leakproof() )
				sb.append( " LEAKPROOF");
			sb.append( '\n');
			if ( OnNullInput.RETURNS_NULL.equals( onNullInput()) )
				sb.append( "\tRETURNS NULL ON NULL INPUT\n");
			if ( Security.DEFINER.equals( security()) )
				sb.append( "\tSECURITY DEFINER\n");
			if ( ! Parallel.UNSAFE.equals( parallel()) )
				sb.append( "\tPARALLEL ").append( parallel()).append( '\n');
			if ( -1 != cost() )
				sb.append( "\tCOST ").append( cost()).append( '\n');
			if ( -1 != rows() )
				sb.append( "\tROWS ").append( rows()).append( '\n');
			for ( String s : settings() )
				sb.append( "\tSET ").append( s).append( '\n');
			sb.append( "\tAS '");
			appendAS( sb);
			sb.append( '\'');
			al.add( sb.toString());

			String comm = comment();
			if ( null != comm )
			{
				sb.setLength( 0);
				sb.append( "COMMENT ON FUNCTION ");
				appendNameAndParams( sb, false);
				sb.append( "\nIS ");
				sb.append( DDRWriter.eQuote( comm));
				al.add( sb.toString());
			}
			
			for ( Trigger t : triggers() )
				for ( String s : ((TriggerImpl)t).deployStrings() )
					al.add( s);
			return al.toArray( new String [ al.size() ]);
		}
		
		public String[] undeployStrings()
		{
			if ( subsumed )
				return new String[0];

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

		/**
		 * Test whether the type {@code tm} is, directly or indirectly,
		 * a specialization of generic type {@code dt}.
		 * @param tm a type to be checked
		 * @param dt known generic type to check for
		 * @return null if {@code tm} does not extend {@code dt}, otherwise the
		 * list of type arguments with which it specializes {@code dt}
		 */
		List<? extends TypeMirror> specialization(
			TypeMirror tm, DeclaredType dt)
		{
			if ( ! typu.isAssignable( typu.erasure( tm), dt) )
				return null;

			List<TypeMirror> pending = new LinkedList<>();
			pending.add( tm);
			while ( ! pending.isEmpty() )
			{
				tm = pending.remove( 0);
				if ( typu.isSameType( typu.erasure( tm), dt) )
					return ((DeclaredType)tm).getTypeArguments();
				pending.addAll( typu.directSupertypes( tm));
			}
			/*
			 * This is a can't-happen: tm is assignable to dt but has no
			 * supertype that's dt? Could throw an AssertionError, but returning
			 * an empty list will lead the caller to report an error, and that
			 * will give more information about the location in the source being
			 * compiled.
			 */
			return Collections.emptyList();
		}
	}

	static enum BaseUDTFunctionID
	{
		INPUT("in", null, "pg_catalog.cstring", "pg_catalog.oid", "integer"),
		OUTPUT("out", "pg_catalog.cstring", (String[])null),
		RECEIVE("recv", null, "pg_catalog.internal","pg_catalog.oid","integer"),
		SEND("send", "pg_catalog.bytea", null);
		BaseUDTFunctionID( String suffix, String ret, String... param)
		{
			this.suffix = suffix;
			this.param = null == param ? null :
				Arrays.stream(param)
				.map(DBType::fromSQLTypeAnnotation)
				.toArray(DBType[]::new);
			this.ret = null == ret ? null :
				new DBType.Named(Identifier.Qualified.nameFromJava(ret));
		}
		private String suffix;
		private DBType[] param;
		private DBType ret;
		String getSuffix() { return suffix; }
		DBType[] getParam( BaseUDTImpl u)
		{
			if ( null != param )
				return param;
			return new DBType[] { u.qname };
		}
		DBType getRet( BaseUDTImpl u)
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

			returnType = id.getRet( ui);
			parameterTypes = id.getParam( ui);

			_type = returnType.toString();
			_name = Identifier.Simple.fromJava(ui.name())
				.concat("_", id.getSuffix()).toString();
			_schema = ui.schema();
			_cost = -1;
			_rows = -1;
			_onNullInput = OnNullInput.CALLED;
			_security = Security.INVOKER;
			_effects = Effects.VOLATILE;
			_trust = Trust.SANDBOXED;
			_parallel = Parallel.UNSAFE;
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
			sb.append(
				Arrays.stream(id.getParam( ui))
				.map(Object::toString)
				.collect(joining(", "))
			);
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
			return sb.append(qnameFrom(name(), schema()));
		}

		@Override
		public boolean characterize()
		{
			recordImplicitTags();
			recordExplicitTags(_provides, _requires);
			return true;
		}

		public void setType( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"The type of a UDT function may not be changed");
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

		public String derivedComment( Element e)
		{
			String comm = super.derivedComment( e);
			if ( null != comm )
				return comm;
			return id.name() + " method for type " + ui.qname;
		}
	}

	abstract class AbstractUDTImpl
	extends AbstractAnnotationImpl
	implements Snippet, Commentable
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

		DBType qname;

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

			qname = new DBType.Named(qnameFrom(_name, _schema));

			if ( ! tmpr.mappingsFrozen() )
				tmpr.addMap( tclass.asType(), qname);
		}

		protected void addComment( ArrayList<String> al)
		{
			String comm = comment();
			if ( null == comm )
				return;
			al.add( "COMMENT ON TYPE " + qname + "\nIS " +
				DDRWriter.eQuote( comm));
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

		public void registerMapping()
		{
			setQname();
		}

		public boolean characterize()
		{
			if ( null != structure() )
			{
				DependTag t = qname.dependTag();
				if ( null != t )
					provideTags().add(t);
			}
			recordExplicitTags(_provides, _requires);
			return true;
		}

		public String[] deployStrings()
		{
			ArrayList<String> al = new ArrayList<>();
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
				DDRWriter.eQuote( qname.toString()) + ", " +
				DDRWriter.eQuote( tclass.toString()) + ')');
			addComment( al);
			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			ArrayList<String> al = new ArrayList<>();
			al.add( "SELECT sqlj.drop_type_mapping(" +
				DDRWriter.eQuote( qname.toString()) + ')');
			if ( null != structure() )
				al.add( "DROP TYPE " + qname);
			return al.toArray( new String [ al.size() ]);
		}
	}

	class BaseUDTImpl
	extends AbstractUDTImpl
	implements BaseUDT
	{
		class Shell implements Snippet
		{
			@Override
			public Identifier.Simple implementorName()
			{
				return BaseUDTImpl.this.implementorName();
			}

			@Override
			public String[] deployStrings()
			{
				return new String[] { "CREATE TYPE " + qname };
			}

			@Override
			public String[] undeployStrings()
			{
				return new String[0];
			}

			@Override
			public Set<DependTag> provideTags()
			{
				return Set.of();
			}

			@Override
			public Set<DependTag> requireTags()
			{
				return Set.of();
			}

			@Override
			public boolean characterize()
			{
				return false;
			}
		}

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
			storageExplicit = explicit;
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
			else
			{
				in = new BaseUDTFunctionImpl(
					this, tclass, BaseUDTFunctionID.INPUT);
				putSnippet( staticParse, in);
			}

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

			recordImplicitTags();
			recordExplicitTags(_provides, _requires);

			return true;
		}

		void recordImplicitTags()
		{
			Set<DependTag> provides = provideTags();
			Set<DependTag> requires = requireTags();

			provides.add(qname.dependTag());

			for ( BaseUDTFunctionImpl f : List.of(in, out, recv, send) )
				requires.add(new DependTag.Function(
					qnameFrom(f._name, f._schema), f.parameterTypes));

			String s = typeModifierInput();
			if ( ! s.isEmpty() )
				requires.add(new DependTag.Function(
					qnameFrom(s), SIG_TYPMODIN));

			s = typeModifierOutput();
			if ( ! s.isEmpty() )
				requires.add(new DependTag.Function(
					qnameFrom(s), SIG_TYPMODOUT));

			s = analyze();
			if ( ! s.isEmpty() )
				requires.add(new DependTag.Function(qnameFrom(s), SIG_ANALYZE));
		}

		public String[] deployStrings()
		{
			ArrayList<String> al = new ArrayList<>();

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
				sb.append( ",\n\tANALYZE = ").append( analyze());

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
				sb.append( ",\n\tCATEGORY = ").append(
					DDRWriter.eQuote( String.valueOf( category())));

			if ( preferred() )
				sb.append( ",\n\tPREFERRED = true");

			if ( null != defaultValue() )
				sb.append( ",\n\tDEFAULT = ").append(
					DDRWriter.eQuote( defaultValue()));

			if ( ! "".equals( element()) )
				sb.append( ",\n\tELEMENT = ").append( element());

			if ( delimiterExplicit )
				sb.append( ",\n\tDELIMITER = ").append(
					DDRWriter.eQuote( String.valueOf( delimiter())));

			if ( collatable() )
				sb.append( ",\n\tCOLLATABLE = true");

			al.add( sb.append( "\n)").toString());
			addComment( al);
			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			return new String[]
			{
				"DROP TYPE " + qname + " CASCADE"
			};
		}

		@Override
		public Vertex<Snippet> breakCycle(Vertex<Snippet> v, boolean deploy)
		{
			assert this == v.payload;

			/*
			 * Find the entries in my adjacency list that are implicated in the
			 * cycle (that is, that precede, perhaps transitively, me).
			 */
			Vertex<Snippet>[] vs = v.precedesTransitively(v);

			assert null != vs && 0 < vs.length : "breakCycle not in a cycle";

			if ( vs.length < v.indegree )
				return null; // other non-cyclic edges not satisfied yet

			if ( deploy )
			{
				Vertex<Snippet> breaker = new Vertex<>(new Shell());
				v.transferSuccessorsTo(breaker, vs);
				return breaker;
			}

			for ( Vertex<Snippet> subsumed : vs )
				subsumed.payload.subsume();

			/*
			 * Set indegree now to zero, so that when the subsumed snippets are
			 * themselves emitted, they will not decrement it to zero and cause
			 * this to be scheduled again.
			 */
			v.indegree = 0;

			return v; // use this vertex itself in the undeploy case
		}
	}

	/**
	 * Provides the default mappings from Java types to SQL types.
	 */
	class TypeMapper
	{
		ArrayList<Map.Entry<TypeMirror, DBType>> protoMappings;
		ArrayList<Map.Entry<TypeMirror, DBType>> finalMappings;

		TypeMapper()
		{
			protoMappings = new ArrayList<>();

			// Primitives (these need not, indeed cannot, be schema-qualified)
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
			this.addMap(Number.class, "pg_catalog", "numeric");
			this.addMap(String.class, "pg_catalog", "varchar");
			this.addMap(java.util.Date.class, "pg_catalog", "timestamp");
			this.addMap(Timestamp.class, "pg_catalog", "timestamp");
			this.addMap(Time.class, "pg_catalog", "time");
			this.addMap(java.sql.Date.class, "pg_catalog", "date");
			this.addMap(java.sql.SQLXML.class, "pg_catalog", "xml");
			this.addMap(BigInteger.class, "pg_catalog", "numeric");
			this.addMap(BigDecimal.class, "pg_catalog", "numeric");
			this.addMap(ResultSet.class, "pg_catalog", "record");
			this.addMap(Object.class, "pg_catalog", "\"any\"");

			this.addMap(byte[].class, "pg_catalog", "bytea");

			this.addMap(LocalDate.class, "pg_catalog", "date");
			this.addMap(LocalTime.class, "pg_catalog", "time");
			this.addMap(OffsetTime.class, "pg_catalog", "timetz");
			this.addMap(LocalDateTime.class, "pg_catalog", "timestamp");
			this.addMap(OffsetDateTime.class, "pg_catalog", "timestamptz");
		}

		private boolean mappingsFrozen()
		{
			return null != finalMappings;
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
			if ( mappingsFrozen() )
				return; // after the first round, it's too late!

			// Need to check more specific types before those they are
			// assignable to by widening reference conversions, so a
			// topological sort is in order.
			//
			List<Vertex<Map.Entry<TypeMirror, DBType>>> vs = new ArrayList<>(
					protoMappings.size());

			for ( Map.Entry<TypeMirror, DBType> me : protoMappings )
				vs.add( new Vertex<>( me));

			for ( int i = vs.size(); i --> 1; )
			{
				Vertex<Map.Entry<TypeMirror, DBType>> vi = vs.get( i);
				TypeMirror ci = vi.payload.getKey();
				for ( int j = i; j --> 0; )
				{
					Vertex<Map.Entry<TypeMirror, DBType>> vj = vs.get( j);
					TypeMirror cj = vj.payload.getKey();
					boolean oij = typu.isAssignable( ci, cj);
					boolean oji = typu.isAssignable( cj, ci);
					if ( oji == oij )
						continue; // no precedence constraint between these two
					if ( oij )
						vi.precede( vj);
					else
						vj.precede( vi);
				}
			}

			Queue<Vertex<Map.Entry<TypeMirror, DBType>>> q;
			if ( reproducible )
			{
				q = new PriorityQueue<>( 11, new TypeTiebreaker());
			}
			else
			{
				q = new LinkedList<>();
			}

			for ( Vertex<Map.Entry<TypeMirror, DBType>> v : vs )
				if ( 0 == v.indegree )
					q.add( v);

			protoMappings.clear();
			finalMappings = protoMappings;
			protoMappings = null;

			while ( ! q.isEmpty() )
			{
				Vertex<Map.Entry<TypeMirror, DBType>> v = q.remove();
				v.use( q);
				finalMappings.add( v.payload);
			}
		}

		private TypeMirror typeMirrorFromClass( Class<?> k)
		{
			if ( k.isArray() )
			{
				TypeMirror ctm = typeMirrorFromClass( k.getComponentType());
				return typu.getArrayType( ctm);
			}

			if ( k.isPrimitive() )
			{
				TypeKind tk = TypeKind.valueOf( k.getName().toUpperCase());
				return typu.getPrimitiveType( tk);
			}

			String cname = k.getCanonicalName();
			if ( null == cname )
			{
				msg( Kind.WARNING,
					"Cannot register type mapping for class %s" +
					"that lacks a canonical name", k.getName());
				return null;
			}

			TypeElement te = elmu.getTypeElement( cname);
			if ( null == te )
			{
				msg( Kind.WARNING, "Found no TypeElement for %s", cname);
				return null; // hope it wasn't one we'll need!
			}
			return te.asType();
		}

		/**
		 * Add a custom mapping from a Java class to an SQL type identified
		 * by SQL-standard reserved syntax.
		 *
		 * @param k Class representing the Java type
		 * @param v String representing the SQL (language-reserved) type
		 * to be used
		 */
		void addMap(Class<?> k, String v)
		{
			addMap( typeMirrorFromClass( k), new DBType.Reserved(v));
		}

		/**
		 * Add a custom mapping from a Java class to an SQL type identified
		 * by an SQL qualified identifier.
		 *
		 * @param k Class representing the Java type
		 * @param schema String representing the qualifier of the type name
		 * (may be null)
		 * @param local String representing the SQL (language-reserved) type
		 * to be used
		 */
		void addMap(Class<?> k, String schema, String local)
		{
			addMap( typeMirrorFromClass( k),
				new DBType.Named(qnameFrom(local, schema)));
		}

		/**
		 * Add a custom mapping from a Java class to an SQL type, if a class
		 * with the given name exists.
		 *
		 * @param k Canonical class name representing the Java type
		 * @param v String representing the SQL type to be used
		 */
		void addMapIfExists(String k, String v)
		{
			TypeElement te = elmu.getTypeElement( k);
			if ( null != te )
				addMap( te.asType(), new DBType.Reserved(v));
		}

		/**
		 * Add a custom mapping from a Java class (represented as a TypeMirror)
		 * to an SQL type.
		 *
		 * @param tm TypeMirror representing the Java type
		 * @param v String representing the SQL type to be used
		 */
		void addMap(TypeMirror tm, DBType v)
		{
			if ( mappingsFrozen() )
			{
				msg( Kind.ERROR,
					"addMap(%s, %s)\n" +
					"called after workAroundJava7Breakage", tm.toString(), v);
				return;
			}
			protoMappings.add( new AbstractMap.SimpleImmutableEntry<>( tm, v));
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
		DBType getSQLType(TypeMirror tm, Element e)
		{
			return getSQLType( tm, e, null, false, false);
		}


		/**
		 * Return the SQL type for the Java type represented by a TypeMirror,
		 * from an explicit annotation if present, otherwise by applying the
		 * default mappings.
		 *
		 * @param tm Represents the type whose corresponding SQL type is wanted.
		 * @param e Annotated element (chiefly for use as a location hint in
		 * diagnostic messages).
		 * @param st {@code SQLType} annotation, or null if none, explicitly
		 * given for the element.
		 * @param contravariant Indicates that the element whose type is wanted
		 * is a function parameter and should be given the widest type that can
		 * be assigned to it. If false, find the narrowest type that a function
		 * return can be assigned to.
		 * @param withDefault Indicates whether any specified default value
		 * information should also be included in the "type" string returned.
		 */
		DBType getSQLType(TypeMirror tm, Element e, SQLType st,
			boolean contravariant, boolean withDefault)
		{
			boolean array = false;
			boolean row = false;
			DBType rslt = null;
			
			String[] defaults = null;
			
			if ( null != st )
			{
				String s = st.value();
				if ( null != s )
					rslt = DBType.fromSQLTypeAnnotation(s);
				defaults = st.defaultValue();
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

			if ( ! array  &&  typu.isSameType( tm, TY_RESULTSET) )
				row = true;
			
			if ( null != rslt )
				return typeWithDefault(
					e, rslt, array, row, defaults, withDefault);

			if ( tm.getKind().equals( TypeKind.VOID) )
				return DT_VOID; // return type only; no defaults apply

			if ( tm.getKind().equals( TypeKind.ERROR) )
			{
				msg ( Kind.ERROR, e,
					"Cannot determine mapping to SQL type for unresolved type");
				rslt = new DBType.Reserved(tm.toString());
			}
			else
			{    
				ArrayList<Map.Entry<TypeMirror, DBType>> ms = finalMappings;
				if ( contravariant )
					ms = reversed(ms);
				for ( Map.Entry<TypeMirror, DBType> me : ms )
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
				rslt = new DBType.Reserved(tm.toString());
			}

			if ( array )
				rslt = rslt.asArray("[]");
			
			return typeWithDefault( e, rslt, array, row, defaults, withDefault);
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
		 * @param e Annotated element (chiefly for use as a location hint in
		 * diagnostic messages).
		 * @param rslt The bare SQL type string already determined
		 * @param array Whether the Java type was determined to be an array
		 * @param row Whether the Java type was ResultSet, indicating an SQL
		 * record or row type.
		 * @param defaults Array (null if not present) of default value strings
		 * @param withDefault Whether to append the default information to the
		 * type.
		 */
		DBType typeWithDefault(
			Element e, DBType rslt, boolean array, boolean row,
			String[] defaults, boolean withDefault)
		{
			if ( null == defaults || ! withDefault )
				return rslt;
			
			int n = defaults.length;
			if ( row )
			{
				assert ! array;
				if ( n > 0 && rslt.toString().equalsIgnoreCase("record") )
					msg( Kind.ERROR, e,
						"Only supported default for unknown RECORD type is {}");
			}
			else if ( n != 1 )
				array = true;
			else if ( ! array )
				array = rslt.isArray();
			
			StringBuilder sb = new StringBuilder();
			sb.append( " DEFAULT ");
			sb.append( row ? "ROW(" : "CAST(");
			if ( array )
				sb.append( "ARRAY[");
			if ( n > 1 )
				sb.append( "\n\t");
			for ( String s : defaults )
			{
				sb.append( DDRWriter.eQuote( s));
				if ( 0 < -- n )
					sb.append( ",\n\t");
			}
			if ( array )
				sb.append( ']');
			if ( ! row )
				sb.append( " AS ").append( rslt);
			sb.append( ')');
			return rslt.withDefault(sb.toString());
		}
	}

	/**
	 * Work around bizarre javac behavior that silently supplies an Error
	 * class in place of an attribute value for glaringly obvious source errors,
	 * instead of reporting them.
	 * @param av AnnotationValue to extract the value from
	 * @return The result of getValue unless {@code av} is an error placeholder
	 */
	static Object getValue( AnnotationValue av)
	{
		if ( "com.sun.tools.javac.code.Attribute.Error".equals(
			av.getClass().getCanonicalName()) )
			throw new AnnotationValueException();
		return av.getValue();
	}

	/**
	 * Return a reversed copy of an ArrayList.
	 */
	static <E, T extends ArrayList<E>> T reversed(T orig)
	{
		@SuppressWarnings("unchecked")
		T list = (T)orig.clone();
		Collections.reverse(list);
		return list;
	}

	/**
	 * Return an {@code Identifier.Qualified} from discrete Java strings
	 * representing the local name and schema, with a zero-length schema string
	 * producing a qualified name with null qualifier.
	 */
	Identifier.Qualified<Identifier.Simple> qnameFrom(
		String name, String schema)
	{
		Identifier.Simple qualifier =
			"".equals(schema) ? null : Identifier.Simple.fromJava(schema, msgr);
		Identifier.Simple local = Identifier.Simple.fromJava(name, msgr);
		return local.withQualifier(qualifier);
	}

	/**
	 * Return an {@code Identifier.Qualified} from a single Java string
	 * representing the local name and possibly a schema.
	 */
	Identifier.Qualified<Identifier.Simple> qnameFrom(String name)
	{
		return Identifier.Qualified.nameFromJava(name, msgr);
	}
}

/**
 * Exception thrown when an expected annotation value is a compiler-internal
 * Error class instead, which happens in some javac versions when the annotation
 * value wasn't resolved because of a source error the compiler really should
 * have reported.
 */
class AnnotationValueException extends RuntimeException { }

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
	public Identifier.Simple implementorName();
	/**
	 * A {@code DependTag} to represent this snippet's dependence on whatever
	 * determines whether the implementor name is to be recognized.
	 *<p>
	 * Represented for now as a {@code DependTag.Explicit} even though the
	 * dependency is implicitly created; an {@code SQLAction} snippet may have
	 * an explicit {@code provides=} that has to be matched.
	 */
	default DependTag implementorTag()
	{
		return new DependTag.Explicit(implementorName().pgFolded());
	}
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
	public Set<DependTag> provideTags();
	/**
	 * Return an array of arbitrary labels considered "required" by this
	 * Snippet. In generating the final order of the deployment descriptor file,
	 * this Snippet will come after those whose provides method returns any of
	 * the same labels.
	 */
	public Set<DependTag> requireTags();
	/**
	 * Method to be called after all annotations'
	 * element/value pairs have been filled in, to compute any additional
	 * information derived from those values before deployStrings() or
	 * undeployStrings() can be called. May also check for and report semantic
	 * errors that are not easily checked earlier while populating the
	 * element/value pairs.
	 * @return true if this Snippet is standalone and should be scheduled and
	 * emitted based on provides/requires; false if something else will emit it.
	 */
	public boolean characterize();

	/**
	 * If it is possible to break an ordering cycle at this snippet, return a
	 * vertex wrapping a snippet (possibly this one, or another) that can be
	 * considered ready, otherwise return null.
	 *<p>
	 * The default implementation returns null unconditionally.
	 * @param v Vertex that wraps this Snippet
	 * @param deploy true when generating an ordering for the deploy strings
	 * @return a Vertex wrapping a Snippet that can be considered ready
	 */
	default Vertex<Snippet> breakCycle(Vertex<Snippet> v, boolean deploy)
	{
		return null;
	}

	/**
	 * Called when undeploy ordering breaks a cycle by using
	 * {@code DROP ... CASCADE} or equivalent on another object, with effects
	 * that would duplicate or interfere with this snippet's undeploy actions.
	 *<p>
	 * A snippet for which this can matter should note that this method has been
	 * called, and later generate its undeploy strings with any necessary
	 * adjustments.
	 *<p>
	 * The default implementation does nothing.
	 */
	default void subsume()
	{
	}
}

interface Commentable
{
	public String comment();
	public void setComment( Object o, boolean explicit, Element e);
	public String derivedComment( Element e);
}

/**
 * Vertex in a DAG, as used to put things in workable topological order
 */
class Vertex<P>
{
	P payload;
	int indegree;
	List<Vertex<P>> adj;
	
	/**
	 * Construct a new vertex with the supplied payload, indegree zero, and an
	 * empty out-adjacency list.
	 * @param payload Object to be associated with this vertex.
	 */
	Vertex( P payload)
	{
		this.payload = payload;
		indegree = 0;
		adj = new ArrayList<>();
	}
	
	/**
	 * Record that this vertex must precede the specified vertex.
	 * @param v a Vertex that this Vertex must precede.
	 */
	void precede( Vertex<P> v)
	{
		++ v.indegree;
		adj.add( v);
	}
	
	/**
	 * Record that this vertex has been 'used'. Decrement the indegree of any
	 * in its adjacency list, and add to the supplied queue any of those whose
	 * indegree becomes zero.
	 * @param q A queue of vertices that are ready (have indegree zero).
	 */
	void use( Collection<Vertex<P>> q)
	{
		for ( Vertex<P> v : adj )
			if ( 0 == -- v.indegree )
				q.add( v);
	}

	/**
	 * Record that this vertex has been 'used'. Decrement the indegree of any
	 * in its adjacency list; any of those whose indegree becomes zero should be
	 * both added to the ready queue {@code q} and removed from the collection
	 * {@code vs}.
	 * @param q A queue of vertices that are ready (have indegree zero).
	 * @param vs A collection of vertices not yet ready.
	 */
	void use( Collection<Vertex<P>> q, Collection<Vertex<P>> vs)
	{
		for ( Vertex<P> v : adj )
			if ( 0 == -- v.indegree )
			{
				vs.remove( v);
				q.add( v);
			}
	}

	/**
	 * Whether a vertex is known to transitively precede, or not so precede, a
	 * target vertex, or cannot yet be so classified.
	 */
	enum MemoState { YES, NO, PENDING }

	/**
	 * Return the memoized state of this vertex or, if none, enqueue the vertex
	 * for further exploration, memoize its state as {@code PENDING}, and return
	 * that.
	 */
	MemoState classifyOrEnqueue(
		Queue<Vertex<P>> queue, IdentityHashMap<Vertex<P>,MemoState> memos)
	{
		MemoState state = memos.putIfAbsent(this, MemoState.PENDING);
		if ( null == state )
		{
			queue.add(this);
			return MemoState.PENDING;
		}
		return state;
	}

	/**
	 * Execute one step of {@code precedesTransitively} determination.
	 *<p>
	 * On entry, this vertex has been removed from the queue. Its immediate
	 * adjacency successors will be evaluated.
	 *<p>
	 * If any immediate successor is a {@code YES}, this vertex
	 * is a {@code YES}.
	 *<p>
	 * If any immediate successor is {@code PENDING}, this vertex remains
	 * {@code PENDING} and is replaced on the queue, to be encountered again
	 * after all currently pending vertices.
	 *<p>
	 * Otherwise, this vertex is a {@code NO}.
	 */
	MemoState stepOfPrecedes(
		Queue<Vertex<P>> queue, IdentityHashMap<Vertex<P>,MemoState> memos)
	{
		boolean anyPendingSuccessors = false;
		for ( Vertex<P> v : adj )
		{
			switch ( v.classifyOrEnqueue(queue, memos) )
			{
			case YES:
				memos.replace(this, MemoState.YES);
				return MemoState.YES;
			case PENDING:
				anyPendingSuccessors = true;
				break;
			case NO:
				break;
			}
		}

		if ( anyPendingSuccessors )
		{
			queue.add(this);
			return MemoState.PENDING;
		}

		memos.replace(this, MemoState.NO);
		return MemoState.NO;
	}

	/**
	 * Determine whether this vertex (transitively) precedes <em>other</em>,
	 * returning, if so, that subset of its immediate adjacency successors
	 * through which <em>other</em> is reachable.
	 * @param other vertex to which reachability is to be tested
	 * @return array of immediate adjacencies through which other is reachable,
	 * or null if it is not
	 */
	Vertex<P>[] precedesTransitively(Vertex<P> other)
	{
		Queue<Vertex<P>> queue = new LinkedList<>();
		IdentityHashMap<Vertex<P>,MemoState> memos = new IdentityHashMap<>();
		boolean anyYeses = false;

		/*
		 * Initially: the 'other' vertex itself is known to be a YES.
		 * Nothing is yet known to be a NO.
		 */
		memos.put(requireNonNull(other), MemoState.YES);

		/*
		 * classifyOrEnqueue my immediate successors. Any that is not 'other'
		 * itself will be enqueued in PENDING status.
		 */
		for ( Vertex<P> v : adj )
			if ( MemoState.YES == v.classifyOrEnqueue(queue, memos) )
				anyYeses = true;

		/*
		 * After running stepOfPrecedes on every enqueued vertex until the queue
		 * is empty, every vertex seen will be in memos as a YES or a NO.
		 */
		while ( ! queue.isEmpty() )
			if ( MemoState.YES == queue.remove().stepOfPrecedes(queue, memos) )
				anyYeses = true;

		if ( ! anyYeses )
			return null;

		@SuppressWarnings("unchecked") // can't quite say Vertex<P>[]::new
		Vertex<P>[] result = adj.stream()
			.filter(v -> MemoState.YES == memos.get(v))
			.toArray(Vertex[]::new);

		return result;
	}

	/**
	 * Remove <em>successors</em> from the adjacency list of this vertex, and
	 * add them to the adjacency list of <em>other</em>.
	 *<p>
	 * No successor's indegree is changed.
	 */
	void transferSuccessorsTo(Vertex<P> other, Vertex<P>[] successors)
	{
		for ( Vertex<P> v : successors )
		{
			boolean removed = adj.remove(v);
			assert removed : "transferSuccessorsTo passed a non-successor";
			other.adj.add(v);
		}
	}
}

/**
 * A pair of Vertex instances for the same payload, for use when two directions
 * of topological ordering must be computed.
 */
class VertexPair<P>
{
	Vertex<P> fwd;
	Vertex<P> rev;

	VertexPair( P payload)
	{
		fwd = new Vertex<>( payload);
		rev = new Vertex<>( payload);
	}

	P payload()
	{
		return rev.payload;
	}
}

/**
 * Proxy a snippet that 'provides' an implementor tag and has no
 * undeployStrings, returning its deployStrings in their place.
 */
class ImpProvider implements Snippet
{
	Snippet s;

	ImpProvider( Snippet s) { this.s = s; }

	@Override public Identifier.Simple implementorName()
	{
		return s.implementorName();
	}
	@Override public String[]   deployStrings() { return s.deployStrings(); }
	@Override public String[] undeployStrings() { return s.deployStrings(); }
	@Override public Set<DependTag> provideTags() { return s.provideTags(); }
	@Override public Set<DependTag> requireTags() { return s.requireTags(); }
	@Override public boolean     characterize() { return s.characterize(); }
}

/**
 * Resolve ties in {@code Snippet} ordering in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class SnippetTiebreaker implements Comparator<Vertex<Snippet>>
{
	@Override
	public int compare( Vertex<Snippet> o1, Vertex<Snippet> o2)
	{
		Snippet s1 = o1.payload;
		Snippet s2 = o2.payload;
		int diff;
		Identifier.Simple s1imp = s1.implementorName();
		Identifier.Simple s2imp = s2.implementorName();
		if ( null != s1imp  &&  null != s2imp )
		{
			diff = s1imp.pgFolded().compareTo( s2imp.pgFolded());
			if ( 0 != diff )
				return diff;
		}
		else
			return null == s1imp ? -1 : 1;
		String[] ds1 = s1.deployStrings();
		String[] ds2 = s2.deployStrings();
		diff = ds1.length - ds2.length;
		if ( 0 != diff )
			return diff;
		for ( int i = 0 ; i < ds1.length ; ++ i )
		{
			diff = ds1[i].compareTo( ds2[i]);
			if ( 0 != diff )
				return diff;
		}
		assert s1 == s2 : "Two distinct Snippets compare equal by tiebreaker";
		return 0;
	}
}

/**
 * Resolve ties in type-mapping resolution in an arbitrary but deterministic
 * way, for use when {@code ddr.reproducible} is set.
 */
class TypeTiebreaker
implements Comparator<Vertex<Map.Entry<TypeMirror, DBType>>>
{
	@Override
	public int compare(
		Vertex<Map.Entry<TypeMirror, DBType>> o1,
		Vertex<Map.Entry<TypeMirror, DBType>> o2)
	{
		Map.Entry<TypeMirror, DBType> m1 = o1.payload;
		Map.Entry<TypeMirror, DBType> m2 = o2.payload;
		int diff =
			m1.getValue().toString().compareTo( m2.getValue().toString());
		if ( 0 != diff )
			return diff;
		diff = m1.getKey().toString().compareTo( m2.getKey().toString());
		if ( 0 != diff )
			return diff;
		assert
			m1 == m2 : "Two distinct type mappings compare equal by tiebreaker";
		return 0;
	}
}

/**
 * Abstraction of a database type, which is usually specified by an
 * {@code Identifier.Qualified}, but sometimes by reserved SQL syntax.
 */
abstract class DBType
{
	DBType withModifier(String modifier)
	{
		return new Modified(this, modifier);
	}

	DBType asArray(String notated)
	{
		return new Array(this, notated);
	}

	DBType withDefault(String suffix)
	{
		return new Defaulting(this, suffix);
	}

	String toString(boolean withDefault)
	{
		return toString();
	}

	abstract DependTag dependTag();

	/**
	 * Return the original underlying (leaf) type, either a {@code Named} or
	 * a {@code Reserved}.
	 *<p>
	 * Override in non-leaf classes (except {@code Array}).
	 */
	DBType leaf()
	{
		return this;
	}

	boolean isArray()
	{
		return false;
	}

	@Override
	public final boolean equals(Object o)
	{
		return equals(o, null);
	}

	/**
	 * True if the underlying (leaf) types compare equal (overridden for
	 * {@code Array}).
	 *<p>
	 * The assumption is that equality checking will be done for function
	 * signature equivalence, for which defaults and typmods don't matter
	 * (but arrayness does).
	 */
	public final boolean equals(Object o, Messager msgr)
	{
		if ( this == o )
			return true;
		if ( ! (o instanceof DBType) )
			return false;
		DBType dt1 = this.leaf();
		DBType dt2 = ((DBType)o).leaf();
		if ( dt1.getClass() != dt2.getClass() )
			return false;
		if ( dt1 instanceof Array )
		{
			dt1 = ((Array)dt1).m_component.leaf();
			dt2 = ((Array)dt2).m_component.leaf();
			if ( dt1.getClass() != dt2.getClass() )
				return false;
		}
		if ( dt1 instanceof Named )
			return ((Named)dt1).m_ident.equals(((Named)dt2).m_ident, msgr);
		return pgFold(((Reserved)dt1).m_reservedName)
			.equals(pgFold(((Reserved)dt2).m_reservedName));
	}

	/**
	 * Pattern to match type names that are special in SQL, if they appear as
	 * regular (unquoted) identifiers and without a schema qualification.
	 *<p>
	 * This list does not include {@code DOUBLE} or {@code NATIONAL}, as the
	 * reserved SQL form for each includes a following keyword
	 * ({@code PRECISION} or {@code CHARACTER}/{@code CHAR}, respectively).
	 * There is a catch-all test in {@code fromSQLTypeAnnotation} that will fall
	 * back to 'reserved' treatment if the name is followed by anything that
	 * isn't a parenthesized type modifier, so the fallback will naturally catch
	 * these two cases.
	 */
	static final Pattern s_reservedTypeFirstWords = compile(
		"(?i:" +
		"INT|INTEGER|SMALLINT|BIGINT|REAL|FLOAT|DECIMAL|DEC|NUMERIC|" +
		"BOOLEAN|BIT|CHARACTER|CHAR|VARCHAR|TIMESTAMP|TIME|INTERVAL" +
		")"
	);

	/**
	 * Make a {@code DBType} from whatever might appear in an {@code SQLType}
	 * annotation.
	 *<p>
	 * The possibilities are numerous, as that text used to be dumped rather
	 * blindly into the descriptor and thus could be whatever PostgreSQL would
	 * make sense of. The result could be a {@code DBType.Named} if the start of
	 * the text parses as a (possibly schema-qualified) identifier, or a
	 * {@code DBType.Reserved} if it doesn't (or it parses as a non-schema-
	 * qualified regular identifier and matches one of SQL's grammatically
	 * reserved type names). It could be either of those wrapped in a
	 * {@code DBType.Modified} if a type modifier was parsed out. It could be
	 * any of those wrapped in a {@code DBType.Array} if the text ended with any
	 * of the recognized forms of array dimension notation. The one thing it
	 * can't be (as a result from this method) is a {@code DBType.Defaulting};
	 * that wrapping can be applied to the result later, to carry a default
	 * value that has been specified at a particular site of use.
	 *<p>
	 * The parsing strategy is a bit heuristic. An attempt is made to parse a
	 * (possibly schema-qualified) identifier at the start of the string.
	 * An attempt is made to find a match for array-dimension notation that runs
	 * to the end of the string. Whatever lies between gets to be a typmod if it
	 * looks enough like one, or gets rolled with the front of the string into a
	 * {@code DBType.Reserved}, which is not otherwise scrutinized; the
	 * {@code Reserved} case is still more or less a catch-all that will be
	 * dumped blindly into the descriptor in the hope that PostgreSQL will make
	 * sense of it.
	 *<p>
	 * This strategy is used because compared to what can appear in a typmod
	 * (which could require arbitrary constant expression parsing), the array
	 * grammar depends on much less.
	 */
	static DBType fromSQLTypeAnnotation(String value)
	{
		Identifier.Qualified<Identifier.Simple> qname = null;

		Matcher m = SEPARATOR.matcher(value);
		separator(m, false);

		if ( m.usePattern(ISO_AND_PG_IDENTIFIER_CAPTURING).lookingAt() )
		{
			Identifier.Simple id1 = identifierFrom(m);
			m.region(m.end(), m.regionEnd());

			separator(m, false);
			if ( value.startsWith(".", m.regionStart()) )
			{
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);
				if ( m.usePattern(ISO_AND_PG_IDENTIFIER_CAPTURING).lookingAt() )
				{
					Identifier.Simple id2 = identifierFrom(m);
					qname = id2.withQualifier(id1);
					m.region(m.end(), m.regionEnd());
					separator(m, false);
				}
			}
			else
				qname = id1.withQualifier(null);
		}

		/*
		 * At this point, qname may have a local name and qualifier, or it may
		 * have a local name and null qualifier (if a single identifier was
		 * successfully matched but not followed by a dot). It is also possible
		 * for qname to be null, either because the start of the string didn't
		 * look like an identifier at all, or because it did, but was followed
		 * by a dot, and what followed the dot could not be parsed as another
		 * identifier. Probably both of those cases are erroneous, but they can
		 * also be handled by simply treating the content as Reserved and hoping
		 * PostgreSQL can make sense of it.
		 *
		 * Search from here to the end of the string for possible array notation
		 * that can be stripped off the end, leaving just the middle (if any) to
		 * be dealt with.
		 */

		String arrayNotation = arrayNotationIfPresent(m, value);

		/*
		 * If arrayNotation is not null, m's region end has been adjusted to
		 * exclude the array notation.
		 */

		boolean reserved;

		if ( null == qname )
			reserved = true;
		else if ( null != qname.qualifier() )
			reserved = false;
		else
		{
			Identifier.Simple local = qname.local();
			if ( ! local.folds() )
				reserved = false;
			else
			{
				Matcher m1 =
					s_reservedTypeFirstWords.matcher(local.nonFolded());
				reserved = m1.matches();
			}
		}

		/*
		 * If this is a reserved type, just wrap up everything from its start to
		 * the array notation (if any) as a Reserved; there is no need to try to
		 * tease out a typmod separately. (The reserved syntax can be quite
		 * unlike the generic typename(typmod) pattern; there could be what
		 * looks like a (typmod) between TIME and WITH TIME ZONE, or the moral
		 * equivalent of a typmod could look like HOUR TO MINUTE, and so on.)
		 *
		 * If we think this is a non-reserved type, and there is anything left
		 * in the matching region (preceding the array notation, if any), then
		 * it had better be a typmod in the generic form starting with a (. We
		 * will capture whatever is there and call it a typmod as long as it
		 * does start that way. (More elaborate checking, such as balancing the
		 * parens, would require ability to parse an expr_list.) This can allow
		 * malformed syntax to be uncaught until deployment time when PostgreSQL
		 * sees it, but that's unchanged from when the entire SQLType string was
		 * passed along verbatim. The 'threat' model here is just that the
		 * legitimate developer may get an error later when earlier would be
		 * more helpful, not a malicious adversary bent on injection.
		 *
		 * On the other hand, if what's left doesn't start with a ( then we
		 * somehow don't know what we're looking at, so fall back and treat it
		 * as reserved. This will naturally catch the two-token reserved names
		 * DOUBLE PRECISION, NATIONAL CHARACTER or NATIONAL CHAR, which were
		 * therefore left out of the s_reservedTypeFirstWords pattern.
		 */

		if ( ! reserved  &&  m.regionStart() < m.regionEnd() )
			if ( ! value.startsWith("(", m.regionStart()) )
				reserved = true;

		DBType result;

		if ( reserved )
			result = new DBType.Reserved(value.substring(0, m.regionEnd()));
		else
		{
			result = new DBType.Named(qname);
			if ( m.regionStart() < m.regionEnd() )
				result = result.withModifier(
					value.substring(m.regionStart(), m.regionEnd()));
		}

		if ( null != arrayNotation )
			result = result.asArray(arrayNotation);

		return result;
	}

	private static final Pattern s_arrayDimStart = compile(String.format(
		"(?i:(?<!%1$s|%2$s)ARRAY(?!%1$s|%2$s))|\\[",
		ISO_REGULAR_IDENTIFIER_PART.pattern(),
		PG_REGULAR_IDENTIFIER_PART.pattern()
	));

	private static final Pattern s_digits = compile("\\d++");

	/**
	 * Return any array dimension notation (any of the recognized forms) that
	 * "ends" the string (i.e., is followed by at most {@code separator} before
	 * the string ends).
	 *<p>
	 * If a non-null string is returned, the matcher's region-end has been
	 * adjusted to exclude it.
	 *<p>
	 * The matcher's associated pattern may have been changed, and the region
	 * transiently changed, but on return the region will either be the same as
	 * on entry (if no array notation was found), or have only the region end
	 * adjusted to exclude the notation.
	 *<p>
	 * The returned string can include a {@code separator} that followed the
	 * array notation.
	 */
	private static String arrayNotationIfPresent(Matcher m, String s)
	{
		int originalRegionStart = m.regionStart();
		int notationStart;
		int dims;
		boolean atMostOneDimAllowed; // true after ARRAY keyword

restart:for ( ;; )
		{
			notationStart = -1;
			dims = 0;
			atMostOneDimAllowed = false;

			m.usePattern(s_arrayDimStart);
			if ( ! m.find() )
				break restart; // notationStart is -1 indicating not found

			notationStart = m.start();
			if ( ! "[".equals(m.group()) ) // saw ARRAY
			{
				atMostOneDimAllowed = true;
				m.region(m.end(), m.regionEnd());
				separator(m, false);
				if ( ! s.startsWith("[", m.regionStart()) )
				{
					if ( m.regionStart() == m.regionEnd() )
					{
						dims = 1; // ARRAY separator $ --ok (means 1 dim)
						break restart;
					}
					/*
					 * ARRAY separator something-other-than-[
					 * This is not the match we're looking for. The regionStart
					 * already points here, so restart the loop to look for
					 * another potential array notation start beyond this point.
					 */
					continue restart;
				}
				m.region(m.regionStart() + 1, m.regionEnd());
			}

			/*
			 * Invariant: have seen [ and regionStart still points to it.
			 * Accept optional digits, then ]
			 * Repeat if followed by a [
			 */
			for ( ;; )
			{
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);

				if ( m.usePattern(s_digits).lookingAt() )
				{
					m.region(m.end(), m.regionEnd());
					separator(m, false);
				}

				if ( ! s.startsWith("]", m.regionStart()) )
					continue restart;

				++ dims; // have seen a complete [ (\d+)? ]
				m.region(m.regionStart() + 1, m.regionEnd());
				separator(m, false);
				if ( s.startsWith("[", m.regionStart()) )
					continue;
				if ( m.regionStart() == m.regionEnd() )
					if ( ! atMostOneDimAllowed  ||  1 == dims )
						break restart;
				continue restart; // not at end, not at [ --start over
			}
		}

		if ( -1 == notationStart )
		{
			m.region(originalRegionStart, m.regionEnd());
			return null;
		}

		m.region(originalRegionStart, notationStart);
		return s.substring(notationStart);
	}

	static final class Reserved extends DBType
	{
		private final String m_reservedName;

		Reserved(String name)
		{
			m_reservedName = name;
		}

		@Override
		public String toString()
		{
			return m_reservedName;
		}

		@Override
		DependTag dependTag()
		{
			return null;
		}

		@Override
		public int hashCode()
		{
			return pgFold(m_reservedName).hashCode();
		}
	}

	static final class Named extends DBType
	{
		private final Identifier.Qualified m_ident;

		Named(Identifier.Qualified ident)
		{
			m_ident = ident;
		}

		@Override
		public String toString()
		{
			return m_ident.toString();
		}

		@Override
		DependTag dependTag()
		{
			return new DependTag.Type(m_ident);
		}

		@Override
		public int hashCode()
		{
			return m_ident.hashCode();
		}
	}

	static final class Modified extends DBType
	{
		private final DBType m_raw;
		private final String m_modifier;

		Modified(DBType raw, String modifier)
		{
			m_raw = raw;
			m_modifier = modifier;
		}

		@Override
		public String toString()
		{
			return m_raw.toString() + m_modifier;
		}

		@Override
		DBType withModifier(String modifier)
		{
			throw new UnsupportedOperationException(
				"withModifier on a Modified");
		}

		@Override
		DependTag dependTag()
		{
			return m_raw.dependTag();
		}

		@Override
		public int hashCode()
		{
			return m_raw.hashCode();
		}

		@Override
		DBType leaf()
		{
			return m_raw.leaf();
		}
	}

	static final class Array extends DBType
	{
		private final DBType m_component;
		private final int m_dims;
		private final String m_notated;

		Array(DBType component, String notated)
		{
			assert component instanceof Named
				|| component instanceof Reserved
				|| component instanceof Modified;
			int dims = 0;
			for ( int pos = 0; -1 != (pos = notated.indexOf('[', pos)); ++ pos )
				++ dims;
			m_dims = 0 == dims ? 1 : dims; // "ARRAY" with no [ has dimension 1
			m_notated = notated;
			m_component = requireNonNull(component);
		}

		@Override
		Array asArray(String notated)
		{
			/* Implementable in principle, but may never be needed */
			throw new UnsupportedOperationException("asArray on an Array");
		}

		@Override
		public String toString()
		{
			return m_component.toString() + m_notated;
		}

		@Override
		DependTag dependTag()
		{
			return m_component.dependTag();
		}

		@Override
		boolean isArray()
		{
			return true;
		}

		@Override
		public int hashCode()
		{
			return m_component.hashCode();
		}
	}

	static final class Defaulting extends DBType
	{
		private final DBType m_raw;
		private final String m_suffix;

		Defaulting(DBType raw, String suffix)
		{
			assert ! (raw instanceof Defaulting);
			m_raw = requireNonNull(raw);
			m_suffix = suffix;
		}

		@Override
		Modified withModifier(String notated)
		{
			throw new UnsupportedOperationException(
				"withModifier on a Defaulting");
		}

		@Override
		Array asArray(String notated)
		{
			throw new UnsupportedOperationException("asArray on a Defaulting");
		}

		@Override
		Array withDefault(String suffix)
		{
			/* Implementable in principle, but may never be needed */
			throw new UnsupportedOperationException(
				"withDefault on a Defaulting");
		}

		@Override
		public String toString()
		{
			return m_raw.toString() + " " + m_suffix;
		}

		@Override
		String toString(boolean withDefault)
		{
			return withDefault ? toString() : m_raw.toString();
		}

		@Override
		DependTag dependTag()
		{
			return m_raw.dependTag();
		}

		@Override
		boolean isArray()
		{
			return m_raw.isArray();
		}

		@Override
		public int hashCode()
		{
			return m_raw.hashCode();
		}

		@Override
		DBType leaf()
		{
			return m_raw.leaf();
		}
	}
}

/**
 * Abstraction of a dependency tag, encompassing {@code Explicit} ones declared
 * in annotations and distinguished by {@code String}s, and others added
 * implicitly such as {@code Type}s known by {@code Identifier.Qualified}.
 */
abstract class DependTag<T>
{
	protected final T m_value;

	protected DependTag(T value)
	{
		m_value = value;
	}

	@Override
	public int hashCode()
	{
		return hash(getClass(), m_value);
	}

	@Override
	public final boolean equals(Object o)
	{
		return equals(o, null);
	}

	public boolean equals(Object o, Messager msgr)
	{
		if ( this == o )
			return true;
		if ( null == o )
			return false;
		return
			getClass() == o.getClass()
				&&  m_value.equals(((DependTag<?>)o).m_value);
	}

	@Override
	public String toString()
	{
		return '(' + getClass().getSimpleName() + ')' + m_value.toString();
	}

	static final class Explicit extends DependTag<String>
	{
		Explicit(String value)
		{
			super(requireNonNull(value));
		}
	}

	static abstract class Named<T extends Identifier> extends DependTag<T>
	{
		Named(T value)
		{
			super(value);
		}

		@Override
		public boolean equals(Object o, Messager msgr)
		{
			if ( this == o )
				return true;
			if ( null == o )
				return false;
			return
				getClass() == o.getClass()
					&&  m_value.equals(((DependTag<?>)o).m_value, msgr);
		}
	}

	static final class Type extends Named<Identifier.Qualified>
	{
		Type(Identifier.Qualified value)
		{
			super(requireNonNull(value));
		}
	}

	static final class Function extends Named<Identifier.Qualified>
	{
		private DBType[] m_signature;

		Function(Identifier.Qualified value, DBType[] signature)
		{
			super(requireNonNull(value));
			m_signature = signature.clone();
		}

		@Override
		public boolean equals(Object o, Messager msgr)
		{
			if ( ! super.equals(o, msgr) )
				return false;
			Function f = (Function)o;
			if ( m_signature.length != f.m_signature.length )
				return false;
			for ( int i = 0; i < m_signature.length; ++ i )
			{
				if ( null == m_signature[i]  ||  null == f.m_signature[i] )
				{
					if ( m_signature[i] != f.m_signature[i] )
						return false;
					continue;
				}
				if ( ! m_signature[i].equals(f.m_signature[i], msgr) )
					return false;
			}
			return true;
		}
	}
}

/**
 * Tiny 'record' used in factoring duplicative operations on function parameter
 * lists into operations on streams of these.
 */
class ParameterInfo
{
	final TypeMirror tm;
	final VariableElement ve;
	final SQLType st;
	final DBType dt;

	ParameterInfo(TypeMirror m, VariableElement e, SQLType t, DBType d)
	{
		tm = m;
		ve = e;
		st = t;
		dt = d;
	}
}
