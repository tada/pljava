# Building PL/Java

**For the impatient:**

    mvn  clean  install

PL/Java is built using [Apache Maven][mvn]. The above command will build it
and produce the files you need, but *not* install them into PostgreSQL.
To do that, continue with the [installation instructions][inst].

[mvn]: https://maven.apache.org/
[orjava]: http://www.oracle.com/technetwork/java/javase/downloads/index.html
[OpenJDK]: https://adoptopenjdk.net/
[hsj9]: https://www.eclipse.org/openj9/oj9_faq.html
[GraalVM]: https://www.graalvm.org/downloads/

**In case of build difficulties:**

There is a "troubleshooting the build" section at the end of this page.

## Software Prerequisites

0. You need the C compiling and linking tools for your platform.
    On many platforms that means `gcc` and `g++`, and your normal search
    path should include them, which you can test with

        g++  --version

    at the command line, which should tell you the version you have installed.

0. The Java Development Kit (not just the Java Runtime Environment)
    version that you plan to use should be installed, also ideally in your
    search path so that

        javac  -version

    just works. PL/Java can be built with [Oracle Java][orjava] or [OpenJDK][],
    the latter with [either the Hotspot or the OpenJ9 JVM][hsj9], or with
    [GraalVM][]. It is not necessary to use the same JDK to build PL/Java that
    will later be used to run it in the database, and PL/Java applications can
    generally take advantage of recent features in whatever Java version is
    used at run time. (See more on [version compatibility](versions.html).)

0. The PostgreSQL server version that you intend to use should be installed,
    and on your search path so that the command

        pg_config

    succeeds.

0. Development files (mostly `.h` files) for that PostgreSQL version must also
    be installed. To check, look in the output of that `pg_config` command for
    an `INCLUDEDIR-SERVER` line, and list the directory it refers to. There
    should be a bunch of `*.h` files there. If not, you probably installed
    PostgreSQL from a packaged distribution, and there is probably another
    package with a similar name but a suffix like `-devel` that needs to be
    installed to provide the `.h` files.

0. Naturally, [Maven][mvn] needs to be installed. When it properly is,

        mvn --version

    succeeds. It reports not only the version of Maven, but the version of Java
    that Maven has found and is using, which must be a Java version supported
    for building PL/Java (see more on [version compatibility](versions.html)).
    If Maven is not finding and using the intended Java version, the environment
    variable `JAVA_HOME` can be set to point to the desired Java installation,
    and `mvn --version` should then confirm that the Java being found is the
    one intended.

If you have more than one version installed of PostgreSQL, Java, or the
compile/link tools, make sure the ones found on your search path are the
ones you plan to use, and the version-test commands above give the output
you expect.

## Special topics

Please review any of the following that apply to your situation:

* [Version compatibility](versions.html)
* Building on [FreeBSD](freebsd.html)
* Building on [Mac OS X](macosx.html)
* Building on [Solaris](solaris.html)
* Building on [Ubuntu](ubuntu.html)
* Building on [Linux `ppc64le`](ppc64le-linux-gpp.html)
* Building on Microsoft Windows: [with Visual Studio](buildmsvc.html)
    | [with MinGW-w64](mingw64.html)
* Building on an EnterpriseDB PostgreSQL distribution that bundles system
    libraries, or other situations where
    [a linker runpath](runpath.html) can help
* Building if you are
    [making a package for a software distribution](package.html)
* Building [with debugging or optimization options](debugopt.html)

[protofail]: versions.html#Maven_failures_when_downloading_dependencies

## Obtaining PL/Java sources

### Sources for a specific release

Obtain source for a specific PL/Java release from the
[Releases page on GitHub][ghrp], archived in your choice of `zip` or `tar.gz`
format.

If you have `git`, you can also obtain specific-release source by cloning
the repository and checking out the tag that identifies the release.

[ghrp]: https://github.com/tada/pljava/releases

### Current development sources

The best way to obtain up-to-date development PL/Java sources is to have
[git][] installed and `clone` the PL/Java GitHub repository, using either of
these commands:

    git clone https://github.com/tada/pljava.git
    git clone ssh://git@github.com/tada/pljava.git

The second only works if you have a GitHub account, but has the advantage
of being faster if you do `git pull` later on to stay in sync with updated
sources.

From a clone, you can also build specific released versions, by first
using `git checkout` with the tag that identifies the release.

Building from unreleased, development sources will be of most interest when
hacking on PL/Java itself. The GitHub "Branches" page can be used to see which
branch has had the most recent development activity (this will not always be
the branch named `master`; periods of development can be focused on the branch
corresponding to current releases).

