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
import java.util.Vector;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Enumeration;
import java.net.URL;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Distribute your work as a self-extracting jar file by including one file,
 * JarX.class, that also safely converts text files to the receiver's encoding
 * and newline conventions, and adds less than 6kB to your jar.
 *<P>
 * A self-extracting file is handy if your recipient might have a
 * Java 1.1 or 1.2 runtime environment but not the jar tool.
 * The text conversion offered by JarX is useful if your distribution will
 * include text files, source, documentation, scripts, etc., and your recipients
 * have platforms with different newline conventions.
 *<H3>Text conversion background</H3>
 * There are two issues in the cross-platform delivery of text files.
 *<OL><LI>Different platforms indicate the end of a line differently.
 * The UNIX convention uses the single character LINE FEED; the Macintosh
 * uses only the CARRIAGE RETURN character, and DOS/Windows systems require
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
 *<H4>The ZIP approach and why it loses</H4>
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
 *<H4>A better way</H4>
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
 HREF="http://java.sun.com/products/jdk/1.3/docs/guide/jar/jar.html#Per-Entry Attributes">
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
 * on the local system.
 *<H3>What JarX Does</H3>
 * <CODE>Content-Type</CODE> entries in a Manifest were introduced in Java 1.3
 * but are compatible with earlier jar specifications; a jar file containing
 * such entries can be processed without any trouble by any jar tool compliant
 * with the old or new standard.  However, there is not yet a full jar tool
 * available that will honor the content types and do automatic transformation
 * of text entries.  To fill the need until that functionality is added to the
 * widely-available jar tools, JarX is available now.
 *<P>
 * JarX.Build produces a jar, working from a manifest file prepared by the
 * developer.  Entries with any <CODE>text</CODE> will be translated from the
 * local encoding into the specified <CODE>charset</CODE> if given, and entries
 * with the specific type <CODE>text/plain</CODE> will have their line endings
 * converted to the CRLF canonical form.  Line endings are left alone for all
 * other subtypes of <CODE>text</CODE>, but this decision is open to comment.
 *<P>
 * The file produced by JarX.Build is a fully compliant jar and can be unpacked
 * by any jar or unzip tool, but current tools will not automatically convert
 * the text files to the local conventions.  By including the single class file
 * <CODE>JarX.class</CODE> in the jar, a developer produces a self-extracting
 * archive that can be executed to unpack itself on any Java 1.1 or later
 * virtual machine, performing all automatic conversions and requiring no jar
 * tool at all.
 *<H3>Building a Jar</H3>
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
 *<H3>Extracting a jar</H3>
 * Under Java 1.2, the command <CODE>java -jar foo.jar</CODE> is all it takes
 * to extract a jar.  The <CODE>Main-Class</CODE> entry in the manifest
 * identifies the entry point of JarX so it does not need to be specified.
 * Under Java 1.1, the <CODE>Main-Class</CODE> attribute is not supported and
 * the slightly longer command<BR>
 * <CODE>java -classpath foo.jar org.gjt.cuspy.JarX foo.jar</CODE><BR>
 * is required.
 *<H3>Miscellany</H3>
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
 * JarX would be much simpler if I could have used the Java 1.2 API, but the
 * goal of making it usable on 1.1 virtual machines made it uglier.
 *<P>
 * The coding style is a little contrived just to arrange it so JarX.class is
 * the only file needed in the jar to make it self-extracting.  In particular
 * the JarX class is also written to serve as the class of tokens returned by
 * the content-type lexer, to avoid introducing a second class.  Weird, perhaps,
 * but harmless weird.
 *@author <A HREF="mailto:chap@gjt.org">Chapman Flack</A>
 *@version $Id$
 */
public class JarX {

  /**Token type, when JarX objects are used to return content type tokens*/
  public short type;
  /**Token text when JarX objects are used to return content type tokens*/
  public String value;
  
