package org.postgresql.pljava.fdw;

public interface FDWScanState {
	void open();
	void next(Object slot);
	void reset();
	void close();

	// void explain(); ??
}
