# Installation on PostgreSQL releases earlier than 9.2

In PostgreSQL releases 9.2 and later, PL/Java can be installed entirely
without disturbing the `postgresql.conf` file or reloading/restarting the
server: the configuration variables can be set interactively in a session
until PL/Java loads sucessfully, then saved with a simple
`ALTER DATABASE` _dbname_ `SET` _var_ `FROM CURRENT` for each setting
that had to be changed.

Releases earlier than 9.2 are slightly less convenient. It is still possible
to work out the right settings in an interactive session, but once found,
the settings must be added to `postgresql.conf` to be persistent, and the
postmaster signalled (with `pg_ctl reload`) to pick up the new settings.

Releases before 9.2 also require setting `custom_variable_classes` in
`postgresql.conf` to include the prefix `pljava`, and that assignment must
be earlier in the file than any settings of `pljava.*` variables.

## Trying settings interactively

It is still possible to do an exploratory session to find the variable settings
that work before touching `postgresql.conf` at all, but
the details are slightly different.

In later PostgreSQL versions, you would typically use some `SET` commands
followed by a `LOAD` (followed, perhaps, by more `SET` commands unless you
always get things right on the first try).

Before release 9.2, however, the order has to be `LOAD` first, which typically
will lead to an incompletely-started warning because the configuration settings
have not been made yet. _Then_, because the module has been loaded,
`pljava.*` variables will be recognized and can be set and changed until
PL/Java successfully loads, just as in the newer releases.

Once working settings are found, edit `postgresql.conf`, make sure that
`custom_variable_classes` includes `pljava`, copy in the variable settings
that worked, and use `pg_ctl reload` to make the new settings effective.

## But what if I want the load to fail and it doesn't?

The procedure above relies on the way loading stops when the settings are not
right, giving you a chance to adjust them interactively. That turns out to be
a problem if there are previously-saved settings, or the original defaults,
that happen to *work* even if they are not the settings you want. In that case,
the `LOAD` command starts PL/Java right up, leaving you no chance in the
interactive session to change anything.

To escape that behavior, there is one more very simple configuration variable,
`pljava.enable`. If it is `false`, `LOAD`ing PL/Java will always stop early and
allow you to set other variables before setting `pljava.enable` to `true`.

To answer the hen-and-egg question of how to set `pljava.enable` to `false`
before loading the module, it defaults to `false` on PostgreSQL releases
earlier than 9.2, so you will always have the chance to test your settings
interactively (and you will always have to set it explicitly `true` when
you are ready).

If it is already `true` because of an earlier configuration saved in
`postgresql.conf`, it will be recognized in your interactive session and you
can set it `false` as needed.

On later PostgreSQL releases with no such complications, it defaults to `true`
and can be ignored.
