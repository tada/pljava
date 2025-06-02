package org.postgresql.pljava.fdw;

import java.util.Collections;
import java.util.Map;

/**
 * The Validator
 *
 * This class is used to validate the options provided to
 * the FDWForeignDataWrapper, FDWForeignServer, and FDWForeignTable
 * constructors.
 *
 * Note: the Foreign Data Wrapper or Foreign Server may already
 * exist. If so they will be provided a copy of the appropriate
 * options.
 */
public interface FDWValidator {
	enum Scope {
		FOREIGN_DATA_WRAPPER,
		FOREIGN_SERVER,
		FOREIGN_TABLE
		// plus two others...
	};

	/**
	 * Add an option
	 *
	 * @param scope
	 * @param key
	 * @param value
	 */
	default void addOption(Scope scope, String key, String value) { }

	/**
	 * Get options
	 */
	default Map<String, String> getOptions(Scope scope)
	{
		return Collections.emptyMap();
	}

	/**
	 * Validate all options.
	 *
	 * This method should create any missing objects and add
	 * them to an internal cache.
	 *
	 * @TODO - should the return value indicate where the validation
	 * failed? FDW, SERVER, TABLE, bad property or unable to create
	 * object?
	 *
	 * @param fdwId if for existing ForeignDataWrapper, or null
	 * @param srvId if for existing Server, or null
	 * @param ftId if for existing Foreign Table, or null
	 *
	 * @return true if successfully validated
	 */
	default boolean validate(long fdwId, Long srvId, Long ftId) { return true; }
}
