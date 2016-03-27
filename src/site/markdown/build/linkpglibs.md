# Including `pgtypes`, `pq`, `ecpg` libraries at link time

The PL/Java build process has, for some time, explicitly included these
three PostgreSQL libraries when linking PL/Java. In many cases this is
unnecessary and, in fact, better avoided. Leaving these out at link time
eliminates the chance of certain run-time library version mismatches.

However, there may be some platforms where these libraries must be included
in the link. If you have a failing build, especially if the failure involves
undefined symbol errors, try the build again, adding

    -Plinkpglibs

on the `mvn` command line. If that helps, please report your platform and
configuration so we know which platforms require it.

If it doesn't help, the problem lies somewhere else.

## Library version mismatches when using `-Plinkpglibs`

If you must use this option when building, and you will use PL/Java on a
system where several PostgreSQL versions are installed and one has been marked
as the system default, it is possible to see version-mismatch problems where
PL/Java running in one of the non-default PostgreSQL versions will have found
the libraries from the default version.

That problem and its solutions are described near the end of the
[Building PL/Java with a `RUNPATH`](runpath.html) page.
