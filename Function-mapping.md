#Functions#
A Java function is declared with the name of a class and a public static method on that class. The class will be resolved using the classpath that has been defined for the schema where the function is declared. If no classpath has been defined for that schema, the "public" schema is used. Please note that the [system classloader] will take precedence always. There is no way to override classes loaded with that loader.

The following function can be declared to access the static method <tt>getProperty</tt> on <tt>java.lang.System</tt> class:
Sample method returning a System property
```sql
CREATE FUNCTION getsysprop(VARCHAR)
  RETURNS VARCHAR
  AS 'java.lang.System.getProperty'
  LANGUAGE java;

SELECT getsysprop('user.home');
```
Both the parameters and the return value can be explicitly stated so the above example could also have been written:
```sql
CREATE FUNCTION getsysprop(VARCHAR)
  RETURNS VARCHAR
  AS 'java.lang.String java.lang.System.getProperty(java.lang.String)'
  LANGUAGE java;
```
This way of declaring the function is useful when the default mapping is inadequate. PL/Java will use a standard PostgreSQL explicit cast when the SQL type of the parameter or return value does not correspond to the Java type defined in the mapping.