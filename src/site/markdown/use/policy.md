# Configuring permissions in PL/Java

This page describes how PL/Java operates when enforcing security policy,
available when using Java 23 or earlier.

When using PL/Java with stock Java 24 or later, please see instead the
[PL/Java without policy enforcement][unenforced] page.

To operate with policy enforcement as described here, no special configuration
is needed on Java 17 and earlier, while on Java 18 through 23, an entry
`-Djava.security.manager=allow` in [`pljava.vmoptions`][confvar] must be present
for PL/Java to start. For just how to configure specific Java versions, see
[Available policy-enforcement settings by Java version][smprop].

## `TRUSTED` (and untrusted) procedural languages

PostgreSQL allows a procedural language to be installed with or without
the designation `TRUSTED`. For a language designated `TRUSTED`, functions
can be created in that language by any user (PostgreSQL role) with `USAGE`
permission on that language, as configured with the SQL commands
`GRANT USAGE ON LANGUAGE ...` and `REVOKE USAGE ON LANGUAGE ...`. For a
language that is _not_ designated `TRUSTED`, only a database superuser
may create functions that use it. No `USAGE` permission can be granted on it.

In either case, once any function has been created, that function may
be executed by any user/role granted `EXECUTE` permission on the function
itself; a language's `USAGE` privilege (or superuser status, if the language
is not `TRUSTED`) is only needed to create a function that uses the language.

Because PL functions execute in the database server, a general-purpose
programming language with no restrictions on access to its containing process
or the server file system may be used for actions or access that PostgreSQL
would normally not permit. A superuser can implement such a function using
a non-`TRUSTED` PL, and design the function to enforce its own limits and
be safe for use by whatever roles will be granted `EXECUTE` permission on it.

A `TRUSTED` PL is expected to enforce appropriate restrictions so that
non-superusers can be allowed to use it to create functions on their own,
while still subject to PostgreSQL's normal protections.

Both kinds have their uses, and many of the available PLs, including PL/Java,
install two similarly-named 'languages' to permit both. Although either can be
renamed, a normal installation of PL/Java will create the language `java` with
the `TRUSTED` property, and `javaU` without it.

*Note: like any SQL identifier, these language names are case-insensitive
when not quoted, and are stored in lowercase in PostgreSQL. The spelling with
capital `U` for untrusted is a common convention.*

### `TRUSTED`/untrusted versus sandboxed/unsandboxed

In various places in PL/Java's API, and in the sections below, the words
'sandboxed' or 'unsandboxed' are used in place of the PostgreSQL `TRUSTED` or
untrusted, respectively. That choice reflects a little trick of language some
readers may notice when new to PostgreSQL: it is about equally easy to read
'trusted'/'untrusted' in two opposite ways. (Is this language trusted because of
how tightly I restrict it? Or do I restrict it less tightly because I trust it?
Is it like a teenager with the car keys?) Old hands at PostgreSQL know which
reading is correct, but because some users of PL/Java may be old hands at Java
and newcomers to PostgreSQL, it seems safer for PL/Java to use terms that
should give the right idea to readers in both groups.

## Permissions available in sandboxed/unsandboxed PL/Java

Most PLs that offer both variants, including PL/Java before 1.6, hardcode
the differences between what a function in each language is allowed to do.
The sandboxed language would apply a fixed set of limitations, such as
forbidding access to the server's file system, and those limits were
not adjustable.

A needed function that would only access one, specific, known-safe file, or
perhaps would need no file access but have to make a network connection to one
known server, might _almost_ be written under those predetermined restrictions,
but that wouldn't count. It would simply have to be created for the unsandboxed
language instead, and written defensively against a much wider range of possible
misuses or mistakes.

Beginning with 1.6, PL/Java takes a more configurable approach. Using the Java
[policy file syntax][pfsyn], any of the permissions known to the JDK can
be granted to chosen Java code. The default policy file installed with PL/Java
includes these lines:

```
grant principal org.postgresql.pljava.PLPrincipal$Sandboxed * {
};

grant principal org.postgresql.pljava.PLPrincipal$Unsandboxed * {

    // Java does not circumvent operating system access controls;
    // this grant will still be limited to what the OS allows a
    // PostgreSQL backend process to do.
    permission java.io.FilePermission
        "<<ALL FILES>>", "read,write,delete,readlink";
};
```

