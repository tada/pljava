package org.postgresql.pljava.examples.springframework.containers;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.postgresql.pljava.examples.springframework.security.LogDatabaseMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Stack;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

// withLabel()
// withLabels()
// withCopyFileToContainer(MountableFile, path)
// withCopyToContainer(Transferable, path)
// BindMode = READ_ONLY, READ_WRITE
// withClasspathResourceMapping(https://javadoc.io/static/org.testcontainers/testcontainers/1.19.3/org/testcontainers/containers/BindMode.html)

public class AugmentedPostgreSQLContainer<T extends PostgreSQLContainer<T>> extends PostgreSQLContainer<T> {
    private final Stack<MDC.MDCCloseable> mdcStack = new Stack<>();

    protected final Logger log;
    protected final MySlf4jLogConsumer<?> logConsumer;

    public AugmentedPostgreSQLContainer(String dockerImageName) {
        this(dockerImageName, Collections.emptyMap());
    }

    public AugmentedPostgreSQLContainer(String dockerImageName, Map<String, String> classpathResources) {
        super(DockerImageName.parse(dockerImageName).asCompatibleSubstituteFor("postgres"));
        mdcStack.push(MDC.putCloseable("imageName", dockerImageName));
        mdcStack.push(MDC.putCloseable("containerId", "            "));

        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        if (elements.length >= 2) {
            this.log = LoggerFactory.getLogger(elements[2].getClassName());
        } else {
            this.log = LoggerFactory.getLogger(AugmentedPostgreSQLContainer.class);
        }

        this.logConsumer = new MySlf4jLogConsumer<>(this.log);
        this.withLogConsumer(logConsumer);

        // map is resourcePath -> containerPath
        if (!classpathResources.isEmpty()) {
            for (Map.Entry<String, String> entry : classpathResources.entrySet()) {
                log.info("adding {}", entry.getKey());
                this.withClasspathResourceMapping(entry.getKey(), entry.getValue(), BindMode.READ_ONLY);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void containerIsCreated(String containerId) {
        try {
            mdcStack.pop();
            mdcStack.push(MDC.putCloseable("containerId", (containerId.length() < 12) ? containerId : containerId.substring(0, 12)));
        } catch (IllegalArgumentException e) {
            log.warn("{}: {}", e.getClass().getName(), e.getMessage());
        }

        super.containerIsCreated(containerId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void containerIsStarted(InspectContainerResponse containerInfo) {
        super.containerIsStarted(containerInfo);
        logConsumer.setDisableStdout(false);

        log.info("\n" + getDetails(containerInfo));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {
        super.stop();

        // drop MDC information
        while (!mdcStack.isEmpty()) {
            mdcStack.pop().close();
        }
    }

    Map<String, String> getContainerInfoMap(InspectContainerResponse containerInfo) {
        final Map<String, String> info = new LinkedHashMap<>();
        info.put("Image Name", containerInfo.getConfig().getImage());
        info.put("Container Name", containerInfo.getName());
        if (isNotBlank(containerInfo.getConfig().getDomainName())) {
            info.put("FDQN", containerInfo.getConfig().getHostName() + "." + containerInfo.getConfig().getDomainName());
        } else {
            info.put("FDQN", containerInfo.getConfig().getHostName());
        }
        info.put("Container Id", containerInfo.getId());
        info.put("Container Image Id", containerInfo.getImageId());
        info.put("Created On", containerInfo.getCreated());
        // containerInfo.getHostConfig().
        // info.put("conf user", containerInfo.getConfig().getUser());
        // info.put("driver", containerInfo.getDriver());
        // info.put("platform", containerInfo.getPlatform());
        // info.put("processLabel", containerInfo.getProcessLabel());

        return info;
    }

    Map<String, String> getContainerLabelsMap(InspectContainerResponse containerInfo) {
        final Map<String, String> labels = new LinkedHashMap<>();
        if (!containerInfo.getConfig().getLabels().isEmpty()) {
            for (Map.Entry<String, String> entry : containerInfo.getConfig().getLabels().entrySet()) {
                final String[] elements = entry.getKey().split("\\.");
                final StringBuilder sb = new StringBuilder();
                if (elements.length > 1) {
                    for (String element : elements) {
                        sb.append(element.charAt(0));
                        sb.append(".");
                    }
                    if (sb.length() > 2) {
                        sb.setLength(sb.length() - 2);
                    }
                }
                sb.append(elements[elements.length - 1]);

                // works even if there's no match!
                labels.put(sb.toString(), entry.getKey() + ": " + entry.getValue());
            }
        }

        return labels;
    }

    protected String getDetails(InspectContainerResponse containerInfo) {
        final Map<String, Map<String, String>> additionalInformation = new LinkedHashMap<>();

        try (Connection conn = this.hikariDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("select * from pg_catalog.pg_extension")) {
                final Map<String, String> extensions = new LinkedHashMap<>();
                while (rs.next()) {
                    extensions.put(rs.getString("extname"), rs.getString("extversion"));
                }

                additionalInformation.put("Server Extensions", extensions);
                additionalInformation.put("Container Details", getContainerInfoMap(containerInfo));
                final Map<String, String> labels = getContainerLabelsMap(containerInfo);
                if (!labels.isEmpty()) {
                    additionalInformation.put("Container Labels", labels);
                }
            }

            return LogDatabaseMetaData.format(conn.getMetaData(), additionalInformation);
        } catch (SQLException | IOException e) {
            final String message = String.format("%s: unable to get connection: %s", e.getClass().getName(), e.getMessage());                              // .withUsername("test")                new PostgreSQLContainer<>(TEST_IMAGE_NAME) {
            return "";
        }
    }

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
