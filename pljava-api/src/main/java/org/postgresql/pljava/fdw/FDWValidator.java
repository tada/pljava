package org.postgresql.pljava.fdw;

public interface FDWValidator {
	void addOption(int relid, String key, String value);

	boolean validate();

	FDWForeignDataWrapper getForeignDataWrapper();
}