  /**Token types from the structured field body lexer defined in
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc0822.txt">RFC822</A>
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
  /**Constant "content type token list" stored by
   *{@link #section(BufferedReader,Dictionary) section} for any entry
   * with an explicit content type that isn't text.  We only care that it
   * isn't text, so no need to store the actual tokens.
   */
  public static final JarX[] NOTTEXT = new JarX[0];
  /**Name of the JarX class file as stored in the jar*/
  public static final String me
  = JarX.class.getName().replace('.', '/') + ".class";
  /**Name of the manifest file as stored in the jar*/
  public static final String manifestName = "META-INF/MANIFEST.MF";
  /**The (fixed) encoding used for manifest content*/
  public static final String manifestCode = "UTF-8";
  /**A constant token list representing the content type of the manifest*/
  public static final JarX[] manifestType = new JarX[] {
    new JarX(     ATOM, "text"	      ),
    new JarX( TSPECIAL, "/"	      ),
    new JarX(     ATOM, "plain"       ),
    new JarX( TSPECIAL, ";"	      ),
    new JarX(     ATOM, "charset"     ),
    new JarX( TSPECIAL, "="	      ),
    new JarX(     ATOM, manifestCode  )
  };

  /**The entry point for extracting.  There is one optional argument: if
   * present, it will be used as the name of the jar to extract.  If the name
   * isn't given, JarX will try to find the jar it was loaded from (which will
   * only work in Java 1.2).
   *@param args argument list
   *@throws Exception if anything doesn't work, punt
   */
  public static void main( String[] args) throws Exception {
    JarX e = new JarX();
    
    if ( args.length > 0 )
      e.extract( args[0]);
    else
      e.extract();
  }
  
  /**Extract all entries (except JarX.class itself) from a given zip file.
   * The archive is opened twice: first as a {@link ZipFile} (random
   * access) to read the manifest, then closed and reopened as a
   * {@link ZipInputStream} to get the entries sequentially.  I hate doing
   * this as it introduces more assumptions that the file is a regular file,
   * still there the second time we try to open it, etc.  But the other
   * way, using {@link ZipFile#entries()}, extracted files in an
   * unintuitive, hashed order, and, because of the associated seeking,
   * scaled poorly to larger jars.
   *@param zipFile the archive
   *@throws Exception if anything doesn't work, punt
   */
  public void extract( String zipFile) throws Exception {
    ZipFile zf = new ZipFile( zipFile);
    ZipEntry ze;
    FileInputStream fis;
    ZipInputStream zis;
    InputStream is;
    Dictionary mf = new Hashtable();
    
    ze = zf.getEntry( manifestName);
    if ( ze != null ) {
      is = zf.getInputStream( ze);
      manifest( is, mf);
      is.close();
    }
    
    zf.close();
    fis = new FileInputStream( zipFile);
    zis = new ZipInputStream( fis);
    
    for ( ;; ) {
      ze = zis.getNextEntry();
      if ( ze == null )
      	break;
      if ( ! ze.getName().equals( me) )
      	extract( ze, zis, mf);
      zis.closeEntry();
    }
    
    zis.close();
  }

  /**Find the jar I was loaded from and extract all entries except my own
   * class file.
   *@throws Exception if anything doesn't work, punt
   */
  public void extract() throws Exception {
    ClassLoader cl = this.getClass().getClassLoader();
    URL jarURL, fileURL = null, mfURL;
    String s;
    int offset;
    
    if ( cl != null )
      jarURL = cl.getResource( me);
    else
      jarURL = ClassLoader.getSystemResource( me);
    
    if ( jarURL == null  ||  ! jarURL.getProtocol().equals( "jar") ) {
      System.err.println(
"Can't find myself; please add the source jar file name to the command line.");
      System.exit( 1);
    }
    
    s = jarURL.toString();
    offset = s.indexOf( "!/");
    jarURL = new URL( s.substring( 0, offset + 2));
    s = jarURL.getFile();
    fileURL = new URL( fileURL, s.substring( 0, s.length() - 2));
    mfURL = new URL( jarURL, manifestName);
    
    InputStream is = mfURL.openStream();
    Dictionary mf = new Hashtable();
    manifest( is, mf);
    is.close();
    
    is = fileURL.openStream();
    ZipInputStream zis = new ZipInputStream( is);
    
    for ( ZipEntry ze;; ) {
      ze = zis.getNextEntry();
      if ( ze == null )
      	break;
      if ( ! ze.getName().equals( me) )
      	extract( ze, zis, mf);
      zis.closeEntry();
    }
    
    zis.close();
  }
  
