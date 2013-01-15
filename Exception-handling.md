You can catch and handle an exception in the PostgreSQL back-end just like any other exception. The back-end _ErrorData_ structure is exposed as a property in a class called _org.postgresql.pljava.ServerException_ (derived from _java.sql.SQLException_) and the Java try/catch mechanism is synchronized with the back-end mechanism.

PL/Java will always catch exceptions that you don't. They will cause a PostgreSQL error and the message is logged using the PostgreSQL logging utilities. The stack trace of the exception will also be printed if the PostgreSQL configuration parameter _log_min_messages_ is set to _DEBUG1_ or lower.

###Important Note:###
You will not be able to continue executing back-end functions until your function has returned and the error has been propagated when the back-end has generated an exception unless you have used a save-point. When a save-point is rolled back, the exceptional condition is reset and execution can continue.