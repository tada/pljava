ALTER
USER SESSION_USER SET pljava.module_path = '/usr/share/postgresql/17/pljava/pljava-1.6.9.jar:/usr/share/postgresql/17/pljava/pljava-api-1.6.9.jar';

-- ALTER USER SESSION_USER SET pljava.policy_urls = '%s'", policyFile));

ALTER
USER SESSION_USER SET pljava.vmoptions = '-Djava.security.manager=allow';
ALTER
USER SESSION_USER SET check_function_bodies = off;
CREATE
EXTENSION pljava; -- WITH SCHEMA sqlj VERSION '1.6.9';