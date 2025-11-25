package org.postgresql.pljava.example.fdw;

import org.postgresql.pljava.fdw.FDWForeignDataWrapper;
import org.postgresql.pljava.fdw.FDWPlanState;
import org.postgresql.pljava.fdw.FDWScanState;
import org.postgresql.pljava.fdw.FDWForeignTable;
import org.postgresql.pljava.fdw.FDWUser;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

	private final List<Map<String, Object>> dummyTable = new ArrayList<>();

	public BlackholeForeignTable() {
		this(Collections.emptyMap());
	}

	public BlackholeForeignTable(Map<String, String> options) {
		LOG.info("constructor");
	}

	@Override
	public void getRelSize() {
	}

	/*
	public void blackholeGetForeignPaths(PlannerInfo *root,
										 RelOptInfo *baserel,
										 Oid foreigntableid) {
	}
	 */

	@Override
	public FDWPlanState newPlanState(FDWUser user) {
		return null;
	}

	@Override
	public FDWScanState newScanState(FDWUser user, boolean explainOnly) {
		return null;
	}

	/*
	@Override
	public boolean isUpdatable(FDWUser user);

	@Override
	public boolean supportsConcurrency();

	@Override
	public boolean supportsAsyncOperation();

	@Override
	public void analyze();

	@Override
	public void vacuum();

	@Override
	public ResultSetMetaData getMetaData();
	 */
}
