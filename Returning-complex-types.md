The SQL-2003 draft suggest that a complex return value is handled as an IN/OUT parameter and PL/Java implements it that way. If you declare a function that returns a complex type, you will need to use a Java method with boolean return type with a last parameter of type <tt>java.sql.ResultSet</tt>. The parameter will be initialized to an updatable ResultSet that contains exactly one row.
```sql
CREATE FUNCTION createComplexTest(int, int)
  RETURNS complexTest
  AS 'foo.fee.Fum.createComplexTest'
  IMMUTABLE LANGUAGE java;
```
The PL/Java method resolver will now find the following method in class foo.fee.Fum:
```java
public static boolean complexReturn(int base, int increment, ResultSet receiver)
throws SQLException
{
  receiver.updateInt(1, base);
  receiver.updateInt(2, base + increment);
  receiver.updateTimestamp(3, new Timestamp(System.currentTimeMillis()));
  return true;
}
```
The return value denotes if the receiver should be considered as a valid tuple (true) or NULL (false).