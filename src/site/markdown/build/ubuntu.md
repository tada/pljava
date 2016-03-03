# Building on Ubuntu

The [generic \*n\*x build instructions][gnxbi] largely apply, with a few
new points deserving attention.

## Ubuntu packages PostgreSQL pieces separately

In addition to the `postgresql-`*version* and `postgresql-server-dev-`*version*
packages, you may also need to separately install:

* `libecpg-dev`
* `libkrb5-dev`

## Self-extracting jar may fail with some Ubuntu-packaged Java versions

The final product of the build is a jar file meant to be self-extracting
(it contains a short JavaScript snippet that runs `pg_config` to learn where
the extracted files should be put), but there seem to be issues with the
JavaScript engine in some Ubuntu-packaged Java 6 and Java 7 versions.
Java 8 works fine. There is more information in an
[Ubuntu bug report][ubr] and a [StackOverflow thread][sot].

In the worst case, if Java 8 is not an option and one of the affected Java 6
or 7 builds must be used, simply extract the jar file normally and move the
few files it contains into their proper locations.

[gnxbi]: build.html
[ubr]: https://bugs.launchpad.net/ubuntu/+source/openjdk-7/+bug/1553654
[sot]: http://stackoverflow.com/questions/35713768/
