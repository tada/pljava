package org.postgresql.pljava.examples.springframework.security;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Log DatabaseMetaData (Database + Driver)
 * <p>
 * This class provides a user-friendly description of the
 * database and driver properties in a DatabaseMetaData object.
 * It is intended to be easily found when skimming through
 * logs - the same information should also be recorded in
 * a log-analyzer-friendly format.
 * <p>
 * This method should be called the first time the database
 * is used, and then at regular interval. The latter ensures
 * that the information is available despite log rotation.
 * <p>
 * This has been incredibly useful in the past - you would
 * be shocked how often an error is because of a connection
 * to the wrong database!
 * <p>
 * This class has been put in the `security` package since
 * the site may wish to mask some or all of the data, with
 * modified filtering based on the Spring Profile, the location
 * of the server (e.g., 'localhost'), etc.
 */
public class LogDatabaseMetaData {

    private LogDatabaseMetaData() {
    }

    /**
     * Provide information about the database and local client
     * <p>
     * Note: we can also look at /etc/os-release - it's on many (not all)
     * linux * distros. It would allow us to record things like ID ("ubuntu"),
     * VERSION_CODENAME ("ocular") and/or VERSION_ID ("24.10"). This is of
     * secondary concern vs the database server and jdbc driver details but
     * may still be useful.
     * <p>
     * Determining the actual version of the database client package is also
     * possible but much harder due to the number of ways that is captured.
     *
     * @param dbmd
     * @param additionalInfo
     * @return
     * @throws SQLException
     * @throws IOException
     */

    public static String format(DatabaseMetaData dbmd, Map<String, Map<String, String>> additionalInfo) throws SQLException, IOException {
        try (StringWriter sw = new StringWriter();
             PrintWriter pw = new PrintWriter(sw)) {
            final String separator0 = "+================================================================================================+";
            final String separator1 = "+--------------------+--------------------+------------------------------------------------------+";

            final String format = "| %-18.18s : %-18.18s : %-52.52s |\n";

            pw.println(separator0);
            pw.printf(format, "Database Server", "Name", dbmd.getDatabaseProductName());
            pw.printf(format, "", "Version", dbmd.getDatabaseProductVersion());
            pw.printf(format, "", "URL", sanitizeJdbcUrl(dbmd.getURL()));
            try (ResultSet rs = dbmd.getSchemas()) {
                String label = dbmd.getSchemaTerm() + "s";
                while (rs.next()) {
                    // remember 'catalog' is the same as the database name
                    pw.printf(format, "", label, rs.getString("TABLE_SCHEM"));
                    label = "";
                }
            }
            pw.println(separator1);

            pw.printf(format, "Driver", "Name", dbmd.getDriverName());
            pw.printf(format, "", "Version", dbmd.getDriverVersion());
            pw.printf(format, "", "JDBC Version", dbmd.getJDBCMajorVersion() + "." + dbmd.getJDBCMinorVersion());
            pw.println(separator1);

            pw.printf(format, "Client", "User", dbmd.getUserName());
            pw.printf(format, "", "Connection", dbmd.getConnection().getClass().getName());
            pw.println(separator1);

            pw.printf(format, "Client Host", "User", System.getProperty("user.name"));
            // this is the best we can do without a lot of extra effort
            try (Stream<String> lines = Files.lines(new File("/etc/hostname").toPath(), Charset.defaultCharset())) {
                Optional<String> hostname = lines.findFirst();
                hostname.ifPresent(s -> pw.printf(format, "", "Hostname", s));
            } catch (IOException e) {
                // not a problem...
                pw.printf(format, "", "Hostname", "(unknown)");
            }
            pw.printf(format, "", "OS Name", getOsDetails());
            pw.printf(format, "", "OS Kernel", System.getProperty("os.version"));

            if ((additionalInfo != null) && !additionalInfo.isEmpty()) {
                for (Map.Entry<String, Map<String, String>> block : additionalInfo.entrySet()) {
                    if (!block.getValue().isEmpty()) {
                        pw.println(separator1);
                        String label = block.getKey();
                        for (Map.Entry<String, String> entry : block.getValue().entrySet()) {
                            pw.printf(format, label, entry.getKey(), entry.getValue());
                            label = "";
                        }
                    }
                }
                pw.println(separator0);
            }

            pw.flush();
            return sw.toString();
        }
    }

    public static String format(DatabaseMetaData dbmd) throws SQLException, IOException {
        return format(dbmd, Collections.emptyMap());
    }

    /**
     * Sanitize JDBC URL
     * <p>
     * A JDBC URL may have user credentials in both the hostname/password are and as
     * a query fragment. This method strips those values.
     * <p>
     * Note: The URI class doesn't understand subprotocols - for this we
     * However the URI class doesn't understand subprotocols.
     *
     * @param url
     * @return
     */
    static String sanitizeJdbcUrl(String url) {
        if (!url.startsWith("jdbc:")) {
            return "(undetermined)";
        }

        final URI uri = URI.create(url.substring(5)).normalize();

        final StringBuilder sb = new StringBuilder("jdbc:");
        sb.append(uri.getScheme());
        sb.append("://");

        sb.append(uri.getHost());
        if (uri.getPort() > 0) {
            sb.append(":" + uri.getPort());
        }

        if (isNotBlank(uri.getPath())) {
            sb.append(uri.getPath());
        }

        // we *could* add query + fragments but only after ensuring we aren't providing credentials.
        // LOG.info("query: {}", uri.getRawQuery());
        // example: loggerLevel=cat /etc/os-rel;ease
        return sb.toString();
    }

    /**
     * Extract OS details from /etc/os-release (Linux-specific)
     *
     * @return
     */
    static String getOsDetails() {
        try {
            String name = null;
            String id = null;
            String version = null;

            final List<String> lines = Files.readAllLines(new File("/etc/os-release").toPath());
            for (String line : lines) {
                if (line.startsWith("PRETTY_NAME=")) {
                    return line.substring(12).replace("\"", "");
                }

                if (line.startsWith("NAME=")) {
                    name = line.substring(5);
                } else if (line.startsWith("ID=")) {
                    id = line.substring(3);
                } else if (line.startsWith("VERSION=")) {
                    version = line.substring(8);
                }
            }

            StringBuilder sb = new StringBuilder();
            if (isNotBlank(name)) {
                sb.append(name);
            } else if (isNotBlank(id)) {
                sb.append(id);
            } else {
                return System.getProperty("os.name");
            }

            if (isNotBlank(version)) {
                sb.append(" ");
                sb.append(version);
            }

            return sb.toString().replace("\"", "");
        } catch (IOException e) {
            // not a problem - may not be Linux, etc....
        }
        return System.getProperty("os.name");
    }
}

