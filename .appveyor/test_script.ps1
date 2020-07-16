$packageJar = 'pljava-packaging' |
  Get-ChildItem -Recurse -Filter pljava-pg*.jar
$packageJar = $packageJar.fullName
java -jar $packageJar
$mavenRepo = "$env:UserProfile\.m2\repository"
$saxonVer = Join-Path $mavenRepo "net\sf\saxon\Saxon-HE" |
  Get-ChildItem
$jdbcJar = Join-Path $mavenRepo "com\impossibl\pgjdbc-ng\pgjdbc-ng-all" |
  Get-ChildItem -Recurse -Filter pgjdbc-ng-all-*.jar
$jdbcJar = $jdbcJar.fullName
@'
import static java.nio.file.Paths.get
import java.sql.Connection
import org.postgresql.pljava.packaging.Node
import static org.postgresql.pljava.packaging.Node.qp

String vmopts = "-enableassertions:org.postgresql.pljava... -Xcheck:jni"

Node n1 = Node.get_new_node("TestNode1")

try (
  AutoCloseable t1 = n1.initialized_cluster();
  AutoCloseable t2 = n1.started_server(Map.of(
    "client_min_messages", "info",
    "pljava.vmoptions", vmopts,
    "pljava.libjvm_location",
    get(System.getProperty("java.home"), "bin", "server", "jvm.dll")
      .toString()
  ));
)
{
  try ( Connection c = n1.connect() )
  {
    qp(c, "create extension pljava");
  }

  /*
   * Get a new connection; 'create extension' always sets a near-silent
   * logging level, and PL/Java only checks once at VM start time, so in
   * the same session where 'create extension' was done, logging is
   * somewhat suppressed.
   */
  try ( Connection c = n1.connect() )
  {
    qp(Node.installSaxonAndExamplesAndPath(c,
      System.getProperty("mavenRepo"),
      System.getProperty("saxonVer"),
      true));
  }
}
/exit
'@ |
jshell `
  -execution local `
  "-J--class-path=$packageJar;$jdbcJar" `
  "--class-path=$packageJar" `
  "-J--add-modules=java.sql,java.sql.rowset" `
  "-J-Dcom.impossibl.shadow.io.netty.noUnsafe=true" `
  "-J-DmavenRepo=$mavenRepo" `
  "-J-DsaxonVer=$saxonVer" -
