This text assumes that you have some familiarity with how scalar types are created and added to the PostgreSQL type system. For more info on that topic, please read [this chapter in the PostgreSQL docs](http://www.postgresql.org/docs/8.4/static/xtypes.html).
Creating new scalar type using Java functions is very similar to how they are created using C functions from an SQL perspective but of course very different when looking at the actual implementation. Java stipulates that the mapping between a Java class and a corresponding SQL type should be done using the interfaces <tt>java.sql.SQLData</tt>, <tt>java.sql.SQLInput</tt>, and <tt>java.sql.SQLOutput</tt> and that is what PL/Java is using. In addition, the PostgreSQL type system stipulates that each type must have a textual representation.

Let us create a type called <tt>javatest.complex</tt> (similar to the complex type used in the PostgreSQL documentation).
##The SQL DDL for the scalar type##
###The SHELL type###
You must start by creating what PostgreSQL refers to as a <tt>SHELL</tt> type. A <tt>SHELL</tt> type is a type that doesn't really exist yet and it is there to overcome the hen and egg problem that arises when the functions needed for the type declaration must use parameters and return values of that same type. Thus, each scalar type definition must start with the creation of the <tt>SHELL</tt> type. In PostgreSQL 8.2 and later, the syntax is straight forward:
```sql
CREATE TYPE javatest.complex;
```
Versions prior to PostgreSQL 8.2 had no special syntax for creating a <tt>SHELL</tt> type. The creation was implicit when the C language functions for the scalar type was created (it still is, but only for C language functions). So in those versions, we create a dummy function that maps to some arbitrary well known C language function.
```sql
CREATE FUNCTION shelltypedummy(cstring) RETURNS javatest.complex AS 'lower' LANGUAGE INTERNAL;
```
###The scalar type functions###
Once the <tt>SHELL</tt> type is defined, we must create each of the functions that are required in order to do input and output. We use a the specific notation <tt>UDT[&lt;class name&gt;] &lt;method type&gt;</tt>. This tells PL/Java that the functions should be considered UDT functions. PL/Java will then recognize the new type and connect the functions to their correspondance using the standard java.sql interfaces.
```sql
/* The scalar input function */
CREATE FUNCTION complex_in(cstring)
	RETURNS javatest.complex
	AS 'UDT[org.postgresql.pljava.example.ComplexScalar] input'
	LANGUAGE java IMMUTABLE STRICT;

/* The scalar output function */
CREATE FUNCTION complex_out(javatest.complex)
	RETURNS cstring
	AS 'UDT[org.postgresql.pljava.example.ComplexScalar] output'
	LANGUAGE java IMMUTABLE STRICT;

/* The scalar receive function */
CREATE FUNCTION complex_recv(internal)
	RETURNS javatest.complex
	AS 'UDT[org.postgresql.pljava.example.ComplexScalar] receive'
	LANGUAGE java IMMUTABLE STRICT;

/* The scalar send function */
CREATE FUNCTION complex_send(javatest.complex)
	RETURNS bytea
	AS 'UDT[org.postgresql.pljava.example.ComplexScalar] send'
	LANGUAGE java IMMUTABLE STRICT;
```
Finally, we create the type itself. The created type will supplant the SHELL type that we created earlier.
```sql
CREATE TYPE javatest.complex (
	internallength = 16,
	input = javatest.complex_in,
	output = javatest.complex_out,
	receive = javatest.complex_recv,
	send = javatest.complex_send,
	alignment = double
	);
```
##The java code for scalar type##
###Prerequisites for the Java implementation###
The java class for a scalar UDT must implement the <tt>java.sql.SQLData</tt> interface. In addition, it must also implement a <tt>static method &lt;T&gt; parse(String stringRepresentation)</tt> and the <tt>java.lang.String toString()</tt> method. The <tt>toString()</tt> method must return something that the <tt>parse()</tt> method can parse.
```java
package org.postgresql.pljava.example;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;
import java.util.logging.Logger;

public class ComplexScalar implements SQLData
{
   private double m_x;
   private double m_y;
   private String m_typeName;

   public static ComplexScalar parse(String input, String typeName) throws SQLException
   {
      try
      {
         StreamTokenizer tz = new StreamTokenizer(new StringReader(input));
         if(tz.nextToken() == '('
         && tz.nextToken() == StreamTokenizer.TT_NUMBER)
         {
            double x = tz.nval;
            if(tz.nextToken() == ','
            && tz.nextToken() == StreamTokenizer.TT_NUMBER)
            {
               double y = tz.nval;
               if(tz.nextToken() == ')')
               {
                  return new ComplexScalar(x, y, typeName);
               }
            }
         }
         throw new SQLException("Unable to parse complex from string \"" + input + '"');
      }
      catch(IOException e)
      {
         throw new SQLException(e.getMessage());
      }
   }

   public ComplexScalar()
   {
   }

   public ComplexScalar(double x, double y, String typeName)
   {
      m_x = x;
      m_y = y;
      m_typeName = typeName;
   }

   public String getSQLTypeName()
   {
      return m_typeName;
   }

   public void readSQL(SQLInput stream, String typeName) throws SQLException
   {
      m_x = stream.readDouble();
      m_y = stream.readDouble();
      m_typeName = typeName;
   }

   public void writeSQL(SQLOutput stream) throws SQLException
   {
      stream.writeDouble(m_x);
      stream.writeDouble(m_y);
   }

   public String toString()
   {
      s_logger.info(m_typeName + " toString");
      StringBuffer sb = new StringBuffer();
      sb.append('(');
      sb.append(m_x);
      sb.append(',');
      sb.append(m_y);
      sb.append(')');
      return sb.toString();
   }

   /* Meaningful code that actually does something with this type was
    * intentionally left out.
    */
}
```