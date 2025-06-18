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

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import static java.util.Objects.requireNonNull;
import java.util.Queue;

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
