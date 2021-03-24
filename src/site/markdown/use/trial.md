# Migrating to policy-based permissions from an earlier PL/Java release

When migrating existing code from a PL/Java 1.5 or earlier release to 1.6,
it may be necessary to add permission grants in the new `pljava.policy` file,
which grants few permissions by default. PL/Java's security policy configuration
is described [here][policy].

To simplify migration, it is possible to run with a 'trial' policy initially,
allowing code to run but logging permissions that may need to be added in
`pljava.policy`.

## Configuring a trial policy

Even when running with a trial policy, the [configuration variable][vbls]
`pljava.policy_urls` should point to the normal policy file(s), as usual.
That is where the ultimate policy for production will be developed.

The trial policy is configured by creating another policy file somewhere, using
the same policy file syntax, and pointing to it with
`-Dorg.postgresql.pljava.policy.trial=`_url_ added to the configuration variable
`pljava.vmoptions`.

Anything _this_ policy allows will be allowed, but will be logged if the regular
policy would have denied it. So you can make this one more generous than the
regular policy, and use the log entries to identify grants that might belong in
the regular policy. As you add the missing ones to the real policy, they stop
getting logged by this one, and the log gets quieter. You can make this one as
generous as you are comfortable making it during the period of testing and
tuning.

At the very extreme of generosity it could be this:

```
grant {
  permission java.security.AllPermission;
};
```

and it would happily allow the code under test to do _anything at all_, while
logging whatever permissions aren't in the regular policy. (A side effect of
this would be to erase any distinction between `java` and `javaU` for as long as
the trial policy is in place.) Such a setting would be difficult to recommend in
general, but it might suffice if the only code being tested has already been in
use for years under PL/Java 1.5 and is well trusted, users of the database have
not been granted permission to install more PL/Java functions, and if
the purpose of testing is only to learn what permissions the code uses that
may need to be granted in the 1.6 policy.

### Granting `TrialPolicy$Permission`

When `AllPermission` is too broad, there is the difficulty that Java's
permission model does not have a subtractive mode; it is not simple to say
"grant `AllPermission` except for this list of the ones I'd really rather not."
Therefore, PL/Java offers a custom "meta-permission" with roughly that meaning:

```
grant {
  permission org.postgresql.pljava.policy.TrialPolicy$Permission;
};
```

`TrialPolicy$Permission`  is effectively `AllPermission` but excluding any
`FilePermission` (so that `java`/`javaU` distinction stays meaningful) as well
as a couple dozen other various
`SecurityPermission`/`ReflectPermission`/`RuntimePermission` instances in the
"really rather not" category. If its hard-coded exclusion list excludes
any permissions that some unusual code under test might legitimately need,
those can be explicitly added to the trial policy too.

Configuring a trial policy can be a bit of a balancing act: if it is very
generous, that minimizes the chance of breaking the code under test because of
a denied permission, but increases potential exposure if that code misbehaves.
A more limited trial policy decreases exposure but increase the risk of
service interruption if the code under test really does need some permission
that you weren't comfortable putting in the trial policy. Somewhere near
the sweet spot is where `TrialPolicy$Permission` is aimed.

All other normal policy features also work in the trial policy. If your
code is installed in several different jars, you can use `grant codebase`
separately to put different outer limits around different jars, and completely
remove the grants for one jar after another as you are satisfied you have added
the right things for each one in the regular policy. You could also set
different limits for `java` and `javaU` by granting to the `PLPrincipal`,
just as you can in the regular policy.

## About false positives

One thing to be aware of is that the trial policy can give false alarms. It is
not uncommon for software to include configuration-dependent bits that
tentatively try certain actions, catch exceptions, and then proceed normally,
having discovered what the configuration allows. The trial policy can log
permission denials that happen in the course of such checks, even if the denial
has no functional impact on the code.

