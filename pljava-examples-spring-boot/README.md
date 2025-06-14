# Example using Spring Boot + Test Containers

This module contains a minimal Spring Boot environment that
uses Test Containers for a temporary PostgreSQL database.

At the moment it provides a `DataSource` bean but does not yet
include explicit support  for `JdbcTemplate`, `JPATemplate`,
or `jOOQ`. All should be easy to add.

This module can be used to test PL/Java stored procedures
if the docker image is preconfigured to support PL/Java,
is installed automatically as part of the pljava DDR, or
it is explicitly installed via the FlywayDB initialization.

## Limitations / Enhancements

The proper integration of Spring, Test Containers, and JUnit 5
is a very complex issue since each of them has ideas on how
it should be done.

The current software works but there's ongoing research so
the final version may be different.

## Requirements

This module requires docker. It can be a local service or
a cloud service.

## Future Work

- Document how to run this using IDE-provided resources.

- Include local Dockerfile definition?

## Self-Documentation

The test implementation will automatically document
its configuration at launch and when explicitly
requested (e.g., the 'happy path' test).

```
+==============================================================================+
|  Database Server : Name         : PostgreSQL                                 |
|                  : Version      : 16.3                                       |
|                  : URL          : jdbc:postgresql://localhost:32834/test     |
+------------------+--------------+--------------------------------------------+
|  Driver          : Name         : PostgreSQL JDBC Driver                     |
|                  : Version      : 42.6.0                                     |
|                  : JDBC Version : 4.2                                        |
+------------------+--------------+--------------------------------------------+
|  Client          : User         : test                                       |
|                  : Connection   : com.zaxxer.hikari.pool.HikariProxyConnecti |
+------------------+--------------+--------------------------------------------+
|  Client Host     : User         : bgiles                                     |
|                  : Hostname     : eris.coyotesong.net                        |
|                  : OS Name      : Ubuntu 24.04.2 LTS                         |
|                  : OS Kernel    : 6.8.0-59-generic                           |
+==============================================================================+
```

This does not (yet) include the name of the docker image.