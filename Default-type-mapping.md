##Scalar types##
Scalar types are mapped in a straight forward way. Here's a table of the current mappings (will be updated as more mappings are implemented).
<table>
<tr><th>PostgreSQL</th><th>Java</th></tr>
<tr><td>bool</td><td>boolean</td></tr>
<tr><td>"char"</td><td>byte</td></tr>
<tr><td>int2</td><td>short</td></tr>
<tr><td>int4</td><td>int</td></tr>
<tr><td>int8</td><td>long</td></tr>
<tr><td>float4</td><td>float</td></tr>
<tr><td>float8</td><td>double</td></tr>
<tr><td>char</td><td>java.lang.String</td></tr>
<tr><td>varchar</td><td>java.lang.String</td></tr>
<tr><td>text</td><td>java.lang.String</td></tr>
<tr><td>name</td><td>java.lang.String</td></tr>
<tr><td>bytea</td><td>byte[]</td></tr>
<tr><td>date</td><td>java.sql.Date</td></tr>
<tr><td>time</td><td>java.sql.Time (stored value treated as local time)</td></tr>
<tr><td>timetz</td><td>java.sql.Time</td></tr>
<tr><td>timestamp</td><td>java.sql.Timestamp (stored value treated as local time)</td></tr>
<tr><td>timestamptz</td><td>java.sql.Timestamp</td></tr>
</table>

##Array scalar types##
All scalar types can be represented as an array. Although PostgreSQL will allow that you declare multi dimensional arrays with fixed sizes, PL/Java will still treat all arrays as having one dimension (with the exception of the bytea[] which maps to byte[][]). The reason for this is that the information about dimensions and sizes is not stored anywhere and not enforced in any way. You can read more about this in the [PostgreSQL Documentation](http://www.postgresql.org/docs/8.4/static/arrays.html).
However, the current implementation does not enforce the array size limits — the behavior is the same as for arrays of unspecified length.

Actually, the current implementation does not enforce the declared number of dimensions either. Arrays of a particular element type are all considered to be of the same type, regardless of size or number of dimensions. So, declaring number of dimensions or sizes in CREATE TABLE is simply documentation, it does not affect run-time behavior.}}
<table>
<tr><th>PostgreSQL</th><th>Java</th></tr>
<tr><td>bool[]</td><td>boolean[]</td></tr>
<tr><td>"char"[]</td><td>byte[]</td></tr>
<tr><td>int2[]</td><td>short[]</td></tr>
<tr><td>int4[]</td><td>int[]</td></tr>
<tr><td>int8[]</td><td>long []</td></tr>
<tr><td>float4[]</td><td>float[]</td></tr>
<tr><td>float8[]</td><td>double[]</td></tr>
<tr><td>char[]</td><td>java.lang.String[]</td></tr>
<tr><td>varchar[]</td><td>java.lang.String[]</td></tr>
<tr><td>text[]</td><td>java.lang.String[]</td></tr>
<tr><td>name[]</td><td>java.lang.String[]</td></tr>
<tr><td>bytea[]</td><td>byte[][]</td></tr>
<tr><td>date[]</td><td>java.sql.Date[]</td></tr>
<tr><td>time[]</td><td>java.sql.Time[] (stored value treated as local time)</td></tr>
<tr><td>timetz[]</td><td>java.sql.Time[]</td></tr>
<tr><td>timestamp[]</td><td>java.sql.Timestamp[] (stored value treated as local time)</td></tr>
<tr><td>timestamptz[]</td><td>java.sql.Timestamp[]</td></tr>
</table>

##Domain types##
A domain type will be mapped in accorance with the type that it extends unless you have installed a specific mapping to override that behavior.

##Pseudo types##
<table>
<tr><th>PostgreSQL</th><th>Java</th></tr>
<tr><td>"any"</td><td>java.lang.Object</td></tr>
<tr><td>anyelement</td><td>java.lang.Object</td></tr>
<tr><td>anyarray</td><td>java.lang.Object[]</td></tr>
<tr><td>cstring</td><td>java.lang.String</td></tr>
<tr><td>record</td><td>java.sql.ResultSet</td></tr>
<tr><td>trigger</td><td>org.postgresql.pljava.TriggerData (see [[Triggers]])</td></tr>
</table>

##NULL handling of primitives##
The scalar types that map to Java primitives can not be passed as null values. To enable this, those types can have an alternative mapping. You enable this mapping by explicitly denoting it in the method reference.
```sql
CREATE FUNCTION trueIfEvenOrNull(integer)
  RETURNS bool
  AS 'foo.fee.Fum.trueIfEvenOrNull(java.lang.Integer)'
  LANGUAGE java;
```
In java, you would have something like:
```java
package foo.fee;

public class Fum
{
  static boolean trueIfEvenOrNull(Integer value)
  {
    return (value == null)
      ? true
      : (value.intValue() % 1) == 0;
  }
}
```
The following two statements should both yield true:
```sql
SELECT trueIfEvenOrNull(NULL);
SELECT trueIfEvenOrNull(4);
```
In order to return null values from a Java method, you simply use the object type that corresponds to the primitive (i.e. you return java.lang.Integer instead of int). The PL/Java resolver mechanism will find the method regardless. Since Java cannot have different return types for methods with the same name, this does not introduce any ambiguities.

Starting with PostgreSQL version 8.2 it will be possible to have NULL values in arrays. PL/Java will handle that the same way as with normal primitives, i.e. you can declare methods that use a java.lang.Integer[] parameter instead of an int[] parameter.

##Composite types##
A composite type will be passed as a read-only java.sql.ResultSet with exaclty one row by default. The ResultSet will be positioned on its row so no call to next() should be made. The values of the composite type are retrieved using the standard getter methods of the ResultSet.
Example:
```sql
CREATE TYPE compositeTest
  AS(base integer, incbase integer, ctime timestamptz);

CREATE FUNCTION useCompositeTest(compositeTest)
  RETURNS VARCHAR
  AS 'foo.fee.Fum.useCompositeTest'
  IMMUTABLE LANGUAGE java;
```
In class Fum we add the static following static method
The foo.fee.Fum.useCompositeTest method:
```java
public static String useCompositeTest(ResultSet compositeTest)
throws SQLException
{
  int base = compositeTest.getInt(1);
  int incbase = compositeTest.getInt(2);
  Timestamp ctime = compositeTest.getTimestamp(3);
  return "Base = \\"" + base +
    "\\", incbase = \\"" + incbase +
    "\\", ctime = \\"" + ctime + "\\"";
}
```

##Default mapping##
Types that have no mapping are currently mapped to java.lang.String. The standard PostgreSQL textin/textout routines registered for respective type will be used when the values are converted.