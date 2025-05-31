package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWForeignDataWrapper;
import org.postgresql.pljava.fdw.FDWPlanState;
import org.postgresql.pljava.fdw.FDWScanState;
import org.postgresql.pljava.fdw.FDWForeignTable;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;

/**
 * A Foreign Table (persistent)
 *
 * A single ForeignTable may have multiple PlanStates and ScanStates
 * however they are transient and unlikely to be reused.
 */
public class BlackholeForeignTable implements FDWForeignTable {
	private static final Logger LOG = Logger.getLogger(BlackholeForeignTable.class.getName());

	public BlackholeForeignTable() {
		this(Collections.emptyMap());
	}

	public BlackholeForeignTable(Map<String, String> options) {
		LOG.info("constructor");
	}

	@Override
	public FDWPlanState newPlanState() {
		LOG.info("getPlanState()");
		return new BlackholePlanState(this);
	}

	@Override
	public FDWScanState newScanState() {
		LOG.info("newScanState()");
		return new BlackholeScanState(this);
	}

	@Override
	public boolean updatable() {
		LOG.info("updatable()");
		return false;
	}
}
