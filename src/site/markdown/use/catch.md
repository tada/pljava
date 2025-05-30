# Catching PostgreSQL exceptions in Java

When your Java code calls into PostgreSQL to do database operations,
a PostgreSQL error may result. It gets converted into a special subclass
of `SQLException` that (internally to PL/Java) retains all the elements
of the PostgreSQL error report. If your Java code does not catch this exception
and it propagates all the way out of your function, it gets turned back into
the original error report and is handled by PostgreSQL in the usual way.

Your Java code can also catch this exception in any `catch` block that
covers `SQLException`. After catching one, there are two legitimate things
your Java code can do with it:

0. Perform some cleanup as needed and rethrow it, or construct some other,
    more-descriptive or higher-level exception and throw that, so that the
    exception continues to propagate and your code returns exceptionally
    to PostgreSQL.

0. Roll back to a previously-established `Savepoint`, perform any other
    recovery actions needed, and continue processing, without throwing or
    rethrowing anything.

If your code catches a PostgreSQL exception, and continues without rethrowing
it or throwing a new one, and also without rolling back to a prior `Savepoint`,
that is a bug. Without rolling back, the current PostgreSQL transaction is
spoiled and any later calls your Java function tries to make into PostgreSQL
will throw their own exceptions because of that. Historically, such bugs have
been challenging to track down, as you may end up only seeing a later exception
having nothing at all to do with the one that was originally mishandled,
which you never see.

## Tips for debugging mishandled exceptions

Some features arriving in PL/Java 1.6.10 simplify debugging code that catches
but mishandles exceptions.

### More-informative in-failed-transaction exception

First, the exception that results when a call into PostgreSQL fails because of
an earlier mishandled exception has been made more informative. It has an
`SQLState` of `25P02` (PostgreSQL's "in failed SQL transaction" code), and its
`getCause` method actually returns the unrelated earlier exception that was
mishandled (and so, in that sense, really is the original 'cause'). Java code
that catches this exception can use `getStackTrace` to examine its stack
trace, or call `getCause` and examine the stack trace of the earlier exception.
The stack trace of the failed-transaction exception shows the context of the
later call that failed because of the earlier mishandling, and the stack trace
of the 'cause' shows the context of the original mishandled problem.

Note, however, that while your code may mishandle an exception, the next call
into PostgreSQL that is going to fail as a result might not be made from your
code at all. It could, for example, happen in PL/Java's class loader and appear
to your code as an unexplained `ClassNotFoundException`. The failed-transaction
`SQLException` and its cause should often be retrievable from the `cause` chain
of whatever exception you get, but could require following multiple `cause`
links.

### Additional logging

Additionally, there is logging that can assist with debugging when it isn't
practical to add to your Java code or run with a debugger to catch and examine
exceptions.

When your Java function returns to PostgreSQL, normally or exceptionally,
PL/Java checks whether there was any PostgreSQL error raised during your
function's execution but not resolved by rolling back to a savepoint.

If there was, the logging depends on whether your function is returning normally
or exceptionally.

#### If your function has returned normally

If a PostgreSQL error was raised, and was not resolved by rolling back to
a savepoint, and your function is making a normal non-exception return, then,
technically, your function has mishandled that exception. The mishandling may be
more benign (your function made no later attempts to call into PostgreSQL that
failed because of it) or less benign (if one or more later calls did get made
and failed). In either case, an exception stack trace will be logged, but the
log level will differ.

_Note: "More benign" still does not mean "benign". Such code may be the cause
of puzzling PostgreSQL warnings about active snapshots or unclosed resources,
or it may produce no visible symptoms, but it is buggy and should be found and
fixed._

In the more-benign case, it is possible that your code has long been mishandling
that exception without a problem being noticed, and it might not be desirable
for new logging added in PL/Java 1.6.10 to create a lot of new log traffic about
it. Therefore, the stack trace will be logged at `DEBUG1` level. You can use
`SET log_min_messages TO DEBUG1` to see any such stack traces.

In the less-benign case, the mishandling is likely to be causing some problem,
and the stack trace will be logged at `WARNING` level, and so will appear in the
log unless you have configured warnings not to be logged. The first
in-failed-transaction exception is the one whose stack trace will be logged, and
that stack trace will include `Caused by:` and the original mishandled exception
with its own stack trace.

#### If your function has returned exceptionally

If a PostgreSQL error was raised and your function is returning
exceptionally, then there may have been no mishandling at all. The exception
emerging from your function may be the original PostgreSQL exception,
or a higher-level one your code constructed around it. That would be normal,
non-buggy behavior.

It is also possible, though, that your code could have caught a PostgreSQL
exception, mishandled it, and later returned exceptionally on account of some
other, even unrelated, exception. PL/Java has no way to tell the difference,
so it will log the PostgreSQL exception in this case too, but only at `DEBUG2`
level.

PL/Java's already existing pre-1.6.10 practice is to log an exception stack
trace at `DEBUG1` level any time your function returns exceptionally. Simply
by setting `log_level` to `DEBUG1`, then, you can see the stack trace of
whatever exception caused the exceptional return of your function. If that
exception was a direct result of the original PostgreSQL exception or of a later
in-failed-transaction exception, then the `cause` chain in its stack trace
should have all the information you need.

If, on the other hand, the exception causing your function's exceptional return
is unrelated and its `cause` chain does not include that information, then by
bumping the log level to `DEBUG2` you can ensure the mishandled exception's
stack trace also is logged.

### Example

PL/Java's supplied examples include a [`MishandledExceptions`][] class creating
a `mishandle` function that can be used to demonstrate the effects of
mishandling and what is visble at different logging levels.

[`MishandledExceptions`]: ../pljava-examples/apidocs/org/postgresql/pljava/example/annotation/MishandledExceptions.html#method-detail
