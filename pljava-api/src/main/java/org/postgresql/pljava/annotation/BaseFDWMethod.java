/*
 * Copyright (c) 2015-2022 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Chapman Flack
 */
package org.postgresql.pljava.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import java.sql.SQLData; // referred to in javadoc

/**
 * Annotation on a PL/Java method that will ...
 *
 * Would this be better handled via a standardized interface?
 * Or by breaking the enums apart and using logic later?...
 */
@Target(ElementType.METHOD) @Retention(RetentionPolicy.CLASS) @Documented
public @interface BaseFDWMethod
{
    enum Operation {
        // initialization...
        GET_REL_SIZE,
        GET_PATHS,

        // preparation and cleanup
        SCAN_PLAN,
        SCAN_OPEN,
        SCAN_CLOSE,
        SCAN_EXPLAIN,

        INSERT_PLAN,
        INSERT_OPEN,
        INSERT_CLOSE,

        MODIFY_PLAN,
        MODIFY_OPEN,
        MODIFY_CLOSE,
        MODIFY_EXPLAIN,

        DIRECT_PLAN,
        DIRECT_OPEN,
        DIRECT_CLOSE,
        DIRECT_EXPLAIN,

        GET_SCAN_BATCH_SIZE,
        GET_MODIFY_BATCH_SIZE,
        GET_DIRECT_BATCH_SIZE,

        // actual data transfer
        NEXT,
        RESET,

        INSERT,
        INSERT_BATCH,

        UPDATE,
        DELETE,
        TRUNCATE,

        // some of the rest...
        SUPPORTS_PARALLEL_SCANS,
        SUPPORTS_ASYNCHRONOUS_EXECUTION,

        VACUUM,
        ANALYZE,

        IMPORT_SCHEMA_STATEMENT,

        //...
    };

    Operation operation();

    // an alternative...
    enum Step {
        PLAN,
        OPEN,
        CLOSE,
        EXPLAIN
    };

    enum Operation1 {
        SCAN,
        INSERT,
        UPDATE,
        DELETE
        // TRUNCATE
    };

}