  /**Extract a single entry, performing any appropriate conversion
   *@param ze ZipEntry for the current entry
   *@param is InputStream with the current entry content
   *@param mf Dictionary filled in by
   *{@link #manifest(InputStream,Dictionary) manifest} to look up content type
   * for this entry
   */
  public void extract( ZipEntry ze, InputStream is, Dictionary mf)
  throws IOException {
    if ( ze.isDirectory() )
      return;
      
    String s = ze.getName();
    System.err.print( s + " ");
    JarX[] type = (JarX[])mf.get( s);
    
    if ( File.separatorChar != '/' )
      s = s.replace( '/', File.separatorChar);
    
    File f = new File( s);
    
    OutputStream os;
    
    try {
      os = new FileOutputStream( f);
    }
    catch ( IOException e ) {
      File fp = new File( f.getParent());
      if ( fp == null  ||  ! fp.mkdirs() )
      	throw e;
      os = new FileOutputStream( f);
    }
    
    if ( type == null  ||  type == NOTTEXT )
      shovelBytes( is, os);
    else
      shovelText( is, os, type);
    
    os.close();
  }
  
  /**Copy <EM>bytes</EM> from an input to an output stream until end.
   * No character encoding or newline conversion applies.
   *@param is source of input
   *@param os destination for output
   *@throws IOException
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
   *@param type the content type as an array of token structures returned
   * by the {@link JarX#structuredFieldBody(String,int) structuredFieldBody}
   * lexer.
   *@throws IOException
   */
  public void
  shovelText( InputStream is, OutputStream os, JarX[] type)
  throws IOException {
    if ( type.length < 3
      || ! type[0].value.equalsIgnoreCase( "text")
      || ! type[1].value.equals( "/")
      || ! ( type[2].type == ATOM  ||  type[2].type == QUOTEDSTRING ) ) {
      	System.err.println("shovelText called on non-text (shouldn't happen!)");
	System.exit( 1);
      }
      
    String charset = null;
    int i;
     
    for ( i = 3; i < type.length; ) {
      if ( ! type[i].value.equals( ";") )
      	break;
      if ( type[++i].value.equalsIgnoreCase( "charset") ) {
      	if ( ! type[++i].value.equals( "=") )
	  break;
	if ( ! (type[++i].type == ATOM  ||  type[i].type == QUOTEDSTRING) )
	  break;
	charset = type[i].value;
	break;
      }
      if ( ! type[++i].value.equals( "=") )
	break;
      if ( ! (type[++i].type == ATOM  ||  type[i].type == QUOTEDSTRING) )
	break;
      ++i;
    }
    
    if ( charset == null ) {
      if ( i >= type.length )
      	charset = "US-ASCII";
      else {
      	System.err.println( "Malformed Content-Type specification!");
      	System.exit( 1);
      }
    }
    
    String enc = blindDesperateSearchForCorrespondingEncodingName( charset);
    
    if ( enc == null ) {
      System.err.println( "Can't find converter for charset " + charset);
      System.exit( 1);
    }
    
    if ( type[2].value.equalsIgnoreCase( "plain") )
      shovelLines( is, os, enc);
    else
      shovelChars( is, os, enc);
  }

