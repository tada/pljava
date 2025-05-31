package org.postgresql.pljava.fdw;

import java.sql.ResultSetMetaData;

public interface FDWServer {
	FDWForeignTable getForeignTable();

	// For 'importSchemaStmt()
	ResultSetMetaData getMetaData();
}
