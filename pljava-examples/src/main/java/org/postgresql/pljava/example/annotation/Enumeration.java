/*
 * Copyright (c) 2015-2023 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.example.annotation;

import java.util.Iterator;
import java.util.Arrays;

import org.postgresql.pljava.annotation.SQLAction;
import org.postgresql.pljava.annotation.SQLType;
import org.postgresql.pljava.annotation.Function;

/**
 * Confirms the mapping of PG enum and Java String, and arrays of each, as
 * parameter and return types.
 */
@SQLAction(provides="mood type",
	install="CREATE TYPE mood AS ENUM ('sad', 'ok', 'happy')",
	remove="DROP TYPE mood"
)
@SQLAction(
	requires={"textToMood", "moodToText", "textsToMoods", "moodsToTexts"},
	install={
		"SELECT textToMood('happy')",
		"SELECT moodToText('happy'::mood)",
		"SELECT textsToMoods(array['happy','happy','sad','ok'])",
		"SELECT moodsToTexts(array['happy','happy','sad','ok']::mood[])"
	}
)
public class Enumeration
{
	@Function(requires="mood type", provides="textToMood", type="mood")
	public static String textToMood(String s)
	{
		return s;
	}
	@Function(requires="mood type", provides="moodToText")
	public static String moodToText(@SQLType("mood")String s)
	{
		return s;
	}
	@Function(requires="mood type", provides="textsToMoods", type="mood")
	public static Iterator<String> textsToMoods(String[] ss)
	{
		return Arrays.asList(ss).iterator();
	}
	@Function(requires="mood type", provides="moodsToTexts")
	public static Iterator<String> moodsToTexts(@SQLType("mood[]")String[] ss)
	{
		return Arrays.asList(ss).iterator();
	}
}
