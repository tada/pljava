/*
 * This file contains software that has been made available under The Mozilla
 * Public License 1.1. Use and distribution hereof are subject to the
 * restrictions set forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden All Rights Reserved
 */
package org.postgresql.pljava.jdbc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.ParameterMetaData;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import org.postgresql.pljava.internal.ExecutionPlan;
import org.postgresql.pljava.internal.Oid;
import org.postgresql.pljava.internal.SPI;
import org.postgresql.pljava.internal.SPIException;

/**
 *
 * @author Thomas Hallgren
 */
public class SPIPreparedStatement extends SPIStatement implements PreparedStatement
{
	private static final Object s_undef = new Object();

	public static class ParamEntry
	{
		private final int    m_columnIndex;
		private final int    m_sqlType;
		private final Object m_value;

		ParamEntry(int columnIndex, int sqlType, Object value)
		{
			m_columnIndex = columnIndex;
			m_sqlType = sqlType;
			m_value = value;
		}

		int getIndex()
		{
			return m_columnIndex;
		}

		int getSqlType()
		{
			return m_sqlType;
		}

		Oid getTypeId()
		{
			Oid id = (m_sqlType == Types.OTHER)
				? Oid.forJavaClass(m_value.getClass())
				: Oid.forSqlType(m_sqlType);
	
			// Default to String.
			//
			if(id == null)
				id = Oid.forSqlType(Types.VARCHAR);
			return id;
		}

		Object getValue()
		{
			return m_value;
		}
	}

	private final String m_statement;
	private final int    m_paramCount;
	private final ArrayList m_paramList;
	private ExecutionPlan m_plan;
	private int[] m_sqlTypes;
	private Oid[] m_typeIds;

	public SPIPreparedStatement(SPIConnection conn, String statement, int paramCount)
	{
		super(conn);
		m_statement  = statement;
		m_paramCount = paramCount;
		m_paramList  = new ArrayList(m_paramCount);
	}

	public void close()
	{
		if(m_plan != null)
		{
			m_plan.invalidate();
			m_plan = null;
		}
	}

	public ResultSet executeQuery()
	throws SQLException
	{
		this.execute();
		return this.getResultSet();
	}

	public int executeUpdate()
	throws SQLException
	{
		this.execute();
		return this.getUpdateCount();
	}

	public void setNull(int columnIndex, int sqlType)
	throws SQLException
	{
		this.setObject(columnIndex, null, sqlType);
	}

	public void setBoolean(int columnIndex, boolean value) throws SQLException
	{
		this.setObject(columnIndex, value ? Boolean.TRUE : Boolean.FALSE, Types.BOOLEAN);
	}

	public void setByte(int columnIndex, byte value) throws SQLException
	{
		this.setObject(columnIndex, new Byte(value), Types.TINYINT);
	}

	public void setShort(int columnIndex, short value) throws SQLException
	{
		this.setObject(columnIndex, new Short(value), Types.SMALLINT);
	}

	public void setInt(int columnIndex, int value) throws SQLException
	{
		this.setObject(columnIndex, new Integer(value), Types.INTEGER);
	}

	public void setLong(int columnIndex, long value) throws SQLException
	{
		this.setObject(columnIndex, new Long(value), Types.BIGINT);
	}

	public void setFloat(int columnIndex, float value) throws SQLException
	{
		this.setObject(columnIndex, new Float(value), Types.FLOAT);
	}

	public void setDouble(int columnIndex, double value) throws SQLException
	{
		this.setObject(columnIndex, new Double(value), Types.DOUBLE);
	}

