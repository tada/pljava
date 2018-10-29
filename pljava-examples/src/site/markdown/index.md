## About PL/Java examples

The `pljava-examples` subproject builds the supplied examples; this
machine-generated page is a project summary for developers of PL/Java.

If you arrived here from a search for PL/Java examples, you probably want
[the Examples section of the user guide][esug], or maybe
[to browse the example sources][tbtes], or even [read their javadocs][rtj].
(Note: the source browser link shows the current development sources, which
may differ from a particular release.)

### Optionally-built example code for XML processing with Saxon

The [optional example code][exsaxon] for providing actual XML Query-based
alternatives to PostgreSQL's XPath 1.0-based query and `XMLTABLE` functions
does not get built by default, because it pulls in the sizeable [Saxon-HE][]
library from Saxonica, and because (unlike the rest of PL/Java) it requires
Java 8.

To include these optional functions when building the examples, be sure to use
a Java 8 build environment, and add `-Psaxon-examples` to the `mvn` command
line. The functions are [documented here][exsaxon].


[esug]: ../examples/examples.html
[tbtes]: https://github.com/tada/pljava/tree/master/pljava-examples/src/main/java/org/postgresql/pljava/example
[rtj]: apidocs/index.html
[appcds]: ../install/appcds.html
[j9cds]: ../install/oj9vmopt.html#How_to_set_up_class_sharing_in_OpenJ9
[Saxon-HE]: http://www.saxonica.com/html/products/products.html
[exsaxon]: ../examples/saxon.html
