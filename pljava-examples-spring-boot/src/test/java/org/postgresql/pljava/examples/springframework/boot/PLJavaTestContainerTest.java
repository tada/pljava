package org.postgresql.pljava.examples.springframework.boot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.pljava.examples.springframework.boot.config.PersistenceTestConfiguration;
import org.postgresql.pljava.examples.springframework.boot.extensions.PostgreSQLContainerExtension;
import org.postgresql.pljava.examples.springframework.boot.security.LogDatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                PersistenceTestConfiguration.class
        }
)
@ExtendWith(PostgreSQLContainerExtension.class)
@ContextConfiguration
// @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// @Testcontainers
@ActiveProfiles("test")
public class PLJavaTestContainerTest {
    private static final Logger LOG = LoggerFactory.getLogger(PLJavaTestContainerTest.class);

    @Autowired
    private DataSource dataSource;

    @Test
    public void testHappyPath() throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection()) {
            LOG.info("\n" + LogDatabaseMetaData.format(conn.getMetaData()));
        }
    }
}
