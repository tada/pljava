# Flyway Database Migration Files

This directory contains the database migration files used by Flyway.

There is no standard way to initialize your database but breaking it
down into five distinct steps has proven to have many benefits:

- create schema, additional users, etc.
- create tables with minimal constraints
- add the initial data
- add the table constraints
- add the views (if any)

There can be a significant performance boost when inserting or
updating data when constraints are dropped. This is probably not
an issue when running tests but it's a good habit to develop.

The 'primary key' is a potential exception. You can add a
primary key when you add other table constraints but if you
want to use a 'btree' primary key, e.g., to take advantage
of index-only query lookups, then you take the required steps
before inserting any data. 



