/*
 * Copyright (c) 2004-2020 Tada AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Tada AB
 *   Chapman Flack
 *
 * @author Thomas Hallgren
 */
#ifndef __pljava_type_FDW_priv_h
#define __pljava_type_FDW_priv_h

#include "pljava/type/Type_priv.h"
#include "pljava/type/FDW.h"

#ifdef __cplusplus
extern "C" {
#endif

/**************************************************************************
 * @author Thomas Hallgren
 **************************************************************************/

/**
 * This forms a tree since each wrapper may have multiple servers and
 * each server may have multiple tables.
 *
 * It goes without saying that each table can have multiple concurrent
 * queries even if the database backend only allows one to execute at any
 * time.
 */

struct FDW_
{
    const char *fdw_name;
}

struct FDW_Server
{
    struct FDW_ *fdw;
    const char *server_name;
}


struct FDW_Table
{
    struct FDW_Server *server;
    const char *table_name;
}
}

struct FDW_Plan_State
{
    struct FDW_Table table;
}

struct FDW_Scan_State
{
    struct FDW_Table table;
}

// ???
struct FDW_Modify_State
{
    struct FDW_Table table;
}

// ???
struct FDW_Direct_State
{
    struct FDW_Table table;
}

extern jvalue _FDW_coerceDatum(Type self, Datum value);

#ifdef __cplusplus
}
#endif
#endif
