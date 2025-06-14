# Example using Spring Boot + Test Containers

Thees module contains a minimal Spring Boot environment that
uses Test Containers for a temporary PostgreSQL database.

At the moment it provides a `DataSource` bean but does not yet
include explicit support for `JdbcTemplate`, `JPATemplate`,
or `jOOQ`. All should be easy to add.

This module can be used to test PL/Java stored procedures
if the docker image is preconfigured to support PL/Java,
is installed automatically as part of the pljava DDR, or
it is explicitly installed via the FlywayDB initialization.

## Usage

There are three child modules.

### Test-Framework

This module contains a test framework that integrates Spring Boot,
TestContainers, and JUnit 5. It should not need to be modified.

### Backend

This module contains the software that will be pushed to the
database server. It should expand on the current 'examples'
module.

This module is also responsible for generating the test
docker image. At the moment this is still a manual process
but it could be automated using either TestContainers or Jib.

(See `src/main/docker`)

This module should include its own unit tests.

### Application

This module contains a minimal Spring Boot application
that tests the backend functionality.

## Limitations / Enhancements

The proper integration of Spring, Test Containers, and JUnit 5
is a very complex issue since each of them has ideas on how
it should be done.

The current software works but there's ongoing research so
the final version may be different.

## Requirements

This module requires docker. It can be a local service or
a cloud service.

At the moment there is a single test.

## Future Work

- Document how to run this using IDE-provided resources.

## Self-documentation

### TestContainer self-documentation

The `AugmentedPostgreSQLContainer` includes a bit of code to
self-document the container and database once the database is
running. Typical output is

```shell
+================================================================================================+
| Database Server    : Name               : PostgreSQL                                           |
|                    : Version            : 17.3 (Debian 17.3-3.pgdg120+1)                       |
|                    : URL                : jdbc:postgresql://localhost:33039/test               |
|                    : schemas            : information_schema                                   |
|                    :                    : javatest                                             |
|                    :                    : pg_catalog                                           |
|                    :                    : public                                               |
|                    :                    : sqlj                                                 |
+--------------------+--------------------+------------------------------------------------------+
| Driver             : Name               : PostgreSQL JDBC Driver                               |
|                    : Version            : 42.7.7                                               |
|                    : JDBC Version       : 4.2                                                  |
+--------------------+--------------------+------------------------------------------------------+
| Client             : User               : test                                                 |
|                    : Connection         : com.zaxxer.hikari.pool.HikariProxyConnection         |
+--------------------+--------------------+------------------------------------------------------+
| Client Host        : User               : bgiles                                               |
|                    : Hostname           : eris.coyotesong.net                                  |
|                    : OS Name            : Ubuntu 24.04.2 LTS                                   |
|                    : OS Kernel          : 6.8.0-59-generic                                     |
+--------------------+--------------------+------------------------------------------------------+
| Server Extensions  : plpgsql            : 1.0                                                  |
|                    : pljava             : 1.6.9                                                |
+--------------------+--------------------+------------------------------------------------------+
| Container Details  : Image Name         : tada/pljava-examples:17.3-1.6.9-bookworm             |
|                    : Container Name     : /elegant_montalcini                                  |
|                    : FDQN               : dcb097b453fb                                         |
|                    : Container Id       : dcb097b453fb792cc0775b463d5a8efbdc877f7c47e2cc9a7231 |
|                    : Container Image Id : sha256:6154eecafc0a68bcef75e0406e0f0022695fa98c10246 |
|                    : Created On         : 2025-06-18T03:23:23.239721175Z                       |
+--------------------+--------------------+------------------------------------------------------+
| Container Labels   : maintainer         : maintainer: Bear Giles <bgiles@coyotesong.com>       |
|                    : o.o.i.authors      : org.opencontainers.image.authors: bgiles@coyotesong. |
|                    : o.o.i.description  : org.opencontainers.image.description: PostgreSQL wit |
|                    : o.o.i.source       : org.opencontainers.image.source: https://github.com/ |
|                    : o.testcontainers   : org.testcontainers: true                             |
|                    : o.t.lang           : org.testcontainers.lang: java                        |
|                    : o.t.sessionId      : org.testcontainers.sessionId: e77b9355-4722-4c92-b4c |
|                    : o.t.version        : org.testcontainers.version: 1.21.0                   |
+================================================================================================+

```

