/*
 * Copyright (c) 2004, 2005 TADA AB - Taby Sweden
 * Distributed under the terms shown in the file COPYRIGHT
 * found in the root folder of this project or at
 * http://eng.tada.se/osprojects/COPYRIGHT.html
 */
package org.postgresql.pljava.jdbc;

/**
 *
 * @author Filip Hrbek
 */

public class ResultSetField
{
    private final String m_name;
    private final int m_oid;
    private final int m_len;
    private final int m_mod;

    /*
     * Construct a field based on the information fed to it.
     *
     * @param name the name (column name and label) of the field
     * @param oid the OID of the field
     * @param len the length of the field
     */
    public ResultSetField(String name, int oid, int len, int mod)
    {
        m_name = name;
        m_oid = oid;
        m_len = len;
        m_mod = mod;
    }

    /*
     * Constructor without mod parameter.
     *
     * @param name the name (column name and label) of the field
     * @param oid the OID of the field
     * @param len the length of the field
     */
    public ResultSetField(String name, int oid, int len)
    {
        this(name, oid, len, 0);
    }

    /*
     * @return the oid of this Field's data type
     */
    public final int getOID()
    {
        return m_oid;
    }

    /*
     * @return the mod of this Field's data type
     */
    public final int getMod()
    {
        return m_mod;
    }

    /*
     * @return the column label of this Field's data type
     */
    public final String getColumnLabel()
    {
        return m_name;
    }

    /*
     * @return the length of this Field's data type
     */
    public final int getLength()
    {
        return m_len;
    }
 }