A few observations fall out. Whatever the names may suggest, neither alternative
is truly "unsandboxed". Both are subject to the same Java policy, but can
be granted different permissions within it.

As distributed, the only difference between the two is access to the filesystem.
The "sandboxed" case grants no additional permissions at all, and the
"unsandboxed" case adds read, readlink, write, and delete permission for any
file (still subject to the operating system permissions in effect for the
PostgreSQL server process, which will be enforced independently of Java).

The permissions granted for either case are freely configurable. Granting
the more lenient or dangerous permissions to the "unsandboxed" language is
conventional, and reflected in the way PostgreSQL is more restrictive about
what roles can create functions in that language.

The [permissions known to the JDK][jdkperms] are plentiful and fine-grained.
New permissions can also be defined and required in custom code, and selectively
granted in the policy like any other permission.

The `PLPrincipal` indicating sandboxed/unsandboxed is only one of the conditions
that can be referred to in a policy to control the permissions granted. Others
are described below.

## Sources of Java policy

Java's standard `Policy` implementation will read from a sequence of policy
files specified as URLs. The first is normally part of the Java installation,
supplying permission grants necessary for trouble-free operation of the JVM
itself, and a second will be read, if present, from a user's home directory.

PL/Java, by default, uses the first Java-supplied URL, for the policy file
installed with Java, followed by the file `pljava.policy` in the directory
reported by `pg_config --sysconfdir`. A default version of that file is
installed with PL/Java.

The `pljava.policy` file, by default, is used _instead of_ any `.java.policy`
file in the OS user's home directory that Java would normally load. There
probably is no such file in the `postgres` user's home directory, and if
for any reason there is one, it probably was not put there with PL/Java in mind.

The [configuration variable][confvar] `pljava.policy_urls` can be
used to name different, or additional, policy files.

Permission grants are cumulative in Java's standard `Policy` implementation:
there is no policy syntax to _deny_ a permission if it is conveyed by some other
applicable grant in any of the files on the `policy_urls` list. If an
application must restrict a permission that is granted unconditionally
in the Java-supplied policy file, for example, the typical approach would be
to copy that file, remove the grant of that permission, and alter
`pljava.policy_urls` to read the modified file in place of the original.

## Conditional and unconditional permission grants

A `grant` in a policy can be unconditional, for example:

```
grant {
    permission java.util.PropertyPermission
        "sqlj.defaultconnection", "read";
};
```

That grant (which is included in the default `pljava.policy`) allows any Java
code to read that property.

Conditional grants to `PLPrincipal$Sandboxed` and `PLPrincipal$Unsandboxed` were
shown above.

It is also possible to condition a grant on the codebase (represented as
a URL) of the code being executed. If the `SQLJ.INSTALL_JAR` function is used
to install PL/Java's examples jar under the name `examples`, this grant will
allow the JSR-310 test example to work:

```
grant codebase "sqlj:examples" {
    permission java.util.PropertyPermission "user.timezone", "write";
};
```

The `sqlj` URL scheme is (trivially, and otherwise nonfunctionally) defined
within PL/Java to allow forming a codebase URL from the name of an installed
jar.

### Grant conditions currently unsupported

A reader familiar with Java security policy may consider granting permissions
based on the signer identity of a cryptographically signed jar, or on a
`Principal` representing the PostgreSQL role executing the current function.
In this version of PL/Java, such grants are not yet supported.

While it is not yet possible to grant permissions based on a principal
representing the PostgreSQL session user or role, it is possible for
a superuser, with `ALTER ROLE ... SET`, to set user-specific values of
`pljava.policy_urls` that will load different, or additional, policy files.
While that will only reflect the connected user at the start of the session
and not any role changes during the session, it may be enough for some uses.

### `PLPrincipal` with a language name

The grants for sandboxed/unsandboxed shown above have a `*` wildcard after
the principal class name. It is possible to replace the wildcard with the name
of the language (as used in SQL with `CREATE LANGUAGE` and `CREATE FUNCTION`)
in which a function is declared.

A basic installation of PL/Java creates just two named languages, `java` and
`javaU`, declared as `TRUSTED`/sandboxed and untrusted/unsandboxed,
respectively. In such an installation, these grants would be effectively
equivalent to those shown earlier:

```
grant principal org.postgresql.pljava.PLPrincipal$Sandboxed "java" {
};

grant principal org.postgresql.pljava.PLPrincipal$Unsandboxed "javaU" {
    permission java.io.FilePermission
        "<<ALL FILES>>", "read,readlink,write,delete";
};
```

