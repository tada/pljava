$packageJar = 'pljava-packaging' |
  Get-ChildItem -Recurse -Filter pljava-pg*.jar

$packageJar = $packageJar.fullName

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

System.setErr(System.out); // PowerShell makes a mess of stderr output

Node.main(new String[0]); // Extract the files (with output to stdout)

String vmopts = "-enableassertions:org.postgresql.pljava... -Xcheck:jni"

Node n1 = Node.get_new_node("TestNode1")

try (
  AutoCloseable t1 = n1.initialized_cluster(p->p.redirectErrorStream(true));
  AutoCloseable t2 = n1.started_server(Map.of(
    "client_min_messages", "info",
    "pljava.vmoptions", vmopts,
    "pljava.libjvm_location",
    get(System.getProperty("java.home"), "bin", "server", "jvm.dll")
      .toString()
  ), p->p.redirectErrorStream(true));
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
