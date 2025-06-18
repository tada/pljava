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
 *   PostgreSQL Global Development Group
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import static java.nio.charset.StandardCharsets.US_ASCII;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.SQLXML;
import java.util.Arrays;
import java.util.Calendar;

import org.postgresql.pljava.internal.ExecutionPlan;
import org.postgresql.pljava.internal.Oid;

/**
 * Implementation of {@link PreparedStatement} for the SPI connection.
 * @author Thomas Hallgren
 */
public class SPIPreparedStatement extends SPIStatement implements PreparedStatement
{
	private final Oid[]    m_typeIds;
	private final Object[] m_values;
	private final int[]    m_sqlTypes;
	private final String   m_statement;
	private ExecutionPlan  m_plan;

	public SPIPreparedStatement(SPIConnection conn, String statement, int paramCount)
	{
		super(conn);
		m_statement = statement;
		m_typeIds   = new Oid[paramCount];
		m_values    = new Object[paramCount];
		m_sqlTypes  = new int[paramCount];
		Arrays.fill(m_sqlTypes, Types.NULL);
	}

	@Override
	public void close()
	throws SQLException
	{
		if(m_plan != null)
		{
			m_plan.close();
			m_plan = null;
		}
		clearParameters();
		super.close();
	}

	@Override
	public ResultSet executeQuery()
	throws SQLException
	{
		execute();
		return getResultSet();
	}

	@Override
	public int executeUpdate()
	throws SQLException
	{
		execute();
		return getUpdateCount();
	}

	@Override
	public void setNull(int columnIndex, int sqlType)
	throws SQLException
	{
		setObject(columnIndex, null, sqlType);
	}

	@Override
	public void setBoolean(int columnIndex, boolean value) throws SQLException
	{
		setObject(columnIndex, value, Types.BOOLEAN);
	}

	@Override
	public void setByte(int columnIndex, byte value) throws SQLException
	{
		setObject(columnIndex, value, Types.TINYINT);
	}

	@Override
	public void setShort(int columnIndex, short value) throws SQLException
	{
		setObject(columnIndex, value, Types.SMALLINT);
	}

	@Override
	public void setInt(int columnIndex, int value) throws SQLException
	{
		setObject(columnIndex, value, Types.INTEGER);
	}

	@Override
	public void setLong(int columnIndex, long value) throws SQLException
	{
		setObject(columnIndex, value, Types.BIGINT);
	}

	@Override
	public void setFloat(int columnIndex, float value) throws SQLException
	{
		setObject(columnIndex, value, Types.FLOAT);
	}

	@Override
	public void setDouble(int columnIndex, double value) throws SQLException
	{
		setObject(columnIndex, value, Types.DOUBLE);
	}

	@Override
	public void setBigDecimal(int columnIndex, BigDecimal value) throws SQLException
	{
		setObject(columnIndex, value, Types.DECIMAL);
	}

	@Override
	public void setString(int columnIndex, String value) throws SQLException
	{
		setObject(columnIndex, value, Types.VARCHAR);
	}

	@Override
	public void setBytes(int columnIndex, byte[] value) throws SQLException
	{
		setObject(columnIndex, value, Types.VARBINARY);
	}

	@Override
	public void setDate(int columnIndex, Date value) throws SQLException
	{
		setObject(columnIndex, value, Types.DATE);
	}

	@Override
	public void setTime(int columnIndex, Time value) throws SQLException
	{
		setObject(columnIndex, value, Types.TIME);
	}

	@Override
	public void setTimestamp(int columnIndex, Timestamp value) throws SQLException
	{
		setObject(columnIndex, value, Types.TIMESTAMP);
	}

