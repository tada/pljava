Using PL/Java, you can install a mapping between an arbitrary type and a Java class. There are two prerequisites for doing this:
* You must know the storage layout of the SQL type that you are mapping.
* The Java class that you map to must implement the interface <tt>java.sql.SQLData</tt>.

##Mapping an existing SQL data type to a java class##
Here is an example of how to map the PostgreSQL geometric point type to a Java class. We know that the point is stored as two float8's, the x and the y coordinate.

You can consult the postgresql source code when the exact layout of a basic type is unknown. I peeked at the <tt>point_recv</tt> function in file <tt>src/backend/utils/adt/geo_ops.c</tt> to determine the exact layout of the point type.

Once the layout is known, you can create the <tt>java.sql.SQLData</tt> implementation that uses the class <tt>java.sql.SQLInput</tt> to read and the class <tt>java.sql.SQLOutput</tt> to write data:

```java
package org.postgresql.pljava.example;

import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;

public class Point implements SQLData {
   private double m_x;
   private double m_y;
   private String m_typeName;

   public String getSQLTypeName() {
      return m_typeName;
   }

   public void readSQL(SQLInput stream, String typeName) throws SQLException {
      m_x = stream.readDouble();
      m_y = stream.readDouble();
      m_typeName = typeName;
   }

   public void writeSQL(SQLOutput stream) throws SQLException {
      stream.writeDouble(m_x);
      stream.writeDouble(m_y);
   }

   /* Meaningful code that actually does something with this type was
    * intentionally left out.
    */
}
```

Finally, you install the type mapping using the [[add_type_mapping]] command:
```sql
SELECT sqlj.add_type_mapping('point', 'org.postgresql.pljava.example.Point');
```
You should now be able to use your new class. PL/Java will henceforth map any point parameter to the org.postgresql.pljava.example.Point class.

##Creating a composite UDT and map it to a java class##
Here is an example of a complex type created as a composite UDT.
```sql
CREATE TYPE javatest.complextuple AS (x float8, y float8);

SELECT sqlj.add_type_mapping('javatest.complextuple', 'org.postgresql.pljava.example.ComplexTuple');
```
```java
package org.postgresql.pljava.example;

import java.sql.SQLData;
import java.sql.SQLException;
import java.sql.SQLInput;
import java.sql.SQLOutput;

public class ComplexTuple implements SQLData {
   private double m_x;
   private double m_y;
   private String m_typeName;

   public String getSQLTypeName()
   {
      return m_typeName;
   }

   public void readSQL(SQLInput stream, String typeName) throws SQLException
   {
      m_typeName = typeName;
      m_x = stream.readDouble();
      m_y = stream.readDouble();
   }

   public void writeSQL(SQLOutput stream) throws SQLException
   {
      stream.writeDouble(m_x);
      stream.writeDouble(m_y);
   }

   /* Meaningful code that actually does something with this type was
    * intentionally left out.
    */
}
```
