# Building PL/Java

**For the impatient:**

    mvn  clean  package

PL/Java is built using [Apache Maven][mvn]. The above command will build it
and package up the resulting files that you need to then
[install it into PostgreSQL][inst].

[mvn]: https://maven.apache.org/

There are prerequisites, of course:

0. You need the C compiling and linking tools for your platform.
    On many platforms that means `gcc` and `g++`, and your normal search
    path should include them, which you can test with

        g++  --version

    at the command line, which should tell you the version you have installed.

0. The Java Development Kit (not just the Java Runtime Environment) version
    that you plan to use should be installed, also ideally in your search path
    so that

        javac  -version

    just works.

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

0. Naturally, [Maven][mvn] needs to be installed.

If you have more than one version installed of PostgreSQL, Java, or the
compile/link tools, make sure the ones found on your search path are the
ones you plan to use, and the version-test commands above give the output
you expect.

## Special topics

Please review any of the following that apply to your situation:

* [Version compatibility](versions.html)

## Obtaining PL/Java sources

The best way to obtain up-to-date PL/Java sources is to have [git][] installed
and `clone` the PL/Java GitHub repository, using either of these commands:

    git clone https://github.com/tada/pljava.git
    git clone ssh://git@github.com/tada/pljava.git

The second only works if you have a GitHub account, but has the advantage
of being faster if you do `git pull` later on to stay in sync with updated
sources.

[git]: https://git-scm.com/

## The build

To start the build, your current directory should be the one the sources were
checked out into. Looking around, there should be a `pom.xml` file there, and
several subdirectories `pljava`, `pljava-api`, `pljava-so`, etc.

A successful `mvn clean package` should produce output like this near the end:

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

### I know PostgreSQL and PGXS. Explain Maven!

[Maven][mvn] is a widely used tool for building and maintaining projects in
Java. The `pom.xml` file contains the information Maven needs not only for
building the project, but obtaining its dependencies and generating reports
and documentation (including the web site you see here).

If this is your first use of Maven, your first `mvn clean package` command will
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

#### Shouldn't that command be `mvn clean install`?

There is a Maven goal called `install`, but it has a Maven-specific meaning:
it does not, as you might be thinking, set up your newly-built PL/Java as a
language in PostgreSQL. (Neither does the `deploy` goal, if you are wondering.)

What Maven's `install` does is save the newly-built artifact into the local
repository, so other Maven-built projects can list it as a dependency. That
_is_ useful for the `pljava-api` subproject: if you run `mvn clean install`
instead of just `package`, you can then easily
[build your Java projects that _use_ PL/Java][jproj].

To "install" your built PL/Java as a language in PostgreSQL, proceed to
the [installation instructions][inst].

[jproj]: ../use/hello.html
[inst]: ../install/install.html

### I know Java and Maven. Explain the PostgreSQL picture!

The process of downloading and building PL/Java with Maven will be familiar
to you, but the step saving artifacts into the local repository with the
`install` goal is not necessary or useful for most of PL/Java; PostgreSQL itself
is not Maven-aware and will not find them there. Instead, once the `package`
goal has been reached, just proceed to the [installation instructions][inst].

The exception is the `pljava-api` subproject. Installing `pljava-api` into
the local Maven repository will allow you to declare it like any other Maven
dependency when [building your own projects that _use_ PL/Java][jproj].

### Troubleshooting the build

If something fails, two tricks may be helpful. The C compilation may produce
a lot of nuisance warnings, because the Maven plugin driving it enables many
types of warning that would be impractical to fix. With many warnings it may
be difficult to pick out messages that matter.

If the compiler is `gcc`, an extra option `-Pwnosign` can be given on the
`mvn` command line, and will suppress the most voluminous and least useful
warnings. It adds the compiler option `-Wno-sign-conversion` which might not
be understood by other compilers, so may not have the intended effect if the
compiler is not `gcc`.

On a machine with many cores, messages from several compilation threads may be
intermingled in the output so that related messages are hard to identify.
The option `-Dnar.cores=1` will force the messages into a sequential order
(and has little effect on the speed of a PL/Java build).

The `-X` option will add a lot of information on the details of Maven's
build activities.

    mvn  -X  -Pwnosign  -Dnar.cores=1  clean  package
