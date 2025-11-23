
## Classes

List of java classes accessed via ClassLoader

- `java/lang/Boolean`
- `java/lang/Byte`
- `java/lang/Class`
- `java/lang/Double`
- `java/lang/Float`
- `java/lang/IllegalArgumentException`
- `java/lang/Integer`
- `java/lang/Long`
- `java/lang/NoSuchFieldError`
- `java/lang/NoSuchMethodError`
- `java/lang/Object`
- `java/lang/Short`
- `java/lang/String`
- `java/lang/System$CallersHolder`
- `java/lang/Thread`
- `java/lang/Throwable`
- `java/lang/UnsupportedOperationException`
- `java/math/BigDecimal`
- `java/nio/Buffer`
- `java/nio/CharBuffer`
- `java/nio/charset/Charset`
- `java/nio/charset/CharsetDecoder`
- `java/nio/charset/CharsetEncoder`
- `java/nio/charset/CoderResult`
- `java/nio/charset/StandardCharsets`
- `java/sql/Date`
- `java/sql/SQLException`
- `java/sql/Time`
- `java/sql/Timestamp`
- `java/time/LocalDate`
- `java/time/LocalDateTime`
- `java/time/LocalTime`
- `java/time/OffsetDateTime`
- `java/time/OffsetTime`
- `java/time/ZoneOffset`
- `java/util/Iterator`
- `java/util/Map`

# List of visible pljava classes accessed via ClassLoader

- `org/postgresql/pljava/jdbc/BlobValue`
- `org/postgresql/pljava/jdbc/Invocation`
- `org/postgresql/pljava/jdbc/SingleRowReader`
- `org/postgresql/pljava/jdbc/SingleRowWriter`
- `org/postgresql/pljava/jdbc/SQLChunkIOOrder`
- `org/postgresql/pljava/jdbc/SQLInputFromChunk`
- `org/postgresql/pljava/jdbc/SQLInputFromTuple`
- `org/postgresql/pljava/jdbc/SQLOutputToChunk`
- `org/postgresql/pljava/jdbc/SQLOutputToTuple`
- `org/postgresql/pljava/jdbc/SQLXMLImpl`
- `org/postgresql/pljava/jdbc/SQLXMLImpl$Readable$PgXML`
- `org/postgresql/pljava/jdbc/SQLXMLImpl$Readable$Synthetic`
- `org/postgresql/pljava/jdbc/SQLXMLImpl$Writable`
- `org/postgresql/pljava/jdbc/TypeBridge`
- `org/postgresql/pljava/jdbc/TypeBridge$Holder`
- `org/postgresql/pljava/ResultSetHandle`
- `org/postgresql/pljava/ResultSetProvider`
- `org/postgresql/pljava/sqlj/Loader`

# List of internal pljava classes accessed via ClassLoader

- `org/postgresql/pljava/internal/AclId`
- `org/postgresql/pljava/internal/Backend`
- `org/postgresql/pljava/internal/Backend$EarlyNatives`
- `org/postgresql/pljava/internal/DualState`
- `org/postgresql/pljava/internal/DualState$Key`
- `org/postgresql/pljava/internal/DualState$SingleFreeErrorData`
- `org/postgresql/pljava/internal/DualState$SingleFreeTupleDesc`
- `org/postgresql/pljava/internal/DualState$SingleHeapFreeTuple`
- `org/postgresql/pljava/internal/DualState$SingleMemContextDelete`
- `org/postgresql/pljava/internal/DualState$SinglePfree`
- `org/postgresql/pljava/internal/DualState$SingleSPIcursorClose`
- `org/postgresql/pljava/internal/DualState$SingleSPIfreeplan`
- `org/postgresql/pljava/internal/EntryPoints`
- `org/postgresql/pljava/internal/ErrorData`
- `org/postgresql/pljava/internal/ExecutionPlan`
- `org/postgresql/pljava/internal/Function`
- `org/postgresql/pljava/internal/Function$EarlyNatives`
- `org/postgresql/pljava/internal/Function$ParameterFrame`
- `org/postgresql/pljava/internal/InstallHelper`
- `org/postgresql/pljava/internal/Oid`
- `org/postgresql/pljava/internal/PgSavepoint`
- `org/postgresql/pljava/internal/Portal`
- `org/postgresql/pljava/internal/Relation`
- `org/postgresql/pljava/internal/ResultSetPicker`
- `org/postgresql/pljava/internal/ServerException`
- `org/postgresql/pljava/internal/Session`
- `org/postgresql/pljava/internal/SPI`
- `org/postgresql/pljava/internal/SubXactListener`
- `org/postgresql/pljava/internal/TriggerData`
- `org/postgresql/pljava/internal/Tuple`
- `org/postgresql/pljava/internal/TupleDesc`
- `org/postgresql/pljava/internal/TupleTable`
- `org/postgresql/pljava/internal/VarlenaWrapper`
- `org/postgresql/pljava/internal/VarlenaWrapper$Input`
- `org/postgresql/pljava/internal/VarlenaWrapper$Input$State`
- `org/postgresql/pljava/internal/VarlenaWrapper$Output`
- `org/postgresql/pljava/internal/VarlenaWrapper$Output$State`
- `org/postgresql/pljava/internal/XactListener`

