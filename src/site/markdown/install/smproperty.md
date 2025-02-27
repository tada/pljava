# Available policy-enforcement settings by Java version

In the PostgreSQL [configuration variable][variables] `pljava.vmoptions`,
whether and how to set the `java.security.manager` property depends on
the Java version in use (that is, on the version of the Java library that
the `pljava.libjvm_location` configuration variable points to).

There are two ways of setting the `java.security.manager` property that may be
allowed or required depending on the Java version in use.

`-Djava.security.manager=allow`
: PL/Java's familiar operating mode in which
    security policy is enforced. More on that mode can be found in
    [Configuring permissions in PL/Java][policy].

`-Djava.security.manager=disallow`
: A mode required on Java 24 and later, in which there is no enforcement of
    policy. Before setting up PL/Java in this mode, the implications in
    [PL/Java with no policy enforcement][unenforced] should be carefully
    reviewed.

This table lays out the requirements by specific version of Java.

|Java version|Available settings|
|---------|:---|
|9–11|There must be no appearance of `-Djava.security.manager` in `pljava.vmoptions`. Mode will be policy-enforcing.|
|12–17|Either `-Djava.security.manager=allow` or `-Djava.security.manager=disallow` may appear in `pljava.vmoptions`. Default is policy-enforcing (same as `allow`) if neither appears.|
|18–23|One of `-Djava.security.manager=allow` or `-Djava.security.manager=disallow` must appear in `pljava.vmoptions`, or PL/Java will fail to start. There is no default.|
|24–|`-Djava.security.manager=disallow` must appear in `pljava.vmoptions`, or PL/Java will fail to start.|
[Allowed `java.security.manager` settings by Java version]

When `pljava.libjvm_location` points to a Java 17 or earlier JVM, there is
no special VM option needed, and PL/Java will operate with policy enforcement
by default. However, when `pljava.libjvm_location` points to a Java 18 or later
JVM, `pljava.vmoptions` must contain either `-Djava.security.manager=allow` or
`-Djava.security.manager=disallow`, to select operation with or without policy
enforcement, respectively. No setting other than `allow` or `disallow` will
work. Only `disallow` is available for stock Java 24 or later.

The behavior with `allow` (and the default before Java 18) is further described
in [Configuring permissions in PL/Java][policy].

The behavior with `disallow`, the only mode offered for Java 24 and later,
is detailed in [PL/Java with no policy enforcement][unenforced], which
should be carefully reviewed when PL/Java will be used in this mode.

[variables]: ../use/variables.html
[policy]: ../use/policy.html
[unenforced]: ../use/unenforced.html
