package org.postgresql.pljava.examples.springframework.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Configuration
@Profile("dev")
public class DataSourceConfig {
    @Value(value = "${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driverClassName}")
    private String driverClassName;

    @Value("${spring.datasource.connectionTestQuery}")
    private String connectionTestQuery;

    @Bean
    public DataSource dataSource() {
        // @formatter:off
		return dataSourceProperties()
			.initializeDataSourceBuilder()
			.type(HikariDataSource.class)
			.build();
		// @formatter:on
    }

    /*
     * Datasource properties
     *
     * @return
     */
    @Bean
    public DataSourceProperties dataSourceProperties() {
        final DataSourceProperties props = new org.springframework.boot.autoconfigure.jdbc.DataSourceProperties();
        props.setUrl(url);
        props.setUsername(username);
        props.setPassword(password);
        if (isNotBlank(driverClassName)) {
            props.setDriverClassName(driverClassName);
        }
        // if (isNotBlank(connectionTestQuery)) {
        //    props.setConnectionTestQuery(connectionTestQuery);
        // }

        return props;
    }
}
