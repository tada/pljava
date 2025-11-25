package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWForeignDataWrapper;
import org.postgresql.pljava.fdw.FDWValidator;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class BlackholeValidator implements FDWValidator {
	private static final Logger LOG = Logger.getLogger(BlackholeValidator.class.getName());

	// note: we know there's only five possible integer values.
	private final Map<Integer, Map<String, String>> options = new HashMap<>();

	public BlackholeValidator() {
		this(Collections.emptyMap());
	}

	public BlackholeValidator(Map<String, String> options) {
		LOG.info("constructor");
	}

	@Override
	public void addOption(int relid, String key, String value) {
		LOG.info(String.format("addOption(%d, %s, %s)", relid, key, value));

		if (!options.containsKey(relid)) {
			options.put(relid, new HashMap<>());
		}

		options.get(relid).put(key, value);
	}

	@Override
	public boolean validate() {
		LOG.info("validate()");
		return true;
	}

	@Override
	public FDWForeignDataWrapper getForeignDataWrapper() {
		LOG.info("getForeignDataWrapper()");
		return new BlackholeForeignDataWrapper();
	}
}
