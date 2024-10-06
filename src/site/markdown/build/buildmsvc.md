# Building PL/Java with Microsoft Visual Studio

[edb]: http://www.enterprisedb.com/products-services-training/pgdownload
[msvc]: https://www.visualstudio.com/downloads/download-visual-studio-vs
[java]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[ant]: https://ant.apache.org/bindownload.cgi
[git]: https://git-scm.com/downloads
[ghd]: https://desktop.github.com/
[mvn]: https://maven.apache.org/


PL/Java may be built on Windows using the compilers in Microsoft Visual Studio
(including the Express and Community editions).

Most Windows users will install PostgreSQL using the binary distributions from
[EnterpriseDB][edb]. You may find that using the same version of Visual Studio
to compile PL/Java as that used by EnterpriseDB to compile their PostgreSQL
distribution will result in fewer compile warnings and a somewhat smaller
runtime memory footprint because the same runtime DLLs will be used by both
PostgreSQL and PL/Java. Using a *newer* version of Visual Studio (including
the Community 2015 version) will generally work, while older versions are more
likely to be problematic.

## Software Prerequisites

0. You will need an appropriate version of [Microsoft Visual Studio][msvc]. When
    installing Visual Studio be sure to select the "compiler tools" option so
    that the command line compiler is installed.

0. The [Java Development Kit][java] (not just the Java Runtime Environment)
    version that you plan to use. that you plan to use should be installed, also
    ideally in your PATH environment variable so that

        javac  -version

    just works.

0. The PostgreSQL server version that you intend to use should be installed,
    and on your PATH so that the command

        pg_config

    succeeds.

0. Development files (mostly `.h` files) for that PostgreSQL version must also
    be installed. To check, look in the output of that `pg_config` command for
    an `INCLUDEDIR-SERVER` line, and list the directory it refers to. There
    should be a bunch of `*.h` files there.

0. You will need to install [Maven][mvn] and add it to your PATH so that

        mvn --version

    just works.

0. You will need either [Git][git] or [GitHub for Windows][ghd]. If you are
    using Git, add it to your PATH so that

        git --version

    just works.

You **must** match the 32-bit vs 64-bit version of the Java JVM, C compiler and
PostgreSQL installation  used to build PL/Java. (All must be either 32-bit or
64-bit.)

If you have more than one version installed of PostgreSQL, Java, or the
compile/link tools, make sure the ones found on your search path are the
ones you plan to use, and the version-test commands above give the output
you expect.

## Visual C Configuration

You will need to open a command window with the appropriate Visual C native
tools environment variables defined. You may do this by using the preconfigured
links accessible from the Start menu (for example at
`Visual Studio 2013 | Visual Studio Tools`) or by creating a desktop shortcut
for the tools. 

* Visual Studio 2013:

        "C:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\vcvarsall.bat" x86
        "C:\Program Files (x86)\Microsoft Visual Studio 12.0\VC\vcvarsall.bat" amd64

* Visual Studio 2010:

        "C:\Program Files (x86)\Microsoft Visual Studio 10.0\VC\vcvarsall.bat" x86
        "C:\Program Files (x86)\Microsoft Visual Studio 10.0\VC\vcvarsall.bat" amd64

## Obtaining PL/Java sources

### Sources for a specific release

Obtain source for a specific PL/Java release from the
[Releases page on GitHub][ghrp], archived in your choice of `zip` or `tar.gz`
format.

If you have `git`, you can also obtain specific-release source by cloning
the repository and checking out the tag that identifies the release.

[ghrp]: https://github.com/tada/pljava/releases

### Current development sources

The best way to obtain up-to-date development PL/Java sources is to `clone`
the PL/Java GitHub repository, which can be done with GitHub for Windows by
opening your browser to

    https://github.com/tada/pljava

and clicking on the appropriate icon. At the time these notes were written, the
icon is located to the left of the "Download ZIP" button.

Alternatively you may use [git][] to `clone` the PL/Java GitHub repository,
using either of these commands:

    git clone https://github.com/tada/pljava.git
    git clone ssh://git@github.com/tada/pljava.git

The second only works if you have a GitHub account, but has the advantage
of being faster if you do `git pull` later on to stay in sync with updated
sources.


## Building PL/Java

0. Open a command window using the Visual Studio shortcut for the appropriate
    version.

0. To start the build, your current directory should be the one the sources were
    checked out into. In the
    command window, change the directory to the location of the cloned PL/Java
    repository. For example,

        cd C:\GitHub\pljava

    Looking around, there should be a `pom.xml` file there,
    and several subdirectories `pljava`, `pljava-api`, `pljava-so`, etc.

0. PL/Java is built using [Apache Maven][mvn].

        mvn  clean  install

    This command will build PL/Java and produce the files you need, but
    does not install them as a language in PostgreSQL. To complete that step,
    proceed to the [installation instructions][inst].


A successful `mvn clean install` should produce output like this near the end:

    [INFO] PostgreSQL PL/Java ................................ SUCCESS
    [INFO] PL/Java API ....................................... SUCCESS
    [INFO] PL/Java backend Java code ......................... SUCCESS
    [INFO] PL/Java backend native code ....................... SUCCESS
    [INFO] PL/Java Deploy .................................... SUCCESS
    [INFO] PL/Java Ant tasks ................................. SUCCESS
    [INFO] PL/Java examples .................................. SUCCESS
    [INFO] PL/Java packaging ................................. SUCCESS

(the real output will include timings on each line). You will then be ready
to [try out PL/Java in PostgreSQL][inst].

[inst]: ../install/install.html

### I know PostgreSQL and PGXS. Explain Maven!

If Maven is unfamiliar, please see the "Explain Maven!" section on the
[main build page][mbp], which covers most of the subject. However,
there are some Windows-specific details:

[mbp]: build.html

* The Maven project has an extra page on [Windows prerequisites][wprq].
* They don't very clearly document the location of your Maven settings file
    when running on Windows. If you want to change any Maven settings, it may
    be easiest to run

        mvn -X

    and look for the lines

        [DEBUG] Reading global settings from (somewhere)/settings.xml
        [DEBUG] Reading user settings from (somewhere)/settings.xml

    to find and edit a settings file.

A last reminder, `mvn install` does not add PL/Java as a language in your
PostgreSQL database; the Maven `install` goal only adds things to your
Maven repository. That isn't even necessary for installing the language in
PostgreSQL, but it will be convenient when you
[build your Java projects that _use_ PL/Java][jproj].

To "install" your built PL/Java as a language in PostgreSQL, proceed to
the [installation instructions][inst].

[wprq]: https://maven.apache.org/guides/getting-started/windows-prerequisites.html
[jproj]: ../use/hello.html
[inst]: ../install/install.html

### I know Java and Maven. Explain the PostgreSQL picture!

The process of downloading and building PL/Java with Maven will be familiar
to you, but the step saving artifacts into the local repository with the
`install` goal is only a first step; PostgreSQL itself
is not Maven-aware and will not find them there. After the `mvn clean install`,
just proceed to the [installation instructions][inst].

The `pljava-api` subproject does benefit from being saved in your local
Maven repository; you can then declare it like any other Maven
dependency when [building your own projects that _use_ PL/Java][jproj].

### Troubleshooting the build

There is an extensive "troubleshooting the build" section
on the [main build page][mbp].
