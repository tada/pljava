--
-- Install saxon and saxonb jars
--
SET client_min_messages TO NOTICE;

--
-- Install saxon jars
--
SELECT sqlj.install_jar('file:///usr/share/java/saxon.jar', 'saxon', true);
SELECT sqlj.install_jar('file:///usr/share/java/saxon-jdom.jar', 'saxon_jdom', true);

--
-- Install saxonb jars
--
SELECT sqlj.install_jar('file:///usr/share/java/dom4j.jar', 'dom4j', true);
SELECT sqlj.install_jar('file:///usr/share/java/jaxen.jar', 'jaxen', true);
SELECT sqlj.install_jar('file:///usr/share/java/jdom1.jar', 'jdom1', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb.jar', 'saxonb', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-ant.jar', 'saxonb_ant', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-dom.jar', 'saxonb_dom', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-dom4j.jar', 'saxonb_dom4j', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-jdom.jar', 'saxonb_jdom', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-spl.jar', 'saxonb_spl', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-xom.jar', 'saxonb_xom', true);
-- SELECT sqlj.install_jar('file:///usr/share/java/saxonb-xpath.jar', 'saxonb_xpath', true);
SELECT sqlj.install_jar('file:///usr/share/java/xercesImpl.jar', 'xercesImpl', true);
SELECT sqlj.install_jar('file:///usr/share/java/xml-apis-ext.jar', 'xml_apis_ext', true);
SELECT sqlj.install_jar('file:///usr/share/java/xml-commons-external.jar', 'xml_commons_external', true);
SELECT sqlj.install_jar('file:///usr/share/java/xmlParserAPIs.jar', 'xmlParserAPIs', true);
SELECT sqlj.install_jar('file:///usr/share/java/xml-resolver.jar', 'xml_resolver', true);
SELECT sqlj.install_jar('file:///usr/share/java/xom.jar', 'xom', true);
