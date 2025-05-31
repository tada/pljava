package org.postgresql.pljava.fdw;

public interface FDWPlanState {
	void open();

	void close();

	// int rows();
}
