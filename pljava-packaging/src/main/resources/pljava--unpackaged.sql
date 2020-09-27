\echo 'Use "CREATE EXTENSION pljava VERSION unpackaged" to load this file.'
\echo 'Then start a new connection (use \\c in psql) and'
\echo 'use "ALTER EXTENSION pljava UPDATE" to complete packaging.' \quit

/*
 * PostgreSQL 13 drops support for CREATE EXTENSION ... FROM unpackaged;
 * on the rationale that any sensible site has already updated old unpackaged
 * extensions to their extension versions. For PL/Java, though, there is still
 * a realistic scenario where it ends up installed as 'unpackaged': if a
 * CREATE EXTENSION failed because a setting needed adjustment, the admin
 * supplied the right setting, and the installation then succeeded. That leaves
 * PL/Java installed, but not as a packaged extension. The old CREATE EXTENSION
 * ... FROM unpackaged; syntax was the perfect recovery method for that. It will
 * still work in versions < 13.
 *
 * For PostgreSQL 13, recovery now requires two steps instead. The first step
 * is CREATE EXTENSION pljava VERSION unpackaged; which will use this script to
 * simply confirm the unpackaged installation has already happened, and
 * otherwise do absolutely nothing. The second step (which must happen in a new
 * session) is ALTER EXTENSION pljava UPDATE; which will package it as the
 * latest extension version, even running the exact script that CREATE EXTENSION
 * ... FROM unpackaged; would have run to do it.
 */

SELECT sqlj.get_classpath('public'); -- just fail unless already installed
