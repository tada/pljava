package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWForeignTable;
import org.postgresql.pljava.fdw.FDWServer;

import java.sql.ResultSetMetaData;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A Foreign Server (persistent)
 *
 * Note: a single Server may contain multiple ForeignTables
 * so there should be caching somewhere.
 */
public class BlackholeServer implements FDWServer {
	private static final Logger LOG = Logger.getLogger(BlackholeServer.class.getName());

	public BlackholeServer() {
		this(Collections.emptyMap());
	}

	public BlackholeServer(Map<String, String> options) {
		LOG.info("constructor()");
	}

	@Override
	public FDWForeignTable getForeignTable() {
		LOG.info("getForeignTable()");
		return new BlackholeForeignTable() {};
	}

	@Override
	public ResultSetMetaData getMetaData() {
		LOG.info("getMetaData()");
		return null;
	}
}