There may be no perfect way to tell which denials being logged by the trial
policy are false alarms. One approach would be to collect a sampling of log
entries, figure out what user-visible functions of the code they were coming
from, and then start a dedicated session without the
`-Dorg.postgresql.pljava.policy.trial` setting (or with it pointing to a
different, more restrictive version of the policy, not granting the permissions
you're curious about), then exercise those functions of the code and see if
anything breaks. Other users could still have the more generous trial setting in
their sessions, so as not to be affected by your experiments.

False positives, of course, are also affected by the choice of how generous to
make the trial policy. Log entries are only produced for permissions that the
regular policy denies but the trial policy allows. If the permissions being
silently checked by benign code are not granted in the trial policy, they will
be silently denied, just as they would in normal operation, and produce no
log entries.

## Format of the log entries

To avoid bloating logs too much, `TrialPolicy` emits an abbreviated form of
stack trace for each entry. The approach is to keep one stack frame above and
one below each crossing of a module or protection-domain boundary, with `...`
replacing intermediate frames within the same module/domain, and the code
source/principals of the denied domain shown wrapped in `>> <<`at
the appropriate position in the trace. For the purpose of identifying the
source of a permission request and the appropriate domain(s) to be granted
the permission, this is probably more usable than the very long full traces
available with `java.security.debug`.

The messages are sent through the PostgreSQL log if the thread making the
permission check knows it can do so without blocking; otherwise they just go to
standard error, which should wind up in the PostgreSQL log anyway, if
`logging_collector` is on; otherwise it may be system-dependent where they go.

There isn't really a reliable "can I do so without blocking?" check for every
setting of the `pljava.java_thread_pg_entry` configuration variable.
If it is set to `throw` (and that is a workable setting for the code under
test), the logging behavior will be more predictable; entries from the main
thread will go through PostgreSQL's log facility always, and those from any
other thread will go to standard error.

Here is an example of two log entries, generated by the same permission check:

```
POLICY DENIES/TRIAL POLICY ALLOWS: ("java.net.SocketPermission" "127.0.0.1:5432" "connect,resolve")
java.base/java.security.ProtectionDomain.implies(ProtectionDomain.java:321)
...
java.base/java.net.Socket.<init>(Socket.java:294)
>> null [PLPrincipal.Sandboxed: java] <<
jdk.translet/die.verwandlung.GregorSamsa.template$dot$0()
...
jdk.translet/die.verwandlung.GregorSamsa.transform()
java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet.transform(AbstractTranslet.java:624)
...
java.xml/com.sun.org.apache.xalan.internal.xsltc.trax.TransformerImpl.transform(TransformerImpl.java:383)
schema:public//org.postgresql.pljava.example.annotation.PassXML.transformXML(PassXML.java:561)

POLICY DENIES/TRIAL POLICY ALLOWS: ("java.net.SocketPermission" "127.0.0.1:5432" "connect,resolve")
java.base/java.security.ProtectionDomain.implies(ProtectionDomain.java:321)
...
java.base/java.net.Socket.<init>(Socket.java:294)
jdk.translet/die.verwandlung.GregorSamsa.template$dot$0()
...
jdk.translet/die.verwandlung.GregorSamsa.transform()
java.xml/com.sun.org.apache.xalan.internal.xsltc.runtime.AbstractTranslet.transform(AbstractTranslet.java:624)
...
java.xml/com.sun.org.apache.xalan.internal.xsltc.trax.TransformerImpl.transform(TransformerImpl.java:383)
>> sqlj:examples [PLPrincipal.Sandboxed: java] <<
schema:public//org.postgresql.pljava.example.annotation.PassXML.transformXML(PassXML.java:561)
```

The example shows the use of an XSLT 1.0 transform that appears to
make use of the Java XSLT ability to call out to arbitrary Java, and is trying
to make a network connection back to PostgreSQL on `localhost`. Java's XSLTC
implementation compiles the transform to a class in `jdk.translet` with null
as its codebase, and the first log entry shows permission is denied at that
level (the protection domain shown as
`>> null [PLPrincipal.Sandboxed: java] <<`).

A second log entry results because `TrialPolicy` turns the first failure to
success, allowing the permission check to continue, and it next fails at
the PL/Java function being called, in the `sqlj:examples` jar. Under the trial
policy, that also is logged and then allowed to succeed.

The simplest way to allow this connection in the production policy would be
to grant the needed `java.net.SocketPermission` to `PLPrincipal$Sandboxed`,
as that is present in both denied domains. It would be possible to grant
the permission by codebase to `sqlj:examples` instead, but not to
the nameless codebase of the compiled XSLT transform.

[policy]: policy.html
[vbls]: variables.html
