# Building on FreeBSD

At one time, [FreeBSD][]'s threading library would malfunction if it was
dynamically loaded after the start of a program that did not use threads
itself. That was a problem for PL/Java on FreeBSD, because PostgreSQL
itself does not use threads, but Java does. The only known workaround was
to build PostgreSQL itself from source, with the thread library included
in linking.

The same problem was [reported to affect other PostgreSQL extensions][rep]
such as `plv8` and `imcs` also.

The [manual page for FreeBSD's libthr][manthr] was edited
[in February 2015][thrdif] to remove the statement of that limitation,
and the updated manual page appears first in [FreeBSD 10.2][rel102],
so in FreeBSD 10.2 or later, PL/Java (and other affected extensions)
may work without the need to build PostgreSQL from source.

[FreeBSD]: https://www.freebsd.org/
[rep]: https://lists.freebsd.org/pipermail/freebsd-hackers/2014-April/044961.html
[manthr]: https://www.freebsd.org/cgi/man.cgi?query=libthr&amp;apropos=0&amp;sektion=3&amp;manpath=FreeBSD+10.2-RELEASE&amp;arch=default&amp;format=html
[thrdif]: https://svnweb.freebsd.org/base/head/lib/libthr/libthr.3?r1=272153&amp;r2=278627
[rel102]: https://www.freebsd.org/releases/10.2R/announce.html
