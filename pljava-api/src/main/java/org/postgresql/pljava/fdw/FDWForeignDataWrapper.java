package org.postgresql.pljava.fdw;

import java.util.Collections;
import java.util.Map;

/**
 * The Foreign Data Wrapper.
 *
 * This is the highest-level abstraction, e.g., for information
 * contained in S3 files.
 *
 * It could also capture an abstract concept, e.g., one FDW
 * to capture multiple authentication implementations.
 *
 * There may be multiple instances of a single FOREIGN DATA WRAPPER.
 */
public interface FDWForeignDataWrapper {

	/**
	 * The instances unique ID. It should be used to maintain a cache.
	 * @return
	 */
	default Long getId() { return null; }

	/**
	 * Return a copy of the options provided to `CREATE FOREIGN DATA WRAPPER...`
	 * @return
	 */
	default Map<String, String> getOptions() { return Collections.emptyMap(); };

	/**
	 * Validate a set of options against an existing instance. There should be
	 * a similar static method before creating a new instance.
	 *
	 * @param options
	 * @return
	 */
	default boolean validateOptions(Map<String, String> options) { return true; };
}
