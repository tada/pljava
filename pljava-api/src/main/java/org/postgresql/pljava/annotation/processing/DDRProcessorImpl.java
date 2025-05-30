/*
 * Copyright (c) 2004-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Purdue University
 *   Chapman Flack
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
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import static java.util.Objects.requireNonNull;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import static java.util.function.UnaryOperator.identity;

import java.util.stream.Stream;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;

import javax.lang.model.SourceVersion;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.NestingKind;
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

import org.postgresql.pljava.annotation.Aggregate;
import org.postgresql.pljava.annotation.Cast;
import org.postgresql.pljava.annotation.Function;
import org.postgresql.pljava.annotation.Operator;
import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLActions;
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.Trigger;
import org.postgresql.pljava.annotation.BaseUDT;
import org.postgresql.pljava.annotation.MappedUDT;

import org.postgresql.pljava.sqlgen.Lexicals;
import org.postgresql.pljava.sqlgen.Lexicals.Identifier;

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
	final Identifier.Simple nameTrusted;
	final Identifier.Simple nameUntrusted;
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
	final TypeElement  AN_SQLTYPE;
	final TypeElement  AN_TRIGGER;
	final TypeElement  AN_BASEUDT;
	final TypeElement  AN_MAPPEDUDT;
	final TypeElement  AN_SQLACTION;
	final TypeElement  AN_SQLACTIONS;
	final TypeElement  AN_CAST;
	final TypeElement  AN_CASTS;
	final TypeElement  AN_AGGREGATE;
	final TypeElement  AN_AGGREGATES;
	final TypeElement  AN_OPERATOR;
	final TypeElement  AN_OPERATORS;

	// Certain familiar DBTypes (capitalized as this file historically has)
	//
	final DBType DT_BOOLEAN = new DBType.Reserved("boolean");
	final DBType DT_INTEGER = new DBType.Reserved("integer");
	final DBType DT_RECORD  = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.RECORD"));
	final DBType DT_TRIGGER = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.trigger"));
	final DBType DT_VOID = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.void"));
	final DBType DT_ANY = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.\"any\""));
	final DBType DT_BYTEA = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.bytea"));
	final DBType DT_INTERNAL = new DBType.Named(
		Identifier.Qualified.nameFromJava("pg_catalog.internal"));

	// Function signatures for certain known functions
	//
	final DBType[] SIG_TYPMODIN =
		{ DBType.fromSQLTypeAnnotation("pg_catalog.cstring[]") };
	final DBType[] SIG_TYPMODOUT = { DT_INTEGER };
	final DBType[] SIG_ANALYZE = { DT_INTERNAL };
	
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
			nameTrusted = Identifier.Simple.fromJava(optv);
		else
			nameTrusted = Identifier.Simple.fromJava("java");
		
		optv = opts.get( "ddr.name.untrusted");
		if ( null != optv )
			nameUntrusted = Identifier.Simple.fromJava(optv);
		else
			nameUntrusted = Identifier.Simple.fromJava("javaU");
		
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
		
		TY_ITERATOR          = declaredTypeForClass(java.util.Iterator.class);
		TY_OBJECT            = declaredTypeForClass(Object.class);
		TY_RESULTSET         = declaredTypeForClass(java.sql.ResultSet.class);
		TY_RESULTSETPROVIDER = declaredTypeForClass(ResultSetProvider.class);
		TY_RESULTSETHANDLE   = declaredTypeForClass(ResultSetHandle.class);
		TY_SQLDATA           = declaredTypeForClass(SQLData.class);
		TY_SQLINPUT          = declaredTypeForClass(SQLInput.class);
		TY_SQLOUTPUT         = declaredTypeForClass(SQLOutput.class);
		TY_STRING            = declaredTypeForClass(String.class);
		TY_TRIGGERDATA       = declaredTypeForClass(TriggerData.class);
		TY_VOID              = typu.getNoType(TypeKind.VOID);

		AN_FUNCTION    = elmu.getTypeElement( Function.class.getName());
		AN_SQLTYPE     = elmu.getTypeElement( SQLType.class.getName());
		AN_TRIGGER     = elmu.getTypeElement( Trigger.class.getName());
		AN_BASEUDT     = elmu.getTypeElement( BaseUDT.class.getName());
		AN_MAPPEDUDT   = elmu.getTypeElement( MappedUDT.class.getName());

		// Repeatable annotations and their containers.
		//
		AN_SQLACTION   = elmu.getTypeElement( SQLAction.class.getName());
		AN_SQLACTIONS  = elmu.getTypeElement( SQLActions.class.getName());
		AN_CAST   = elmu.getTypeElement( Cast.class.getName());
		AN_CASTS  = elmu.getTypeElement(
			Cast.Container.class.getCanonicalName());
		AN_AGGREGATE   = elmu.getTypeElement( Aggregate.class.getName());
		AN_AGGREGATES  = elmu.getTypeElement(
			Aggregate.Container.class.getCanonicalName());
		AN_OPERATOR   = elmu.getTypeElement( Operator.class.getName());
		AN_OPERATORS  = elmu.getTypeElement(
			Operator.Container.class.getCanonicalName());
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
	 * Map a {@code Class} to a {@code TypeElement} and from there to a
	 * {@code DeclaredType}.
	 *<p>
	 * This needs to work around some weird breakage in javac 10 and 11 when
	 * given a {@code --release} option naming an earlier release, as described
	 * in commit c763cee. The version of of {@code getTypeElement} with a module
	 * parameter is needed then, because the other version will go bonkers and
	 * think it found the class <em>in every module that transitively requires
	 * its actual module</em> and then return null because the result wasn't
	 * unique. That got fixed in Java 12, but because 11 is the LTS release and
	 * there won't be another for a while yet, it is better to work around the
	 * issue here.
	 *<p>
	 * If not supporting Java 10 or 11, this could be simplified to
	 * {@code typu.getDeclaredType(elmu.getTypeElement(className))}.
	 */
	private DeclaredType declaredTypeForClass(Class<?> clazz)
	{
		String className = clazz.getName();
		String moduleName = clazz.getModule().getName();

		TypeElement e;

		if ( null == moduleName )
			e = elmu.getTypeElement(className);
		else
		{
			ModuleElement m = elmu.getModuleElement(moduleName);
			if ( null == m )
				e = elmu.getTypeElement(className);
			else
				e = elmu.getTypeElement(m, className);
		}

		requireNonNull(e,
			() -> "unexpected failure to resolve TypeElement " + className);

		DeclaredType t = typu.getDeclaredType(e);

		requireNonNull(t,
			() -> "unexpected failure to resolve DeclaredType " + e);

		return t;
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
	/*
	 * This is a LinkedHashMap so that the order of handling annotation types
	 * in process() below will be preserved in calling their characterize()
	 * methods at end-of-round, and so, for example, characterize() on a Cast
	 * can use values set by characterize() on an associated Function.
	 */
	Map<SnippetsKey, Snippet> snippets = new LinkedHashMap<>();

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
	 * that 'provides' it (snippets, in some cases). Has to be out here as an
	 * instance field for the same reason {@code snippetVPairs} does.
	 *<p>
	 * Originally limited each tag to have only one provider; that is still
	 * enforced for implicitly-generated tags, but relaxed for explicit ones
	 * supplied in annotations, hence the list.
	 */
	Map<DependTag, List<VertexPair<Snippet>>> provider = new HashMap<>();
	
	/**
	 * Find the elements in each round that carry any of the annotations of
	 * interest and generate code snippets accordingly. On the last round, with
	 * all processing complete, generate the deployment descriptor file.
	 */
	boolean process( Set<? extends TypeElement> tes, RoundEnvironment re)
	{
		boolean functionPresent = false;
		boolean sqlActionPresent = false;
		boolean baseUDTPresent = false;
		boolean mappedUDTPresent = false;
		boolean castPresent = false;
		boolean aggregatePresent = false;
		boolean operatorPresent = false;
		
		boolean willClaim = true;
		
		for ( TypeElement te : tes )
		{
			if ( AN_FUNCTION.equals( te) )
				functionPresent = true;
			else if ( AN_BASEUDT.equals( te) )
				baseUDTPresent = true;
			else if ( AN_MAPPEDUDT.equals( te) )
				mappedUDTPresent = true;
			else if ( AN_SQLTYPE.equals( te) )
				; // these are handled within FunctionImpl
			else if ( AN_SQLACTION.equals( te) || AN_SQLACTIONS.equals( te) )
				sqlActionPresent = true;
			else if ( AN_CAST.equals( te) || AN_CASTS.equals( te) )
				castPresent = true;
			else if ( AN_AGGREGATE.equals( te) || AN_AGGREGATES.equals( te) )
				aggregatePresent = true;
			else if ( AN_OPERATOR.equals( te) || AN_OPERATORS.equals( te) )
				operatorPresent = true;
			else
			{
				msg( Kind.WARNING, te,
					"PL/Java annotation processor version may be older than " +
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
			for ( Element e
				: re.getElementsAnnotatedWithAny( AN_SQLACTION, AN_SQLACTIONS) )
				processRepeatable(
					e, AN_SQLACTION, AN_SQLACTIONS, SQLActionImpl.class, null);

		if ( castPresent )
			for ( Element e
				: re.getElementsAnnotatedWithAny( AN_CAST, AN_CASTS) )
				processRepeatable(
					e, AN_CAST, AN_CASTS, CastImpl.class, null);

		if ( operatorPresent )
			for ( Element e
				: re.getElementsAnnotatedWithAny( AN_OPERATOR, AN_OPERATORS) )
				processRepeatable(
					e, AN_OPERATOR, AN_OPERATORS, OperatorImpl.class,
					this::operatorPreSynthesize);

		if ( aggregatePresent )
			for ( Element e
				: re.getElementsAnnotatedWithAny( AN_AGGREGATE, AN_AGGREGATES) )
				processRepeatable(
					e, AN_AGGREGATE, AN_AGGREGATES, AggregateImpl.class, null);

		tmpr.workAroundJava7Breakage(); // perhaps to be fixed in Java 9? nope.

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
			Set<Snippet> ready = snip.characterize();
			for ( Snippet readySnip : ready )
			{
				VertexPair<Snippet> v = new VertexPair<>( readySnip);
				snippetVPairs.add( v);
				for ( DependTag t : readySnip.provideTags() )
				{
					List<VertexPair<Snippet>> ps =
						provider.computeIfAbsent(t, k -> new ArrayList<>());
					/*
					 * Explicit tags are allowed more than one provider.
					 */
					if ( t instanceof DependTag.Explicit  ||  ps.isEmpty() )
						ps.add(v);
					else
						msg(Kind.ERROR, "tag %s has more than one provider", t);
				}
			}
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
			List<VertexPair<Snippet>> ps;

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
				ps = provider.get( imp);
				if ( null != ps )
				{
					fwdConsumers.add( imp);
					revConsumers.add( imp);

					ps.forEach(p ->
					{
						p.fwd.precede( v.fwd);
						p.rev.precede( v.rev);

						/*
						 * A snippet providing an implementor tag probably has
						 * no undeployStrings, because its deployStrings should
						 * be used on both occasions; if so, replace it with a
						 * proxy that returns deployStrings for undeployStrings.
						 */
						if ( 0 == p.rev.payload.undeployStrings().length )
							p.rev.payload = new ImpProvider( p.rev.payload);
					});
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
				ps = provider.get( s);
				if ( null != ps )
				{
					fwdConsumers.add( s);
					revConsumers.add( s);
					ps.forEach(p ->
					{
						p.fwd.precede( v.fwd);
						v.rev.precede( p.rev); // these relationships do reverse
					});
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

	<T extends Repeatable> void putRepeatableSnippet(Element e, T snip)
	{
		if ( null != snip )
			putSnippet( snip, (Snippet)snip);
	}
	
	/**
	 * Process an element carrying a repeatable annotation, the container
	 * of that repeatable annotation, or both.
	 *<p>
	 * Snippets corresponding to repeatable annotations might not be entered in the
	 * {@code snippets} map keyed by the target element, as that might not be
	 * unique. Each populated snippet is passed to <em>putter</em> along with
	 * the element it annotates, and <em>putter</em> determines what to do with
	 * it. If <em>putter</em> is null, the default enters the snippet with a key
	 * made from its class and itself, as typical repeatable snippets are are
	 * not expected to be looked up, only processed when all of the map entries
	 * are enumerated.
	 *<p>
	 * After all snippets of the desired class have been processed for a given
	 * element, a final call to <em>putter</em> is made passing the element and
	 * null for the snippet.
	 */
	<T extends Repeatable> void processRepeatable(
		Element e, TypeElement annot, TypeElement container, Class<T> clazz,
		BiConsumer<Element,T> putter)
	{
		if ( null == putter )
			putter = this::putRepeatableSnippet;

		for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( e) )
		{
			Element asElement = am.getAnnotationType().asElement();
			if ( asElement.equals( annot) )
			{
				T snip;
				try
				{
					snip = clazz.getDeclaredConstructor( DDRProcessorImpl.class,
						Element.class, AnnotationMirror.class)
						.newInstance( DDRProcessorImpl.this, e, am);
				}
				catch ( ReflectiveOperationException re )
				{
					throw new RuntimeException(
						"Incorrect implementation of annotation processor", re);
				}
				populateAnnotationImpl( snip, e, am);
				putter.accept( e, snip);
			}
			else if ( asElement.equals( container) )
			{
				Container<T> c = new Container<>(clazz);
				populateAnnotationImpl( c, e, am);
				for ( T snip : c.value() )
					putter.accept( e, snip);
			}
		}

		putter.accept( e, null);
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
				msg( Kind.ERROR, e, "A PL/Java UDT must be a class");
			default:
				return;
		}
		Set<Modifier> mods = e.getModifiers();
		if ( ! mods.contains( Modifier.PUBLIC) )
		{
			msg( Kind.ERROR, e, "A PL/Java UDT must be public");
		}
		if ( mods.contains( Modifier.ABSTRACT) )
		{
			msg( Kind.ERROR, e, "A PL/Java UDT must not be abstract");
		}
		if ( ! ((TypeElement)e).getNestingKind().equals(
			NestingKind.TOP_LEVEL) )
		{
			if ( ! mods.contains( Modifier.STATIC) )
			{
				msg( Kind.ERROR, e,
					"When nested, a PL/Java UDT must be static (not inner)");
			}
			for ( Element ee = e; null != ( ee = ee.getEnclosingElement() ); )
			{
				if ( ! ee.getModifiers().contains( Modifier.PUBLIC) )
					msg( Kind.ERROR, ee,
						"A PL/Java UDT must not have a non-public " +
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
	 * it has the right modifiers to be called via PL/Java, analyze its type
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
			msg( Kind.ERROR, e, "A PL/Java function must be public");
		}

		for ( Element ee = e; null != ( ee = ee.getEnclosingElement() ); )
		{
			ElementKind ek = ee.getKind();
			switch ( ek )
			{
			case CLASS:
			case INTERFACE:
				break;
			default:
				msg( Kind.ERROR, ee,
					"A PL/Java function must not have an enclosing " + ek);
				return;
			}

			// It's a class or interface, represented by TypeElement
			TypeElement te = (TypeElement)ee;
			mods = ee.getModifiers();

			if ( ! mods.contains( Modifier.PUBLIC) )
				msg( Kind.ERROR, ee,
					"A PL/Java function must not have a non-public " +
					"enclosing class");

			if ( ! te.getNestingKind().isNested() )
				break; // no need to look above top-level class
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
		boolean commentDerived;

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
			{
				_comment = ((Commentable)this).derivedComment( e);
				commentDerived = true;
			}
		}

		protected void replaceCommentIfDerived( String comment)
		{
			if ( ! commentDerived )
				return;
			commentDerived = false;
			_comment = comment;
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

	class Repeatable extends AbstractAnnotationImpl
	{
		final Element m_targetElement;
		final AnnotationMirror m_origin;

		Repeatable(Element e, AnnotationMirror am)
		{
			m_targetElement = e;
			m_origin = am;
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
		public boolean optional() { return Boolean.TRUE.equals(_optional); }
		public String name() { return _name; }
		
		String _value;
		String[] _defaultValue;
		String _name;
		Boolean _optional; // boxed so it can be null if not explicit
		
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

		public void setOptional( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_optional = (Boolean)o;
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

	class Container<T extends Repeatable>
	extends AbstractAnnotationImpl
	{
		public T[] value() { return _value; }
		
		T[] _value;
		final Class<T> _clazz;

		Container(Class<T> clazz)
		{
			_clazz = clazz;
		}
		
		public void setValue( Object o, boolean explicit, Element e)
		{
			AnnotationMirror[] ams = avToArray( o, AnnotationMirror.class);

			@SuppressWarnings("unchecked")
			T[] t = (T[])Array.newInstance( _clazz, ams.length);
			_value = t;

			int i = 0;
			for ( AnnotationMirror am : ams )
			{
				try
				{
					T a = _clazz.getDeclaredConstructor(DDRProcessorImpl.class,
						Element.class, AnnotationMirror.class)
						.newInstance(DDRProcessorImpl.this, e, am);
					populateAnnotationImpl( a, e, am);
					_value [ i++ ] = a;
				}
				catch ( ReflectiveOperationException re )
				{
					throw new RuntimeException(
						"Incorrect implementation of annotation processor", re);
				}
			}
		}
	}

	class SQLActionImpl
	extends Repeatable
	implements SQLAction, Snippet
	{
		SQLActionImpl(Element e, AnnotationMirror am)
		{
			super(e, am);
		}

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

		public Set<Snippet> characterize()
		{
			recordExplicitTags(_provides, _requires);
			return Set.of(this);
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

		public Set<Snippet> characterize()
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
			return Set.of();
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
			func.appendNameAndParams( sb, true, false, false);
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

	/**
	 * Enumeration of different method "shapes" and the treatment of
	 * {@code type=} and {@code out=} annotation elements they need.
	 *<p>
	 * Each member has a {@code setComposite} method that will be invoked
	 * by {@code checkOutType} if the method is judged to have a composite
	 * return type according to the annotations present.
	 *<p>
	 * There is one case (no {@code out} and a {@code type} other than
	 * {@code RECORD}) where {@code checkOutType} will resolve the
	 * ambiguity by assuming composite, and will have set
	 * {@code assumedComposite} accordingly. The {@code MAYBECOMPOSITE}
	 * shape checks that assumption against the presence of a countervailing
	 * {@code SQLType} annotation, the {@code ITERATOR} shape clears it and
	 * behaves as noncomposite as always, and the {@code PROVIDER} shape
	 * clears it because that shape is unambiguously composite.
	 */
	enum MethodShape
	{
		/**
		 * Method has the shape {@code boolean foo(..., ResultSet)}, which
		 * could be an ordinary method with an incoming record parameter and
		 * boolean return, or a composite-returning method whose last
		 * a writable ResultSet supplied by PL/Java for the return value.
		 */
		MAYBECOMPOSITE((f,msgr) ->
		{
			boolean sqlTyped = null !=
				f.paramTypeAnnotations[f.paramTypeAnnotations.length - 1];
			if ( ! sqlTyped )
				f.complexViaInOut = true;
			else if ( f.assumedComposite )
				f.assumedComposite = false; // SQLType cancels assumption
			else
				msgr.printMessage(Kind.ERROR,
					"no @SQLType annotation may appear on " +
					"the return-value ResultSet parameter", f.func);
		}),

		/**
		 * Method has the shape {@code Iterator<T> foo(...)} and represents
		 * a set-returning function with a non-composite return type.
		 *<p>
		 * If the shape has been merely <em>assumed</em> composite, clear
		 * that flag and proceed as if it is not. Otherwise, issue an error
		 * that it can't be composite.
		 */
		ITERATOR((f,msgr) ->
		{
			if ( f.assumedComposite )
				f.assumedComposite = false;
			else
				msgr.printMessage(Kind.ERROR,
					"the iterator style cannot return a row-typed result",
					f.func);
		}),

		/**
		 * Method has the shape {@code ResultSetProvider foo(...)} or
		 * {@code ResultSetHandle foo(...)} and represents
		 * a set-returning function with a non-composite return type.
		 *<p>
		 * If the shape has been merely <em>assumed</em> composite, clear
		 * that flag; for this shape that assumption is not tentative.
		 */
		PROVIDER((f,msgr) -> f.assumedComposite = false),

		/**
		 * Method is something else (trigger, for example) for which no
		 * {@code type} or {@code out} is allowed.
		 *<p>
		 * The {@code setComposite} method for this shape will never
		 * be called.
		 */
		OTHER(null);

		private final BiConsumer<FunctionImpl,Messager> compositeSetter;

		MethodShape(BiConsumer<FunctionImpl,Messager> setter)
		{
			compositeSetter = setter;
		}

		void setComposite(FunctionImpl f, Messager msgr)
		{
			compositeSetter.accept(f, msgr);
		}
	}

	class FunctionImpl
	extends AbstractAnnotationImpl
	implements Function, Snippet, Commentable
	{
		public String             type() { return _type; }
		public String[]            out() { return _out; }
		public String             name() { return _name; }
		public String           schema() { return _schema; }
		public boolean        variadic() { return _variadic; }
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
		public String         language()
		{
			return _languageIdent.toString();
		}

		ExecutableElement func;

		public String      _type;
		public String[]    _out;
		public String      _name;
		public String      _schema;
		public boolean     _variadic;
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

		public Identifier.Simple _languageIdent;

		boolean complexViaInOut = false;
		boolean setof = false;
		TypeMirror setofComponent = null;
		boolean trigger = false;
		TypeMirror returnTypeMapKey = null;
		SQLType[] paramTypeAnnotations;

		DBType returnType;
		DBType[] parameterTypes;
		List<Map.Entry<Identifier.Simple,DBType>> outParameters;
		boolean assumedComposite = false;
		boolean forceResultRecord = false;

		boolean subsumed = false;

		FunctionImpl(ExecutableElement e)
		{
			func = e;
		}

		public void setType( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_type = (String)o;
		}

		public void setOut( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_out = avToArray( o, String.class);
		}

		public void setTrust( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_trust = Trust.valueOf(
					((VariableElement)o).getSimpleName().toString());
		}

		public void setLanguage( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_languageIdent = Identifier.Simple.fromJava((String)o);
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

		public Set<Snippet> characterize()
		{
			if ( "".equals( _name) )
				_name = func.getSimpleName().toString();

			resolveLanguage();

			Set<Modifier> mods = func.getModifiers();
			if ( ! mods.contains( Modifier.STATIC) )
			{
				msg( Kind.ERROR, func, "A PL/Java function must be static");
			}

			TypeMirror ret = func.getReturnType();
			if ( ret.getKind().equals( TypeKind.ERROR) )
			{
				msg( Kind.ERROR, func,
					"Unable to resolve return type of function");
				return Set.of();
			}

			ExecutableType et = (ExecutableType)func.asType();
			List<? extends TypeMirror> ptms = et.getParameterTypes();
			List<? extends TypeMirror> typeArgs;
			int arity = ptms.size();

			/*
			 * Collect the parameter type annotations now, in case needed below
			 * in checkOutType(MAYBECOMPOSITE) to disambiguate.
			 */

			collectParameterTypeAnnotations();

			/*
			 * If a type= annotation is present, provisionally set returnType
			 * accordingly. Otherwise, leave it null, to be filled in by
			 * resolveParameterAndReturnTypes below.
			 */

			if ( null != _type )
				returnType = DBType.fromSQLTypeAnnotation(_type);

			/*
			 * Take a first look according to the method's Java return type.
			 */
			if ( ret.getKind().equals( TypeKind.BOOLEAN) )
			{
				if ( 0 < arity )
				{
					TypeMirror tm = ptms.get( arity - 1);
					if ( ! tm.getKind().equals( TypeKind.ERROR)
						// unresolved things seem assignable to anything
						&& typu.isSameType( tm, TY_RESULTSET) )
					{
						checkOutType(MethodShape.MAYBECOMPOSITE);
					}
				}
			}
			else if ( null != (typeArgs = specialization( ret, TY_ITERATOR)) )
			{
				setof = true;
				if ( 1 != typeArgs.size() )
				{
					msg( Kind.ERROR, func,
						"Need one type argument for Iterator return type");
					return Set.of();
				}
				setofComponent = typeArgs.get( 0);
				if ( null == setofComponent )
				{
					msg( Kind.ERROR, func,
						"Failed to find setof component type");
					return Set.of();
				}
				checkOutType(MethodShape.ITERATOR);
			}
			else if ( typu.isAssignable( ret, TY_RESULTSETPROVIDER)
				|| typu.isAssignable( ret, TY_RESULTSETHANDLE) )
			{
				setof = true;
				checkOutType(MethodShape.PROVIDER);
			}
			else if ( ret.getKind().equals( TypeKind.VOID) && 1 == arity )
			{
				TypeMirror tm = ptms.get( 0);
				if ( ! tm.getKind().equals( TypeKind.ERROR)
					// unresolved things seem assignable to anything
					&& typu.isSameType( tm, TY_TRIGGERDATA) )
				{
					trigger = true;
					checkOutType(MethodShape.OTHER);
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

			/*
			 * Report any unmappable types now that could appear in
			 * deployStrings (return type or parameter types) ... so that the
			 * error messages won't be missing the source location, as they can
			 * with javac 7 throwing away symbol tables between rounds.
			 */
			resolveParameterAndReturnTypes();

			if ( _variadic )
			{
				int last = parameterTypes.length - 1;
				if ( 0 > last  ||  ! parameterTypes[last].isArray() )
					msg( Kind.ERROR, func,
						"VARIADIC function must have a last, non-output " +
						"parameter that is an array");
			}

			recordImplicitTags();

			recordExplicitTags(_provides, _requires);

			for ( Trigger t : triggers() )
				((TriggerImpl)t).characterize();
			return Set.of(this);
		}

		void resolveLanguage()
		{
			if ( null != _trust  &&  null != _languageIdent )
				msg( Kind.ERROR, func, "A PL/Java function may specify " +
					"only one of trust, language");
			if ( null == _languageIdent )
			{
				if ( null == _trust  ||  Trust.SANDBOXED == _trust )
					_languageIdent = nameTrusted;
				else
					_languageIdent = nameUntrusted;
			}
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
			boolean anyOptional = false;
			for ( VariableElement ve : ves )
			{
				for ( AnnotationMirror am : elmu.getAllAnnotationMirrors( ve) )
				{
					if ( am.getAnnotationType().asElement().equals(AN_SQLTYPE) )
					{
						SQLTypeImpl sti = new SQLTypeImpl();
						populateAnnotationImpl( sti, ve, am);
						paramTypeAnnotations[i] = sti;

						if (null != sti._optional && null != sti._defaultValue)
							msg(Kind.ERROR, ve, "Only one of optional= or " +
								"defaultValue= may be given");

						anyOptional |= sti.optional();
					}
				}
				++ i;
			}

			if ( anyOptional && OnNullInput.RETURNS_NULL.equals(_onNullInput) )
				msg(Kind.ERROR, func, "A PL/Java function with " +
					"onNullInput=RETURNS_NULL may not have parameters with " +
					"optional=true");
		}

		private static final int   NOOUT = 0;
		private static final int  ONEOUT = 4;
		private static final int MOREOUT = 8;

		private static final int     NOTYPE = 0;
		private static final int RECORDTYPE = 1;
		private static final int  OTHERTYPE = 2;

		/**
		 * Reads the tea leaves of the {@code type=} and {@code out=}
		 * annotation elements to decide whether the method has a composite
		 * or noncomposite return.
		 *<p>
		 * This is complicated by the PostgreSQL behavior of treating a function
		 * declared with <em>one</em> {@code OUT} parameter, or as
		 * a <em>one</em>-element {@code TABLE} function, as <em>not</em>
		 * returning a row type.
		 *<p>
		 * This method avoids rejecting the case of a one-element {@code out=}
		 * with an explicit {@code type=RECORD}, to provide a way to explicitly
		 * request composite behavior for that case, on the chance that some
		 * future PostgreSQL version may accept it, though as of this writing
		 * no current version does.
		 *<p>
		 * If the {@code MAYBECOMPOSITE} shape is used with a single {@code out}
		 * parameter, it is likely a mistake (what are the odds the developer
		 * wanted a function with a row-typed input parameter and a named out
		 * parameter of boolean type?), and will be rejected unless the
		 * {@code ResultSet} final parameter has been given an {@code SQLType}
		 * annotation.
		 */
		void checkOutType(MethodShape shape)
		{
			int out =
				null == _out ? NOOUT : 1 == _out.length ? ONEOUT : MOREOUT;

			/*
			 * The caller will have set returnType from _type if present,
			 * or left it null otherwise. We know RECORD is a composite type;
			 * we don't presume here to know whether any other type is or not.
			 */
			int type =
				null == returnType ? NOTYPE :
					DT_RECORD.equals(returnType) ? RECORDTYPE : OTHERTYPE;

			if ( MethodShape.OTHER == shape  &&  0 != (out | type) )
			{
				msg( Kind.ERROR, func,
					"no type= or out= element may be applied to this method");
				return;
			}

			switch ( out | type )
			{
			case   NOOUT | OTHERTYPE:
				assumedComposite = true; // annotations not definitive; assume
				shape.setComposite(this, msgr);
				return;
			case   NOOUT | RECORDTYPE:
			case MOREOUT | NOTYPE:
				shape.setComposite(this, msgr);
				return;
			case  ONEOUT | RECORDTYPE: // in case PostgreSQL one day allows this
				forceResultRecord = true;
				shape.setComposite(this, msgr);
				return;
			case  ONEOUT | NOTYPE:
				/*
				 * No special action needed here except for the MAYBECOMPOSITE
				 * or PROVIDER shapes, to check for likely mistakes.
				 */
				if ( MethodShape.MAYBECOMPOSITE == shape
					&& null ==
						paramTypeAnnotations[paramTypeAnnotations.length - 1] )
				{
					msg(Kind.ERROR, func,
						"a function with one declared OUT parameter returns " +
						"it normally, not through an extra ResultSet " +
						"parameter. If the trailing ResultSet parameter is " +
						"intended as an input, it can be marked with an " +
						"@SQLType annotation");
				}
				else if ( MethodShape.PROVIDER == shape )
				{
					msg(Kind.ERROR, func,
						"a set-returning function with one declared OUT " +
						"parameter must return an Iterator, not a " +
						"ResultSetProvider or ResultSetHandle");
				}
				return;
			case   NOOUT | NOTYPE:
				/*
				 * No special action; MAYBECOMPOSITE will treat as noncomposite,
				 * ITERATOR and PROVIDER will behave as they always do.
				 */
				return;
			case  ONEOUT | OTHERTYPE:
				msg( Kind.ERROR, func,
					"no type= allowed here (the out parameter " +
					"declares its own type)");
				return;
			case MOREOUT | RECORDTYPE:
			case MOREOUT | OTHERTYPE:
				msg( Kind.ERROR, func,
					"type= and out= may not be combined here");
				return;
			default:
				throw new AssertionError("unhandled case");
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
			if ( null != returnType )
				/* it was already set from a type= attribute */;
			else if ( null != setofComponent )
				returnType = tmpr.getSQLType( setofComponent, func);
			else if ( setof )
				returnType = DT_RECORD;
			else
				returnType = tmpr.getSQLType( returnTypeMapKey, func);

			parameterTypes = parameterInfo()
				.map(i -> tmpr.getSQLType(i.tm, i.ve, i.st, true, true))
				.toArray(DBType[]::new);

			if ( null != _out )
			{
				outParameters = Arrays.stream(_out)
					.map(DBType::fromNameAndType)
					.collect(toList());
				if ( 1 < _out.length  ||  forceResultRecord )
					returnType = DT_RECORD;
				else
					returnType = outParameters.get(0).getValue();
			}
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

			if ( null != outParameters )
				outParameters.stream()
					.map(m -> m.getValue().dependTag())
					.filter(Objects::nonNull)
					.forEach(requires::add);
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
		 * @param sb StringBuilder in which to generate the SQL.
		 * @param names Whether to include the parameter names.
		 * @param outs Whether to include out parameters.
		 * @param dflts Whether to include the defaults, if any.
		 */
		void appendNameAndParams(
			StringBuilder sb, boolean names, boolean outs, boolean dflts)
		{
			appendNameAndParams(sb, names, outs, dflts,
				qnameFrom(name(), schema()), parameterInfo().collect(toList()));
		}

		/**
		 * Internal version taking name and parameter stream as extra arguments
		 * so they can be overridden from {@link Transformed}.
		 */
		void appendNameAndParams(
			StringBuilder sb, boolean names, boolean outs, boolean dflts,
			Identifier.Qualified<Identifier.Simple> qname,
			Iterable<ParameterInfo> params)
		{
			sb.append(qname).append( '(');
			appendParams( sb, names, outs, dflts, params);
			// TriggerImpl relies on ) being the very last character
			sb.append( ')');
		}

		/**
		 * Takes the parameter stream as an extra argument
		 * so it can be overridden from {@link Transformed}.
		 */
		void appendParams(
			StringBuilder sb, boolean names, boolean outs, boolean dflts,
			Iterable<ParameterInfo> params)
		{
			int lengthOnEntry = sb.length();

			Iterator<ParameterInfo> iter = params.iterator();
			ParameterInfo i;
			while ( iter.hasNext() )
			{
				i = iter.next();

				String name = i.name();

				sb.append("\n\t");

				if ( _variadic  &&  ! iter.hasNext() )
					sb.append("VARIADIC ");

				if ( names )
					sb.append(name).append(' ');

				sb.append(i.dt.toString(dflts));

				sb.append(',');
			}

			if ( outs  &&  null != outParameters )
			{
				outParameters.forEach(e -> {
					sb.append("\n\tOUT ");
					if ( null != e.getKey() )
						sb.append(e.getKey()).append(' ');
					sb.append(e.getValue().toString(false)).append(',');
				});
			}

			if ( lengthOnEntry < sb.length() )
				sb.setLength(sb.length() - 1); // that last pesky comma
		}

		String makeAS()
		{
			StringBuilder sb = new StringBuilder();
			if ( ! ( complexViaInOut || setof || trigger ) )
				sb.append( typu.erasure( func.getReturnType())).append( '=');
			Element e = func.getEnclosingElement();
			// e was earlier checked and ensured to be a class or interface
			sb.append( elmu.getBinaryName((TypeElement)e)).append( '.');
			sb.append( trigger ? func.getSimpleName() : func.toString());
			return sb.toString();
		}

		public String[] deployStrings()
		{
			return deployStrings(
				qnameFrom(name(), schema()), parameterInfo().collect(toList()),
				makeAS(), comment());
		}

		/**
		 * Internal version taking the function name, parameter stream,
		 * AS string, and comment (if any) as extra arguments so they can be
		 * overridden from {@link Transformed}.
		 */
		String[] deployStrings(
			Identifier.Qualified<Identifier.Simple> qname,
			Iterable<ParameterInfo> params, String as, String comment)
		{
			ArrayList<String> al = new ArrayList<>();
			StringBuilder sb = new StringBuilder();
			if ( assumedComposite )
				sb.append("/*\n * PL/Java generated this declaration assuming" +
					"\n * a composite-returning function was intended." +
					"\n * If a boolean function with a row-typed parameter" +
					"\n * was intended, add any @SQLType annotation on the" +
					"\n * ResultSet final parameter to make the intent clear." +
					"\n */\n");
			if ( forceResultRecord )
				sb.append("/*\n * PL/Java generated this declaration for a" +
					"\n * function with one OUT parameter that was annotated" +
					"\n * to explicitly request treatment as a function that" +
					"\n * returns RECORD. A given version of PostgreSQL might" +
					"\n * not accept such a declaration. More at" +
					"\n * https://www.postgresql.org/message-id/" +
					"619BBE78.7040009%40anastigmatix.net" +
					"\n */\n");
			sb.append( "CREATE OR REPLACE FUNCTION ");
			appendNameAndParams( sb, true, true, true, qname, params);
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
			sb.append( _languageIdent.toString());
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
			sb.append( "\tAS ").append( DDRWriter.eQuote( as));
			al.add( sb.toString());

			if ( null != comment )
			{
				sb.setLength( 0);
				sb.append( "COMMENT ON FUNCTION ");
				appendNameAndParams( sb, true, false, false, qname, params);
				sb.append( "\nIS ");
				sb.append( DDRWriter.eQuote( comment));
				al.add( sb.toString());
			}
			
			for ( Trigger t : triggers() )
				for ( String s : ((TriggerImpl)t).deployStrings() )
					al.add( s);
			return al.toArray( new String [ al.size() ]);
		}
		
		public String[] undeployStrings()
		{
			return undeployStrings(
				qnameFrom(name(), schema()), parameterInfo().collect(toList()));
		}

		String[] undeployStrings(
			Identifier.Qualified<Identifier.Simple> qname,
			Iterable<ParameterInfo> params)
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
			appendNameAndParams( sb, true, false, false, qname, params);
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

		private Map<DependTag.Function,Transformed> m_variants= new HashMap<>();

		/**
		 * Return an instance representing a transformation of this function,
		 * or null on second and subsequent requests for the same
		 * transformation (so the caller will not register the variant more
		 * than once).
		 */
		Transformed transformed(
			Identifier.Qualified<Identifier.Simple> qname,
			boolean commute, boolean negate)
		{
			Transformed prospect = new Transformed(qname, commute, negate);
			DependTag.Function tag =
				(DependTag.Function)prospect.provideTags().iterator().next();
			Transformed found = m_variants.putIfAbsent(tag, prospect);
			if ( null == found )
				return prospect;
			return null;
		}

		class Transformed implements Snippet
		{
			final Identifier.Qualified<Identifier.Simple> m_qname;
			final boolean m_commute;
			final boolean m_negate;
			final String  m_comment;

			Transformed(
				Identifier.Qualified<Identifier.Simple> qname,
				boolean commute, boolean negate)
			{
				EnumSet<OperatorPath.Transform> how =
					EnumSet.noneOf(OperatorPath.Transform.class);
				if ( commute )
					how.add(OperatorPath.Transform.COMMUTATION);
				if ( negate )
					how.add(OperatorPath.Transform.NEGATION);
				assert ! how.isEmpty() : "no transformation to apply";
				m_qname = requireNonNull(qname);
				m_commute = commute;
				m_negate = negate;
				m_comment = "Function automatically derived by " + how +
					" from " + qnameFrom(
						FunctionImpl.this.name(), FunctionImpl.this.schema());
			}

			List<ParameterInfo> parameterInfo()
			{
				List<ParameterInfo> params =
					FunctionImpl.this.parameterInfo().collect(toList());
				if ( ! m_commute )
					return params;
				assert 2 == params.size() : "commute with arity != 2";
				Collections.reverse(params);
				return params;
			}

			@Override
			public Set<Snippet> characterize()
			{
				return Set.of();
			}

			@Override
			public Identifier.Simple implementorName()
			{
				return FunctionImpl.this.implementorName();
			}

			@Override
			public Set<DependTag> requireTags()
			{
				return FunctionImpl.this.requireTags();
			}

			@Override
			public Set<DependTag> provideTags()
			{
				DBType[] sig =
					parameterInfo().stream()
					.map(p -> p.dt)
					.toArray(DBType[]::new);
				return Set.of(new DependTag.Function(m_qname, sig));
			}

			@Override
			public String[] deployStrings()
			{
				String as = Stream.of(
					m_commute ? "commute" : (String)null,
					m_negate  ? "negate"  : (String)null)
					.filter(Objects::nonNull)
					.collect(joining(",", "[", "]"))
					+ FunctionImpl.this.makeAS();

				return FunctionImpl.this.deployStrings(
					m_qname, parameterInfo(), as, m_comment);
			}

			@Override
			public String[] undeployStrings()
			{
				return FunctionImpl.this.undeployStrings(
					m_qname, parameterInfo());
			}
		}
	}

	static enum BaseUDTFunctionID
	{
		INPUT("in", null, "pg_catalog.cstring", "pg_catalog.oid", "integer"),
		OUTPUT("out", "pg_catalog.cstring", (String[])null),
		RECEIVE("recv", null, "pg_catalog.internal","pg_catalog.oid","integer"),
		SEND("send", "pg_catalog.bytea", (String[])null);
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
			_variadic = false;
			_cost = -1;
			_rows = -1;
			_onNullInput = OnNullInput.CALLED;
			_security = Security.INVOKER;
			_effects = Effects.VOLATILE;
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
		public String[] deployStrings()
		{
			return deployStrings(
				qnameFrom(name(), schema()),
				null, // parameter iterable unused in appendParams below
				"UDT[" + elmu.getBinaryName(te) + "] " + id.name(),
				comment());
		}

		@Override
		public String[] undeployStrings()
		{
			return undeployStrings(
				qnameFrom(name(), schema()),
				null); // parameter iterable unused in appendParams below
		}

		@Override
		void appendParams(
			StringBuilder sb, boolean names, boolean outs, boolean dflts,
			Iterable<ParameterInfo> params)
		{
			sb.append(
				Arrays.stream(id.getParam( ui))
				.map(Object::toString)
				.collect(joining(", "))
			);
		}

		StringBuilder appendTypeOp( StringBuilder sb)
		{
			sb.append( id.name()).append( " = ");
			return sb.append(qnameFrom(name(), schema()));
		}

		@Override
		public Set<Snippet> characterize()
		{
			resolveLanguage();
			recordImplicitTags();
			recordExplicitTags(_provides, _requires);
			return Set.of(this);
		}

		public void setType( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"The type of a UDT function may not be changed");
		}

		public void setOut( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,
					"The type of a UDT function may not be changed");
		}

		public void setVariadic( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				msg( Kind.ERROR, e,	"A UDT function is never variadic");
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
				msg( Kind.ERROR, e,	"A PL/Java UDT must implement %s",
					TY_SQLDATA);
			}

			ExecutableElement niladicCtor =	huntFor(
				constructorsIn( tclass.getEnclosedElements()), null, false,
					null);

			if ( null == niladicCtor )
			{
				msg( Kind.ERROR, tclass,
					"A PL/Java UDT must have a public no-arg constructor");
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

		public Set<Snippet> characterize()
		{
			if ( null != structure() )
			{
				DependTag t = qname.dependTag();
				if ( null != t )
					provideTags().add(t);
			}
			recordExplicitTags(_provides, _requires);
			return Set.of(this);
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
				DDRWriter.eQuote( elmu.getBinaryName(tclass)) + ')');
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
			public Set<Snippet> characterize()
			{
				return Set.of();
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
					"A PL/Java UDT must have a public static " +
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

		public Set<Snippet> characterize()
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

			if ( categoryExplicit && Character.isUpperCase(category()) )
				if ( null == PredefinedCategory.valueOf(category()) )
					msg( Kind.WARNING, tclass,
						"upper-case letters are reserved for PostgreSQL's " +
						"predefined UDT categories, but '%c' is not recognized",
						category());

			recordImplicitTags();
			recordExplicitTags(_provides, _requires);

			return Set.of(this);
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

	class CastImpl
	extends Repeatable
	implements Cast, Snippet, Commentable
	{
		CastImpl(Element e, AnnotationMirror am)
		{
			super(e, am);
		}

		public String                   from() { return _from; }
		public String                     to() { return _to;   }
		public Cast.Path                path() { return _path; }
		public Cast.Application  application() { return _application; }
		public String[]             provides() { return _provides; }
		public String[]             requires() { return _requires; }

		public String _from;
		public String _to;
		public Cast.Path _path;
		public Cast.Application _application;
		public String[] _provides;
		public String[] _requires;

		FunctionImpl func;
		DBType fromType;
		DBType toType;

		public void setPath( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				_path = Path.valueOf(
					((VariableElement)o).getSimpleName().toString());
		}

		public Set<Snippet> characterize()
		{
			boolean ok = true;

			if ( ElementKind.METHOD.equals(m_targetElement.getKind()) )
			{
				func = getSnippet(m_targetElement, FunctionImpl.class,
					() -> (FunctionImpl)null);
				if ( null == func )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"A method annotated with @Cast must also have @Function"
					);
					ok = false;
				}
			}

			if ( null == func  &&  "".equals(_from) )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Cast not annotating a method must specify from="
				);
				ok = false;
			}

			if ( null == func  &&  "".equals(_to) )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Cast not annotating a method must specify to="
				);
				ok = false;
			}

			if ( null == func  &&  null == _path )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Cast not annotating a method, and without path=, " +
					"is not yet supported"
				);
				ok = false;
			}

			if ( ok )
			{
				fromType = ("".equals(_from))
					? func.parameterTypes[0]
					: DBType.fromSQLTypeAnnotation(_from);

				toType = ("".equals(_to))
					? func.returnType
					: DBType.fromSQLTypeAnnotation(_to);
			}

			if ( null != _path )
			{
				if ( ok  &&  Path.BINARY == _path  &&  fromType.equals(toType) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"A cast with from and to types the same can only " +
						"apply a type modifier; path=BINARY will have " +
						"no effect");
					ok = false;
				}
			}
			else if ( null != func )
			{
				int nparams = func.parameterTypes.length;

				if ( ok  &&  2 > nparams  &&  fromType.equals(toType) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"A cast with from and to types the same can only " +
						"apply a type modifier, therefore must have at least " +
						"two parameters");
					ok = false;
				}

				if ( 1 > nparams || nparams > 3 )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"A cast function must have 1, 2, or 3 parameters");
					ok = false;
				}

				if (1 < nparams && ! DT_INTEGER.equals(func.parameterTypes[1]))
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Parameter 2 of a cast function must have integer type"
					);
					ok = false;
				}

				if (3 == nparams && ! DT_BOOLEAN.equals(func.parameterTypes[2]))
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Parameter 3 of a cast function must have boolean type"
					);
					ok = false;
				}
			}

			if ( ! ok )
				return Set.of();

			recordImplicitTags();
			recordExplicitTags(_provides, _requires);
			return Set.of(this);
		}

		void recordImplicitTags()
		{
			Set<DependTag> requires = requireTags();

			DependTag<?> dt = fromType.dependTag();
			if ( null != dt )
				requires.add(dt);

			dt = toType.dependTag();
			if ( null != dt )
				requires.add(dt);

			if ( null == _path )
			{
				dt = func.provideTags().stream()
					.filter(DependTag.Function.class::isInstance)
					.findAny().get();
				requires.add(dt);
			}
		}

		public String[] deployStrings()
		{
			List<String> al = new ArrayList<>();

			StringBuilder sb = new StringBuilder();

			sb.append("CREATE CAST (")
				.append(fromType).append(" AS ").append(toType).append(")\n\t");

			if ( Path.BINARY == _path )
				sb.append("WITHOUT FUNCTION");
			else if ( Path.INOUT == _path )
				sb.append("WITH INOUT");
			else
			{
				sb.append("WITH FUNCTION ");
				func.appendNameAndParams(sb, false, false, false);
			}

			switch ( _application )
			{
			case ASSIGNMENT: sb.append("\n\tAS ASSIGNMENT"); break;
			case EXPLICIT:   break;
			case IMPLICIT:   sb.append("\n\tAS IMPLICIT");
			}

			al.add(sb.toString());

			if ( null != comment() )
				al.add(
					"COMMENT ON CAST (" +
					fromType + " AS " + toType + ") IS " +
					DDRWriter.eQuote(comment()));

			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			return new String[]
			{
				"DROP CAST (" + fromType + " AS " + toType + ")"
			};
		}
	}

	/*
	 * Called by processRepeatable for each @Operator processed.
	 * This happens before characterize, but after populating, so the
	 * operator's name and commutator/negator/synthetic elements can be
	 * inspected. All operators annotating a given element e are processed
	 * consecutively, and followed by a call with the same e and null snip.
	 *
	 * This will accumulate the snippets onto two lists, for non-synthetic and
	 * synthetic ones and, on the final call, process the lists to find possible
	 * paths from non-synthetic to synthetic ones via commutation and/or
	 * negation. The possible paths will be recorded on each synthetic operator.
	 * They will have to be confirmed during characterize after things like
	 * operand types and arity have been resolved.
	 */
	void operatorPreSynthesize( Element e, OperatorImpl snip)
	{
		if ( ! ElementKind.METHOD.equals(e.getKind()) )
		{
			if ( null != snip )
				putSnippet( snip, (Snippet)snip);
			return;
		}

		if ( null != snip )
		{
			if ( snip.selfCommutator  ||  snip.twinCommutator )
				snip.commutator = snip.qname;

			(snip.isSynthetic ? m_synthetic : m_nonSynthetic).add(snip);
			return;
		}

		/*
		 * Initially:
		 *  processed: is empty
		 *      ready: contains all non-synthetic snippets
		 *    pending: contains all synthetic snippets
		 * Step:
		 *  A snippet s is removed from ready and added to processed.
		 *  If s.commutator or s.negator matches a synthetic snippet in pending,
		 *  a corresponding path is recorded on that snippet. If it is
		 *  the first path recorded on that snippet, the snippet is moved
		 *  to ready.
		 */

		List<OperatorImpl> processed =
			new ArrayList<>(m_nonSynthetic.size() + m_synthetic.size());
		Queue<OperatorImpl> ready = new LinkedList<>(m_nonSynthetic);
		LinkedList<OperatorImpl> pending = new LinkedList<>(m_synthetic);
		m_nonSynthetic.clear();
		m_synthetic.clear();

		while ( null != (snip = ready.poll()) )
		{
			processed.add(snip);
			if ( null != snip.commutator )
			{
				ListIterator<OperatorImpl> it = pending.listIterator();
				while ( it.hasNext() )
				{
					OperatorImpl other = it.next();
					if ( maybeAddPath(snip, other,
						OperatorPath.Transform.COMMUTATION) )
					{
						it.remove();
						ready.add(other);
					}
				}
			}
			if ( null != snip.negator )
			{
				ListIterator<OperatorImpl> it = pending.listIterator();
				while ( it.hasNext() )
				{
					OperatorImpl other = it.next();
					if ( maybeAddPath(snip, other,
						OperatorPath.Transform.NEGATION) )
					{
						it.remove();
						ready.add(other);
					}
				}
			}
		}

		if ( ! pending.isEmpty() )
			msg(Kind.ERROR, e, "Cannot synthesize operator(s) (%s)",
				pending.stream()
					.map(o -> o.qname.toString())
					.collect(joining(" ")));

		for ( OperatorImpl s : processed )
			putSnippet( s, (Snippet)s);
	}

	boolean maybeAddPath(
		OperatorImpl from, OperatorImpl to, OperatorPath.Transform how)
	{
		if ( ! to.isSynthetic )
			return false; // don't add paths to a non-synthetic operator

		/*
		 * setSynthetic will have left synthetic null in the synthetic=TWIN
		 * case. That case imposes more constraints on what paths can be added:
		 * an acceptable path must involve commutation (and only commutation)
		 * from another operator that will have a function name (so, either
		 * a non-synthetic one, or a synthetic one given an actual name, other
		 * than TWIN). In the latter case, copy the name here (for the former,
		 * it will be copied from the function's name, in characterize()).
		 */
		boolean syntheticTwin = null == to.synthetic;

		switch ( how )
		{
		case COMMUTATION:
			if ( ! from.commutator.equals(to.qname) )
				return false; // this is not the operator you're looking for
			if ( null != to.commutator && ! to.commutator.equals(from.qname) )
				return false; // you're not the one it's looking for
			break;
		case NEGATION:
			if ( ! from.negator.equals(to.qname) )
				return false; // move along
			if ( null != to.negator && ! to.negator.equals(from.qname) )
				return false; // move along
			if ( syntheticTwin )
				return false;
			break;
		}

		if ( syntheticTwin )
		{
			/*
			 * We will apply commutation to 'from' (the negation case
			 * would have been rejected above). Either 'from' is nonsynthetic
			 * and its function name will be copied in characterize(), or it is
			 * synthetic and must have a name or we reject it here. If not
			 * rejected, copy the name.
			 */
			if ( from.isSynthetic )
			{
				if ( null == from.synthetic )
					return false;
				to.synthetic = from.synthetic;
			}
		}

		if ( null == to.paths )
			to.paths = new ArrayList<>();

		if ( ! from.isSynthetic )
			to.paths.add(new OperatorPath(from, from, null, EnumSet.of(how)));
		else
		{
			for ( OperatorPath path : from.paths )
			{
				to.paths.add(new OperatorPath(
					path.base, from, path.fromBase, EnumSet.of(how)));
			}
		}

		return true;
	}

	/**
	 * Why has {@code Set} or at least {@code EnumSet} not got this?
	 */
	static <E extends Enum<E>> EnumSet<E> symmetricDifference(
		EnumSet<E> a, EnumSet<E> b)
	{
		EnumSet<E> result = a.clone();
		result.removeAll(b);
		b = b.clone();
		b.removeAll(a);
		result.addAll(b);
		return result;
	}

	List<OperatorImpl> m_nonSynthetic = new ArrayList<>();
	List<OperatorImpl> m_synthetic = new ArrayList<>();

	static class OperatorPath
	{
		OperatorImpl base;
		OperatorImpl proximate;
		EnumSet<Transform> fromBase;
		EnumSet<Transform> fromProximate;

		enum Transform { NEGATION, COMMUTATION }

		OperatorPath(
			OperatorImpl base, OperatorImpl proximate,
			EnumSet<Transform> baseToProximate,
			EnumSet<Transform> proximateToNew)
		{
			this.base = base;
			this.proximate = proximate;
			fromProximate = proximateToNew.clone();

			if ( base == proximate )
				fromBase = fromProximate;
			else
				fromBase = symmetricDifference(baseToProximate, proximateToNew);
		}

		public String toString()
		{
			return
				base.commentDropForm() + " " + fromBase +
				(base == proximate
					? ""
					: " (... " + proximate.commentDropForm() +
					  " " + fromProximate);
		}
	}

	class OperatorImpl
	extends Repeatable
	implements Operator, Snippet, Commentable
	{
		OperatorImpl(Element e, AnnotationMirror am)
		{
			super(e, am);
		}

		public String[]                 name() { return qstrings(qname); }
		public String                   left() { return operand(0); }
		public String                  right() { return operand(1);   }
		public String[]             function() { return qstrings(funcName); }
		public String[]            synthetic() { return qstrings(synthetic); }
		public String[]           commutator() { return qstrings(commutator); }
		public String[]              negator() { return qstrings(negator); }
		public boolean                hashes() { return _hashes; }
		public boolean                merges() { return _merges; }
		public String[]             restrict() { return qstrings(restrict); }
		public String[]                 join() { return qstrings(join); }
		public String[]             provides() { return _provides; }
		public String[]             requires() { return _requires; }

		public String[] _provides;
		public String[] _requires;
		public boolean  _hashes;
		public boolean  _merges;

		Identifier.Qualified<Identifier.Operator> qname;
		DBType[] operands = { null, null };
		FunctionImpl func;
		Identifier.Qualified<Identifier.Simple> funcName;
		Identifier.Qualified<Identifier.Operator> commutator;
		Identifier.Qualified<Identifier.Operator> negator;
		Identifier.Qualified<Identifier.Simple> restrict;
		Identifier.Qualified<Identifier.Simple> join;
		Identifier.Qualified<Identifier.Simple> synthetic;
		boolean isSynthetic;
		boolean selfCommutator;
		boolean twinCommutator;
		List<OperatorPath> paths;

		private String operand(int i)
		{
			return null == operands[i] ? null : operands[i].toString();
		}

		public void setName( Object o, boolean explicit, Element e)
		{
			qname = operatorNameFrom(avToArray( o, String.class));
		}

		public void setLeft( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				operands[0] = DBType.fromSQLTypeAnnotation((String)o);
		}

		public void setRight( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				operands[1] = DBType.fromSQLTypeAnnotation((String)o);
		}

		public void setFunction( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				funcName = qnameFrom(avToArray( o, String.class));
		}

		public void setSynthetic( Object o, boolean explicit, Element e)
		{
			if ( ! explicit )
				return;

			/*
			 * Use isSynthetic to indicate that synthetic= has been used at all.
			 * Set synthetic to the supplied qname only if it is a qname, and
			 * not the distinguished value TWIN.
			 *
			 * Most of the processing below only needs to look at isSynthetic.
			 * The TWIN case, recognized by isSynthetic && null == synthetic,
			 * will be handled late in the game by copying the base function's
			 * qname.
			 */

			isSynthetic = true;
			String[] ss = avToArray( o, String.class);
			if ( 1 != ss.length  ||  ! TWIN.equals(ss[0]) )
				synthetic = qnameFrom(ss);
		}

		public void setCommutator( Object o, boolean explicit, Element e)
		{
			if ( ! explicit )
				return;

			String[] ss = avToArray( o, String.class);
			if ( 1 == ss.length )
			{
				if ( SELF.equals(ss[0]) )
				{
					selfCommutator = true;
					return;
				}
				if ( TWIN.equals(ss[0]) )
				{
					twinCommutator = true;
					return;
				}
			}
			commutator = operatorNameFrom(ss);
		}

		public void setNegator( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				negator = operatorNameFrom(avToArray( o, String.class));
		}

		public void setRestrict(
			Object o, boolean explicit, Element e)
		{
			if ( explicit )
				restrict = qnameFrom(avToArray( o, String.class));
		}

		public void setJoin(
			Object o, boolean explicit, Element e)
		{
			if ( explicit )
				join = qnameFrom(avToArray( o, String.class));
		}

		public Set<Snippet> characterize()
		{
			boolean ok = true;
			Snippet syntheticFunction = null;

			if ( ElementKind.METHOD.equals(m_targetElement.getKind()) )
			{
				func = getSnippet(m_targetElement, FunctionImpl.class,
					() -> (FunctionImpl)null);
			}

			if ( isSynthetic )
			{
				if ( null != funcName )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator may not specify both function= and " +
						"synthetic="
					);
					ok = false;
				}
				funcName = synthetic; // can be null (the TWIN case)
			}

			if ( null == func  &&  null == funcName  &&  ! isSynthetic )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Operator not annotating a method must specify function="
				);
				ok = false;
			}

			if ( null == func )
			{
				if ( null == operands[0]  &&  null == operands[1] )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator not annotating a method must specify " +
						"left= or right= or both"
					);
					ok = false;
				}
			}
			else
			{
				Identifier.Qualified<Identifier.Simple> fn =
					qnameFrom(func.name(), func.schema());

				if ( null == funcName )
					funcName = fn;
				else if ( ! funcName.equals(fn)  &&  ! isSynthetic )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator annotates a method but function= gives a " +
						"different name"
					);
					ok = false;
				}

				long explicit =
					Arrays.stream(operands).filter(Objects::nonNull).count();

				if ( 0 != explicit  &&  isSynthetic )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator with synthetic= must not specify " +
						"operand types"
					);
					ok = false;
				}

				if ( 0 == explicit )
				{
					int nparams = func.parameterTypes.length;
					if ( 1 > nparams  ||  nparams > 2 )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"method annotated with @Operator must take one " +
							"or two parameters"
						);
						ok = false;
					}
					if ( 1 == nparams )
						operands[1] = func.parameterTypes[0];
					else
						System.arraycopy(func.parameterTypes,0, operands,0,2);
				}
				else if ( explicit != func.parameterTypes.length )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator annotates a method but specifies " +
						"a different number of operands"
					);
					ok = false;
				}
				else if ( 2 == explicit
						&& ! Arrays.equals(operands, func.parameterTypes)
					|| 1 == explicit
						&& ! Arrays.asList(operands)
							.contains(func.parameterTypes[0]) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator annotates a method but specifies " +
						"different operand types"
					);
					ok = false;
				}
			}

			/*
			 * At this point, ok ==> there is a non-null funcName ... UNLESS
			 * isSynthetic is true, synthetic=TWIN was given, and we are not
			 * annotating a method (that last condition is currently not
			 * supported, so we could in fact rely on having a funcName here,
			 * but that condition may be worth supporting in the future, so
			 * better to keep the exception in mind).
			 */

			if ( ! ok )
				return Set.of();

			long arity =
				Arrays.stream(operands).filter(Objects::nonNull).count();

			if ( 1 == arity  &&  null == operands[1] )
			{
				msg(Kind.WARNING, m_targetElement, m_origin,
					"Right unary (postfix) operators are deprecated and will " +
					"be removed in PostgreSQL version 14."
				);
			}

			if ( null != commutator )
			{
				if ( 2 != arity )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"unary @Operator cannot have a commutator"
					);
					ok = false;
				}
				else if ( selfCommutator && ! operands[0].equals(operands[1]) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator with different left and right operand " +
						"types cannot have commutator=SELF"
					);
					ok = false;
				}
				else if ( twinCommutator && operands[0].equals(operands[1]) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator with matching left and right operand " +
						"types cannot have commutator=TWIN"
					);
					ok = false;
				}
			}

			boolean knownNotBoolean =
				null != func && ! DT_BOOLEAN.equals(func.returnType);

			if ( null != negator )
			{
				if ( knownNotBoolean )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"negator= only belongs on a boolean @Operator"
					);
					ok = false;
				}
				else if ( negator.equals(qname) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"@Operator can never be its own negator"
					);
					ok = false;
				}
			}

			boolean knownNotBinaryBoolean = 2 != arity || knownNotBoolean;
			boolean knownVolatile =
				null != func && Function.Effects.VOLATILE == func.effects();
			boolean operandTypesDiffer =
				2 == arity && ! operands[0].equals(operands[1]);
			boolean selfCommutates =
				null != commutator && commutator.equals(qname);

			ok &= Stream.of(
				_hashes ? "hashes"  : null,
				_merges ? "merges" : null)
				.filter(Objects::nonNull)
				.map(s ->
				{
					boolean inner_ok = true;
					if ( knownNotBinaryBoolean )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"%s= only belongs on a boolean " +
							"binary @Operator", s
						);
						inner_ok = false;
					}
					if ( null == commutator )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"%s= requires that the @Operator " +
							"have a commutator", s
						);
						inner_ok = false;
					}
					else if ( ! (operandTypesDiffer || selfCommutates) )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"%s= requires the @Operator to be its own" +
							"commutator as its operand types are the same", s
						);
						inner_ok = false;
					}
					if ( knownVolatile )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"%s= requires an underlying function " +
							"declared IMMUTABLE or STABLE", s
						);
						inner_ok = false;
					}
					return inner_ok;
				})
				.allMatch(t -> t);

			if ( null != restrict && knownNotBinaryBoolean )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"restrict= only belongs on a boolean binary @Operator"
				);
				ok = false;
			}

			if ( null != join && knownNotBinaryBoolean )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"join= only belongs on a boolean binary @Operator"
				);
				ok = false;
			}

			if ( ! ok )
				return Set.of();

			if ( isSynthetic )
			{
				if ( null == func )
				{
					/*
					 * It could be possible to relax this requirement if there
					 * is a need, but this way is easier.
					 */
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Synthetic operator annotation must appear " +
						"on the method to be used as the base");
					ok = false;
				}

				if ( paths.isEmpty() )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Synthetic operator %s has no derivation path " +
						"involving negation or commutation from another " +
						"operator", qnameUnwrapped());
					/*
					 * If no paths at all, return empty from here; no point in
					 * further checks.
					 */
					return Set.of();
				}

				/*
				 * Check for conditions where deriving by commutation wouldn't
				 * make sense. Any of these three conditions will trigger the
				 * test of available paths. The conditions are rechecked but the
				 * third one is changed, so either of the first two will always
				 * preclude commutation, but ! operandTypesDiffer only does if
				 * the synthetic function's name will be the same as the base's.
				 * (If the types were different, PostgreSQL overloading would
				 * allow the functions to share a name, but that's not possible
				 * if the types are the same.) In those cases, any commutation
				 * paths are filtered out; if no path remains, that's an error.
				 */
				if ( 2 != arity || selfCommutator || ! operandTypesDiffer )
				{
					List<OperatorPath> filtered =
						paths.stream()
						.filter(
							p -> ! p.fromBase.contains(
								OperatorPath.Transform.COMMUTATION))
						.collect(toList());
					if ( 2 != arity || selfCommutator
						|| null == synthetic ||
						synthetic.equals(qnameFrom(func.name(), func.schema())))
					{
						if ( filtered.isEmpty() )
						{
							msg(Kind.ERROR, m_targetElement, m_origin,
								"Synthetic operator %s cannot be another " +
								"operator's commutator, but found only " +
								"path(s) involving commutation: %s",
								qnameUnwrapped(), paths.toString());
							ok = false;
						}
						else
							paths = filtered;
					}
				}

				ok &= paths.stream().collect(
					groupingBy(p -> p.base,
						mapping(p -> p.fromBase, toSet())))
					.entrySet().stream()
					.filter(e -> 1 < e.getValue().size())
					.map(e ->
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"Synthetic operator %s found paths with " +
							"different transforms %s from same base %s",
							qnameUnwrapped(),
							e.getValue(), e.getKey().qnameUnwrapped());
						return false;
					})
					.allMatch(t -> t);

				ok &= paths.stream().collect(
					groupingBy(p -> p.proximate,
						mapping(p -> p.fromProximate, toSet())))
					.entrySet().stream()
					.filter(e -> 1 < e.getValue().size())
					.map(e ->
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"Synthetic operator %s found paths with " +
							"different transforms %s from %s",
							qnameUnwrapped(),
							e.getValue(), e.getKey().qnameUnwrapped());
						return false;
					})
					.allMatch(t -> t);

				Set<Identifier.Qualified<Identifier.Operator>>
					commutatorCandidates =
						paths.stream()
						.filter(
							p -> p.fromProximate.contains(
								OperatorPath.Transform.COMMUTATION))
						.map(p -> p.proximate.qname)
						.collect(toSet());
				if ( null == commutator  &&  0 < commutatorCandidates.size() )
				{
					if ( 1 == commutatorCandidates.size() )
						commutator = commutatorCandidates.iterator().next();
					else
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"Synthetic operator %s has muliple commutator " +
							"candidates %s",
							qnameUnwrapped(), commutatorCandidates);
						ok = false;
					}
				}

				Set<Identifier.Qualified<Identifier.Operator>>
					negatorCandidates =
						paths.stream()
						.filter(
							p -> p.fromProximate.contains(
								OperatorPath.Transform.NEGATION))
						.map(p -> p.proximate.qname)
						.collect(toSet());
				if ( null == negator  &&  0 < negatorCandidates.size() )
				{
					if ( 1 == negatorCandidates.size() )
						negator = negatorCandidates.iterator().next();
					else
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"Synthetic operator %s has muliple negator " +
							"candidates %s",
							qnameUnwrapped(), negatorCandidates);
						ok = false;
					}
				}

				/*
				 * Filter paths to only those based on an operator that is built
				 * over this method. (That's currently guaranteed by the way
				 * operatorPreSynthesize generates paths, but may as well check
				 * here to ensure sanity during future maintenance.)
				 *
				 * For synthetic=TWIN (represented here by null==synthetic),
				 * also filter out paths that don't involve commutation (without
				 * it, the synthetic function would collide with the base one).
				 */

				boolean nonCommutedOK = null != synthetic;
				
				paths = paths.stream()
					.filter(
						p -> p.base.func == func
						&& (nonCommutedOK || p.fromBase.contains(
							OperatorPath.Transform.COMMUTATION))
					).collect(toList());

				if ( 0 == paths.size() )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Synthetic operator %s has no derivation path " +
						"from an operator that is based on this method%s",
						qnameUnwrapped(),
						nonCommutedOK ? "" : " and involves commutation");
					ok = false;
				}

				if ( ! ok )
					return Set.of();

				/*
				 * Select a base. Could there be more than one? As the checks
				 * for transform inconsistencies above found none, we will
				 * assume any should be ok, and choose one semi-arbitrarily.
				 */

				OperatorPath selected =
					paths.stream()
					.sorted(
						Comparator.<OperatorPath>comparingInt(
							p -> p.fromBase.size())
						.thenComparingInt(
							p -> p.fromBase.stream()
							.mapToInt(Enum::ordinal)
							.max().getAsInt())
						.thenComparing(p -> p.base.qnameUnwrapped()))
					.findFirst().get();

				/*
				 * At last, the possibly null funcName (synthetic=TWIN case)
				 * can be fixed up.
				 */
				if ( null == synthetic )
				{
					FunctionImpl f = selected.base.func;
					funcName = synthetic = qnameFrom(f.name(), f.schema());
				}

				replaceCommentIfDerived("Operator " + qnameUnwrapped()
						+ " automatically derived by "
						+ selected.fromBase + " from "
						+ selected.base.qnameUnwrapped());

				boolean commute = selected.fromBase
					.contains(OperatorPath.Transform.COMMUTATION);
				boolean negate = selected.fromBase
					.contains(OperatorPath.Transform.NEGATION);

				if ( operandTypesDiffer  &&  commute )
				{
					DBType t = operands[0];
					operands[0] = operands[1];
					operands[1] = t;
				}

				syntheticFunction =
					func.transformed(synthetic, commute, negate);
			}

			recordImplicitTags();
			recordExplicitTags(_provides, _requires);
			return null == syntheticFunction
				? Set.of(this) : Set.of(syntheticFunction, this);
		}

		void recordImplicitTags()
		{
			Set<DependTag> provides = provideTags();
			Set<DependTag> requires = requireTags();

			provides.add(new DependTag.Operator(qname, operands));

			/*
			 * Commutator and negator often involve cycles. PostgreSQL already
			 * has its own means of breaking them, so it is not necessary here
			 * even to declare dependencies based on them.
			 *
			 * There is also, for now, no point in declaring dependencies on
			 * selectivity estimators; they can't be written in Java, so they
			 * won't be products of this compilation.
			 *
			 * So, just require the operand types and the function.
			 */

			Arrays.stream(operands)
				.filter(Objects::nonNull)
				.map(DBType::dependTag)
				.filter(Objects::nonNull)
				.forEach(requires::add);

			if ( null != func &&  null == synthetic )
			{
				func.provideTags().stream()
					.filter(DependTag.Function.class::isInstance)
					.forEach(requires::add);
			}
			else
			{
				requires.add(new DependTag.Function(funcName,
					Arrays.stream(operands)
					.filter(Objects::nonNull)
					.toArray(DBType[]::new)));
			}
		}

		/**
		 * Just to keep things interesting, a schema-qualified operator name is
		 * wrapped in OPERATOR(...) pretty much everywhere, except as the guest
		 * of honor in a CREATE OPERATOR or DROP OPERATOR, where the unwrapped
		 * form is needed.
		 */
		private String qnameUnwrapped()
		{
			String local = qname.local().toString();
			Identifier.Simple qualifier = qname.qualifier();
			return null == qualifier ? local : qualifier + "." + local;
		}

		/**
		 * An operator is identified this way in a COMMENT or DROP.
		 */
		private String commentDropForm()
		{
			return qnameUnwrapped() + " (" +
				(null == operands[0] ? "NONE" : operands[0]) + ", " +
				(null == operands[1] ? "NONE" : operands[1]) + ")";
		}

		public String[] deployStrings()
		{
			List<String> al = new ArrayList<>();

			StringBuilder sb = new StringBuilder();

			sb.append("CREATE OPERATOR ").append(qnameUnwrapped());
			sb.append(" (\n\tPROCEDURE = ").append(funcName);

			if ( null != operands[0] )
				sb.append(",\n\tLEFTARG = ").append(operands[0]);

			if ( null != operands[1] )
				sb.append(",\n\tRIGHTARG = ").append(operands[1]);

			if ( null != commutator )
				sb.append(",\n\tCOMMUTATOR = ").append(commutator);

			if ( null != negator )
				sb.append(",\n\tNEGATOR = ").append(negator);

			if ( null != restrict )
				sb.append(",\n\tRESTRICT = ").append(restrict);

			if ( null != join )
				sb.append(",\n\tJOIN = ").append(join);

			if ( _hashes )
				sb.append(",\n\tHASHES");

			if ( _merges )
				sb.append(",\n\tMERGES");

			sb.append(')');

			al.add(sb.toString());
			if ( null != comment() )
				al.add(
					"COMMENT ON OPERATOR " + commentDropForm() + " IS " +
					DDRWriter.eQuote(comment()));

			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			return new String[]
			{
				"DROP OPERATOR " + commentDropForm()
			};
		}
	}

	class AggregateImpl
	extends Repeatable
	implements Aggregate, Snippet, Commentable
	{
		AggregateImpl(Element e, AnnotationMirror am)
		{
			super(e, am);
		}

		public String[]                name() { return qstrings(qname); }
		public String[]           arguments() { return argsOut(aggregateArgs); }
		public String[]     directArguments() { return argsOut(directArgs); }
		public boolean         hypothetical() { return _hypothetical; }
		public boolean[]           variadic() { return _variadic; }
		public Plan[]                  plan() { return new Plan[]{_plan}; }
		public Plan[]            movingPlan() { return _movingPlan; }
		public Function.Parallel   parallel() { return _parallel; }
		public String[]        sortOperator() { return qstrings(sortop); }
		public String[]            provides() { return _provides; }
		public String[]            requires() { return _requires; }

		public boolean           _hypothetical;
		public boolean[]             _variadic = {false, false};
		public Plan                      _plan;
		public Plan[]              _movingPlan;
		public Function.Parallel     _parallel;
		public String[]              _provides;
		public String[]              _requires;

		FunctionImpl func;
		Identifier.Qualified<Identifier.Simple> qname;
		List<Map.Entry<Identifier.Simple,DBType>> aggregateArgs;
		List<Map.Entry<Identifier.Simple,DBType>> directArgs;
		Identifier.Qualified<Identifier.Operator> sortop;
		static final int DIRECT_ARGS = 0; // index into _variadic[]
		static final int AGG_ARGS = 1;    // likewise
		boolean directVariadicExplicit;

		private List<Map.Entry<Identifier.Simple,DBType>>
			argsIn(String[] names)
		{
			return Arrays.stream(names)
				.map(DBType::fromNameAndType)
				.collect(toList());
		}

		private String[]
			argsOut(List<Map.Entry<Identifier.Simple,DBType>> names)
		{
			return names.stream()
				.map(e -> e.getKey() + " " + e.getValue())
				.toArray(String[]::new);
		}

		@Override
		public String derivedComment( Element e)
		{
			/*
			 * When this annotation targets a TYPE, just as a
			 * place to hang it, there's no particular reason to believe a
			 * doc comment on the type is a good choice for this aggregate.
			 * When the annotation is on a method, the chances are better.
			 */
			if ( ElementKind.METHOD.equals(e.getKind()) )
				return super.derivedComment(e);
			return null;
		}

		public void setName( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				qname = qnameFrom(avToArray( o, String.class));
		}

		public void setArguments( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				aggregateArgs = argsIn( avToArray( o, String.class));
		}

		public void setDirectArguments( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				directArgs = argsIn( avToArray( o, String.class));
		}

		public void setSortOperator( Object o, boolean explicit, Element e)
		{
			if ( explicit )
				sortop = operatorNameFrom(avToArray( o, String.class));
		}

		public void setVariadic( Object o, boolean explicit, Element e)
		{
			if ( ! explicit )
				return;

			Boolean[] a = avToArray( o, Boolean.class);

			if ( 1 > a.length  ||  a.length > 2 )
				throw new IllegalArgumentException(
					"supply only boolean or {boolean,boolean} for variadic");

			if ( ! Arrays.asList(a).contains(true) )
				throw new IllegalArgumentException(
					"supply variadic= only if aggregated arguments, direct " +
					"arguments, or both, are variadic");

			_variadic[AGG_ARGS] = a[a.length - 1];
			if ( 2 == a.length )
			{
				directVariadicExplicit = true;
				_variadic[DIRECT_ARGS] = a[0];
			}
		}

		public void setPlan( Object o, boolean explicit, Element e)
		{
			_plan = new Plan(); // always a plan, even if members uninitialized

			if ( explicit )
				_plan = planFrom( _plan, o, e, "plan");
		}

		public void setMovingPlan( Object o, boolean explicit, Element e)
		{
			if ( ! explicit )
				return;

			_movingPlan = new Plan[1];
			_movingPlan [ 0 ] = planFrom( new Moving(), o, e, "movingPlan");
		}

		Plan planFrom( Plan p, Object o, Element e, String which)
		{
			AnnotationMirror[] ams = avToArray( o, AnnotationMirror.class);

			if ( 1 != ams.length )
				throw new IllegalArgumentException(
					which + " must be given exactly one @Plan");

			populateAnnotationImpl( p, e, ams[0]);
			return p;
		}

		public Set<Snippet> characterize()
		{
			boolean ok = true;
			boolean orderedSet = null != directArgs;
			boolean moving = null != _movingPlan;
			boolean checkAccumulatorSig = false;
			boolean checkFinisherSig = false;
			boolean unary = false;

			if ( ElementKind.METHOD.equals(m_targetElement.getKind()) )
			{
				func = getSnippet(m_targetElement, FunctionImpl.class,
					() -> (FunctionImpl)null);
				if ( null == func )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"A method annotated with @Aggregate must " +
						"also have @Function"
					);
					ok = false;
				}
			}

			if ( null != func )
			{
				Identifier.Qualified<Identifier.Simple> funcName =
					qnameFrom(func.name(), func.schema());
				boolean inferAccumulator =
					null == _plan.accumulate  ||  null == aggregateArgs;
				boolean inferFinisher =
					null == _plan.finish  &&  ! inferAccumulator;
				boolean stateTypeExplicit = false;

				if ( null == qname )
				{

					if ( inferFinisher && 1 == aggregateArgs.size()
						&& 1 == func.parameterTypes.length
						&& func.parameterTypes[0] ==
							aggregateArgs.get(0).getValue() )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"Default name %s for this aggregate would " +
							"collide with finish function; use name= to " +
							"specify a name", funcName
						);
						ok = false;
					}
					else
						qname = funcName;
				}

				if ( 1 > func.parameterTypes.length )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Function with no arguments cannot be @Aggregate " +
						"accumulate or finish function"
					);
					ok = false;
				}
				else if ( null == _plan.stateType )
				{
					_plan.stateType = func.parameterTypes[0];
					if (null != _movingPlan
						&& null == _movingPlan[0].stateType)
						_movingPlan[0].stateType = func.parameterTypes[0];
				}
				else
					stateTypeExplicit = true;

				if ( inferAccumulator  ||  inferFinisher )
				{
					if ( ok )
					{
						if ( inferAccumulator )
						{
							if ( null == aggregateArgs )
							{
								aggregateArgs =
									func.parameterInfo()
									.skip(1) // skip the state argument
									.map(pi ->
										(Map.Entry<Identifier.Simple, DBType>)
										new AbstractMap.SimpleImmutableEntry<>(
											Identifier.Simple.fromJava(
												pi.name()
											),
											pi.dt
										)
									)
									.collect(toList());
							}
							else
								checkAccumulatorSig = true;
							_plan.accumulate = funcName;
							if ( null != _movingPlan
								&& null == _movingPlan[0].accumulate )
								_movingPlan[0].accumulate = funcName;
						}
						else // inferFinisher
						{
							_plan.finish = funcName;
							if ( null != _movingPlan
								&& null == _movingPlan[0].finish )
								_movingPlan[0].finish = funcName;
						}
					}

					if ( stateTypeExplicit
						&& ! _plan.stateType.equals(func.parameterTypes[0]) )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"First function argument does not match " +
							"stateType specified with @Aggregate"
						);
						ok = false;
					}
				}
				else if ( funcName.equals(_plan.accumulate) )
					checkAccumulatorSig = true;
				else if ( funcName.equals(_plan.finish) )
					checkFinisherSig = true;
				else
				{
					msg(Kind.WARNING, m_targetElement, m_origin,
						"@Aggregate annotation on a method not recognized " +
						"as either the accumulate or the finish function " +
						"for the aggregate");
				}

				// If the method is the accumulator and is RETURNS_NULL, ensure
				// there is either an initialState or a first aggregate arg that
				// matches the stateType.
				if ( ok && ( inferAccumulator || checkAccumulatorSig ) )
				{
					if ( Function.OnNullInput.RETURNS_NULL == func.onNullInput()
						&& ( 0 == aggregateArgs.size()
							|| ! _plan.stateType.equals(
									aggregateArgs.get(0).getValue()) )
						&& null == _plan._initialState )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"@Aggregate without initialState= must have " +
							"either a first argument matching the stateType " +
							"or an accumulate method with onNullInput=CALLED.");
						ok = false;
					}
				}
			}

			if ( null == qname )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate missing name=");
				ok = false;
			}

			if ( null == aggregateArgs )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate missing arguments=");
				ok = false;
			}
			else
				unary = 1 == aggregateArgs.size();

			if ( null == _plan.stateType )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate missing stateType=");
				ok = false;
			}

			if ( null == _plan.accumulate )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate plan missing accumulate=");
				ok = false;
			}

			// Could check argument count against FUNC_MAX_ARGS, but that would
			// hardcode an assumed value for PostgreSQL's FUNC_MAX_ARGS.

			// Check that, if a stateType is polymorphic, there are compatible
			// polymorphic arg types? Not today.

			// If a plan has no initialState, then either the accumulate
			// function must NOT be RETURNS NULL ON NULL INPUT, or the first
			// aggregated argument type must be the same as the state type.
			// The type check is easy, but the returnsNull check on the
			// accumulate function would require looking up the function (and
			// still we wouldn't know, if it's not seen in this compilation).
			// For another day.

			// Allow hypothetical only for ordered-set aggregate.
			if ( _hypothetical && ! orderedSet )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"hypothetical=true is only allowed for an ordered-set " +
					"aggregate (one with directArguments specified, " +
					"even if only {})");
				ok = false;
			}

			// Allow two-element variadic= only for ordered-set aggregate.
			if ( directVariadicExplicit && ! orderedSet )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"Two values for variadic= are only allowed for an " +
					"ordered-set aggregate (one with directArguments " +
					"specified, even if only {})");
				ok = false;
			}

			// Require a movingPlan to have a remove function.
			if ( moving && null == _movingPlan[0].remove )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"a movingPlan must include a remove function");
				ok = false;
			}

			// Checks if the aggregated argument list is declared variadic.
			// The last element must be an array type or "any"; an ordered-set
			// aggregate allows only one argument and it must be "any".
			if ( _variadic[AGG_ARGS] )
			{
				if ( 1 > aggregateArgs.size() )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"To declare the aggregated argument list variadic, " +
						"there must be at least one argument.");
					ok = false;
				}
				else
				{
					DBType t =
						aggregateArgs.get(aggregateArgs.size() - 1).getValue();
					boolean isAny = // allow omission of pg_catalog namespace
						DT_ANY.equals(t)  ||  "\"any\"".equals(t.toString());
					if ( orderedSet && (! isAny || 1 != aggregateArgs.size()) )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"If variadic, an ordered-set aggregate's " +
							"aggregated argument list must be only one " +
							"argument and of type \"any\".");
						ok = false;
					}
					else if ( ! isAny  &&  ! t.isArray() )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"If variadic, the last aggregated argument must " +
							"be an array type (or \"any\").");
						ok = false;
					}
				}
			}

			// Checks specific to ordered-set aggregates.
			if ( orderedSet )
			{
				if ( 0 == aggregateArgs.size() )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"An ordered-set aggregate needs at least one " +
						"aggregated argument");
					ok = false;
				}

				// Checks specific to hypothetical-set aggregates.
				// The aggregated argument types must match the trailing direct
				// arguments, and the two variadic declarations must match.
				if ( _hypothetical )
				{
					if ( _variadic[DIRECT_ARGS] != _variadic[AGG_ARGS] )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"For a hypothetical-set aggregate, neither or " +
							"both the direct and aggregated argument lists " +
							"must be declared variadic.");
						ok = false;
					}
					if ( directArgs.size() < aggregateArgs.size()
						||
						! directArgs.subList(
							directArgs.size() - aggregateArgs.size(),
							directArgs.size())
							.equals(aggregateArgs) )
					{
						msg(Kind.ERROR, m_targetElement, m_origin,
							"The last direct arguments of a hypothetical-set " +
							"aggregate must match the types of the " +
							"aggregated arguments");
						ok = false;
					}
				}
			}

			// It is allowed to omit a finisher function, but some things
			// make no sense without one.
			if ( orderedSet && null == _plan.finish && 0 < directArgs.size() )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"Direct arguments serve no purpose without a finisher");
				ok = false;
			}

			if ( null == _plan.finish && _plan._polymorphic )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"The polymorphic flag is meaningless with no finisher");
				ok = false;
			}

			// The same finisher checks for a movingPlan, if present.
			if ( moving )
			{
				if ( orderedSet
					&& null == _movingPlan[0].finish
					&& directArgs.size() > 0 )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Direct arguments serve no purpose without a finisher");
					ok = false;
				}

				if ( null == _movingPlan[0].finish
					&& _movingPlan[0]._polymorphic )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"The polymorphic flag is meaningless with no finisher");
					ok = false;
				}
			}

			// Checks involving sortOperator
			if ( null != sortop )
			{
				if ( orderedSet )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"The sortOperator optimization is not available for " +
						"an ordered-set aggregate (one with directArguments)");
					ok = false;
				}

				if ( ! unary || _variadic[AGG_ARGS] )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"The sortOperator optimization is only available for " +
						"a one-argument (and non-variadic) aggregate");
					ok = false;
				}
			}

			// Checks involving serialize / deserialize
			if ( null != _plan.serialize  ||  null != _plan.deserialize )
			{
				if ( null == _plan.combine )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"An aggregate plan without combine= may not have " +
						"serialize= or deserialize=");
					ok = false;
				}

				if ( null == _plan.serialize  ||  null == _plan.deserialize )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"An aggregate plan must have both " +
						"serialize= and deserialize= or neither");
					ok = false;
				}

				if ( ! DT_INTERNAL.equals(_plan.stateType) )
				{
					msg(Kind.ERROR, m_targetElement, m_origin,
						"Only an aggregate plan with stateType " +
						"pg_catalog.internal may have serialize=/deserialize=");
					ok = false;
				}
			}

			if ( ! ok )
				return Set.of();

			Set<DependTag> requires = requireTags();

			DBType[] accumulatorSig =
				Stream.of(
					Stream.of(_plan.stateType),
					aggregateArgs.stream().map(Map.Entry::getValue))
				.flatMap(identity()).toArray(DBType[]::new);

			DBType[] combinerSig = { _plan.stateType, _plan.stateType };

			DBType[] finisherSig =
				Stream.of(
					Stream.of(_plan.stateType),
					orderedSet
						? directArgs.stream().map(Map.Entry::getValue)
						: Stream.of(),
					_plan._polymorphic
						? aggregateArgs.stream().map(Map.Entry::getValue)
						: Stream.of()
				)
				.flatMap(identity())
				.toArray(DBType[]::new);

			if ( checkAccumulatorSig
				&& ! Arrays.equals(accumulatorSig, func.parameterTypes) )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate annotation on a method that matches the name " +
					"but not argument types expected for the aggregate's " +
					"accumulate function");
				ok = false;
			}

			if ( checkFinisherSig
				&& ! Arrays.equals(finisherSig, func.parameterTypes) )
			{
				msg(Kind.ERROR, m_targetElement, m_origin,
					"@Aggregate annotation on a method that matches the name " +
					"but not argument types expected for the aggregate's " +
					"finish function");
				ok = false;
			}

			requires.add(
				new DependTag.Function(_plan.accumulate, accumulatorSig));

			if ( null != _plan.combine )
			{
				DBType[]   serialSig = { DT_INTERNAL };
				DBType[] deserialSig = { DT_BYTEA, DT_INTERNAL };

				requires.add(
					new DependTag.Function(_plan.combine, combinerSig));

				if ( null != _plan.serialize )
				{
					requires.add(
						new DependTag.Function(_plan.serialize, serialSig));
					requires.add(
						new DependTag.Function(_plan.deserialize, deserialSig));
				}
			}

			if ( null != _plan.finish )
				requires.add(
					new DependTag.Function(_plan.finish, finisherSig));

			if ( moving )
			{
				accumulatorSig[0] = _movingPlan[0].stateType;
				Arrays.fill(combinerSig, _movingPlan[0].stateType);
				finisherSig[0] = _movingPlan[0].stateType;

				requires.add(new DependTag.Function(
					_movingPlan[0].accumulate, accumulatorSig));

				requires.add(new DependTag.Function(
					_movingPlan[0].remove, accumulatorSig));

				if ( null != _movingPlan[0].combine )
					requires.add(new DependTag.Function(
						_movingPlan[0].combine, combinerSig));

				if ( null != _movingPlan[0].finish )
					requires.add(new DependTag.Function(
						_movingPlan[0].finish, finisherSig));
			}

			if ( null != sortop )
			{
				DBType arg = aggregateArgs.get(0).getValue();
				DBType[] opSig = { arg, arg };
				requires.add(new DependTag.Operator(sortop, opSig));
			}

			/*
			 * That establishes dependency on the various support functions,
			 * which should, transitively, depend on all of the types. But it is
			 * possible we do not have a whole-program view (perhaps some
			 * support functions are implemented in other languages, and there
			 * are @SQLActions setting them up?). Therefore also, redundantly as
			 * it may be, declare dependency on the types.
			 */

			Stream.of(
				aggregateArgs.stream().map(Map.Entry::getValue),
				orderedSet
					? directArgs.stream().map(Map.Entry::getValue)
					: Stream.<DBType>of(),
				Stream.of(_plan.stateType),
				moving
					? Stream.of(_movingPlan[0].stateType)
					: Stream.<DBType>of()
				)
				.flatMap(identity())
				.map(DBType::dependTag)
				.filter(Objects::nonNull)
				.forEach(requires::add);

			recordExplicitTags(_provides, _requires);
			return Set.of(this);
		}

		public String[] deployStrings()
		{
			List<String> al = new ArrayList<>();

			StringBuilder sb = new StringBuilder("CREATE AGGREGATE ");
			appendNameAndArguments(sb);
			sb.append(" (");

			String[] planStrings = _plan.deployStrings();
			int n = planStrings.length;
			for ( String s : planStrings )
			{
				sb.append("\n\t").append(s);
				if ( 0 < -- n )
					sb.append(',');
			}

			if ( null != _movingPlan )
			{
				planStrings = _movingPlan[0].deployStrings();
				for ( String s : planStrings )
					sb.append(",\n\tM").append(s);
			}

			if ( null != sortop )
				sb.append(",\n\tSORTOP = ").append(sortop);

			if ( Function.Parallel.UNSAFE != _parallel )
				sb.append(",\n\tPARALLEL = ").append(_parallel);

			if ( _hypothetical )
				sb.append(",\n\tHYPOTHETICAL");

			sb.append(')');

			al.add(sb.toString());

			if ( null != comment() )
			{
				sb = new StringBuilder("COMMENT ON AGGREGATE ");
				appendNameAndArguments(sb);
				sb.append(" IS ").append(DDRWriter.eQuote(comment()));
				al.add(sb.toString());
			}

			return al.toArray( new String [ al.size() ]);
		}

		public String[] undeployStrings()
		{
			StringBuilder sb = new StringBuilder("DROP AGGREGATE ");
			appendNameAndArguments(sb);
			return new String[] { sb.toString() };
		}

		private void appendNameAndArguments(StringBuilder sb)
		{
			ListIterator<Map.Entry<Identifier.Simple,DBType>> iter;
			Map.Entry<Identifier.Simple,DBType> entry;

			sb.append(qname).append('(');
			if ( null != directArgs )
			{
				iter = directArgs.listIterator();
				while ( iter.hasNext() )
				{
					entry = iter.next();
					sb.append("\n\t");
					if ( _variadic[DIRECT_ARGS]  &&  ! iter.hasNext() )
						sb.append("VARIADIC ");
					if ( null != entry.getKey() )
						sb.append(entry.getKey()).append(' ');
					sb.append(entry.getValue());
					if ( iter.hasNext() )
						sb.append(',');
					else
						sb.append("\n\t");
				}
				sb.append("ORDER BY");
			}
			else if ( 0 == aggregateArgs.size() )
				sb.append('*');

			iter = aggregateArgs.listIterator();
			while ( iter.hasNext() )
			{
				entry = iter.next();
				sb.append("\n\t");
				if ( _variadic[AGG_ARGS]  &&  ! iter.hasNext() )
					sb.append("VARIADIC ");
				if ( null != entry.getKey() )
					sb.append(entry.getKey()).append(' ');
				sb.append(entry.getValue());
				if ( iter.hasNext() )
					sb.append(',');
			}
			sb.append(')');
		}

		class Plan extends AbstractAnnotationImpl implements Aggregate.Plan
		{
			public String          stateType() { return stateType.toString(); }
			public int             stateSize() { return _stateSize; }
			public String       initialState() { return _initialState; }
			public String[]       accumulate() { return qstrings(accumulate); }
			public String[]          combine() { return qstrings(combine); }
			public String[]           finish() { return qstrings(finish); }
			public String[]           remove() { return qstrings(remove); }
			public String[]        serialize() { return qstrings(serialize); }
			public String[]      deserialize() { return qstrings(deserialize); }
			public boolean       polymorphic() { return _polymorphic; }
			public FinishEffect finishEffect() { return _finishEffect; }

			public int             _stateSize;
			public String       _initialState;
			public boolean       _polymorphic;
			public FinishEffect _finishEffect;

			DBType stateType;
			Identifier.Qualified<Identifier.Simple> accumulate;
			Identifier.Qualified<Identifier.Simple> combine;
			Identifier.Qualified<Identifier.Simple> finish;
			Identifier.Qualified<Identifier.Simple> remove;
			Identifier.Qualified<Identifier.Simple> serialize;
			Identifier.Qualified<Identifier.Simple> deserialize;

			public void setStateType(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					stateType = DBType.fromSQLTypeAnnotation((String)o);
			}

			public void setStateSize(Object o, boolean explicit, Element e)
			{
				_stateSize = (Integer)o;
				if ( explicit  &&  0 >= _stateSize )
					throw new IllegalArgumentException(
						"An explicit stateSize must be positive");
			}

			public void setInitialState(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					_initialState = (String)o;
			}

			public void setAccumulate(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					accumulate = qnameFrom(avToArray( o, String.class));
			}

			public void setCombine(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					combine = qnameFrom(avToArray( o, String.class));
			}

			public void setFinish(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					finish = qnameFrom(avToArray( o, String.class));
			}

			public void setRemove(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					throw new IllegalArgumentException(
						"Only a movingPlan may have a remove function");
			}

			public void setSerialize(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					serialize = qnameFrom(avToArray( o, String.class));
			}

			public void setDeserialize(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					deserialize = qnameFrom(avToArray( o, String.class));
			}

			public void setFinishEffect( Object o, boolean explicit, Element e)
			{
				if ( explicit )
					_finishEffect = FinishEffect.valueOf(
						((VariableElement)o).getSimpleName().toString());
			}

			public Set<Snippet> characterize()
			{
				return Set.of();
			}

			/**
			 * Returns one string per plan element (not per SQL statement).
			 *<p>
			 * This method has to be here anyway because the class extends
			 * {@code AbstractAnnotationImpl}, but it will never be processed as
			 * an actual SQL snippet. This will be called by the containing
			 * {@code AggregateImpl} and return the individual plan elements
			 * that it will build into its own deploy strings.
			 *<p>
			 * When this class represents a moving plan, the caller will prefix
			 * each of these strings with {@code M}.
			 */
			public String[] deployStrings()
			{
				List<String> al = new ArrayList<>();

				al.add("STYPE = " + stateType);

				if ( 0 != _stateSize )
					al.add("SSPACE = " + _stateSize);

				if ( null != _initialState )
					al.add("INITCOND = " + DDRWriter.eQuote(_initialState));

				al.add("SFUNC = " + accumulate);

				if ( null != remove )
					al.add("INVFUNC = " + remove);

				if ( null != finish )
					al.add("FINALFUNC = " + finish);

				if ( _polymorphic )
					al.add("FINALFUNC_EXTRA");

				if ( null != _finishEffect )
					al.add("FINALFUNC_MODIFY = " + _finishEffect);

				if ( null != combine )
					al.add("COMBINEFUNC = " + combine);

				if ( null != serialize )
					al.add("SERIALFUNC = " + serialize);

				if ( null != deserialize )
					al.add("DESERIALFUNC = " + deserialize);

				return al.toArray( new String [ al.size() ]);
			}

			public String[] undeployStrings()
			{
				return null;
			}
		}

		class Moving extends Plan
		{
			public void setRemove(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					remove = qnameFrom(avToArray( o, String.class));
			}

			public void setSerialize(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					throw new IllegalArgumentException(
						"Only a (non-moving) plan may have a " +
						"serialize function");
			}

			public void setDeserialize(Object o, boolean explicit, Element e)
			{
				if ( explicit )
					throw new IllegalArgumentException(
						"Only a (non-moving) plan may have a " +
						"deserialize function");
			}
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
			this.addMap(boolean.class, DT_BOOLEAN);
			this.addMap(Boolean.class, DT_BOOLEAN);
			this.addMap(byte.class, "smallint");
			this.addMap(Byte.class, "smallint");
			this.addMap(char.class, "smallint");
			this.addMap(Character.class, "smallint");
			this.addMap(double.class, "double precision");
			this.addMap(Double.class, "double precision");
			this.addMap(float.class, "real");
			this.addMap(Float.class, "real");
			this.addMap(int.class, DT_INTEGER);
			this.addMap(Integer.class, DT_INTEGER);
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
			this.addMap(ResultSet.class, DT_RECORD);
			this.addMap(Object.class, DT_ANY);

			this.addMap(byte[].class, DT_BYTEA);

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
		 * This should work as long as all the sources containg PL/Java
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

			return declaredTypeForClass(k);
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
		 * Add a custom mapping from a Java class to an SQL type
		 * already in the form of a {@code DBType}.
		 *
		 * @param k Class representing the Java type
		 * @param type DBType representing the SQL type to be used
		 */
		void addMap(Class<?> k, DBType type)
		{
			addMap( typeMirrorFromClass( k), type);
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
			boolean optional = false;
			
			if ( null != st )
			{
				String s = st.value();
				if ( null != s )
					rslt = DBType.fromSQLTypeAnnotation(s);
				defaults = st.defaultValue();
				optional = st.optional();
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
					e, rslt, array, row, defaults, optional, withDefault);

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
			
			return typeWithDefault(
				e, rslt, array, row, defaults, optional, withDefault);
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
			String[] defaults, boolean optional, boolean withDefault)
		{
			if ( ! withDefault  ||  null == defaults && ! optional )
				return rslt;

			if ( optional )
				return rslt.withDefault("DEFAULT NULL");
			
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

	/**
	 * Return an {@code Identifier.Qualified} from an array of Java strings
	 * representing schema and local name separately if of length two, or as by
	 * {@link #qnameFrom(String)} if of length one; invalid if of any other
	 * length.
	 *<p>
	 * The first of two elements may be explicitly {@code ""} to produce a
	 * qualified name with null qualifier.
	 */
	Identifier.Qualified<Identifier.Simple> qnameFrom(String[] names)
	{
		switch ( names.length )
		{
		case 2: return qnameFrom(names[1], names[0]);
		case 1: return qnameFrom(names[0]);
		default:
			throw new IllegalArgumentException(
				"Only a one- or two-element String array is accepted");
		}
	}

	/**
	 * Like {@link #qnameFrom(String[])} but for an operator name.
	 */
	Identifier.Qualified<Identifier.Operator> operatorNameFrom(String[] names)
	{
		switch ( names.length )
		{
		case 2:
			Identifier.Simple qualifier = null;
			if ( ! names[0].isEmpty() )
				qualifier = Identifier.Simple.fromJava(names[0], msgr);
			return Identifier.Operator.from(names[1], msgr)
				.withQualifier(qualifier);
		case 1:
			return Identifier.Qualified.operatorFromJava(names[0], msgr);
		default:
			throw new IllegalArgumentException(
				"Only a one- or two-element String array is accepted");
		}
	}

	String[] qstrings(Identifier.Qualified<?> qname)
	{
		if ( null == qname )
			return null;
		Identifier.Simple q = qname.qualifier();
		String local = qname.local().toString();
		return new String[] { null == q ? null : q.toString(), local };
	}
}

/**
 * Exception thrown when an expected annotation value is a compiler-internal
 * Error class instead, which happens in some javac versions when the annotation
 * value wasn't resolved because of a source error the compiler really should
 * have reported.
 */
class AnnotationValueException extends RuntimeException { }
