##Installation##
Only a PostgreSQL super user can install PL/Java. The PL/Java utility functions are installed using _SECURITY DEFINER_ so that they execute with the access permissions that where granted to the creator of the functions.
##Trusted language##
PL/Java is now a TRUSTED language. PostgreSQL stipulates that a language marked as trusted has no access to the filesystem and PL/Java enforces this. Any user can create and access functions or triggers in a trusted language. PL/Java also installs a language handler for the language "javaU". This version is not trusted and only a superuser can create new functions that use it. Any user can still call the functions.
##Execution of the deployment descriptor##
The [install_jar](SQL Functions#wiki-install_jar), [replace-jar](SQL Functions#wiki-replace_jar), and [remove_jar](SQL Functions#wiki-remove_jar) utility functions, optionally executes commands found in a [[SQL deployment descriptor]]. Such commands are executed with the permissions of the caller. In other words, although the utility function is declared with _SECURITY DEFINER_, it switches back to the session user during execution of the deployment descriptor commands.
##Classpath manipulation##
The utility function [set_classpath](SQL Functions#wiki-set_classpath) requires that the caller of the function has been granted _CREATE_ permission on the affected schema.