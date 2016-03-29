# Building on Solaris

## `mvn` command failing on Solaris 10

If the `mvn` command fails on Solaris 10 with a message
like `mvn: syntax error at line ___: '(' unexpected`, the reason is
that Solaris 10's default `/bin/sh` shell does not understand the `$()`
substitution syntax, which is used in the `mvn` script.

Solaris 10 does supply `bash` and `ksh`, both of which do understand
that syntax. As a workaround, explicitly use `bash` or `ksh` to run the `mvn`
script. That is, if `which mvn` outputs `/path/to/bin/mvn` then change any
command like

    mvn  clean  install

to

    bash /path/to/bin/mvn  clean  install

Solaris 11 [changes the default shells][uefc] to `bash` and `ksh` and so
should not need this workaround.

[uefc]: https://docs.oracle.com/cd/E23824_01/html/E24456/userenv-1.html


## Building with the GNU compiler collection

On Solaris, the NAR Maven Plugin comes with settings for the Solaris Studio
compiler only.

For building with the GNU tools instead, the `pljava-so` project directory
contains an extra settings file `aol.solaris-gcc.properties`. To use it,
add `-Dnar.aolProperties=pljava-so/aol.solaris-gcc.properties` to the `mvn`
command line:

    mvn -Dnar.aolProperties=pljava-so/aol.solaris-gcc.properties clean install