	@Override
	public void setAsciiStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		setObject(columnIndex,
			new ClobValue(new InputStreamReader(value, US_ASCII), length),
			Types.CLOB);
	}

	@SuppressWarnings("deprecation") @Override
	public void setUnicodeStream(int columnIndex, InputStream value, int arg2) throws SQLException
	{
		throw new UnsupportedFeatureException("PreparedStatement.setUnicodeStream");
	}

	@Override
	public void setBinaryStream(int columnIndex, InputStream value, int length) throws SQLException
	{
		setObject(columnIndex, new BlobValue(value, length), Types.BLOB);
	}

	@Override
	public void clearParameters()
	throws SQLException
	{
		Arrays.fill(m_values,   null);
		Arrays.fill(m_sqlTypes, Types.NULL);
	}

	/**
	 * Implemented on {@link #setObject(int,Object,int)}, discarding scale.
	 */
	@Override
	public void setObject(int columnIndex, Object value, int sqlType, int scale)
	throws SQLException
	{
		setObject(columnIndex, value, sqlType);
	}

	@Override
	public void setObject(int columnIndex, Object value, int sqlType)
	throws SQLException
	{
		setObject(columnIndex, value, sqlType, TypeBridge.wrap(value));
	}

	private void setObject(
		int columnIndex, Object value, int sqlType, TypeBridge<?>.Holder vAlt)
	throws SQLException
	{
		if(columnIndex < 1 || columnIndex > m_sqlTypes.length)
			throw new SQLException("Illegal parameter index");

		Oid id = null;

		if ( null != vAlt )
			id = new Oid(vAlt.defaultOid());
		else if ( sqlType != Types.OTHER )
			id = Oid.forSqlType(sqlType);
		else
			id = Oid.forJavaObject(value);

		// Default to String.
		//
		if(id == null)
			id = Oid.forSqlType(Types.VARCHAR);

		Oid op = m_typeIds[--columnIndex];

		/*
		 * Coordinate this behavior with the newly-implemented
		 * setNull(int,int,String), which can have been used to set a specific
		 * PostgreSQL type oid that is not the default mapping from any JDBC
		 * type.
		 *
		 * If no oid has already been set, unconditionally assign the one just
		 * chosen above. If the one just chosen matches one already set, do
		 * nothing. Otherwise, assign the one just chosen and re-prepare, but
		 * ONLY IF WE HAVE NOT BEEN GIVEN A TYPEBRIDGE.HOLDER. If a Holder is
		 * supplied, the value is of one of the types newly allowed for 1.5.1;
		 * it is safe to introduce a different behavior with those, as they had
		 * no prior behavior to match.
		 *
		 * The behavior for the new types is to NOT overwrite whatever PG oid
		 * may have been already assigned, but to simply pass the Holder and
		 * hope the native Type implementation knows how to munge the object
		 * to that PG type. An exception will ensue if it does not.
		 *
		 * The ultimate (future major release) way for PreparedStatement
		 * parameter typing to work will be to rely on the improved SPI from
		 * PG 9.0 to find out the parameter types PostgreSQL's type inference
		 * has come up with, and treat assignments here as coercions to those,
		 * just as for result-set updaters. That will moot most of these goofy
		 * half-measures here. https://www.postgresql.org/message-id/
		 * d5ecbef6-88ee-85d8-7cc2-8c8741174f2d%40anastigmatix.net
		 */

		if(op == null)
			m_typeIds[columnIndex] = id;
		else if ( null == vAlt  &&  !op.equals(id) )
		{
			m_typeIds[columnIndex] = id;
			
			// We must re-prepare
			//
			if ( m_plan != null )
			{
				m_plan.close();
				m_plan = null;
			}
		}
		m_sqlTypes[columnIndex] = sqlType;
		m_values[columnIndex] = null == vAlt ? value : vAlt;
	}

	@Override
	public void setObject(int columnIndex, Object value)
	throws SQLException
	{
		if(value == null)
			throw new SQLException(
				"Can't assign null unless the SQL type is known");

		TypeBridge<?>.Holder vAlt = TypeBridge.wrap(value);

		int sqlType;
		if ( null == vAlt )
			sqlType = SPIConnection.getTypeForClass(value.getClass());
		else
			sqlType = Types.OTHER;

		setObject(columnIndex, value, sqlType, vAlt);
	}

	/**
	 * Obtains the XOPEN SQL types for the parameters. 
	 * @return The array of types.
	 */
	private int[] getSqlTypes()
	{
		int   idx   = m_sqlTypes.length;
		int[] types = (int[])m_sqlTypes.clone();
		while(--idx >= 0)
		{
			if(types[idx] == Types.NULL)
				types[idx] = Types.VARCHAR;	// Default.
		}
		return types;
	}

	@Override
	public boolean execute()
	throws SQLException
	{
		int[] sqlTypes = m_sqlTypes;
		int idx = sqlTypes.length;
		while(--idx >= 0)
			if(sqlTypes[idx] == Types.NULL)
				throw new SQLException("Not all parameters have been set");

		if(m_plan == null)
			m_plan = ExecutionPlan.prepare(m_statement, m_typeIds);

		boolean result = executePlan(m_plan, m_values);
		clearParameters(); // Parameters are cleared upon successful completion.
		return result;
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

	@Override
	public void addBatch()
	throws SQLException
	{
		internalAddBatch(new Object[]{m_values.clone(), m_sqlTypes.clone(), m_typeIds.clone()});
		clearParameters(); // Parameters are cleared upon successful completion.
	}

	/**
	 * The prepared statement cannot have other statements added too it.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public void addBatch(String statement)
	throws SQLException
	{
		throw new UnsupportedFeatureException("Can't add batch statements to a prepared statement");
	}

	@Override
	public void setCharacterStream(int columnIndex, Reader value, int length)
	throws SQLException
	{
		setObject(columnIndex, new ClobValue(value, length), Types.CLOB);
	}

	@Override
	public void setRef(int columnIndex, Ref value) throws SQLException
	{
		setObject(columnIndex, value, Types.REF);
	}

	@Override
	public void setBlob(int columnIndex, Blob value) throws SQLException
	{
		setObject(columnIndex, value, Types.BLOB);
	}

	@Override
	public void setClob(int columnIndex, Clob value) throws SQLException
	{
		setObject(columnIndex, value, Types.CLOB);
	}

	@Override
	public void setArray(int columnIndex, Array value) throws SQLException
	{
		setObject(columnIndex, value, Types.ARRAY);
	}

	/**
	 * ResultSetMetaData is not yet supported.
	 * @throws SQLException indicating that this feature is not supported.
	 */
	@Override
	public ResultSetMetaData getMetaData()
	throws SQLException
	{
		throw new UnsupportedFeatureException("ResultSet meta data is not yet implemented");
	}

	@Override
	public void setDate(int columnIndex, Date value, Calendar cal)
	throws SQLException
	{
		if(cal != null && cal != Calendar.getInstance())
			throw new UnsupportedFeatureException(
				"Setting date using explicit Calendar");
		setObject(columnIndex, value, Types.DATE);
	}

	@Override
	public void setTime(int columnIndex, Time value, Calendar cal)
	throws SQLException
	{
		if(cal != null && cal != Calendar.getInstance())
			throw new UnsupportedFeatureException(
				"Setting time using explicit Calendar");
		setObject(columnIndex, value, Types.TIME);
	}

	@Override
	public void setTimestamp(int columnIndex, Timestamp value, Calendar cal)
	throws SQLException
	{
		if(cal != null && cal != Calendar.getInstance())
			throw new UnsupportedFeatureException(
				"Setting time using explicit Calendar");
		setObject(columnIndex, value, Types.TIMESTAMP);
	}

	/**
	 * This method can (and is the only method that can, until JDBC 4.2 SQLType
	 * is implemented) assign a specific PostgreSQL type, by name, to a
	 * PreparedStatement parameter.
	 *<p>
	 * However, to avoid a substantial behavior change in a 1.5.x minor release,
	 * its effect is limited for now. Any subsequent assignment of a non-null
	 * value for the parameter, using any of the setter methods or
	 * setObject-accepted classes from pre-JDBC 4.2, will reset the associated
	 * PostgreSQL type to what would have been assigned according to the JDBC
	 * {@code sqlType} or the type of the object.
	 *<p>
	 * In contrast, setObject with any of the object types newly recognized
	 * in PL/Java 1.5.1 will not overwrite the PostgreSQL type assigned by this
	 * method, but will let it stand, on the assumption that the object's native
	 * to-Datum coercions will include one that applies to the type. If not, an
	 * exception will result.
	 *<p>
	 * The {@code sqlType} supplied here will be remembered, only to be used by
	 * the somewhat-functionally-impaired {@code ParameterMetaData}
	 * implementation. It is not checked for compatibility with the supplied
	 * PostgreSQL {@code typeName} in any way.
	 */
	@Override
	public void setNull(int columnIndex, int sqlType, String typeName)
	throws SQLException
	{
		Oid id = Oid.forTypeName(typeName);
		Oid op = m_typeIds[--columnIndex];
		if ( null == op )
			m_typeIds[columnIndex] = id;
		else if ( !op.equals(id) )
		{
			m_typeIds[columnIndex] = id;
			if ( null != m_plan )
			{
				m_plan.close();
				m_plan = null;
			}
		}
		m_sqlTypes[columnIndex] = sqlType;
		m_values[columnIndex] = null;
	}

	@Override
	public void setURL(int columnIndex, URL value) throws SQLException
	{
		setObject(columnIndex, value, Types.DATALINK);
	}

	public String toString()
	{
		return m_statement;
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
	@Override
	public ParameterMetaData getParameterMetaData()
	throws SQLException
	{
		return new SPIParameterMetaData(getSqlTypes());
	}

	protected long executeBatchEntry(Object batchEntry)
	throws SQLException
	{
		long ret = SUCCESS_NO_INFO;
		Object batchParams[] = (Object[])batchEntry;
		Object batchValues = batchParams[0];
		Object batchSqlTypes = batchParams[1];
		Object batchTypeIds[] = (Object[])batchParams[2];

		System.arraycopy(batchValues, 0, m_values, 0, m_values.length);
		System.arraycopy(batchSqlTypes, 0, m_sqlTypes, 0, m_sqlTypes.length);

		// Determine if we need to replan the query because the
		// types have changed from the last execution.
		//
		for (int i=0; i<m_typeIds.length; i++) {
			if (m_typeIds[i] != batchTypeIds[i]) {
				// We must re-prepare
				//
				if(m_plan != null) {
					m_plan.close();
					m_plan = null;
				}

				System.arraycopy(batchTypeIds, 0, m_typeIds, 0, m_typeIds.length);
				break;
			}
		}

		if(execute())
			getResultSet().close();
		else
		{
			long updCount = getUpdateCount();
			if(updCount >= 0)
				ret = updCount;
		}
		return ret;
	}

	// ************************************************************
	// Implementation of JDBC 4 methods. Methods go here if they
	// don't throw SQLFeatureNotSupportedException; they can be
	// considered implemented even if they do nothing useful, as
	// long as that's an allowed behavior by the JDBC spec.
	// ************************************************************

	@Override
	public void setSQLXML(int parameterIndex, SQLXML xmlObject)
		throws SQLException
	{
	    setObject(parameterIndex, xmlObject, Types.SQLXML);
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	@Override
	public void setNClob(int parameterIndex,
			     Reader reader)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNClob( int, Reader ) not implemented yet.",
			  "0A000" );

	}

	@Override
	public void setNClob(int parameterIndex,
			     NClob value)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNClob( int, NClob ) not implemented yet.",
			  "0A000" );

	}

	@Override
	public void setNClob(int parameterIndex,
			 Reader reader,long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNClob( int, Reader, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setBlob(int parameterIndex,
			    InputStream inputStream)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setBlob( int, InputStream ) not "
			  + "implemented yet.",
			  "0A000" );
	}

	@Override
	public void setBlob(int parameterIndex,
			    InputStream inputStream,long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setBlob( int, InputStream, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setClob(int parameterIndex,
			    Reader reader)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setClob( int, Reader ) not implemented yet.",
			  "0A000" );

	}

	@Override
	public void setClob(int parameterIndex,
			    Reader reader,long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setClob( int, Reader, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setNCharacterStream(int parameterIndex,
					Reader value)
	    throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNCharacterStream( int, Reader ) not "
			  + "implemented yet.",
			  "0A000" );

	}

	@Override
	public void setNCharacterStream(int parameterIndex,
					Reader value,long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNCharacterStream( int, Reader, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setCharacterStream(int parameterIndex,
				       Reader reader)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setCharacterStream( int, Reader ) not "
			  + "implemented yet.",
			  "0A000" );

	}

	@Override
	public void setCharacterStream(int parameterIndex,
				       Reader reader, long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setCharacterStream( int, Reader, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setBinaryStream(int parameterIndex,
				    InputStream x)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setBinaryStream( int, InputStream ) not "
			  + "implemented yet.",
			  "0A000" );

	}

	@Override
	public void setBinaryStream(int parameterIndex,
				    InputStream x, long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setBinaryStream( int, InputStream, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setAsciiStream(int parameterIndex,
				   InputStream x)
                    throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setAsciiStream( int, InputStream ) not "
			  + "implemented yet.",
			  "0A000" );

	}
	
	@Override
	public void setAsciiStream(int parameterIndex,
				   InputStream x,long length)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setAsciiStream( int, InputStream, long ) not "
			  + "implemented yet.",
			  "0A000" );

	}

	@Override
	public void setNString(int parameterIndex,
			       String value)
                throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setNString( int, String ) not implemented yet.",
			  "0A000" );
	}
	
	@Override
	public void setRowId(int parameterIndex,
			     RowId x)
		throws SQLException
	{
		throw new SQLFeatureNotSupportedException
			( getClass()
			  + ".setRowId( int, RowId ) not implemented yet.",
			  "0A000" );
	}
}
