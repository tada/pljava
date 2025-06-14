/*
 * Copyright (c) 2016-2025 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Purdue University
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation.processing;

import javax.lang.model.element.Element;

interface Commentable
{
	public String comment();
	public void setComment( Object o, boolean explicit, Element e);
	public String derivedComment( Element e);
}
