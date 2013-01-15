##Debugging with dbx##
Copied from [Debugging PL/Java Applications with Solaris Studio dbx](http://my.opera.com/myrkraverk/blog/2010/12/11/debugging-pl-java-with-dbx), Johann 'Myrkraverk's blog on my.opera.

###Setting up the Server###
Debugging PL/Java code requires debugging of the server process itself. This means the debugger must be run as the same (or more privileged) user id as the server itself. That may not be possible in a production environment for access control/security reasons so for the remainder of this text we assume the developer is running his<sup>1</sup> own server (under his own uid) for debugging.

As per the dbx manual, the Java virtual machine must be started with the options -Xdebug -Xnoagent and -Xrundbx_agent. This can be done by having the following line in postgresql.conf.
 pljava.vmoptions = ' -Xdebug -Xnoagent -Xrundbx_agent'
This means the jvm will load libdbx_agent.so whose location must be in the server's runtime load path (LD_LIBRARY_PATH). The Solaris Studio 12.2 manual gives the wrong pathname for the Solaris amd64 binary. It is found under <install dir>/solstudio12.2/lib/dbx/amd64/runtime and can be specified as
```properties
LD_LIBRARY_PATH_64=/opt/myrkraverk/solstudio12.2/lib/dbx/amd64/runtime
```
in the server's environment<sup>2</sup> where Studio is installed in /opt/myrkraverk.

###Setting up the debugger (dbx)###
PL/Java loads classes from the database which dbx does not know about so it must be told where the jar files can be found. This is done with the CLASSPATHX environment variable. Note the appended X. In our case it is
```properties
 CLASSPATHX=/home/johann/src/Java/PLJava/Hello.jar
```
which must be set in the debugger's environment. In addition it must also be told where to find the Java source files. For this we use
```properties
 JAVASRCPATH=/home/johann/src/Java/PLJava
```
as well.

To debug PL/Java itself we need its source path in JAVASRCPATH too.

###Attaching dbx to the Server's Process###
Before we attach to the server we need to make sure that PL/Java has been loaded and that the virtual machine has been created. Otherwise dbx does not know anything about Java. An example.
```properties
 (dbx) stop in com.myrkraverk.Hello.hello
 dbx: "com" is not defined as a function or procedure in the scope `postgres`be-secure.c`secure_read`
```
The best way is to run some simple Java function before we attach the debugger. In a psql session one way is to run the following commands.
```sql
 CREATE FUNCTION getsysprop(VARCHAR)
   RETURNS VARCHAR
   AS 'java.lang.System.getProperty'
   LANGUAGE java;
 SELECT getsysprop('user.home');
```
Now it is just a matter of getting the server's pid
```psql
 johann=# select pg_backend_pid();
  pg_backend_pid 
 ----------------
           10767
 (1 row)
```
and attach dbx.
```sh
 $ dbx - 10767
 Reading postgres
 Reading ld.so.1
 Reading libxslt.so.1
 Output elided.
 Reading libjava.so
 Reading libzip.so
 Attached to process 10767 with 10 LWPs
 t@1 (l@1) stopped in __so_recv at 0xfffffd7fff23d14a
 0xfffffd7fff23d14a: __so_recv+0x000a:	jae      __so_recv+0x16	[ 0xfffffd7fff23d156, .+0xc ]
 Current function is secure_read
   303   		n = recv(port->sock, ptr, len, 0);
 (dbx)
```

###Debugging our Java Code###
Our "hello world" is very simple.
```java
 package com.myrkraverk;
 
 class Hello
 {
     public static int hello()
     {
 	return 17;
     }
 }
```
Assuming we have already compiled (with -g) and jar archived our code<sup>3</sup> we can tell dbx to stop in our method whether we have run _sqlj.install_jar()_ first or not.
```dbx
 (dbx) stop in com.myrkraverk.Hello.hello
 (2) java stop in com.myrkraverk.Hello.hello()
```
And if not, we just detach dbx, re-compile/re-archive and place it where dbx can find it before we attach again.

And of course we have to let the server continue running.
```dbx
 (dbx) cont
```
In our psql session, we can now<sup>4</sup> load our class into the database,
```psql
 johann=# select sqlj.install_jar('file:///home/johann/src/Java/PLJava/Hello.jar','Hello',false);
  install_jar 
 -------------
  
 (1 row)
```
set the classpath
```psql
 johann=# select sqlj.set_classpath( 'johann', 'Hello' );
  set_classpath 
 ---------------
  
 (1 row)
```
and create the sql function.
```psql
 johann=# create function hello() returns int4
   as 'com.myrkraverk.Hello.hello' language java;
 CREATE FUNCTION
```
Now when we run it,
```psql
 johann=# select hello();
```
dbx halts at the breakpoint.
```dbx
 stopped in com.myrkraverk.Hello.hello at line 14 in file "Hello.java"
    14   	return 17;
```

###Final Notes###

It is outside the scope of this tutorial to teach debugging Java applications with dbx. See the Solaris Studio manual for the details.

###Download###

[Download the hello world source code](http://files.myopera.com/myrkraverk/files/pljava/Hello.java) from my.opera. Boost Licensed.

###Footnotes###

* <sup>1</sup> It's been fashionable lately to use the pronoun "her" in these cases. The author firmly believes the pronoun's gender should be chosen as the writer's gender however.
* <sup>2</sup> This means the environment the postgres command is run in.
* <sup>3</sup> And that dbx can find it, as described above.
* <sup>4</sup> Or before, it doesn't matter.

##Debugging with jdb##

PL/Java is debugged like any other Java application using [JPDA](http://docs.oracle.com/javase/6/docs/technotes/guides/jpda/). Here is an example of how to set it up using the PostgreSQL psql utility and the bundled command line debugger jdb (you will probably use your favourite IDE instead but the setup will be similar).

Let's assume we want to debug the SQL function _javatest.testSavepointSanity()_ and that the function is mapped to the java method org.postgresql.pljava.example.SPIActions.testSavepointSanity() (the example is from the examples.jar found in the PL/Java source distribution).

Fire up psql and issue the following commands:
```sql
SET pljava.vmoptions TO '-agentlib:jdwp=transport=dt_socket,server=y,address=8444,suspend=y';
SELECT javatest.testSavepointSanity();
```
Now your application hangs. In the server log you should find a message similar to:
```sh
Listening for transport dt_socket at address: 8444
```
Use another command window and attach your remote debugger:<br/>
```sh
/home/testbench> jdb -connect com.sun.jdi.SocketAttach:port=8444 -sourcepath /home/workspaces/org.postgresql.pljava/src/java/examples
Set uncaught java.lang.Throwable
Set deferred uncaught java.lang.Throwable
Initializing jdb ...
&gt;
VM Started: No frames on the current call stack

main[1]
```
This means that the debugger has attached. Now you can set breakpoints etc.:<br/>
```sh
main[1] stop in org.postgresql.pljava.example.SPIActions.testSavepointSanity
Deferring breakpoint org.postgresql.pljava.example.SPIActions.testSavepointSanity.
It will be set after the class is loaded.
main[1] cont
> Set deferred breakpoint org.postgresql.pljava.example.SPIActions.testSavepointSanity

Breakpoint hit: "thread=main", org.postgresql.pljava.example.SPIActions.testSavepointSanity(), line=78 bci=0
78              Connection conn = DriverManager.getConnection("jdbc:default:connection");

main[1]
```
now it's up to you...