However, it is possible to use `CREATE LANGUAGE` to create any number of
named languages that share PL/Java's handler entries and can be used to
declare PL/Java functions. For example, suppose `CREATE TRUSTED LANGUAGE` is
used to create another language entry with the name `java_tzset` and this
grant is included in the policy:

```
grant principal org.postgresql.pljava.PLPrincipal$Sandboxed "java_tzset" {
    permission java.util.PropertyPermission "user.timezone", "write";
};
```

If the JSR-310 test example in PL/Java's examples jar is declared with
`LANGUAGE java_tzset` rather than `LANGUAGE java` (as, in fact, it is),
it will be able to set the time zone and succeed.

The [`SQLJ.ALIAS_JAVA_LANGUAGE`][sqljajl] function can be used to create such
aliases conveniently.

When grants to specific named languages and grants with the wildcard are
present, code will have all of the permissions granted to the specific
language by name, in addition to all permissions that appear in grants to the
language class (`PLPrincipal$Sandboxed` or `PLPrincipal$Unsandboxed`, whichever
applies) with a wildcard name.

A grant is silently ignored unless the class and the name both match. If the
`java_tzset` language were declared as above but a grant entry used the right
name but the `PLPrincipal$Unsandboxed` class by mistake, that grant would be
silently ignored.

### Grants to a codebase compared with grants to a principal

Whenever a Java operation requires a permission check, it could be on a call
stack several levels deep, perhaps involving code from more than one codebase
(or, more generally, "protection domain"). The Java rule is that the needed
permission must be in effect, one way or another, for every protection domain
on the call stack at the point where the permission is needed. In other words,
the available permissions are the _intersection_, over all domains on the stack,
of the permissions in effect for each domain. The rationale is that the proposed
action must not only be something the currently executing method is allowed to
do; there is a calling method causing this method to do it, so it must also be
something the caller is allowed to do, and so on up the stack. (For one crucial
exception to this rule, see [handling privileges][dopriv].)

Permissions granted to a `Principal` are not so tightly bound to what specific
code is executing; the same code may execute at different times on behalf of
more than one principal. A principal often represents a user or role for whom
the code is executing, though role principals are not implemented in this
PL/Java release. The sandboxed/unsandboxed function distinction is represented
as a kind of `Principal` because it, too, is a property of the thread of
execution, from its entry at the SQL-declared function entry point and through
any number of protection domains the thread may traverse. Any permissions
granted by principal may be thought of as combined with any codebase-specific
permissions in every domain present on the stack.

### Entry points other than SQL-declared functions

Not every entry into PL/Java is through an SQL-declared function with an
associated language name or sandboxed/unsandboxed property. For those that are
not, permission decisions are based on an "access control context"
(essentially, the in-effect `Principal`s and initial protection domains)
constructed as described here.

#### Set-returning functions

While a set-returning function _is_ declared as an SQL function, the
initial call is followed by repeated calls to the returned iterator or 
ResultSet provider or handle, and a final call to close the provider or handle.
The access control context constructed for the initial call is saved, and reused
while iterating and closing.

#### Savepoint and transaction listeners

Java code may register listeners for callbacks at lifecycle stages of savepoints
or transactions. Each callback will execute in the access control context of
the code that registered it, except that PL/Java's own domain will also be
represented on the stack. Because effective permissions are an intersection
over all domains on the stack, if any permission has been granted to the
callback's codebase that is not also granted to PL/Java's own code, the
callback code will be unable to exercise that permission except within
a [`doPrivileged`][dopriv] block.

#### Mapped UDT `readSQL`/`writeSQL` methods

When a Java user-defined type is defined without fully integrating it into
PostgreSQL's type system as a `BaseUDT`, its `readSQL` and `writeSQL` methods
will not have corresponding SQL function declarations, but will be called
directly as PL/Java converts values between PostgreSQL and Java form. Those
calls will be made without any `PLPrincipal`, sandboxed or unsandboxed, so
they will execute with only the permissions granted to their codebase or
unconditionally.

The conversion functions for a `BaseUDT` do have SQL function declarations, and
will execute in a context constructed based on the declaration in the usual way.

### SQL-declared functions not in PL/Java-managed jars

