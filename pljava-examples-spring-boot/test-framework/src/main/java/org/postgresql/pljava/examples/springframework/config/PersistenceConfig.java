package org.postgresql.pljava.examples.springframework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * This class configures the persistence implementation.
 */
@Configuration
@EnableTransactionManagement
public class PersistenceConfig {

    // @Autowired
    // private Environment environment;

    @Autowired
    private DataSource dataSource;

    // ---------------------------------------------------------------------
    // code beyond this point is boilerplate
    // ---------------------------------------------------------------------

    @Bean
    public TransactionAwareDataSourceProxy transactionAwareDataSource() {
        return new TransactionAwareDataSourceProxy(dataSource);
    }

    @Bean
    public DataSourceTransactionManager transactionManager() {
        return new DataSourceTransactionManager(dataSource);
    }

    // ---------------------------------------------------------------------
    // code beyond this point is boilerplate for Spring + jOOQ
    // ---------------------------------------------------------------------

	/*
	@Bean
	public DataSourceConnectionProvider connectionProvider() {
		return new DataSourceConnectionProvider(transactionAwareDataSource());
	}
	 */

	/*
    @Bean
    public ExceptionTranslator exceptionTransformer() {
        return new ExceptionTranslator();
    }
	 */
}
