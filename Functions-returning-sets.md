Returning sets is tricky. You don't want to first build a set and then return it since large sets would eat too much resources. Its far better to produce one row at a time. Incidentally, that's exactly what the PostgreSQL backend expects a function that _RETURNS SETOF &lt;type&gt;_ to do. The _&lt;type&gt;_ can be a _scalar type_ such as an _int_, _float_ or _varchar_, it can be a _complex type_, or a _RECORD_.

###Returning a SETOF &lt;scalar type&gt;###
In order to return a set of a scalar type, you need create a Java method that returns an implementation the java.util.Iterator interface.
```sql
CREATE FUNCTION javatest.getNames()
  RETURNS SETOF varchar
  AS 'foo.fee.Bar.getNames'
  IMMUTABLE LANGUAGE java;
```
The corresponding Java class:
```java
package foo.fee;
import java.util.Iterator;

public class Bar
{
    public static Iterator getNames()
    {
        ArrayList names = new ArrayList();
        names.add("Lisa");
        names.add("Bob");
        names.add("Bill");
        names.add("Sally");
        return names.iterator();
    }
}
```
###Returning a SETOF &lt;complex type&gt;###
A method returning a SETOF &lt;complex type&gt; must use either the interface _org.postgresql.pljava.ResultSetProvider_ or _org.postgresql.pljava.ResultSetHandle_. The reason for having two interfaces is that they cater for optimal handling of two distinct use cases. The former is great when you want to dynamically create each row that is to be returned from the _SETOF_ function. The latter makes sense when you want to return the result of an executed query.
###Using the ResultSetProvider interface###
This interface has two methods. The _boolean assignRowValues(java.sql.ResultSet tupleBuilder, int rowNumber)_ and the _void close()_ method. The PostgreSQL query evaluator will call the _assignRowValues()_ repeatedly until it returns false or until the evaluator decides that it does not need any more rows. It will then call _close()_.

You can use this interface the following way:
```sql
CREATE FUNCTION javatest.listComplexTests(int, int)
  RETURNS SETOF complexTest
  AS 'foo.fee.Fum.listComplexTest'
  IMMUTABLE LANGUAGE java;
```
The function maps to a static java method that returns an instance that implements the _ResultSetProvider_ interface.
```java
public class Fum implements ResultSetProvider
{
  private final int m_base;
  private final int m_increment;
  public Fum(int base, int increment)
  {
    m_base = base;
    m_increment = increment;
  }
  public boolean assignRowValues(ResultSet receiver, int currentRow)
  throws SQLException
  {
    // Stop when we reach 12 rows.
    //
    if(currentRow >= 12)
      return false;
    receiver.updateInt(1, m_base);
    receiver.updateInt(2, m_base + m_increment * currentRow);
    receiver.updateTimestamp(3, new Timestamp(System.currentTimeMillis()));
    return true;
  }
  public void close()
  {
  	// Nothing needed in this example
  }
  public static ResultSetProvider listComplexTests(int base, int increment)
  throws SQLException
  {
    return new Fum(base, increment);
  }
}
```
The _listComplexTests(int base, int increment)_ method is called once. It may return null if no results are available or an instance of the _ResultSetProvider_. Here the Fum class implements this interface so it returns an instance of itself. The method _assignRowValues(ResultSet receiver, int currentRow)_ will then be called repeatedly until it returns false. At that time, _close()_ will be called.

###Using the ResultSetHandle interface###
This interface is similar to the _ResultSetProvider_ interface in that it has a <tt>close()</tt> method that will be called at the end. But instead of having the evaluator call a method that builds one row at a time, this method has a method that returns a <tt>ResultSet</tt>. The query evaluator will iterate over this set and deliver it's contents, one tuple at a time, to the caller until a call to <tt>next()</tt> returns _false_ or the evaluator decides that no more rows are needed.

Here is an example that executes a query using a statement that it obtained using the default connection. The SQL looks like this:
```sql
CREATE FUNCTION javatest.listSupers()
  RETURNS SETOF pg_user
  AS 'org.postgresql.pljava.example.Users.listSupers'
  LANGUAGE java;

CREATE FUNCTION javatest.listNonSupers()
  RETURNS SETOF pg_user
  AS 'org.postgresql.pljava.example.Users.listNonSupers'
  LANGUAGE java;
```
And here is the Java code:
```java
public class Users implements ResultSetHandle
{
  private final String m_filter;
  private Statement m_statement;

  public Users(String filter)
  {
    m_filter = filter;
  }

  public ResultSet getResultSet()
  throws SQLException
  {
    m_statement = DriverManager.getConnection("jdbc:default:connection").createStatement();
    return m_statement.executeQuery("SELECT * FROM pg_user WHERE " + m_filter);
  }

  public void close()
  throws SQLException
  {
    m_statement.close();
  }

  public static ResultSetHandle listSupers()
  {
    return new Users("usesuper = true");
  }

  public static ResultSetHandle listNonSupers()
  {
    return new Users("usesuper = false");
  }
}
```