  /**Try to find a Java character encoding matching a MIME charset name.
   * As of Java 1.3, Sun has specified that character encodings must be
   * accessible <EM>by</EM> their MIME names, and that the names are case
   * insensitive as required by RFC2046, but that was never specified
   * (or consistently true) previously.  Also, the API provides no way to
   * examine a list of supported encodings, so this method just tries a bunch
   * of plausible mungings of the charset name until one succeeds.  If this
   * method gives up, it does not necessarily mean that the implementation
   * doesn't support the needed encoding, only that the implementors were more
   * clever at hiding it than I am at guessing the name.
   *@param charSet a MIME charset name
   *@return The original argument if it was accepted as an encoding name,
   * or some munging of it that was accepted, or null if we struck out.
   */
  public static String
  blindDesperateSearchForCorrespondingEncodingName( String charset) {
    String s = null;
    bewilderedObsessiveRepetition: for ( int i = 0 ;; ++i ) {
      switch ( i ) {
      	case 0: s = charset; break;
      	case 1: s = s.toLowerCase(); break;
	case 2: s = s.toUpperCase(); break;
	case 3: s = s.substring( 0, 1) + s.substring( 1).toLowerCase(); break;
	case 4: {
	  char[] c = new char [ charset.length() ];
	  charset.getChars( 0, charset.length(), c, 0);
	  int dst = 0;
	  for ( int src = 0; src < c.length; ++src )
	    if ( Character.isJavaIdentifierPart( c[src]) )
	      c [ dst++ ] = c [ src ];
	  s = new String( c, 0, dst);
	  }
	  break;
	case 5: s = s.toLowerCase(); break;
	case 6: s = s.toUpperCase(); break;
	case 7: s = s.substring( 0, 1) + s.substring( 1).toLowerCase(); break;
	case 8:
	  if ( charset.equalsIgnoreCase( "us-ascii") ) {
	    s = charset.substring( 3);
	    break;
	  }
	  i += 3;
	  continue;
	case 9: s = s.toLowerCase(); break;
	case 10: s = s.toUpperCase(); break;
	case 11: s = s.substring( 0, 1) + s.substring( 1).toLowerCase(); break;
	default:
	  break bewilderedObsessiveRepetition;
      }
      if ( isEncoding( s) )
      	return s;
    }
    return null;
  }
  
  /**Test if an encoding name is supported by the implementation.
   * Uses the only test allowed by the API--try to encode something with it
   * and catch the exception.
   *@param name a prospective encoding name
   *@return true if and only if the named encoding is supported, false otherwise
   */
  public static boolean isEncoding( String name) {
    byte[] b = null;
    try {
      b = "".getBytes( name);
    }
    catch ( Exception e ) {
      return false;
    }
    return b != null;
  }

  /**Copy <EM>lines</EM> of text from an input from an output stream, applying
   * the specified character encoding and translating newlines.
   * This method handles the extracting case, where the named encoding is
   * associated with the input stream (jar) and the platform default encoding
   * with the output (local file), and the local line.separator is used to
   * separate lines on the output.
   * Overridden in
   * {@link JarX.Build#shovelLines(InputStream,OutputStream,String) build} to do
   * the reverse when building a jar.
   * To avoid silent corruption of data, this method verifies that all
   * characters from the jar are successfully converted to the local platform's
   * encoding.
   *@param is the source of input
   *@param os destination for output
   *@param enc the character encoding name (not necessarily the same as
   * the MIME charset name pre-Java-1.3) used in the jar
   */
  public void
  shovelLines( InputStream is, OutputStream os, String enc)
  throws IOException {
    InputStreamReader isr = new InputStreamReader( is, enc);
    BufferedReader br = new BufferedReader( isr);
    OutputStreamWriter osw = new OutputStreamWriter( os);
    BufferedWriter bw = new BufferedWriter( osw);
    
    String s;
    byte[] b;
    
    for ( ;; ) {
      s = br.readLine();
      if ( s == null )
      	break;
      b = s.getBytes();
      if ( ! new String( b).equals( s) ) {
      	System.err.println( "Characters unrepresentable in local encoding!");
	System.err.println( s);
	System.err.println( new String( b));
	System.exit( 1);
      }
      os.write( b);
      bw.newLine();
      bw.flush();
    }
    
    System.err.println( "as lines ("+enc+" -> "+osw.getEncoding()+")");
  }
  
