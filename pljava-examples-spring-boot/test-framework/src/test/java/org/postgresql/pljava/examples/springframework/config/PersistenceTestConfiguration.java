package org.postgresql.pljava.examples.springframework.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Test configuration (test profile)
 * <p>
 * Note: this class works with dynamic properties so there
 * is no difference between using a temporary TestContainer
 * or a permanent test server.
 *
 * @see "https://www.baeldung.com/spring-dynamicpropertysource"
 */
@EnableAutoConfiguration
@Configuration
@ComponentScan({
        "org.postgresql.pljava.examples.testcontainers.config",
        "org.postgresql.pljava.examples.testcontainers.security"
})
@Profile("test")
public class PersistenceTestConfiguration {
}
