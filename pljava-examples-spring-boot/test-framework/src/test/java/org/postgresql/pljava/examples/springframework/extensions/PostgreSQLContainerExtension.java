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

package org.postgresql.pljava.examples.springframework.extensions;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.postgresql.pljava.examples.springframework.containers.MySlf4jLogConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.utility.DockerImageName;

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
public class PostgreSQLContainerExtension<T extends PostgreSQLContainer<T>> implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(PostgreSQLContainerExtension.class);

    final String LOCAL_IMAGE_NAME = "tada/pljava:17.3-bookworm-local";

    final DockerImageName TEST_IMAGE_NAME =
            DockerImageName
                    .parse(LOCAL_IMAGE_NAME)
                    .asCompatibleSubstituteFor("postgres");

    public final PostgreSQLContainer<T> container;

    protected PostgreSQLContainer<T> newContainer(Consumer<OutputFrame> logConsumer) {
        /*
        PostgreSQLContainer<T> container = new AugmentedContainer<T>(TEST_IMAGE_NAME, LOG)
                // .withDatabaseName("test")
                // .withUserName("test")
                // .withPassword("password")
                .withLogConsumer(logConsumer)
                .withEnv("POSTGRES_PASSWORD", "password");

        container.start();

        System.setProperty("spring.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.datasource.username", container.getUsername());
        System.setProperty("spring.datasource.password", container.getPassword());
        if (isNotBlank(container.getDriverClassName())) {
            System.setProperty("spring.datasource.driverClassName", container.getDriverClassName());
        }
        if (isNotBlank(container.getTestQueryString())) {
            System.setProperty("spring.datasource.connectionTestQuery", container.getTestQueryString());
        }

        return container;
         */
        return null;
    }

    private final Logger log = LoggerFactory.getLogger("Container[" + LOCAL_IMAGE_NAME + "]");
    private final Consumer<OutputFrame> logConsumer = new MySlf4jLogConsumer<>(log);

    // @Autowired
    // private Environment env;

    /**
     * Default constructor
     */
    public PostgreSQLContainerExtension() {

        // super.withEnv(Map.of("LANG", "en_US.UTF8", "LC_ALL", "en_US.UTF8"));
        // super.withLocalCompose(true);

        // LOG.info("LC_ALL: {}", env.get("ALL"));
        // LOG.info("-----------------------------------");

        /*
        org.testcontainers.utility.
        Transferable t = new Transferable();
        super.copyFileToContainer(transferable, "/etc/locale.conf");
         */
        this.container = newContainer(logConsumer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        System.setProperty("spring.datasource.url", container.getJdbcUrl());
        System.setProperty("spring.datasource.username", container.getUsername());
        System.setProperty("spring.datasource.password", container.getPassword());
        if (isNotBlank(container.getDriverClassName())) {
            System.setProperty("spring.datasource.driverClassName", container.getDriverClassName());
        }
        if (isNotBlank(container.getTestQueryString())) {
            System.setProperty("spring.datasource.connectionTestQuery", container.getTestQueryString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterAll(ExtensionContext context) throws Exception {
        if (container.isRunning()) {
            container.stop();
        }
    }
}
