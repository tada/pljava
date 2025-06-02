package org.postgresql.pljava.fdw;

import java.util.Map;

public interface FDWScanState {
	/**
	 * The database has already performed an initial check for
	 * the user's permission to execute SELECT - but our java
	 * code may impose additional requirements.
	 *
	 * The minimal implementation just adds the ability to check
	 * whether * the user is authorized. (The current FDWUser is
	 * transparently * passed to the appropriate method.)
	 *
	 * A more advanced implementation would allow us to
	 * add row and column filtering beyond what will already
	 * be done by the database.
	 */
	default boolean isAuthorizedUser() { return true; }

	/**
	 * Verify that we have a valid configuration.
	 *
	 * No external resources should be accessed if
	 * the `explainOnly` flag is true. (It's okay to
	 * check a file exists and is readable but it should
	 * not be opened. A REST service can have its hostname
	 * verified but it should not be called.
	 *
	 * If the `explainOnly` flag is false than external
	 * resources can be accessed in order to verify
	 * that it's a valid URL and we have valid credentials.
	 * However all external resources should be released
	 * before this method exits.
	 */
	void open(boolean explainOnly);

	// values from TableTupleType. It is an element
	// of the ForeignScanState mentioned above.
	Map<String, Object> next();

	/**
	 * Reset scan to initial state.
	 */
	void reset();

	/**
	 * Release resources
	 */
	void close();

	default FDWExplainState explain() { return null; }

	default FDWUser getUser() { return null; }
}
