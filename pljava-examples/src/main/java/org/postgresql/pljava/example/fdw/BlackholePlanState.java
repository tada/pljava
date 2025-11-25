package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWPlanState;

import java.util.logging.Logger;

/**
 * A ForeignTable plan state. (Temporary)
 */
public class BlackholePlanState implements FDWPlanState {
	private static final Logger LOG = Logger.getLogger(BlackholePlanState.class.getName());

	private final BlackholeForeignTable table;

	public BlackholePlanState(BlackholeForeignTable table) {
		LOG.info("constructor()");
		this.table = table;
	}

	@Override
	public void open() {
		LOG.info("open()");
	}

	@Override
	public void close() {
		LOG.info("close()");
	}
}
