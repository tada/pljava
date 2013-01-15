PL/Java uses the standard java.util.logging.Logger Hence, you can write things like:
```java
Logger.getAnonymousLogger().info( "Time is " + new Date(System.currentTimeMillis()));
```
At present, the logger is hardwired to a handler that maps the current state of the PostgreSQL configuration setting log_min_messages to a valid Logger level and that outputs all messages using the backend function ereport(). The following mapping apply between the Logger levels and the PostgreSQL backend levels:
<table>
<tr><th>java.util.logging.Level</th><th>PostgreSQL level</th></tr>
<tr><td>SEVERE</td><td>ERROR</td></tr>
<tr><td>WARNING</td><td>WARNING</td></tr>
<tr><td>INFO</td><td>INFO</td></tr>
<tr><td>FINE</td><td>DEBUG1</td></tr>
<tr><td>FINER</td><td>DEBUG2</td></tr>
<tr><td>FINEST</td><td>DEBUG3</td></tr>
</table>