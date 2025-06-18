package org.postgresql.pljava.examples.springframework;

import org.junit.jupiter.api.Test;
import org.postgresql.pljava.examples.springframework.config.PersistenceTestConfiguration;
import org.postgresql.pljava.examples.springframework.containers.AugmentedPostgreSQLContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

//
// see https://java.testcontainers.org/features/configuration/ for remote docker server options
// see https://javadoc.io/doc/org.testcontainers/testcontainers/latest/org/testcontainers/images/RemoteDockerImage.html

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                PersistenceTestConfiguration.class
        }
)
@ContextConfiguration
// @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
// @TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HappyPathTest {
    private static final Logger LOG = LoggerFactory.getLogger(HappyPathTest.class);
    protected static final String LOCAL_IMAGE_NAME = "tada/pljava-examples:17.3-1.6.9-bookworm";

    @Container
    @ServiceConnection
    protected static PostgreSQLContainer<?> postgres = new AugmentedPostgreSQLContainer<>(LOCAL_IMAGE_NAME);

    private final DataSource dataSource;

    @Autowired
    public HappyPathTest(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    public void happyPathTest() throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT javatest.randomInts(10)")) {
                while (rs.next()) {
                    LOG.info(rs.getString(1));
                }
            }
        }
    }
}
