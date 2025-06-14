/*
 * Copyright (c) 2024 Bear Giles <bgiles@coyotesong.com>.
 * All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.postgresql.pljava.examples.springframework.boot.extensions;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.postgresql.pljava.examples.springframework.boot.containers.MySlf4jLogConsumer;
import org.postgresql.pljava.examples.springframework.boot.security.LogDatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * JUnit test extension that provides a PostgreSQL test container
 * <p>
 * This class publishes the required connection properties via
 * system properties and the JUnit ExtensionContext. This allows
 * the embedded server to be treated like any other server when
 * initializing the spring beans.
 * <p>
 * In addition, the embedded server uses FlywayDB to initialize the
 * database with the contents of the `db/migration` directory.
 * The COPY command is not yet supported but TestContainers
 * do have a mechanism that would allow us to copy files to the
 * container prior to running the flyway engine.
 * <p>
 * We have two options(*) when providing a DataSource to other spring
 * beans. We can use a static TestContainer instance so we use the
 * DynamicPropertyRegistry or we can use a JUnit test extension
 * that does not limit us to a single static instance.
 * <p>
 * The former may seem easiest but it appears to require duplicating
 * the code in every test class. At least that's been my experience
 * to date.
 * <p>
 * The latter is a little more complicated - and I definitely don't
 * like it requiring the use of the system properties - but it also
 * provides more flexibility since it's easy to see how it could
 * handle FlywayDB initialization better than the current embedded
 * approach. That would be useful if you want to use different
 * initialization files.
 * <p>
 * (*) There are many other options - but they may rely on the
 * use of a specific version of Spring Boot, etc., so it's risky
 * to assume they'll be available. The JUnit test extension may
 * not be the cleanest approach but it should be widely available.
 *
 * @param <T> PostgreSQLContainer
 * @see "https://www.baeldung.com/spring-dynamicpropertysource"
 * @see "https://junit.org/junit5/docs/current/api/org.junit.jupiter.api/org/junit/jupiter/api/extension/ExtensionContext.html"
 */
public class PostgreSQLContainerExtension<T extends PostgreSQLContainer<T>> extends PostgreSQLContainer<T>
        implements BeforeAllCallback, AfterAllCallback {

    private static final String DEFAULT_DOCKER_IMAGE_NAME = "postgres:16-alpine";

    // @Autowired
    // private Environment env;

    public PostgreSQLContainerExtension() {
        this(DEFAULT_DOCKER_IMAGE_NAME);
    }

    /**
     * Constructor taking explicit docker image name.
     * <p>
     * Note: we must add `.asCompatibleSubstituteFor("postgres")`
     * in order to use arbitrary docker images providing a PostgreSQL
     * database. Otherwise the container will reject most docker images.
     *
     * @param dockerImageName
     */
    public PostgreSQLContainerExtension(String dockerImageName) {
        super(DockerImageName.parse(dockerImageName).asCompatibleSubstituteFor("postgres"));

        // super.withEnv(Map.of("LANG", "en_US.UTF8", "LC_ALL", "en_US.UTF8"));
        // super.withLocalCompose(true);

        // LOG.info("LC_ALL: {}", env.get("ALL"));
        // LOG.info("-----------------------------------");

		// FIXME - do we want to use the container's log?...
        final Logger log = LoggerFactory.getLogger("Container[" + dockerImageName + "]");
        final List<Consumer<OutputFrame>> consumers = new ArrayList<>();
        consumers.add(new MySlf4jLogConsumer<>(log));
        super.setLogConsumers(consumers);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        /*
        org.testcontainers.utility.
        Transferable t = new Transferable();
        super.copyFileToContainer(transferable, "/etc/locale.conf");
         */
        super.start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void runInitScriptIfRequired() {
        // initialize schemas and initialized data via flyway
        try (HikariDataSource dataSource = hikariDataSource()) {
            final Flyway flyway = Flyway.configure().dataSource(dataSource).load();
            flyway.migrate();
        } catch (FlywayException e) {
            // this is typically due to an 'Unsupported Database'
            super.logger().error("FlywayException: " + e.getMessage());
        }

        super.runInitScriptIfRequired();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        try (Connection conn = this.hikariDataSource().getConnection()) {
            super.logger().info("\n" + LogDatabaseMetaData.format(conn.getMetaData()));
        } catch (IOException | SQLException e) {
            final String message = String.format("%s: unable to get connection: %s", e.getClass().getName(), e.getMessage());
            super.logger().error(message, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (!isRunning()) {
            start();
        }

        System.setProperty("spring.datasource.url", getJdbcUrl());
        System.setProperty("spring.datasource.username", getUsername());
        System.setProperty("spring.datasource.password", getPassword());
        if (isNotBlank(getDriverClassName())) {
            System.setProperty("spring.datasource.driverClassName", getDriverClassName());
        }
        if (isNotBlank(getTestQueryString())) {
            System.setProperty("spring.datasource.connectionTestQuery", getTestQueryString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (isRunning()) {
            stop();
        }
    }

    /**
     * Get Hikari DataSource
     * <p>
     * Get Hikari DataSource - this is an improvement over a standard datasource
     * since it's auto-closeable.
     *
     * @return new dataSource
     */
    // @Bean
    public HikariDataSource hikariDataSource() {
        final HikariConfig config = new HikariConfig();
        config.setJdbcUrl(getJdbcUrl());
        config.setUsername(getUsername());
        config.setPassword(getPassword());
        if (isNotBlank(getDriverClassName())) {
            config.setDriverClassName(getDriverClassName());
        }
        if (isNotBlank(getTestQueryString())) {
            config.setConnectionTestQuery(getTestQueryString());
        }

        return new HikariDataSource(config);
    }
}
