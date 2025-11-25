package org.postgresql.pljava.fdw;

import java.sql.DatabaseMetaData;
import java.util.Collections;
import java.util.Map;

/**
 * The Foreign Server
 *
 * This is the middle-level abstraction, e.g., a specific
 * AWS account with access to the required resources.
 *
 * There may be multiple instances of a Foreign Server
 * for a single Foreign Data Wrapper.
 */
public interface FDWServer {

	/**
	 * The instances unique ID. It should be used to maintain a cache.
	 */
	default Long getId() { return null; }

	/**
	 * Return a copy of the options provided to `CREATE FOREIGN SERVER...`
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

	/**
	 * Get the server's entire schema. This can be useful
	 * information even if the backend only gets the
	 * schema for individual tables.
	 *
	 * (It's not clear since the backend struct supports
	 * foreign keys but I don't think the individual
	 * ResultSetMetadata includes that information.)
	 *
	 * @return
	 */
	default DatabaseMetaData getMetaData() { return null; }
}
