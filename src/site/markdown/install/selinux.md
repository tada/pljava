# Using PL/Java with SELinux

These notes were made running PostgreSQL and PL/Java on a Red Hat system,
but should be applicable---with possible changes to details---on other systems
running SELinux.

Anything that gets run by `postgres` itself runs under a special SELinux context
with type `postgresql_t` that severely limits things it can do.  This is
generally good.

## File contexts

SELinux may prevent it from opening important files, such as
`libpljava-[version].so` or `pljava-[version].jar`, depending
on the SELinux contexts set on those files. The contexts can be
listed using `ls -Z`.

The exact SELinux rules being applied, and the file contexts they
would allow, can be different. For example, a `.so` file is considered
executable and triggers one kind of rule, while to SELinux a `.jar` file
is just an ordinary file that Java is trying to open.

Whether a failure is being caused by SELinux can be determined by
searching the system log for `avc: denied` messages. If that is the
problem, it can be solved in more than one way. For example:

* Search the system's existing SELinux policy (using [apol][] or
    [sesearch][], perhaps) to find the rules that apply to what
    the program is trying to do, and what target contexts *would be*
    allowed, then use [chcon][] to set one of those contexts on the
    file in question. For example, on a Red Hat box, changing the
    context type on `libpljava-[version].so` to `lib_t` and on
    `pljava-[version].jar` to `usr_t` did the trick.
* Write and load a new [SELinux policy module][sepolmod] with rules
    that allow the needed operations.

[apol]:     https://raw.githubusercontent.com/wiki/TresysTechnology/setools3/files/images/setools-3.3/apol-rule-search.png
[sesearch]: https://raw.githubusercontent.com/wiki/TresysTechnology/setools3/files/images/setools-3.3/sesearch-help.png
[chcon]:    http://www.gnu.org/software/coreutils/manual/html_node/chcon-invocation.html
[sepolmod]: http://docs.fedoraproject.org/en-US/Fedora/13/html/SELinux_FAQ/index.html#faq-entry-whatare-policy-modules

## Issues requiring policy modules

Something in the JVM uses the `sched_get{param,priority,scheduler}` system
calls, so a process of type `postgresql_t` has to be given the `getsched`
SELinux permission. Also, for SSL connections to work, access to the
`random_device` needs to be allowed. Both of these rules can be added in
a policy module:

```
policy_module(mod-pljava, 1.0)

require {
    type postgresql_t;
    type random_device_t;
}

allow postgresql_t postgresql_t : process { getsched };
allow postgresql_t random_device_t : chr_file { getattr };
```

If that is placed in a file `pljava.te` then

    make -f /usr/share/selinux/devel/Makefile
    semodule -i pljava.pp

will build and install it.

As another example, suppose a PL/Java function needs to make an `ssh`
connection to a remote host. In the default policy, nothing running as
`postgresql_t` is allowed to connect network sockets to remote `ssh` ports.
Again, a policy module can do the trick:

```
policy_module(mod-pgsql-ssh, 1.0)

require {
    type postgresql_t;
    type ssh_port_t;
}

allow postgresql_t ssh_port_t : tcp_socket { name_connect };
```

As it stands, this is quite broad, and would allow connections to the `ssh`
port at _any_ remote address, exactly what the SELinux policy was trying
to prevent. It is reportedly possible (if fiddly) to allow only connecting
to the `ssh` port on the _intended_ remote machine, using the right
[melange of SELinux and iptables][melange].

[melange]: http://serverfault.com/questions/366922/selinux-limit-httpd-outbound-connections-by-address-and-port

### avc: denied { execstack }

At one time, log messages saying `avc: denied { execstack }` might be seen,
because Sun had released a Java distribution with a `libjvm.so` with the
`execstack` attribute missing entirely, which SELinux interprets to mean that
the library _might_ need to execute code on the stack, even if it doesn't
really.

If seen, the problem can be confirmed by running
`execstack -q` ([manual page][execstack]) on the
`libjvm.so` file and seeing that it has no `execstack` attribute.

Recent Java distributions have properly included the attribute, set to false,
so SELinux knows the JVM will not need to execute code on the stack. Therefore,
the best resolution to the issue is to upgrade to a JDK distribution that is
not missing the attribute.

[execstack]: http://man7.org/linux/man-pages/man8/execstack.8.html

## How to break things

One of the best ways to cause a PL/Java installation to stop working, after
being trouble-free for ages, is to replace or update one of the files it
depends on, and forget to reapply the needed SELinux context to the file.
