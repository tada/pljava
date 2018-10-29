# Building on Ubuntu

The [generic \*n\*x build instructions][gnxbi] largely apply, with a few
new points deserving attention.

## Ubuntu packages PostgreSQL pieces separately

In addition to the `postgresql-`*version* and `postgresql-server-dev-`*version*
packages, you may also need to separately install:

* `libecpg-dev`
* `libkrb5-dev`

[gnxbi]: build.html
