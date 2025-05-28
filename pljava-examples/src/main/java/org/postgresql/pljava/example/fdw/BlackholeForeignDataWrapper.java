package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWForeignDataWrapper;
import org.postgresql.pljava.fdw.FDWServer;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A ForeignDataWrapper. (Persistent)
 *
 * Note: a single ForeignDataWrapper may contain multiple servers
 * so there should be caching somewhere.
 */
public class BlackholeForeignDataWrapper implements FDWForeignDataWrapper {
	private static final Logger LOG = Logger.getLogger(BlackholeForeignDataWrapper.class.getName());

	public BlackholeForeignDataWrapper() {
		this(Collections.emptyMap());
	}

	public BlackholeForeignDataWrapper(Map<String, String> options) {
		LOG.info("constructor");
	}

	@Override
	public FDWServer getServer() {
		LOG.info("getServer()");
		return new BlackholeServer();
	}
}