It is possible to issue an SQL `CREATE FUNCTION` naming a method from a codebase
that is not a PL/Java-managed `sqlj:` jar, such as a jar on the filesystem
module path, or a method of the Java runtime itself. For example, many how-to
articles can be found on the web that demonstrate a successful PL/Java
installation by declaring an SQL function that directly calls
`java.lang.System.getProperty`.

Such declarations are allowed, but will execute as if called from a protection
domain with the same `Principal`s, if any, that PL/Java would normally supply,
and no other permissions but those the policy grants unconditionally.

_Note: many of the how-to articles that can be found on the
web happen to demonstrate their `System.getProperty`-calling example functions
on some property that isn't readable under Java's default policy.
Those examples should be changed to use a property that is normally readable,
such as `java.version` or `org.postgresql.pljava.version`._

### Class static initializers

If a class contains several methods that would be given different
access control contexts (declared with different `trust` or
`language` attributes, say), the permissions available when the class
initializer runs will be those of whichever function is called first
in a given session. Therefore, when putting actions that require
permissions into a class's static initializer, those actions should require
only the common subset of permissions that the initializer could be run with
no matter which function is called or declared first. Actions that require
other specific permissions could be deferred until the first call of
a function known to be granted those permissions.

Such actions can be left in the static initializer if a function granted
the needed permissions is known to always be the first one that the application
will call in any given session.

## Troubleshooting

When in doubt what permissions may need to be granted in `pljava.policy` to run
some existing PL/Java code, these techniques may be helpful.

### Running PL/Java with a 'trial' policy

To simplify the job of finding the permissions needed by some existing code,
it is possible to run PL/Java at first with a 'trial' policy, allowing code to
run while logging permissions that `pljava.policy` has not granted. The log
entries have a condensed format meant to be convenient for this use.
Trial policy configuration is described [here][trial].

### Using policy debug features provided by Java

Java itself offers a number of debugging switches to reveal details of
permission decisions. It may be useful to add `-Djava.security.debug=access` in
the setting of `pljava.vmoptions`, and observe the messages on the PostgreSQL
backend's standard error (which should be included in the log file,
if `logging_collector` is `on`). It is not necessary to change the
`pljava.vmoptions` setting cluster-wide, such as in `postgresql.conf`; it can
be set in a single session for troubleshooting purposes.

Other options for `java.security.debug` can be found in
[Troubleshooting Security][tssec]. Some can be used to filter the logging down
to requests for specific permissions or from a specific codebase.

The log output produced by Java's debug options can be voluminous compared to
the condensed output of PL/Java's trial policy.

## Forward compatibility

The current implementation makes use of the Java classes
`Subject` and `SubjectDomainCombiner` in the `javax.security.auth` package.
That should be regarded as an implementation detail; it may change in a future
release, so relying on it is not recommended.

The developers of Java have elected to phase out important language features
used by PL/Java to enforce policy. The functionality has been removed in
Java 24. For migration planning, this version of PL/Java can still enable
policy enforcement in Java versions up to and including 23, and Java 17 and 21
are positioned as long-term support releases. (There is a likelihood,
increasing with later Java versions, even before policy stops being enforceable,
that some internal privileged operations by Java itself, or other libraries,
will cease to work transparently, and may have to be manually added to a site's
PL/Java policy.)

For details on how PL/Java will adapt, please bookmark
[the JEP 411 topic][jep411] on the PL/Java wiki.


[pfsyn]: https://docs.oracle.com/en/java/javase/14/security/permissions-jdk1.html#GUID-7942E6F8-8AAB-4404-9FE9-E08DD6FFCFFA
[jdkperms]: https://docs.oracle.com/en/java/javase/14/security/permissions-jdk1.html#GUID-1E8E213A-D7F2-49F1-A2F0-EFB3397A8C95
[confvar]: variables.html
[dopriv]: https://docs.oracle.com/en/java/javase/14/security/java-se-platform-security-architecture.html#GUID-E8898CB5-65BB-4D1A-A574-8F7112FC353F
[sqljajl]: ../pljava/apidocs/org.postgresql.pljava.internal/org/postgresql/pljava/management/Commands.html#alias_java_language
[tssec]: https://docs.oracle.com/en/java/javase/14/security/troubleshooting-security.html
[trial]: trial.html
[unenforced]: unenforced.html
[jep411]: https://github.com/tada/pljava/wiki/JEP-411
[smprop]: ../install/smproperty.html
