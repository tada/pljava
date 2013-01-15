The [install_jar](SQL Functions#wiki-install_jar), [replace_jar](SQL Functions#wiki-replace_jar), and [remove_jar](SQL Functions#wiki-remove_jar) can act on a _Deployment Descriptor_ allowing SQL commands to be executed after the jar has been installed or prior to removal.

The descriptor is added as a normal text file to your jar file. In the Manifest of the jar there must be an entry that appoints the file as the SQLJ deployment descriptor.
```yaml
Name: deployment/examples.ddr
SQLJDeploymentDescriptor: TRUE
```
The format of the deployment descriptor is stipulated by ISO/IEC 9075-13:2003.
```bnf
<descriptor file> ::=
  SQLActions <left bracket> <rightbracket> <equal sign>
  { [ <double quote> <action group> <double quote>
    [ <comma> <double quote> <action group> <double quote> ] ] }

<action group> ::=
    <install actions>
  | <remove actions>

<install actions> ::=
  BEGIN INSTALL [ <command> <semicolon> ]... END INSTALL

<remove actions> ::=
  BEGIN REMOVE [ <command> <semicolon> ]... END REMOVE

<command> ::=
    <SQL statement>
  | <implementor block>

<SQL statement> ::= <SQL token>...

<implementor block> ::=
  BEGIN <implementor name> <SQL token>... END <implementor name>

<implementor name> ::= <identifier>

<SQL token> ::= ! an SQL lexical unit specified by the term "<token>"
                  in Sub clause 5.2, "<token> and <separator>", in ISO/IEC 9075-2.
```
If implementor blocks are used, PL/Java will consider only those with implementor name PostgreSQL (case insensitive). Here is a sample deployment descriptor:
```java
SQLActions[] = {
  "BEGIN INSTALL
    CREATE FUNCTION javatest.java_getTimestamp()
      RETURNS timestamp
      AS 'org.postgresql.pljava.example.Parameters.getTimestamp'
      LANGUAGE java;
      END INSTALL",
  "BEGIN REMOVE
    DROP FUNCTION javatest.java_getTimestamp();
  END REMOVE"
}
```