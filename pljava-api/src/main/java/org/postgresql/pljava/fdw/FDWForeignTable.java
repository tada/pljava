package org.postgresql.pljava.fdw;

import java.sql.ResultSetMetaData;
import java.util.Collections;
import java.util.Map;

/**
 * The Foreign Table
 *
 * This is the lowest-level abstraction, e.g., a specific
 * S3 file.
 *
 * There may be multiple instances of a Foreign Table
 * for a single Foreign Server.
 */
public interface FDWForeignTable {

	/**
	 * The instances unique ID. It should be used to maintain a cache.
	 */
	default Long getId() { return null; }

	/**
	 * Return a copy of the options provided to `CREATE FOREIGN TABLE...`
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
	 * Create an object used for query planning.
	 *
	 * @param user
	 * @return
	 */
	FDWPlanState newPlanState(FDWUser user);

	/**
	 * Create an object used for SELECT statements.
	 * @param user
	 * @return
	 */
	FDWScanState newScanState(FDWUser user, boolean explainOnly);

	// values from PlannerInfo *root, RelOptInfo *baserel
	// BUT NOT foreigntableoid
	void getRelSize();

/*
	static void blackholeGetForeignPaths(PlannerInfo *root,
										 RelOptInfo *baserel,
										 Oid foreigntableid);
*/

	// values from PlannerInfo *root, RelOptInfo *baserel
	// BUT NOT foreigntableoid
	void getForeignPaths();

	/**
	 * Is this table updatable by this user?
	 *
	 * @param user
	 * @return
	 */
	default boolean isUupdatable(FDWUser user) { return false; }

	/**
	 * Does this table support concurrent access?
	 * @return
	 */
	default boolean supportsConcurrency() { return false; }

	/**
	 * Does this table support asynchronous queries?
	 * @return
	 */
	default boolean supportsAsyncOperations() { return false; }

	/**
	 * Collect statistics used by the query optimizer.
	 * This can be supported for read-only tables.
	 *
	 * Details TBD
	 */
	default void analyze() { }

	/**
	 * Compact the data, if appropriate.
	 * This should be a noop for read-only tables.
	 *
	 * Details TBD.
	 */
	default void vacuum() { }

	/**
	 * Get the table's schema. This information
	 * will be used when executing `IMPORT FOREIGN SCHEMA...`
	 *
	 * @return
	 */
	default ResultSetMetaData getMetaData() { return null; }

	/**
	 * Estimate the number of rows.
	 *
	 * @return
	 */
	default long getRows() { return 0; }
}
