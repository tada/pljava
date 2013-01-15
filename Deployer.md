##Deploying using JAVA##
The PL/Java Deployer is a Java client program that helps you deploy PL/Java in the database.
You must use a classpath that can includes the deploy.jar (from the PL/Java distribution) and the postgresql.jar (from the [PostgreSQL JDBC distribution](http://jdbc.postgresql.org/)) when running the deployer. The former contains the code for the deployer command and the second includes the PostgreSQL JDBC driver. You then run thedeployer with the command:

java -cp &lt;your classpath&gt; org.postgresql.pljava.deploy.Deployer [ options ]

It's recommended that create a shell script or a .bat script that does this for you so that you don't have to do this over and over again.
###Deployer options###
<table>
<tr><th>Option</th><td>Description</th></tr>
<tr><td> -install</td><td>Installs the Java™ language along with the sqlj procedures. The deployer will fail if the language is installed already</td></tr>
<tr><td> -reinstall</td><td>Reinstalls the Java™ language and the sqlj procedures. This will effectively drop all jar files that have been loaded</td></tr>
<tr><td> -remove</td><td>Drops the Java™ language and the sqlj procedures and loaded jars</td></tr>
<tr><td> -user <user name></td><td>Name of user that connects to the database. Default is the current user</td></tr>
<tr><td> -password <password></td><td>Password of user that connects to the database. Default is no password</td></tr>
<tr><td> -database <database></td><td>The name of the database to connect to. Default is to use the user name</td></tr>
<tr><td> -host <host name></td><td>Name of the host. Default is "localhost"</td></tr>
<tr><td> -port <port number></td><td>Port number. Default is blank</td></tr>
</table>

##Deploying using SQL##
An alternative to using the deployer is to run the install.sql and uninstall.sql scripts that are included in the distribution using <i>psql</i>.