	public void setBigDecimal(int columnIndex, BigDecimal value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DECIMAL);
	}

	public void setString(int columnIndex, String value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.VARCHAR);
	}

	public void setBytes(int columnIndex, byte[] value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.VARBINARY);
	}

	public void setDate(int columnIndex, Date value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DATE);
	}

	public void setTime(int columnIndex, Time value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.TIME);
	}

	public void setTimestamp(int columnIndex, Timestamp value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.TIMESTAMP);
	}

	public void setAsciiStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		try
		{
			m_paramList.add(new ParamEntry(columnIndex, Types.CLOB,
					new ClobValue(new InputStreamReader(value, "US-ASCII"), length)));
		}
		catch(UnsupportedEncodingException e)
		{
			throw new SQLException("US-ASCII encoding is not supported by this JVM");
		}
	}

	/**
	 * @deprecated
	 */
	public void setUnicodeStream(int columnIndex, InputStream value, int arg2) throws SQLException
	{
		throw new UnsupportedFeatureException("PreparedStatement.setUnicodeStream");
	}

	public void setBinaryStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		this.setObject(columnIndex, new BlobValue(value, length), Types.BLOB);
	}

	public void clearParameters() throws SQLException
	{
		m_paramList.clear();
	}

	public void setObject(int columnIndex, Object value, int sqlType, int scale)
	throws SQLException
	{
		this.setObject(columnIndex, value, sqlType);
	}

	public void setObject(int columnIndex, Object value, int sqlType)
	throws SQLException
	{
		if(columnIndex < 1 || columnIndex > m_paramCount)
			throw new SQLException("Illegal parameter index");

		m_paramList.add(new ParamEntry(columnIndex - 1, sqlType, value));
	}

	public void setObject(int columnIndex, Object value)
	throws SQLException
	{
		if(value == null)
			throw new SQLException("Can't assign null unless the SQL type is known");

		this.setObject(columnIndex, value, SPIConnection.getTypeForClass(value.getClass()));
	}

	/**
	 * Obtains the XOPEN SQL types for the parameters. 
	 * @return The array of types.
	 */
	private int[] getSqlTypes()
	{
		if(m_sqlTypes != null)
			return m_sqlTypes;

		int top = m_paramList.size();
		int[] types   = new int[m_paramCount];
		Arrays.fill(types, Types.VARCHAR);	// Default.
		for(int idx = 0; idx < top; ++idx)
		{
			ParamEntry pe = (ParamEntry)m_paramList.get(top);
			types[pe.getIndex()] = pe.getSqlType();
		}

		m_sqlTypes = types;
		return types;
	}

	public boolean execute()
	throws SQLException
	{
		Object[] values = null;
		ArrayList params = m_paramList;
		int top = params.size();
		if(top < m_paramCount)
			throw new SQLException("Not all parameters have been set");

		if(top > 0)
		{
			// Instead of checking that top does not exceed paramCount, we
			// verify no value is set more than once. Since the size of the
			// paramList is equal or greater, this will ensure that all values
			// have been set and that no more values exist.
			//
			values = new Object[m_paramCount];
			Arrays.fill(values, s_undef);
			for(int idx = 0; idx < top; ++idx)
			{
				ParamEntry pe = (ParamEntry)params.get(top);
				int pIdx = pe.getIndex();
				if(values[pIdx] != s_undef)
					throw new SQLException("Parameter with index " + (idx + 1) + " was set more than once");
				values[pIdx] = pe.getValue();
			}
		}

		if(m_plan == null)
		{	
			if(m_typeIds == null && top > 0)
			{	
				int[] types   = new int[top];
				Oid[] typeIds = new Oid[top];
				for(int idx = 0; idx < top; ++idx)
				{
					ParamEntry pe = (ParamEntry)params.get(top);
					int pIdx = pe.getIndex();
					types[pIdx] = pe.getSqlType();
					typeIds[pIdx] = pe.getTypeId();
				}
				m_sqlTypes = types;
				m_typeIds  = typeIds;
			}
			m_plan = ExecutionPlan.prepare(m_statement, m_typeIds);
			if(m_plan == null)
				throw new SPIException(SPI.getResult());
			m_plan.makeDurable();
		}

		return this.executePlan(m_plan, values);
	}

	/**
	 * The prepared statement cannot be used for executing oter statements.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public boolean execute(String statement)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Can't execute other statements using a prepared statement");
	}

	public void addBatch()
	throws SQLException
	{
		this.internalAddBatch(m_paramList.clone());
		m_paramList.clear();
	}

	/**
	 * The prepared statement cannot have other statements added too it.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public void addBatch(String statement)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Can't add batch statements to a prepared statement");
	}

	public void setCharacterStream(int columnIndex, Reader value, int length)
	throws SQLException
	{
		this.setObject(columnIndex, new ClobValue(value, length), Types.CLOB);
	}

	public void setRef(int columnIndex, Ref value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.REF);
	}

	public void setBlob(int columnIndex, Blob value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.BLOB);
	}

	public void setClob(int columnIndex, Clob value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.CLOB);
	}

	public void setArray(int columnIndex, Array value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.ARRAY);
	}

	/**
	 * ResultSetMetaData is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		throw new UnsupportedFeatureException("ResultSet meta data is not yet implemented");
	}

	public void setDate(int columnIndex, Date value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.DATE);
		throw new UnsupportedFeatureException("Setting date using explicit Calendar");
	}

	public void setTime(int columnIndex, Time value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.TIME);
		throw new UnsupportedFeatureException("Setting time using explicit Calendar");
	}

	public void setTimestamp(int columnIndex, Timestamp value, Calendar cal)
	throws SQLException
	{
		if(cal == null || cal == Calendar.getInstance())
			this.setObject(columnIndex, value, Types.TIMESTAMP);
		throw new UnsupportedFeatureException("Setting time using explicit Calendar");
	}

	public void setNull(int columnIndex, int sqlType, String typeName)
	throws SQLException
	{
		this.setNull(columnIndex, sqlType);
	}

	public void setURL(int columnIndex, URL value) throws SQLException
	{
		this.setObject(columnIndex, value, Types.DATALINK);
	}

	/**
	 * Due to the design of the <code>SPI_prepare</code>, it is currently impossible to
	 * obtain the correct parameter meta data before all the parameters have been
	 * set, hence a ParameterMetaData obtained prior to setting the paramteres
	 * will have all parameters set to the default type {@link Types#VARCHAR}.
	 * Once the parameters have been set, a fair attempt is made to generate this
	 * object based on the supplied values.
	 * @return The meta data for parameter values.
	 */
	public ParameterMetaData getParameterMetaData()
	throws SQLException
	{
		return new SPIParameterMetaData(this.getSqlTypes());
	}

	protected int executeBatchEntry(Object batchEntry)
	throws SQLException
	{
		int ret = SUCCESS_NO_INFO;
		this.clearParameters();
		m_paramList.addAll((ArrayList)batchEntry);
		if(this.execute())
			this.getResultSet().close();
		else
		{	
			int updCount = this.getUpdateCount();
			if(updCount >= 0)
				ret = updCount;
		}
		return ret;
	}
}