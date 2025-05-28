package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWScanState;

import java.util.logging.Logger;

/**
 * A ForeignTable scan state. (Temporary)
 */
public class BlackholeScanState implements FDWScanState {
	private static final Logger LOG = Logger.getLogger(BlackholeScanState.class.getName());

	private final BlackholeForeignTable table;

	public BlackholeScanState(BlackholeForeignTable table) {
		LOG.info("constructor()");
		this.table = table;
	}

	@Override
	public void open() {
		LOG.info("open()");
	}

	@Override
	public void next(Object slot) {
		LOG.info("next()");
	}

	@Override
	public void reset() {
		LOG.info("reset()");
	}

	@Override
	public void close() {
		LOG.info("close()");
	}
}
