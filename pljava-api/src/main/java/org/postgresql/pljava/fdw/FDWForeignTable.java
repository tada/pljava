package org.postgresql.pljava.fdw;

public interface FDWForeignTable {
	FDWPlanState newPlanState();
	FDWScanState ScanState();

	default boolean updatable() { return false; }

	default void analyze() { };
//
//	default void vacuum() { };
}
