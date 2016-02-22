# Building for Microsoft Windows using MinGW-w64

If your PostgreSQL server was built using [MinGW-w64][] (which you can
check by running `pg_config` and looking for `mingw`), then you can build
PL/Java using the same toolchain. PL/Java has been successfully built
on Windows using MinGW-w64 and [MSYS2][], with a 64-bit PostgreSQL build
and a 64-bit Java development kit (JDK).

MSYS2 can be installed using its ["one-click installer"][MSYS2]
(which, by one way of counting, takes closer to six clicks).

*If you are building on Windows* (that is, not using some other platform
to cross-compile *for* Windows), then installation of any other **software
prerequisites** mentioned in the [build instructions][bld] should be
done from within an `MSYS2 Shell`, which can be found under the `MSYS2`
entry on the `Start` menu.

For example, the 64-bit compiler toolchain can be installed with the
command

    pacman -S mingw-w64-x86_64-gcc

Other prerequisites can be installed similarly. You should make sure that
all of the test commands shown for the **software prerequisites** in the
[build instructions][bld] succeed, and the versions they report are
the versions that you intend to use.

## Building PL/Java

The Maven commands for building PL/Java should be run within the
`MinGW-w64` shell, which should be available under the `MSYS2` entry
on the `Start` menu after the toolchain has been installed.

The PL/Java build detects this environment by seeing the environment
variable `MSYSTEM` with the value `MINGW64`.

## 32-bit builds

These may be possible, but have not been tested. If there is a need
to build PL/Java with a 32-bit PostgreSQL and 32-bit JDK, please
[open an issue][ghbug].

## More information

More information on installing `MSYS2` and `MinGW-w64` can be found in
a helpful [StackOverflow answer][soa].

[MinGW-w64]: http://mingw-w64.org/doku.php
[MSYS2]: https://msys2.github.io/
[bld]: build.html
[soa]: http://stackoverflow.com/questions/30069830/how-to-install-mingw-w64-and-msys2/30071634#30071634
[ghbug]: https://github.com/tada/pljava/issues/
