REM a bat file because PowerShell makes a mess of stderr output, and a multiline
REM command intended for CMD in appveyor.yml gets broken up.

IF %SYS%==MINGW (
  set pgConfig=C:\msys64\mingw64\bin\pg_config
) ELSE (
  set pgConfig=%ProgramFiles%\PostgreSQL\%PG%\bin\pg_config
  set libjvm=%JAVA_HOME%/bin/server/jvm.dll
)

IF %SYS%==MINGW (
  C:\msys64\usr\bin\env MSYSTEM=MINGW64 ^
    C:\msys64\usr\bin\bash -l ^
    -c "/c/projects/pljava/.appveyor/appveyor_mingw.sh %JDK%"
) ELSE (
  "%pgConfig%"
  mvn clean install ^
    -Dpgsql.pgconfig="%pgConfig%" ^
    -Dpljava.libjvmdefault="%libjvm%" ^
    -Psaxon-examples -Ppgjdbc-ng --batch-mode ^
    -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn
)
