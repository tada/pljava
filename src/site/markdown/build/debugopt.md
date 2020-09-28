# Building for debugging or with optimization

Some options can be given on the `mvn` command line to control whether
debugging information is included in the built files, or omitted to save
space at the cost of making use of a debugger less practical. It is also
possible to tailor how aggressively the C compiler will optimize the
native-code portion of PL/Java.

## Debugging information in the Java portion of PL/Java

`-Dmaven.compiler.debug=` with a value of `true` or `false` can be given on
the `mvn` command line. If `true`, debugging information is included so a
runtime debugger (`jdb`, Eclipse, etc.) can see local variables, source lines,
etc. The default is `true`.

## Debugging information in the native portion of PL/Java

`-Dso.debug=` with a value of `true` or `false` on the `mvn` command line
will control whether debugging information is included in PL/Java's native
code shared object. This is most useful when developing PL/Java itself, or,
perhaps, troubleshooting a low-level issue. The default is `false`.

Although it is not required, debugging of PL/Java's native code can be
more comfortable when the PostgreSQL server in use was also configured
and built with `--enable-debug`.

## Compiler optimization in the native portion of PL/Java

PL/Java used to support a `-Dso.optimize` option earlier. However, it is not
yet implemented in the current build system. Following is the description
of how the option worked when it was supported.

`-Dso.optimize=` can be given on the `mvn` command line, with a value
chosen from `none`, `size`, `speed`, `minimal`, `full`, `aggressive`,
`extreme`, or `unsafe`. Depending on the compiler, these settings may
not all be distinct optimization levels. The default is `none`.

Because `none` has long been the default, PL/Java has not seen extensive
testing at higher optimization levels, which should, therefore, be considered
experimental. Before reporting an issue, please make sure it is reproducible
with no optimization.
