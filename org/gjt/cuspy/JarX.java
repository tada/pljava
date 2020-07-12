package org.gjt.cuspy;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.net.URL;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * Distribute your work as a self-extracting jar file by including one file,
 * JarX.class, that also safely converts text files to the receiver's encoding
 * and newline conventions, and adds less than 7 kB to your jar.
 *<P>
 * A self-extracting file is handy if your recipient might have a
 * Java runtime environment but not the jar tool.
 * The text conversion offered by JarX is useful if your distribution will
 * include text files, source, documentation, scripts, etc., and your recipients
 * have platforms with different newline conventions.
 *<H2>Text conversion background</H2>
 * There are two issues in the cross-platform delivery of text files.
 *<OL><LI>Different platforms indicate the end of a line differently.
 * The UNIX convention uses the single character LINE FEED; the (old) Macintosh
 * used only the CARRIAGE RETURN character, and DOS/Windows systems require
 * every line to end with a CARRIAGE RETURN followed by a LINE FEED.
 * If some conversion isn't done, a Windows file appears to have garbage
 * characters at the ends of lines if moved to UNIX, or the beginnings of lines
 * if moved to a Mac; UNIX and Mac files moved to Windows, or Mac files moved
 * to UNIX, appear to be squished into one insanely long line.
 * These effects can complicate viewing and editing the files, and interfere
 * with automated processes like diff or version control.
 *<LI>Different platforms may use different default character encodings.
 * Ideally, text files within a jar should be extracted into the local encoding.
 *</OL><P>
 * It's important to apply such transformations <EM>only</EM> to the files
 * within the archive that are actually <EM>known</EM> to contain text.
 * Passing binary data or class files through character and newline
 * transformations will corrupt them.
 *<H3>The ZIP approach and why it loses</H3>
 * The popular zip format on which jar is based already has a provision for
 * newline (but not character set) conversion. Each entry includes a text/binary
 * bit, and the unzip program applies newline conversion while extracting, but
 * only to the files flagged as text.
 *<P>
 * One problem, though not the fatal one, with this scheme is that there is no
 * single convention for newlines inside the zip file.  Instead, files are
 * stored just as they are found on the source system, and a code indicating the
 * source operating system is stored in the archive.  The receiving unzip
 * program must interpret the code and know what newline convention that
 * operating system used, in order to convert newlines appropriately.
 *<P>
 * The fatal flaw, however, has to do with the way the text/binary
 * bit gets set in the first place.  While building the archive, the common zip
 * programs look at statistical properties of byte frequencies in the input,
 * and set the text bit for any entry that looks like it might be text!  If a
 * binary file happens to contain an unlucky sequence of bytes, it will be
 * flagged as text and then silently corrupted by any unzip program that honors
 * the text bit.  That can happen, and has happened, to class files in zip
 * archives if the recipient uses unzip -a, and causes significant misery if
 * the package is widely distributed.
 *<H3>A better way</H3>
 * Even though the jar format is based on zip, it would be a mistake to make jar
 * tools that rely on the zip text/binary bit, because common
 * practice has made that bit unreliable.  What's needed is a standard way for
 * the developer to explicitly indicate the processing needed for each entry
 * in the jar.  Also, a single representation should be adopted for newlines
 * in text files inside a jar, so an extracting program only needs to convert
 * from that representation to the local one, and does not need to concern
 * itself with details of the system where the jar was created.
 *<P>
 * As of JDK 1.3, Sun has extended the
 *<A
 HREF="http://java.sun.com/products/jdk/1.3/docs/guide/jar/jar.html#Per-Entry%20Attributes">
 *Jar File Specification</A> to allow a <CODE>Content-Type</CODE> in the
 * Manifest for each jar entry.  The value of <CODE>Content-Type</CODE> is a
 *<A HREF="http://www.isi.edu/in-notes/iana/assignments/media-types/media-types">MIME
 * type</A>, and with this a developer can specify exactly which entries in a
 * jar should be treated as text.  The question of a standard representation
 * for newlines inside the jar is settled, because
 * <A HREF="ftp://ftp.isi.edu/in-notes/rfc2046.txt">[RFC2046 section 4.1.1]</A>
 * establishes a canonical line break representation for all subtypes of the
 * <CODE>text</CODE> MIME type.  Therefore, correct translation of line breaks from any
 * platform to any platform can be achieved if a jar-building program just
 * converts from its local convention to the canonical CRLF form, and a jar
 * extraction program just converts the canonical to its own local form. Neither
 * program needs to know anything about the other environment.
 * Finally, the <CODE>charset</CODE> parameter of the <CODE>text</CODE> type
 * allows explicit specification of the character encoding used in a jar entry,
 * and the extracting program can automatically convert into the encoding used
 * on the local system. (But see <STRONG>Call to action</STRONG> below.)
 *<H2>What JarX Does</H2>
 * <CODE>Content-Type</CODE> entries in a Manifest were introduced in Java 1.3
 * but are compatible with earlier jar specifications; a jar file containing
 * such entries can be processed without any trouble by any jar tool compliant
 * with the old or new standard.  However, there is not yet a full jar tool
 * available that will honor the content types and do automatic transformation
 * of text entries.  To fill the need until that functionality is added to the
 * widely-available jar tools, JarX is available now.
 *<P>
 * JarX.Build produces a jar, working from a manifest file prepared by the
 * developer.  Entries with any <CODE>text</CODE> type will be translated from
 * the local encoding into the specified <CODE>charset</CODE> if given, and
 * entries with the specific type <CODE>text/plain</CODE> will have their line
 * endings converted to the CRLF canonical form.  Line endings are left alone
 * for all other subtypes of <CODE>text</CODE>, but this decision is open to
 * comment.
 *<P>
 * The file produced by JarX.Build is a fully compliant jar and can be unpacked
 * by any jar or unzip tool, but current tools will not automatically convert
 * the text files to the local conventions.  By including the single class file
 * <CODE>JarX.class</CODE> in the jar, a developer produces a self-extracting
 * archive that can be executed to unpack itself on any Java 1.6 or later
 * virtual machine, performing all automatic conversions and requiring no jar
 * tool at all.
 *<H2>Building a Jar</H2>
 * To build a jar file, first prepare the manifest, using any text editor or,
 * more likely, a script.  Include a <CODE>Name:</CODE> entry for every file
 * to be included in the jar.  JarX.Build archives only the files named in
 * the manifest.  Be sure to include <CODE>Manifest-Version: 1.0</CODE> as
 * the first line of the manifest; JarX.Build does not do it for you.  To make
 * the jar self-extracting, make the next line<BR>
 * <CODE>Main-Class: org.gjt.cuspy.JarX</CODE><BR> and be sure to include a
 * <CODE>Name:</CODE> entry for <CODE>org/gjt/cuspy/JarX.class</CODE>.
 *<P>
 * Add an appropriate <CODE>Content-Type:</CODE> line after the
 * <CODE>Name:</CODE> line for every entry that needs one.  JarX itself only
 * distinguishes the <CODE>text</CODE> types from nontext (everything else),
 * and treats a missing <CODE>Content-Type:</CODE> as nontext, so for purposes
 * of JarX you only need to add content types for text files.  For other
 * purposes you may wish to include the types of other entries as well.
 * In the simplest case, just omit content types for your non-text files,
 * and add <CODE>Content-Type: text/plain; charset=UTF-8</CODE> for files that
 * you want auto-converted.  Then give the command<BR>
 * <CODE>java org.gjt.cuspy.JarX$Build foo.jar manifest</CODE><BR> if
 * <CODE>manifest</CODE> is the name of your prepared manifest file and
 * <CODE>foo.jar</CODE> names the jar you want to create.
 * The order of files in the jar will be the order of their names in the
 * manifest.
 *<H3>Special manifest attributes</H3>
 * For 2016, JarX now recognizes some special manifest attributes:
 * <DL>
 *  <DT>_JarX_CharsetInArchive</DT>
 *  <DD>As a per-entry attribute, identifies the character set of the associated
 *   text member as stored in the archive. This is entirely equivalent to the
 *   earlier method using {@code ;charset=} on the Content-Type attribute,
 *   which JarX still supports, but has not been widely adopted. As a main
 *   attribute, sets a default for any text members without a per-entry value.
 *  </DD>
 *  <DT>_JarX_CharsetWhenUnpacked</DT>
 *  <DD>As a per-entry attribute, identifies the character set of the associated
 *   text member when not in the archive. At Build time, the member will be
 *   transcoded from this charset (instead of the platform's default) to the
 *   specified InArchive charset, and, on extraction, will be transcoded back
 *   to this charset regardless of the platform's default encoding. This
 *   attribute can be used for files conforming to specifications that define
 *   a fixed encoding. In other cases, omitting this attribute allows the
 *   member to be extracted into the receiving platform's default charset.
 *   As a main attribute, sets a default for text members without a per-entry
 *   value.</DD>
 *  <DT>_JarX_Permissions</DT>
 *  <DD>As a per-entry attribute, declares permissions to apply to the
 *   extracted file. (At present, not applied to directories.) Only the
 *   Java SE 6 {@link java.io.File} permissions are supported, a small subset
 *   of what most platforms support. A comma-separated list of
 *   <em>usage</em>{@code =}<em>bywhom</em>, where <em>usage</em> can be
 *   {@code read}, {@code write}, or {@code execute} and <em>bywhom</em> can be
 *   {@code none}, {@code owner}, or {@code all}. As a main attribute, sets a
 *   default for members without a per-entry attribute. For any <em>usage</em>
 *   that is left unspecified, no {@link java.io.File File} method will be
 *   called to change that permission, so the system's defaults will apply.
 *  </DD>
 *  <DT>_JarX_PathResolver</DT>
 *  <DD>Only recognized as a main attribute, this specifies a script that JarX
 *   will invoke for every archive member, with the following bindings in scope:
 *    <DL>
 *     <DT>properties</DT><DD>The Java system Properties object.</DD>
 *     <DT>storedPath</DT><DD>The full pathname of the member, exactly as
 *      stored in the archive.</DD>
 *     <DT>platformPath</DT><DD>The full pathname after only replacing the
 *      {@code /} separator character with the platform's {@code file.separator}
 *      if different.
 *     </DD>
 *     <DT>computedPath</DT><DD>Initially the same as {@code platformPath}.
 *      If the script stores a new value in {@code computedPath}, the member
 *      will be extracted to that full path.</DD>
 *    </DL>
 *   The script is given as the value of this attribute, using the same
 *   RFC822-ish lexical conventions the jar spec says it was "inspired by".
 *   The value must begin with a MIME type (two atoms separated by a slash,
 *   as in {@code application/javascript}, followed by at least one
 *   {@code QUOTEDSTRING}. RFC822 uses the double-quote for this purpose, and
 *   backslash to escape it when needed, which also means you must double any
 *   backslash intended for the script. Additional {@code QUOTEDSTRING}s simply
 *   append to the script. The RFC822 line-continuation rule can be exploited
 *   by supplying the script as multiple quoted strings, one per line, each
 *   indented by a space. The strings are appended with nothing in between
 *   (so, the continuation newlines do not become newlines in the script), but
 *   a {@code /} can appear between any two quoted strings to insert an
 *   explicit newline in the script. In addition to whatever comment syntax is
 *   allowed in the scripting language, RFC822 comments (marked by parentheses,
 *   and nestable) are allowed outside of the quoted strings.
 *  </DD>
 * </DL>
 *<H3>Alternative to {@code ScriptEngine} for a path resolver</H3>
 * With the removal of Nashorn in Java 15, leaving no scripting language that
 * can be assumed present in the Java runtime, a script in the manifest may
 * no longer be the simplest way to customize the resolution of path names when
 * extracting. This class has been refactored now to expose two methods,
 * {@link #prepareResolver(String) prepareResolver} and
 * {@link #resolve(String,String) resolve}, easily overridden in a subclass.
 * The value of the {@code _JarX_PathResolver} main attribute is passed to
 * {@code prepareResolver} as a string (so it can be parsed in any way useful to
 * the subclass, not necessarily as described above, or ignored), and
 * {@code resolve} is passed the stored path and platform path, and returns the
 * platform path unchanged or a replacement. A self-extracting jar with
 * resolution can be made without depending on any script engine, by placing
 * <em>two</em> classes in the jar, JarX and the subclass, and naming the
 * subclass as the jar's {@code Main-Class}. It needs a {@code main} method that
 * simply instantiates the class and calls {@code extract()}.
 *<H2>Extracting a jar</H2>
 * The command <CODE>java -jar foo.jar</CODE> is all it takes
 * to extract a jar.  The <CODE>Main-Class</CODE> entry in the manifest
 * identifies the entry point of JarX so it does not need to be specified.
 *<H2>Call to action</H2>
 * At the moment, Sun's Jar File Specification contains a mistake in the
 * description of a content type that could lead to implementations
 * that reject valid content types.  Squash this bug before it bites:
 * log on to the
 *<A HREF="http://developer.java.sun.com/developer/">Java Developer
 * Connection</A> (it's free) and cast one, two, or all three of your Bug Votes
 * for
 *<A HREF="http://developer.java.sun.com/developer/bugParade/bugs/4310708.html">
 *Bug #4310708</A>.
 *<H2>Miscellany</H2>
 * This class is a little sloppy and relatively slow, especially the Build side
 * when converting plain text files.  The idea for JarX is a natural outgrowth
 * of the Java 1.3 manifest standard and I have suggested that the functionality
 * of JarX be added into the widely available jar tools.  If Sun takes the
 * suggestion then the functionality of JarX will soon be provided by nice
 * fast optimized tools and it won't be necessary to spend a lot of time
 * polishing JarX.
 *<P>
 * Error handling is roughly nonexistent.  JarX is careful to avoid silent
 * corruption of data, even verifying that all character encoding calls are
 * successful, but makes no attempt to be graceful about errors or surprises.
 * If something doesn't work the likely result is a one line message and abrupt
 * exit, or an uncaught exception and stack trace.
 *<P>
 * The coding style is a little contrived just to arrange it so JarX.class is
 * the only file needed in the jar to make it self-extracting.  In particular
 * the JarX class is also written to serve as the class of tokens returned by
 * the structured-field-body lexer, to avoid introducing a second class.  Weird,
 * perhaps, but harmless weird.
 *@author <A HREF="mailto:chap@gjt.org">Chapman Flack</A>
 *@version $Id$
 */
public class JarX {
  /**How to treat the entry being processed: bytes, characters, lines.
   * Used only in the JarX instance created by main(). Set by classify().
   * Only the exact String instances BYTES, CHARACTERS, LINES are to be used.
   */
  protected String treatment;
  protected static final String BYTES = "bytes";
  protected static final String CHARACTERS = "characters";
  protected static final String LINES = "lines";
  /**Charset (in archive) of the entry being processed.
   * Used only in the JarX instance created by main(). Set by classify().
   */
  protected Charset archiveCharset;
  /**Charset when unpacked of the entry being processed.
   * Used only in the JarX instance created by main(). Set by classify().
   */
  protected Charset unpackedCharset;

  /**Read permission to be set on the file.
   * Only the final Strings NONE, OWNER, or ALL are to be used, or null, in
   * which case no explicit setting is made and the OS defaults apply.
   */
  protected String readPermission;
  /**Write permission to be set on the file.
   * Only the final Strings NONE, OWNER, or ALL are to be used, or null, in
   * which case no explicit setting is made and the OS defaults apply.
   */
  protected String writePermission;
  /**Execute permission to be set on the file.
   * Only the final Strings NONE, OWNER, or ALL are to be used, or null, in
   * which case no explicit setting is made and the OS defaults apply.
   */
  protected String executePermission;

  protected static final String NONE = "none";
  protected static final String OWNER = "owner";
  protected static final String ALL = "all";

  /**As for treatment, but set from main attributes (or BYTES if not present).*/
  protected String defaultTreatment = BYTES;
  /**As for archiveCharset, but set from main attributes (default UTF-8).*/
  protected Charset defaultArchiveCharset = Charset.forName( "UTF-8");
  /**As for unpackedCharset, but set from main attributes or platform default.*/
  protected Charset defaultUnpackedCharset = Charset.defaultCharset();

  /**As for readPermission but set from main attributes, null if not present.*/
  protected String defaultReadPermission;
  /**As for writePermission but set from main attributes, null if not present.*/
  protected String defaultWritePermission;
  /**As for executePermission but set from main attributes, null if not present.
   */
  protected String defaultExecutePermission;

  /**Script engine to run the name resolver script, if any.*/
  protected ScriptEngine resolverEngine;
  /**The name resolver script, if any.*/
  protected String resolverScript;

  /**Attribute name for specifying the in-archive charset.
   * The Java powers that be didn't go for
   *<A HREF="http://developer.java.sun.com/developer/bugParade/bugs/4310708.html">
   *Bug #4310708</A> so there needs to be a dedicated manifest key for this
   * (though JarX will still honor ;charset= on the Content-Type too).
   */
  public final Attributes.Name ARCHIVE_CHARSET =
    new Attributes.Name( "_JarX_CharsetInArchive");
  /**Attribute name for specifying the when-unpacked charset.
   * This was not in the original JarX; the platform default was always used,
   * and still is if this attribute is not present.
   */
  public final Attributes.Name UNPACKED_CHARSET =
    new Attributes.Name( "_JarX_CharsetWhenUnpacked");
  /**Permissions (only as supported in java.io.File for SE 6)
   * spec *(, spec) where spec is action=whom, action is read, write, or
   * execute, and whom is none, owner, or all.
   */
  public final Attributes.Name PERMISSIONS =
    new Attributes.Name( "_JarX_Permissions");
  /** Main attribute to specify a JSR223 script to control extracted names. */
  public final Attributes.Name PATHRESOLVER =
    new Attributes.Name( "_JarX_PathResolver");

  /**Main attributes saved from the manifest (which must be seen early).*/
  protected Attributes mainAttributes;

  /**Token type, when JarX objects are used to return content type tokens*/
  public short type;
  /**Token text when JarX objects are used to return content type tokens*/
  public String value;
  
  /**Token types from the structured field body lexer defined in
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc822.txt">RFC822</A>
   * as modified in
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc2045.txt">RFC2045</A>.
   * Also state numbers for the automaton in
   * {@link #structuredFieldBody(String,int) structuredFieldBody}.
   */
  public static final short ATOM = 5;
  public static final short COMMENT = 4;
  public static final short DOMAINLITERAL = 3;
  public static final short QUOTEDSTRING = 2;
  public static final short TSPECIAL = 1;
  static final short START = 0;

  /**True if this JarX object represents a token of one of the given types.
   * @param type allowable types
   * @return as titled
   */
  public boolean is( short... type) {
    for ( short t : type )
      if ( t == this.type )
        return true;
    return false;
  }

  /**True if this JarX object represents a token of one of the given types
   * and its value equals the given string.
   * @param value string value for comparison
   * @param type allowable types
   * @return as titled
   */
  public boolean holds( String value, short... type) {
    return is( type) && value.equals( this.value);
  }

  /**True if this JarX object represents a token of one of the given types
   * and its value equals the given string, case-insensitively.
   * @param value string value for comparison
   * @param type allowable types
   * @return as titled
   */
  public boolean holdsIgnoreCase( String value, short... type) {
    return is( type) && value.equalsIgnoreCase( this.value);
  }

  /**Name of the manifest file as stored in the jar*/
  public static final String manifestName = "META-INF/MANIFEST.MF";
  /**The (fixed) encoding used for manifest content*/
  public static final String manifestCode = "UTF-8";

  /**The entry point for extracting.
   *@param args argument list
   *@throws Exception if anything doesn't work, punt
   */
  public static void main( String[] args) throws Exception {
    JarX e = new JarX();
    
    if ( args.length > 0 ) {
      System.err.println( "usage: java -jar filename.jar");
      System.exit( 1);
    }

    e.extract();
  }

  /**Find the jar I was loaded from and extract all entries except my own
   * class file.
   *@throws Exception if anything doesn't work, punt
   */
  public void extract() throws Exception {
    URL jarURL =
      this.getClass().getProtectionDomain().getCodeSource().getLocation();

    InputStream is = jarURL.openStream();
    JarInputStream jis = new JarInputStream( is);
    
    Manifest mf = null;

    for ( JarEntry je;; ) {
      je = jis.getNextJarEntry();
      if ( je == null )
      	break;
      if ( null == mf ) {
        mf = jis.getManifest();
	if ( null != mf ) {
	  Attributes mainAttributes = mf.getMainAttributes();
	  setDefaults( mainAttributes);
	  if ( null != mainAttributes ) {
	    String v = mainAttributes.getValue( PATHRESOLVER);
	    if ( null != v )
	      prepareResolver( v);
	  }
	}
      }
      if ( notMe( je.getName()) )
	extract( je, jis);
      jis.closeEntry();
    }
    
    jis.close();
  }

  /** True if the passed <em>name</em> is not the in-jar name of this class or
   *  related classes that should not be extracted.
   *<p>
   * If not overridden, this method returns false only for names matching the
   * class of {@code this} or any ancestral superclass. Interfaces are not
   * considered. A subclass could apply a different policy.
   */
  public boolean notMe( String name) {
    for ( Class<?> c = getClass(); null != c; c = c.getSuperclass() )
      if ( name.equals( c.getName().replace('.', '/') + ".class") )
        return false;
    return true;
  }
  
  /**Examine the main attributes to set any defaults.
   * Includes loading the required script engine if a name resolver script
   * is given.
   * @param mainAttributes as obtained from the manifest
   */
  public void setDefaults( Attributes mainAttributes) {
    this.mainAttributes = mainAttributes;

    classify( mainAttributes, false);

    defaultTreatment = treatment;
    defaultArchiveCharset = archiveCharset;
    defaultUnpackedCharset = unpackedCharset;

    defaultReadPermission = readPermission;
    defaultWritePermission = writePermission;
    defaultExecutePermission = executePermission;
  }

  /**Prepare a resolver of pathnames, given the value of the PATHRESOLVER
   * main attribute.
   *<p>
   * If not overridden in a subclass, this method parses it as a MIME type and
   * script as described in the class comments, loads a {@code ScriptEngine}
   * for the MIME type, and saves references to the engine in
   * {@code resolverEngine} and the script in {@code resolverScript}.
   * @param v value of the _JarX_PathResolver main attribute
   * @throws Exception this implementation throws no checked exceptions, but an
   * overriding implementation may
   */
  public void prepareResolver( String v) throws Exception {
    JarX[] toks = structuredFieldBody( v, 0);
    if ( toks.length < 4
      || ! toks[0].is( ATOM)
      || ! toks[1].holds("/", TSPECIAL)
      || ! toks[2].is( ATOM)
      || ! toks[3].is( QUOTEDSTRING) ) {
      System.err.printf( "Malformed name resolver attribute: %s\n", v);
      System.exit( 1);
    }

    String mimetype = toks[0].value + "/" + toks[2].value;
    StringBuilder script = new StringBuilder( toks[3].value);
    int i = 4;
    while ( i < toks.length ) {
      if ( toks[i].holds( "/", TSPECIAL) )
        script.append( '\n');
      else if ( toks[i].is( QUOTEDSTRING) )
        script.append( toks[i].value);
      else
        break;
      ++i;
    }

    if ( i < toks.length ) {
      System.err.printf( "Malformed name resolver attribute: %s\n", v);
      System.exit( 1);
    }

    ScriptEngineManager mgr = new ScriptEngineManager();
    resolverEngine = mgr.getEngineByMimeType( mimetype);
    if ( null == resolverEngine ) {
      System.err.printf( "No script engine found for %s\n", mimetype);
      System.exit( 1);
    }
    resolverEngine.put( "properties", System.getProperties());
    resolverScript = script.toString();
  }

  /**Called with every path to be extracted; returns a possibly-corrected path.
   *<p>
   * If not overridden in a subclass, this method returns <em>s</em> unchanged
   * if no {@code resolverScript} has been set, and otherwise invokes the script
   * with {@code storedPath} bound to <em>orig</em>, {@code platformPath} and
   * {@code computedPath} both bound to <em>plat</em>, then returns the value
   * bound to {@code computedPath} when the script has returned.
   * @param orig The path as stored in the archive, always /-separated
   * @param plat The path after only replacing / with the platform separator
   * @return plat unchanged, or a corrected location for extracting the entry,
   * or null to suppress extracting the entry
   * @throws Exception this implementation may throw ScriptException, an
   * overriding implementation may throw others
   */
  public String resolve(String orig, String plat) throws Exception {
    if ( null == resolverScript )
      return plat;
    resolverEngine.put( "storedPath", orig);
    resolverEngine.put( "platformPath", plat);
    resolverEngine.put( "computedPath", plat);
    resolverEngine.eval( resolverScript);
    return (String)resolverEngine.get( "computedPath");
  }

  /**Set instance variables for text/binary and permissions treatment
   * according to the passed Attributes.
   * @param atts Usually a per-entry attribute set, but {@code classify} is
   * also called by {@code setDefaults} to parse the main attributes.
   * @param lazy In the usual case, as soon as an entry is classified as
   * non-text, {@code classify} can return without looking for charset
   * information. When called by {@code setDefaults}, however, laziness is not
   * appropriate.
   */
  public void classify( Attributes atts, boolean lazy) {
    treatment = defaultTreatment;
    archiveCharset = defaultArchiveCharset;
    unpackedCharset = defaultUnpackedCharset;

    readPermission = defaultReadPermission;
    writePermission = defaultWritePermission;
    executePermission = defaultExecutePermission;

    if ( null == atts )
      return;

    String v = atts.getValue( PERMISSIONS);
    if ( null != v ) {
      String r = null;
      String w = null;
      String x = null;
      JarX[] toks = structuredFieldBody( v, 0);
      int i = 0;
      while ( i + 2 < toks.length ) {
	if ( ! toks[i].is( ATOM) || ! toks[i+1].holds( "=", TSPECIAL) )
	  break;
	if ( ! toks[i+2].is( ATOM) )
	  break;
	String p = toks[i].value;
	String noa = toks[i+2].value;
	if ( NONE.equalsIgnoreCase( noa) )
	  noa = NONE;
	else if ( OWNER.equalsIgnoreCase( noa) )
	  noa = OWNER;
	else if ( ALL.equalsIgnoreCase( noa) )
	  noa = ALL;
	else
	  break;
	if ( "read".equalsIgnoreCase( p) && null == r )
	  r = noa;
	else if ( "write".equalsIgnoreCase( p) && null == w )
	  w = noa;
	else if ( "execute".equalsIgnoreCase( p) && null == x )
	  x = noa;
	else
	  break;
	i += 3;
	if ( i+3 < toks.length && toks[i].holds( ",", TSPECIAL) )
	  ++i;
      }

      if ( i < toks.length ) {
	System.err.printf( "Malformed permissions attribute: %s\n", v);
	System.exit( 1);
      }

      if ( null != r )
        readPermission = r;
      if ( null != w )
        writePermission = w;
      if ( null != x )
        executePermission = x;
    }

    boolean archiveCharsetFound = false;

    v = atts.getValue( Attributes.Name.CONTENT_TYPE);
    if ( null != v ) {
      JarX[] type = structuredFieldBody( v, 0);
      if ( type[0].holdsIgnoreCase( "text", ATOM)
        && type[1].holds( "/", TSPECIAL) ) {
        treatment = type[2].holdsIgnoreCase( "plain", ATOM)? LINES : CHARACTERS;
        archiveCharsetFound = archiveCharsetFromType( type);
      }
    }

    if ( BYTES == treatment && lazy )
      return;

    if ( ! archiveCharsetFound ) {
      v = atts.getValue( ARCHIVE_CHARSET);
      if ( null != v )
        archiveCharset = Charset.forName( v);
    }

    v = atts.getValue( UNPACKED_CHARSET);
    if ( null != v )
      unpackedCharset = Charset.forName( v);
  }

  /**Parse a Content-Type for any {@code charset} parameter.
   * @param type tokenized Content-Type value
   * @return true if the Content-Type specified a charset
   */
  protected boolean archiveCharsetFromType( JarX[] type) {
    String charset = null;
    int i = 3;

    while ( i < type.length ) {
      if ( ! type[i].holds( ";", TSPECIAL) )
      	break;
      if ( type[++i].holdsIgnoreCase( "charset", ATOM) ) {
	if ( ! type[++i].holds( "=", TSPECIAL) )
	  break;
	if ( ! type[++i].is( ATOM, QUOTEDSTRING) )
	  break;
	charset = type[i].value;
	break;
      }
      if ( ! type[++i].holds( "=", TSPECIAL) )
	break;
      if ( ! type[++i].is( ATOM, QUOTEDSTRING) )
	break;
      ++i;
    }

    if ( null != charset ) {
      archiveCharset = Charset.forName( charset);
      return true;
    }

    if ( i < type.length ) {
      System.err.println( "Malformed Content-Type specification!");
      System.exit( 1);
    }

    return false;
  }

  /**Extract a single entry, performing any appropriate conversion
   *@param je JarEntry for the current entry
   *@param is InputStream with the current entry content
   *@throws Exception IOException for any problem involving I/O, ScriptException
   * possible from the non-overridden path resolver, others possible in an
   * overridden implementation
   */
  public void extract( JarEntry je, InputStream is) throws Exception {
    classify( je.getAttributes(), true);

    String orig = je.getName();
    String s = orig;
    
    if ( File.separatorChar != '/' )
      s = s.replace( '/', File.separatorChar);

    s = resolve( orig, s);
    if ( null == s )
      return;

    System.err.print( s + " ");
    
    File f = new File( s);
    
    if ( je.isDirectory() ) {
      if ( f.isDirectory()  ||  f.mkdirs() )
      	System.err.println();
      else
      	System.err.println( "FAILED!");
      return;
    }
      
    OutputStream os;
    
    File tmpf;
    File d = f.getParentFile();
    if ( null == d )
      d = new File( System.getProperty( "user.dir"));
    try {
      tmpf = File.createTempFile( f.getName(), ".tmp", d);
    }
    catch ( IOException e ) {
      if ( ! d.mkdirs() )
      	throw e;
      tmpf = File.createTempFile( f.getName(), ".tmp", d);
    }

    os = new FileOutputStream( tmpf);

    if ( null != readPermission ) {
      if ( ALL == readPermission )
        tmpf.setReadable( true, false);
      else {
        tmpf.setReadable( false, false);
	if ( OWNER == readPermission )
	  tmpf.setReadable( true, true);
      }
    }

    if ( null != writePermission ) {
      if ( ALL == writePermission )
        tmpf.setWritable( true, false);
      else {
        tmpf.setWritable( false, false);
	tmpf.setWritable( true, true); /* will when done writing */
      }
    }
    
    shovel( is, os);
    
    os.close();

    if ( NONE == writePermission )
      tmpf.setWritable( false, false);

    if ( null != executePermission ) {
      if ( ALL == executePermission )
        tmpf.setExecutable( true, false);
      else {
        tmpf.setExecutable( false, false);
	if ( OWNER == executePermission )
	  tmpf.setExecutable( true, true);
      }
    }

    if ( ! tmpf.renameTo( f) ) {
      if ( ! f.delete()  ||  ! tmpf.renameTo( f) )
        System.err.println( "RENAME FAILED!");
    }
  }

  /**Copy content from an input to an output stream until end.
   * Whether the content is shoveled as bytes, characters, or lines will be
   * determined by instance variables that have been set by calling
   * {@link #classify(Attributes,boolean) classify} before calling this method.
   *@param is source of input
   *@param os destination for output
   *@throws IOException for any problem involving I/O
   */
  public void shovel( InputStream is, OutputStream os) throws IOException {
    if ( BYTES == treatment )
      shovelBytes( is, os);
    else
      shovelText( is, os);
  }
  
  /**Copy <EM>bytes</EM> from an input to an output stream until end.
   * No character encoding or newline conversion applies.
   *@param is source of input
   *@param os destination for output
   *@throws IOException for any problem involving I/O
   */
  public static void shovelBytes( InputStream is, OutputStream os)
  throws IOException {
    byte[] buf = new byte [ 1024 ];
    int got;
    
    for ( ;; ) {
      got = is.read( buf, 0, buf.length);
      if ( got == -1 )
      	break;
      os.write( buf, 0, got);
    }
    System.err.println( "as bytes");
  }

  /**Copy <EM>text</EM> from an input to an output stream until end.
   * Determines the encoding transformation to use (based on the
   * <CODE>charset</CODE> content-type parameter) and whether to copy as
   * lines (with newline conversion) or unmolested characters.
   * <CODE>text/plain</CODE> is copied as lines, all other text subtypes
   * as characters.
   *@param is source of input
   *@param os destination of output
   *@throws IOException for any problem involving I/O
   */
  public void
  shovelText( InputStream is, OutputStream os)
  throws IOException {
    if ( LINES == treatment )
      shovelLines( is, os);
    else
      shovelChars( is, os);
  }

  /**Copy <EM>lines</EM> of text from an input from an output stream, applying
   * the specified character encoding and translating newlines.
   * This method handles the extracting case, where the named encoding is
   * associated with the input stream (jar) and the platform default encoding
   * with the output (local file), and the local line.separator is used to
   * separate lines on the output.
   * Overridden in
   * {@link JarX.Build#shovelLines(InputStream,OutputStream) build} to do
   * the reverse when building a jar.
   * To avoid silent corruption of data, this method verifies that all
   * characters from the jar are successfully converted to the local platform's
   * encoding.
   *@param is the source of input
   *@param os destination for output
   *@throws IOException for any problem involving I/O
   */
  public void
  shovelLines( InputStream is, OutputStream os)
  throws IOException {
    InputStreamReader isr =
      new InputStreamReader( is, archiveCharset.newDecoder());
    BufferedReader br = new BufferedReader( isr);
    OutputStreamWriter osw =
      new OutputStreamWriter( os, unpackedCharset.newEncoder());
    BufferedWriter bw = new BufferedWriter( osw);
    
    String s;
    
    for ( ;; ) {
      s = br.readLine();
      if ( s == null )
      	break;
      bw.write( s);
      bw.newLine();
    }
    bw.flush();
    osw.flush();
    
    System.err.printf( "as lines (%s)\n", describeTranscoding(isr, osw));
  }
  
  /**Copy <EM>characters</EM> of text from an input from an output stream,
   * applying the specified character encoding but not translating newlines.
   * This method handles the extracting case, where the named encoding is
   * associated with the input stream (jar) and the platform default encoding
   * with the output (local file).
   * Overridden in
   * {@link Build#shovelChars(InputStream,OutputStream) build} to do
   * the reverse when building a jar.
   * To avoid silent corruption of data, this method verifies that all
   * characters from the jar are successfully converted to the local platform's
   * encoding.
   *@param is the source of input
   *@param os destination for output
   *@throws IOException for any problem involving I/O
   */
  public void
  shovelChars( InputStream is, OutputStream os)
  throws IOException {
    InputStreamReader isr =
      new InputStreamReader( is, archiveCharset.newDecoder());
    OutputStreamWriter osw =
      new OutputStreamWriter( os, unpackedCharset.newEncoder());
    char[] c = new char [ 1024 ];
    int got;
    
    for ( ;; ) {
      got = isr.read( c, 0, c.length);
      if ( got == -1 )
      	break;
      osw.write( c, 0, got);
    }
    osw.flush();
    System.err.printf( "as characters (%s)\n", describeTranscoding(isr, osw));
  }

  public String describeTranscoding(
    InputStreamReader isr, OutputStreamWriter osw) {
    String ie = isr.getEncoding();
    String oe = osw.getEncoding();
    if ( ie.equals( oe) )
      return ie;
    return ie + " -> " + oe;
  }
  
  /**Public constructor for an application using JarX to unpack jars.*/
  public JarX() { }
  /**Constructor for JarX objects used as tokens returned by the lexer.
   *@param t the type of this token
   *@param v the corresponding text (with delimiters removed and backslashes
   * resolved for quoted strings, domain text, and comments)
   */
  protected JarX( short t, String v) { type = t; value = v; }

  /**Lexical analyzer for structured field bodies as described in
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc822.txt">RFC822</A>
   * and modified in
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc2045.txt">RFC2045</A>.
   * Comments are processed and stored in tokens that are, at the last
   * minute, excluded from the returned token list; only two lines would need
   * to be changed to use this lexer in an application that wanted comments
   * returned.
   *@param field a header field
   *@param off offset to the start of the structured field body
   * (skip the field name and colon)
   *@return An array of {@link #JarX(short,String) tokens} with any
   * COMMENT tokens (for JarX purposes) excluded
   */
  public static JarX[] structuredFieldBody( String field, int off) {
    char[] buf = new char [ field.length() - off ];
    field.getChars( off, off + buf.length, buf, 0);
    int beg = 0, end = -1, la;
    int commentDepth = 0;
    short state = START;
    short lastState = state;
    boolean bashed = false;
    ArrayList<JarX> v = new ArrayList<JarX>();
    
    dfa: for ( la = 0; la < buf.length; ) {
      
      if ( end >= beg ) {
      	if ( lastState != COMMENT )
	  v.add(new JarX( lastState, new String( buf, beg, end-beg)));
	end = -1;
      }
      lastState = state;
      
      switch ( state ) {
      	case START:
	  switch ( buf[la] ) {
	    case '"': beg = ++la; state = QUOTEDSTRING; continue dfa;
	    case '[': beg = ++la; state = DOMAINLITERAL; continue dfa;
	    case '(': beg = ++la; state = COMMENT; continue dfa;
	    case '/': case '?': case '=': case ')': case '<': case '>':
	    case '@': case ',': case ';': case ':': case '\\':  case ']':
	      state = TSPECIAL; continue dfa;
	    case ' ': case '\u0009': ++la; continue dfa;
	    default: beg = la++; state = ATOM; continue dfa;
	  }
	case TSPECIAL:
	  beg = la;
	  end = ++la;
	  state = START;
	  continue dfa;
	case QUOTEDSTRING:
	  for ( end = beg; la < buf.length; ++la ) {
	    if ( bashed )
	      bashed = false;
	    else if ( buf [ la ] == '\\' ) {
	      bashed = true;
	      continue;
	    }
	    else if ( buf [ la ] == '"' ) {
	      ++la;
	      state = START;
	      continue dfa;
	    }
	    buf [ end++ ] = buf [ la ];
	  }
	  break dfa;
	case DOMAINLITERAL:
	  for ( end = beg; la < buf.length; ++la ) {
	    if ( bashed )
	      bashed = false;
	    else if ( buf [ la ] == '\\' ) {
	      bashed = true;
	      continue;
	    }
	    else if ( buf [ la ] == ']' ) {
	      ++la;
	      state = START;
	      continue dfa;
	    }
	    buf [ end++ ] = buf [ la ];
	  }
	  break dfa;
	case COMMENT:
	  ++commentDepth;
	  for ( end = beg; la < buf.length; ++la ) {
	    if ( bashed )
	      bashed = false;
	    else if ( buf [ la ] == '\\' ) {
	      bashed = true;
	      continue;
	    }
	    else if ( buf [ la ] == ')' && 0 == --commentDepth ) {
	      ++la;
	      state = START;
	      continue dfa;
	    }
	    else if ( buf [ la ] == '(' )
	      ++commentDepth;
	    buf [ end++ ] = buf [ la ];
	  }
	  break dfa;
	case ATOM:
	  for ( end = la; la < buf.length; ++la ) {
	    if ( buf [ la ] <= ' ' ) {
	      state = START;
	      continue dfa;
	    }
	    switch ( buf [ la ] ) {
	      case '/': case '?': case '=':
	      case '(': case ')': case  '<': case '>': case '@':
	      case ',': case ';': case '\\': case '"':
	      case '[': case ']': state = START; continue dfa;
	      default: ++end;
	    }
	  }
	  state = START;
	  break dfa;
      }
    }
    if ( state != START )
      System.err.println( "Warning: incomplete qstring, dtext, or comment");
    if ( end >= beg )
      if ( lastState != COMMENT )
	v.add(new JarX( lastState, new String( buf, beg, end-beg)));
    return v.toArray( new JarX [ v.size() ]);
  }

  /**Subclass of JarX containing the code needed to build jars.  This class
   * is not needed for extracting and this class
   * file does not need to be included in a self-extracting jar.
   */
  public static class Build extends JarX {
    
    /**Entry point for building a jar.
     * Names of all files to be put in the jar (except the manifest itself)
     * are taken from the manifest.
     *@param args two command line arguments: 1) the name of the jar file
     * to create; 2) the name of the manifest file.
     *@throws Exception if anything goes wrong, punt
     */
    public static void main( String[] args) throws Exception {
      if ( args.length != 2 ) {
      	System.err.println( "usage: JarX.Build jarfile manifest");
	System.exit( 1);
      }
      new Build().build( args[0], args[1]);
    }
    
    /**Names of files to include, in order of appearance in the manifest*/
    ArrayList<String> names = new ArrayList<String>();
    /**Attribute sections of those files, null if not specified*/
    ArrayList<Attributes> sections = new ArrayList<Attributes>();
    
    /**Method to be used by an application using this class to build a jar.
     *@param jarFile name of jar file to be created
     *@param manif name of an existing manifest file containing the names
     * of files to include in the jar. File names in the manifest obey zip
     * conventions with the forward slash / as the path operator, which may
     * differ from the local platform convention.
     *@throws Exception if anything doesn't work, punt
     */
    public void build( String jarFile, String manif) throws Exception {
      FileOutputStream fos = new FileOutputStream( jarFile);
      ZipOutputStream zos = new ZipOutputStream( fos);
      FileInputStream is = new FileInputStream( manif);
      ZipEntry ze;
      File f;
      
      this.manifest( is);
      is.close();
      
      System.err.print( manifestName + " ");
      is = new FileInputStream( manif);
      ze = new ZipEntry( manifestName);
      zos.putNextEntry( ze);
      classify( null, true);
      archiveCharset = Charset.forName( manifestCode);
      this.shovelLines( is, zos);
      is.close();
      zos.closeEntry();
      
      String[] n = new String [ names.size() ];
      Attributes[] t = new Attributes[ sections.size() ];
      
      names.toArray( n);
      sections.toArray( t);
      
      for ( int i = 0; i < n.length; ++i ) {
      	if ( n[i].equals( manifestName) )
	  continue;
	System.err.print( n[i] + " ");
	ze = new ZipEntry( n[i]);
	f = new File( File.separatorChar == '/' ? n[i] :
	              n[i].replace( '/', File.separatorChar));
	ze.setTime( f.lastModified());
	zos.putNextEntry( ze);
	if ( ze.isDirectory() ) {
	  System.err.println();
	}
	else if ( f.isDirectory() ) {
	  System.err.println( "DIRECTORY! add / in manifest.");
	  System.exit( 1);
	}
	else {
	  is = new FileInputStream( f);
	  classify( t[i], true);
	  if ( BYTES == treatment )
	    this.shovelBytes( is, zos);
	  else
	    this.shovelText( is, zos);
	  is.close();
	}
	zos.closeEntry();
      }
      
      zos.close();
    }

    /**Overridden to
     * save name-to-type mappings in Lists instead of the Map, to
     * preserve the order of names in the manifest.
     */
    void store( String name, Attributes atts) {
      names.add( name);
      sections.add( atts);
    }

    /**Overridden to apply the archive encoding to the output stream (jar
     * entry), the unpacked encoding to the input stream (local file), and use
     * the RFC2046-required CRLF line separator on the output.
     *@param is source of input (local file)
     *@param os destination of output (jar entry)
     */
    public void
    shovelLines( InputStream is, OutputStream os)
    throws IOException {
      InputStreamReader isr =
        new InputStreamReader( is, unpackedCharset.newDecoder());
      BufferedReader br = new BufferedReader( isr);
      OutputStreamWriter osw =
        new OutputStreamWriter( os, archiveCharset.newEncoder());
      BufferedWriter bw = new BufferedWriter( osw);

      String crlf = "\r\n";

      String s;
 
      for ( ;; ) {
    	s = br.readLine();
    	if ( s == null )
    	  break;
	bw.write( s);
	bw.write( crlf);
      }
      bw.flush();
      osw.flush();
 
      System.err.printf( "as lines (%s)\n", describeTranscoding(isr, osw));
    }
  
    /**Overridden to apply the archive encoding to the output stream (jar entry)
     * and the unpacked encoding to the input stream (local file).
     *@param is source of input (local file)
     *@param os destination of output (jar entry)
     */
    public void
    shovelChars( InputStream is, OutputStream os)
    throws IOException {
      InputStreamReader isr =
        new InputStreamReader( is, unpackedCharset.newDecoder());
      OutputStreamWriter osw =
        new OutputStreamWriter( os, archiveCharset.newEncoder());
      char[] c = new char [ 1024 ];
      int got;

      for ( ;; ) {
	got = isr.read( c, 0, c.length);
	if ( got == -1 )
      	  break;
	osw.write( c, 0, got);
      }
      osw.flush();

      System.err.printf( "as characters (%s)\n", describeTranscoding(isr, osw));
    }

    /**Read the manifest and build lists of file names and Attributes objects.
     * This was originally here because JarX wanted to support Java 1.1, which
     * lacked java.util.jar. The reason it is still here (in Build only) is that
     * the java.util.jar.Manifest implementation doesn't preserve the order of
     * manifest sections, while it is nice to build the jar in the specified
     * order.
     *@param is an input stream already open on the manifest
     *@throws IOException if unable to read the manifest
     */
    public void manifest( InputStream is)
    throws IOException {
      InputStreamReader isr;
      Charset enc = Charset.forName(manifestCode);
      isr = new InputStreamReader( is, enc.newDecoder());
      BufferedReader br = new BufferedReader( isr);

      while ( section( br) ); /* */
    }

    /**Process one manifest section, adding a dictionary entry if the section
     * contains both a <CODE>Name:</CODE> and a <CODE>Content-Type</CODE>
     * attribute.
     *@param r BufferedReader already open on the manifest
     *@return true if there is another section to read, false if the end of the
     * manifest has been reached
     *@throws IOException if the manifest can't be read
     */
    public boolean section( BufferedReader r)
    throws IOException {
      String field;
      String front;
      String name = null;
      Attributes atts = new Attributes();
      boolean gotany = false;

      for ( ;; ) {
        field = header( r);
        if ( field == null  ||  0 == field.length() )
          break;
        gotany = true;
	int i = field.indexOf( ": ");
	if ( i < 1 ) {
	  System.err.printf( "Malformed line in manifest: %s\n", field);
	  System.exit( 1);
	}
	front = field.substring(0, i);
	field = field.substring(i+2);
        if ( front.equalsIgnoreCase( "Name") ) {
          if ( name == null )
            name = field;
          else
            System.err.println(
              "Warning: name attribute repeated within a section, ignored.");
          continue;
	}
	atts.putValue( front, field);
      }

      if ( ! gotany )
        return null != field;

      if ( null == name ) {
        if ( null != mainAttributes ) {
	  System.err.println(
	    "Main attributes followed by another nameless section");
	  System.exit( 1);
	}
	setDefaults( atts);
      }
      else
        store( name, atts);

      return null != field;
    }

    /**Buffer used between calls to {@link #header(BufferedReader) header}.*/
    String nextManifestLine = null;

    /**Return one header line (complete after RFC822 continuation unfolding).
     * <strong>Note:</strong> The Jar specification says it is "inspired by"
     * RFC822, but the folding rule <strong>differs</strong>. RFC822 allows
     * "linear whitespace" (i.e. space or tab) to start the continuation line,
     * and the LWSP <em>remains in the line</em> (RFC822 lines are only supposed
     * to be folded at places LWSP can appear). A jar manifest line continuation
     * can only begin with a space, and the space is <em>eaten</em>; Java's
     * manifest writer can arbitrarily fold in the middle of anything.
     *@param r BufferedReader to read from
     *@return the line read, or null at end of input
     *@throws IOException if the input cannot be read
     */
    public String header( BufferedReader r) throws IOException {
      if ( nextManifestLine == null )
        nextManifestLine = r.readLine();

      String line = nextManifestLine;

      for  ( ;; ) {
        nextManifestLine = r.readLine();
        if ( nextManifestLine == null
          || ! nextManifestLine.startsWith( " ") )
          break;
        line += nextManifestLine.substring(1);
      }

      return line;
    }
  }
}
