/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
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
/**
 * Package implementing custom Java security policy useful while migrating
 * existing code to policy-based PL/Java; allows permission checks denied by the
 * main policy to succeed, while logging them so any needed permission grants
 * can be identified and added to the main policy.
 *<p>
 * This package is exported to {@code java.base} to provide a custom
 * {@code Permission} that can be granted in policy.
 */
package org.postgresql.pljava.policy;
