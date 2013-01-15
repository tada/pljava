#Triggers#
The method signature of a trigger is predefined. A trigger method must always return void and have a org.postgresql.pljava.TriggerData parameter. No more, no less. The TriggerData interface provides access to two java.sql.ResultSet instances; one representing the old row and one representing the new. The old row is read-only and the new row is updateable.

The ResultSets are only available for triggers that are fired ON EACH ROW. Delete triggers have no new row, and insert triggers have no old row. Only update triggers have both.
In addition to the sets, several boolean methods exists to gain more information about the trigger.
```sql
CREATE TABLE mdt (
  id int4,
  idesc text,
  moddate timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL);

CREATE FUNCTION moddatetime()
  RETURNS trigger
  AS 'org.postgresql.pljava.example.Triggers.moddatetime'
  LANGUAGE java;

CREATE TRIGGER mdt_moddatetime
  BEFORE UPDATE ON mdt
  FOR EACH ROW
  EXECUTE PROCEDURE moddatetime (moddate);
```
And here is the corresponding Java code:
Methods in the <tt>org.postgresql.pljava.example.Triggers</tt> class
```java
/**
 * Update a modification time when the row is updated.
 */
static void moddatetime(TriggerData td)
throws SQLException
{
  if(td.isFiredForStatement())
    throw new TriggerException(td, "can't process STATEMENT events");

  if(td.isFiredAfter())
    throw new TriggerException(td, "must be fired before event");

  if(!td.isFiredByUpdate())
    throw new TriggerException(td, "can only process UPDATE events");

  ResultSet _new = td.getNew();
  String[] args = td.getArguments();
  if(args.length != 1)
    throw new TriggerException(td, "one argument was expected");

  _new.updateTimestamp(args[0], new Timestamp(System.currentTimeMillis()));
}
```