### LOGGER self-documentation

The TestContainer will add the container's docker image name and container ID to the MDC information
once it's available. This information is then added to all log messages. This information is
removed then the container is shut down. This should make it much easier to associate log
messages with specific docker images/containers.

Typical output follows. In this case the unit test is printing 10 random values provided
from a java static method.

```
"2025-06-17 21:31:07.919 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:50] - Starting HappyPathTest using Java 21.0.7 with PID 529463 (started by bgiles in /home/bgiles/local-src/pljava/pljava-examples-spring-boot/application)
"2025-06-17 21:31:07.920 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:660] - The following 1 profile is active: "test"
"2025-06-17 21:31:09.130 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.core.FlywayExecutor:41] - Database: jdbc:postgresql://localhost:33043/test?loggerLevel=OFF (PostgreSQL 17.3)
"2025-06-17 21:31:09.175 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.c.i.s.JdbcTableSchemaHistory:41] - Schema history table "public"."flyway_schema_history" does not exist yet
"2025-06-17 21:31:09.179 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.c.i.c.DbValidate:41] - Successfully validated 0 migrations (execution time 00:00.015s)
"2025-06-17 21:31:09.181 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] WARN  [o.f.c.i.c.DbValidate:45] - No migrations found. Are your locations set up correctly?
"2025-06-17 21:31:09.200 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.c.i.s.JdbcTableSchemaHistory:41] - Creating Schema History table "public"."flyway_schema_history" ...
"2025-06-17 21:31:09.255 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.c.i.c.DbMigrate:41] - Current version of schema "public": << Empty Schema >>
"2025-06-17 21:31:09.259 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.f.c.i.c.DbMigrate:41] - Schema "public" is up to date. No migration necessary.
"2025-06-17 21:31:09.334 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:56] - Started HappyPathTest in 1.62 seconds (process running for 10.518)
"OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended
WARNING: A Java agent has been loaded dynamically (/home/bgiles/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.19/byte-buddy-agent-1.14.19.jar)
WARNING: If a serviceability tool is in use, please run with -XX:+EnableDynamicAgentLoading to hide this warning
WARNING: If a serviceability tool is not in use, please run with -Djdk.instrument.traceUsage for more information
WARNING: Dynamic loading of agents will be disallowed by default in a future release
2025-06-17 21:31:09.828 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [c.z.h.HikariDataSource:109] - HikariPool-2 - Starting...
"2025-06-17 21:31:09.840 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [c.z.h.pool.HikariPool:576] - HikariPool-2 - Added connection org.postgresql.jdbc.PgConnection@dd07be8
"2025-06-17 21:31:09.841 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [c.z.h.HikariDataSource:122] - HikariPool-2 - Start completed.
"2025-06-17 21:31:10.143 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 1074891001
"2025-06-17 21:31:10.143 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 948213469
"2025-06-17 21:31:10.143 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - -1315899916
"2025-06-17 21:31:10.143 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - -89988107
"2025-06-17 21:31:10.143 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 681378587
"2025-06-17 21:31:10.144 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 235210701
"2025-06-17 21:31:10.144 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - -55815521
"2025-06-17 21:31:10.144 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 1585924748
"2025-06-17 21:31:10.144 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 2122027760
"2025-06-17 21:31:10.144 [tada/pljava-examples:17.3-1.6.9-bookworm - dcc56f2c769f] INFO  [o.p.p.e.s.HappyPathTest:60] - 1785671858
```

(Note: the 'stdout' display is much more colorful!)
