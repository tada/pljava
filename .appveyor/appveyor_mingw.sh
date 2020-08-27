set -e
JAVA_HOME="/c/Program Files/Java/jdk$1"
PATH=$JAVA_HOME/bin:$PATH
javac -version
pacman -S mingw-w64-x86_64-postgresql --noconfirm
pgConfig='C:\msys64\mingw64\bin\pg_config'
"$pgConfig"
cd /c/projects/pljava
mvn clean install \
  -Dpgsql.pgconfig="$pgConfig" \
  -Dnar.cores=1 -Psaxon-examples -Ppgjdbc-ng -Pwnosign --batch-mode \
  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
