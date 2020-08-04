/*
 * Copyright (c) 2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Kartik Ohri
 */
package org.postgresql.pljava.pgxs;

import javax.script.ScriptEngine;
import java.io.File;
import java.util.Locale;

public interface ReportScript
{
	default boolean isExternalReport() {
		return true;
	}

	default String getOutputName ()
	{
		return "apidocs" + File.separator + "index";
	}

	default String getName (Locale locale)
	{
		return String.format(locale, "%s", "Documentation Report");
	}

	default String getDescription (Locale locale)
	{
		return String.format(locale, "%s","Javadoc Generation Goal");
	}

	default String getCategoryName() {
		return "Project Reports";
	}

	default boolean canGenerateReport() {
		return true;
	}

	default void executeReport(ReportScriptingMojo report) {
		try
		{
			ScriptEngine engine = PGXSUtils.getScriptEngine(
				report.script, report.getLog());
			engine.put("report", report);
			String scriptText = report.script.getValue();
			report.getLog().debug(scriptText);
			engine.eval(scriptText);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

}
