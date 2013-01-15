PL/Java makes use of the PostgreSQL custom variable classes extension mechanism in the ''postgresql.conf'' configuration file. PL/Java introduces a custom variable class named "pljava". Here's a sample _postgresql.conf_ entry using all (5) of the variables currently introduced:
```properties
# define "pljava" as a custom variable class. This is a comma separated
# list of names.
#
custom_variable_classes = 'pljava'

# define the class path that the JVM will use when loading the
# initial library. Only meaningful for non GCJ installations
#
pljava.classpath = '/home/Tada/pljava/build/pljava.jar'

# Set the size of the prepared statement MRU cache
#
pljava.statement_cache_size = 10

# If true, lingering savepoints will be released on function exit. If false,
# the will be rolled back
#
pljava.release_lingering_savepoints = true

# Define startup options for the Java VM.
#
pljava.vmoptions = '-Xmx64M'

# Setting debug to true will cause the postgres process to go
# into a sleep(1) loop on its first call to java. This variable is
# only useful if you want to debug the PL/Java internal C code.
#
pljava.debug = false
```