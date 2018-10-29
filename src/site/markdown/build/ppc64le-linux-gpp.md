# Building on Linux ppc64le with GNU tools

Until the `nar-maven-plugin` upstream has architecture-os-linker entries
for `ppc64le.Linux.gpp`, the `pljava-so` project directory
contains an extra settings file `aol.ppc64le-linux-gpp.properties`. To use it,
add `-Dnar.aolProperties=pljava-so/aol.ppc64le-linux-gpp.properties`
to the `mvn` command line:

    mvn -Dnar.aolProperties=pljava-so/aol.ppc64le-linux-gpp.properties clean install
