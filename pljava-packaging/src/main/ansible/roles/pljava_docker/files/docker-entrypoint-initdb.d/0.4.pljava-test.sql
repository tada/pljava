--
-- Verify pl/java installation by collecting information required for submission as prebuilt distribution
--
SET client_min_messages TO INFO;

--
-- Install 'examples' jar
--
SET check_function_bodies TO off;
SELECT sqlj.install_jar('file:///tmp/pljava-examples-1.6.jar', 'ex', true);
SET check_function_bodies TO on;

SELECT sqlj.set_classpath('javatest', 'saxon:saxon_jdom:ex');

--
-- perform simple query
--
SELECT array_agg(javatest.java_getsystemproperty(p))
  FROM (values('org.postgresql.pljava.version'),
              ('org.postgresql.version'),
              ('java.version'),
              ('os.name'),
              ('os.arch')) AS props(p);

--
-- do NOT remove jar - reinstallation is broken
--
--SELECT sqlj.remove_jar('ex', false);
--DROP SCHEMA javatest CASCADE;

SET client_min_messages TO NOTICE;
