PL/Java contains a JDBC driver that maps to the PostgreSQL SPI functions. A connection that maps to the current transaction can be obtained using the following statement:
```java
Connection conn = DriverManager.getConnection("jdbc:default:connection");
```
From there on, you can prepare and execute statements just like with any other JDBC connection. There are a couple of limitations though:
* The transaction cannot be managed in any way. Thus, you cannot use methods on the connection such as:
    * commit()
    * rollback()
    * setAutoCommit()
    * setTransactionIsolation()
* A savepoint cannot outlive the function in which it was set and it must also be rolled back or released by that same function.
* ResultSet's returned from executeQuery() are always FETCH_FORWARD and CONCUR_READ_ONLY.
* Meta-data became available in PL/Java 1.1.
* CallableStatement (for stored procedures) is not yet implemented.
* Clob/Blob types need more work. byte[] and String works fine for bytea/text respectively. A more efficient mapping is planned where the actual array is not copied.