  /**Copy <EM>characters</EM> of text from an input from an output stream,
   * applying the specified character encoding but not translating newlines.
   * This method handles the extracting case, where the named encoding is
   * associated with the input stream (jar) and the platform default encoding
   * with the output (local file).
   * Overridden in
   * {@link Build#shovelChars(InputStream,OutputStream,String) build} to do
   * the reverse when building a jar.
   * To avoid silent corruption of data, this method verifies that all
   * characters from the jar are successfully converted to the local platform's
   * encoding.
   *@param is the source of input
   *@param os destination for output
   *@param enc the Java character encoding name (not necessarily the same as
   * the MIME charset name pre-Java-1.3) used in the jar
   */
  public void
  shovelChars( InputStream is, OutputStream os, String enc)
  throws IOException {
    InputStreamReader isr = new InputStreamReader( is, enc);
    OutputStreamWriter osw = new OutputStreamWriter( os);
    char[] c = new char [ 1024 ];
    byte[] b;
    int got;
    String s;
    
    for ( ;; ) {
      got = isr.read( c, 0, c.length);
      if ( got == -1 )
      	break;
      s = new String( c, 0, got);
      b = s.getBytes();
      if ( ! new String( b).equals( s) ) {
      	System.err.println( "Characters unrepresentable in local encoding!");
	System.exit( 1);
      }
      os.write( b);
    }
    os.flush();
    System.err.println( "as characters ("+enc+" -> "+osw.getEncoding()+")");
  }

  /**Read the manifest and build a map from entry names to content types.
   * Only names for which content types were explicitly given are added to the
   * dictionary. The range of the mapping is arrays of content-type field body
   * tokens returned by the lexer. Non-text content types are replaced by the
   * value {@link JarX#NOTTEXT}, a list of no tokens.
   *@param is an input stream already open on the manifest
   *@param d dictionary in which to build the name to type map
   *@throws IOException if unable to read the manifest
   */
  public void manifest( InputStream is, Dictionary d) throws IOException {
    InputStreamReader isr;
    String enc = blindDesperateSearchForCorrespondingEncodingName(manifestCode);
    if ( enc != null )
      isr = new InputStreamReader( is, enc);
    else
      isr = new InputStreamReader( is); // and hope for the best
    BufferedReader br = new BufferedReader( isr);
    
    while ( section( br, d) ); /**/
    
    store( manifestName, manifestType, d);
  }

  /**Process one manifest section, adding a dictionary entry if the section
   * contains both a <CODE>Name:</CODE> and a <CODE>Content-Type</CODE>
   * attribute.
   *@param r BufferedReader already open on the manifest
   *@param d dictionary in which to add the mapping
   *@return true if there is another section to read, false if the end of the
   * manifest has been reached
   *@throws IOException if the manifest can't be read
   */
  public boolean section( BufferedReader r, Dictionary d)
  throws IOException {
    String field;
    String front;
    String name = null;
    JarX[] type = null;
    
    for ( ;; ) {
      field = header( r);
      if ( field == null  ||  0 == field.length() )
      	break;
      front = field.toLowerCase();
      if ( front.startsWith( "name: ") ) {
      	if ( name == null )
	  name = field.substring( 6);
	else
	  System.err.println(
      	      	"Warning: name attribute repeated within a section, ignored.");
      }
      else if ( front.startsWith( "content-type: ") ) {
      	if ( type == null ) {
	  type = structuredFieldBody( field, 14);
	  if ( ! type[0].value.equalsIgnoreCase( "text")
	    || ! type[1].value.equals( "/") )
	    type = NOTTEXT;
	}
	else
	  System.err.println(
      	 "Warning: content-type attribute repeated within a section, ignored.");
      }
    }
    if ( name != null )
      store( name, type, d);
    return field != null;
  }

  /**Enter one name-to-type mapping in a dictionary; overridden in
   * {@link Build}.
   *@param name an entry name
   *@param type its content type (list of field body tokens)
   *@param d dictionary in which to add the mapping
   */
  void store( String name, JarX[] type, Dictionary d) {
    if ( type != null )
      d.put( name, type);
  }
  
  /**Buffer used between calls to {@link #header(BufferedReader) header}.*/
  String nextManifestLine = null;

  /**Return one header line (complete after RFC822 continuation unfolding).
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
        || ! nextManifestLine.startsWith( " ")
        && ! nextManifestLine.startsWith( "\u0009") )
      	break;
      line += nextManifestLine;
    }
    
    return line;
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
   *<A HREF="ftp://ftp.isi.edu/in-notes/rfc0822.txt">RFC822</A>
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
    Vector v = new Vector();
    
    dfa: for ( la = 0; la < buf.length; ) {
      
      if ( end >= beg ) {
      	if ( lastState != COMMENT )
	  v.addElement(new JarX( lastState, new String( buf, beg, end-beg)));
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
      	v.addElement(new JarX( lastState, new String( buf, beg, end-beg)));
    JarX[] result = new JarX [ v.size() ];
    v.copyInto( result);
    return result;
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
     */
    public static void main( String[] args) throws Exception {
      if ( args.length != 2 ) {
      	System.err.println( "usage: JarX.Build jarfile manifest");
	System.exit( 1);
      }
      new Build().build( args[0], args[1]);
    }
    
