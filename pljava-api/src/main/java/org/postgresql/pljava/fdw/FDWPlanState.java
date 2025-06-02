package org.postgresql.pljava.fdw;

public interface FDWPlanState {

	// values from PlannerInfo *root, RelOptInfo *baserel.
	// the PlannerInfo is only used in advanced queries.
	void open();

	void close();

	default long getRows() { return 0; }

	default FDWUser getUser() { return null; }
}
