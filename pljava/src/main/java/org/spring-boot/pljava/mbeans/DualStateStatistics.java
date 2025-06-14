/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.mbeans;

import javax.management.MXBean;

import org.postgresql.pljava.internal.DualState; // for javadoc

/**
 * Bean exposing some {@link DualState DualState} allocation and lifecycle
 * statistics for viewing in a JMX management client.
 */
@MXBean
public interface DualStateStatistics
{
	long getConstructed();
	long getEnlistedScoped();
	long getEnlistedUnscoped();
	long getDelistedScoped();
	long getDelistedUnscoped();
	long getJavaUnreachable();
	long getJavaReleased();
	long getNativeReleased();
	long getResourceOwnerPasses();
	long getReferenceQueuePasses();
	long getReferenceQueueItems();
	long getContendedLocks();
	long getContendedPins();
	long getRepeatedlyDeferred();
	long getGcReleaseRaces();
	long getReleaseReleaseRaces();
}