    /**Names of files to include, in order of appearance in the manifest*/
    Vector names = new Vector();
    /**Content-Types of those files, null if not specified,
     * NOTTEXT if not text
     */
    Vector types = new Vector();
    
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
      
      this.manifest( is, null);
      is.close();
      
      System.err.print( manifestName + " ");
      is = new FileInputStream( manif);
      ze = new ZipEntry( manifestName);
      zos.putNextEntry( ze);
      this.shovelText( is, zos, manifestType);
      is.close();
      zos.closeEntry();
      
      String[] n = new String [ names.size() ];
      JarX[][] t = new JarX   [ types.size() ] [];
      
      names.copyInto( n);
      types.copyInto( t);
      
      for ( int i = 0; i < n.length; ++i ) {
      	if ( n[i].equals( manifestName) )
	  continue;
	System.err.print( n[i] + " ");
	ze = new ZipEntry( n[i]);
	f = new File( File.separatorChar == '/' ? n[i] :
	              n[i].replace( '/', File.separatorChar));
	ze.setTime( f.lastModified());
	is = new FileInputStream( f);
	zos.putNextEntry( ze);
	if ( t[i] == null  ||  t[i] == NOTTEXT )
	  this.shovelBytes( is, zos);
	else
	  this.shovelText( is, zos, t[i]);
	is.close();
	zos.closeEntry();
      }
      
      zos.close();
    }

    /**Overridden to
     * save name-to-type mappings in Vectors instead of the dictionary, to
     * preserve the order of names in the manifest.
     */
    void store( String name, JarX[] type, Dictionary d) {
      names.addElement( name);
      types.addElement( type);
    }

    /**Overridden to apply the named encoding to the output stream (jar entry),
     * the platform default encoding to the input stream (local file), and
     * use the RFC2046-required CRLF line separator on the output.
     *@param is source of input (local file)
     *@param os destination of output (jar entry)
     *@param enc Java name of encoding to use
     */
    public void
    shovelLines( InputStream is, OutputStream os, String enc)
    throws IOException {
      InputStreamReader isr = new InputStreamReader( is);
      BufferedReader br = new BufferedReader( isr);

      byte[] crlf = "\r\n".getBytes( enc);

      String s;
      byte[] b;
 
      for ( ;; ) {
    	s = br.readLine();
    	if ( s == null )
    	  break;
    	b = s.getBytes( enc);
    	if ( ! new String( b, enc).equals( s) ) {
    	  System.err.println( "Characters unrepresentable in jar encoding!");
    	  System.exit( 1);
    	}
    	os.write( b);
    	os.write( crlf);
      }
 
      System.err.println( "as lines ("+isr.getEncoding()+" -> "+enc+")");
    }
  
    /**Overridden to apply the named encoding to the output stream (jar entry)
     * and the platform default encoding to the input stream (local file).
     *@param is source of input (local file)
     *@param os destination of output (jar entry)
     *@param enc Java name of encoding to use
     */
    public void
    shovelChars( InputStream is, OutputStream os, String enc)
    throws IOException {
      InputStreamReader isr = new InputStreamReader( is);
      char[] c = new char [ 1024 ];
      byte[] b;
      int got;
      String s;

      for ( ;; ) {
	got = isr.read( c, 0, c.length);
	if ( got == -1 )
      	  break;
	s = new String( c, 0, got);
	b = s.getBytes( enc);
	if ( ! new String( b, enc).equals( s) ) {
      	  System.err.println( "Characters unrepresentable in jar encoding!");
	  System.exit( 1);
	}
	os.write( b);
      }
      os.flush();
      System.err.println( "as characters ("+isr.getEncoding()+" -> "+enc+")");
    }
  }
}