## Signatures

Method signatures. This less is less reliable due to the simpliest way
I extracted the data.


- `[B`
- `()Lorg/postgresql/pljava/internal/ErrorData;`
- `(I)Lorg/postgresql/pljava/internal/PgSavepoint;`
- `(ILorg/postgresql/pljava/internal/PgSavepoint;"`
- `<init>", "(Lorg/postgresql/pljava/internal/ErrorData;)V`
- `<init>", "(Lorg/postgresql/pljava/internal/TupleDesc;)V`
- `<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Input;I)V`
- `<init>", "(Lorg/postgresql/pljava/internal/VarlenaWrapper$Output;)V`
- `<init>", "(Lorg/postgresql/pljava/ResultSetHandle;)V`
- `(JI)Lorg/postgresql/pljava/internal/Oid;`
- `(JJ[I[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;`
- `(J[Ljava/lang/Object;)Lorg/postgresql/pljava/internal/Tuple;`
- `(JLjava/lang/String;[Ljava/lang/Object;S)Lorg/postgresql/pljava/internal/Portal;`
- `(J)Lorg/postgresql/pljava/internal/Relation;`
- `(J)Lorg/postgresql/pljava/internal/Tuple;`
- `(J)Lorg/postgresql/pljava/internal/TupleDesc;`
- `(Ljava/lang/Class;Lorg/postgresql/pljava/internal/Oid;)V`
- `(Ljava/lang/Object;Ljava/lang/String;[Lorg/postgresql/pljava/internal/Oid;)Lorg/postgresql/pljava/internal/ExecutionPlan;`
- `(Ljava/lang/String;I)Lorg/postgresql/pljava/jdbc/TypeBridge;`
- `(Ljava/lang/String;)Lorg/postgresql/pljava/internal/AclId;`
- `(Ljava/sql/SQLXML;I)Lorg/postgresql/pljava/internal/VarlenaWrapper;`
- `()Lorg/postgresql/pljava/internal/AclId;`
- `(Lorg/postgresql/pljava/internal/AclId;Z)Z`
- `(Lorg/postgresql/pljava/internal/DualState$Key;"`
- `(Lorg/postgresql/pljava/internal/DualState$Key;)J`
- `(Lorg/postgresql/pljava/internal/DualState$Key;J"`
- `(Lorg/postgresql/pljava/internal/DualState$Key;JJI)V`
- `(Lorg/postgresql/pljava/internal/DualState$Key;JJLorg/postgresql/pljava/internal/ExecutionPlan;)V`
- `(Lorg/postgresql/pljava/internal/DualState$Key;JJLorg/postgresql/pljava/internal/TupleDesc;)V`
- `(Lorg/postgresql/pljava/internal/DualState$Key;JJ)V`
- `(Lorg/postgresql/pljava/internal/EntryPoints$Invocable;"`
- `(Lorg/postgresql/pljava/internal/EntryPoints$Invocable;)"`
- `()Lorg/postgresql/pljava/internal/Oid;`
- `(Lorg/postgresql/pljava/internal/Oid;)Z`
- `(Lorg/postgresql/pljava/internal/PgSavepoint;)V`
- `(Lorg/postgresql/pljava/internal/Tuple;`
- `(Lorg/postgresql/pljava/internal/TupleDesc;`
- `(Lorg/postgresql/pljava/internal/TupleDesc;)Lorg/postgresql/pljava/internal/TupleTable;`
- `(Lorg/postgresql/pljava/internal/TupleDesc;[Lorg/postgresql/pljava/internal/Tuple;)V`
- `(Lorg/postgresql/pljava/TriggerData;`
- `()Lorg/postgresql/pljava/jdbc/Invocation;`





## Miscellaneous


files identified by

```shell
$ grep -Ri '"java/"' . (and \"org/postgresql/pljava\", more...)
$  sed -E "s/[^\"]+\"//" /tmp/t2 | sed -E "s/\"[^\"]+$//" > /tmp/t3


```