[git]: https://git-scm.com/

## The build

To start the build, your current directory should be the one the sources were
checked out into. Looking around, there should be a `pom.xml` file there, and
several subdirectories `pljava`, `pljava-api`, `pljava-so`, etc.

A successful `mvn clean install` should produce output like this near the end:

```
[INFO] PostgreSQL PL/Java ................................ SUCCESS
[INFO] PL/Java API ....................................... SUCCESS
[INFO] PL/Java backend Java code ......................... SUCCESS
[INFO] PL/Java backend native code ....................... SUCCESS
[INFO] PL/Java Deploy .................................... SUCCESS
[INFO] PL/Java Ant tasks ................................. SUCCESS
[INFO] PL/Java examples .................................. SUCCESS
[INFO] PL/Java packaging ................................. SUCCESS
```
(the real output will include timings on each line). You will then be ready
to [try out PL/Java in PostgreSQL][inst].

[inst]: ../install/install.html

### PostgreSQL version to build against

If several versions of PostgreSQL are installed on the build host, select
the one to be built for by adding the full path of its `pg_config` executable
with `-Dpgsql.pgconfig=` on the `mvn` command line.

### I know PostgreSQL and PGXS. Explain Maven!

[Maven][mvn] is a widely used tool for building and maintaining projects in
Java. The `pom.xml` file contains the information Maven needs not only for
building the project, but obtaining its dependencies and generating reports
and documentation (including the web site you see here).

If this is your first use of Maven, your first `mvn clean install` command will
do a lot of downloading, obtaining all of PL/Java's dependencies as declared in
its `pom.xml` files, and those dependencies' dependencies, etc. Most of the
dependencies are the various Maven plugins used in the build, and the libraries
they depend on.

Maven will create a local "maven repository" to store what it downloads, so
your later `mvn` commands will complete much faster, with no downloading or
only a few artifacts downloaded if versions have updated.

With default settings, Maven will create this local repository under your home
directory. It will grow to contain artifacts you have built with Maven and all
the artifacts downloaded as dependencies, which can be a large set, especially
if you work on several different Maven-built projects requiring different
versions of the same dependencies. (It may reach 50 MB after building only
PL/Java.) If you would like Maven to create the local repository elsewhere,
the `<localRepository>` element of your [Maven settings][mvnset] can specify
a path.

[mvnset]: https://maven.apache.org/settings.html

It is thinkable to place the repository on storage that is not backed up, as
it contains nothing that cannot be redownloaded or rebuilt from your sources.

#### Why does `mvn clean install` not "install" PL/Java into PostgreSQL?

The Maven goal called `install` has a meaning specific to Maven:
it does not set up your newly-built PL/Java as a
language in PostgreSQL. (Neither does the `deploy` goal, if you are wondering.)

What Maven's `install` does is save the newly-built artifact into the local
repository, so other Maven-built projects can list it as a dependency. That
is useful for the `pljava-api` subproject, so you can then easily
[build your Java projects that _use_ PL/Java][jproj].

To "install" your built PL/Java as a language in PostgreSQL, proceed to
the [installation instructions][inst].

[jproj]: ../use/hello.html
[inst]: ../install/install.html

### I know Java and Maven. Explain the PostgreSQL picture!

The process of downloading and building PL/Java with Maven will be familiar
to you, but the step saving artifacts into the local repository with the
`install` goal is only a first step; PostgreSQL itself
is not Maven-aware and will not find them there. After the
`mvn clean install`, just proceed to the [installation instructions][inst].

The `pljava-api` subproject does benefit from being saved in your local
Maven repository; you can then declare it like any other Maven
dependency when [building your own projects that _use_ PL/Java][jproj].

### Troubleshooting the build

*Note: in addition to this section, there is a [build tips wiki page][btwp],
which may be updated between releases of this document to collect tips for
build issues that are commonly asked about.*

[btwp]: https://github.com/tada/pljava/wiki/Build-tips

#### Capture the output of `mvn -X`

The `-X` option will add a lot of information on the details of Maven's
build activities.

    mvn  -X  clean  install

#### Avoid capturing the first run of Maven

On the first run, Maven will produce a lot of output while downloading all
of the dependencies needed to complete the build. It is better, if the build
fails, to simply run Maven again and capture the output of that run, which
will not include all of the downloading activity.

As an alternative, the flood of messages reflecting successful dependency
downloads in a first run can be suppressed by adding this option on the `mvn`
command line:

```
-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
```
