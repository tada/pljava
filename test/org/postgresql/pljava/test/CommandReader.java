/*
 * This file contains software that has been made available under The BSD
 * license. Use and distribution hereof are subject to the restrictions set
 * forth therein.
 * 
 * Copyright (c) 2003 TADA AB - Taby Sweden
 * All Rights Reserved
 */
package org.postgresql.pljava.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;


/**
 * @author Thomas Hallgren
 */
public class CommandReader extends BufferedReader
{
	private final Process m_process;
	private BufferedReader m_errorReader;

	private CommandReader(Process proc, Reader rdr)
	{
		super(rdr);
		m_process = proc;
	}

	public static CommandReader create(String[] args, String[] env)
	throws IOException
	{
		Runtime rt = Runtime.getRuntime();
		Process proc = rt.exec(args, env);
		return new CommandReader(proc, new InputStreamReader(proc.getInputStream()));
	}
	
	public synchronized BufferedReader getErrorReader()
	{
		if(m_errorReader == null)
			m_errorReader = new BufferedReader(new InputStreamReader(m_process.getErrorStream()));
		return m_errorReader;
	}

	public void close()
	throws IOException
	{
		super.close();
		if(m_errorReader != null)
			m_errorReader.close();
	}
	
	public int getExitValue()
	throws IllegalThreadStateException
	{
		try
		{
			return m_process.exitValue();
		}
		catch(IllegalThreadStateException e)
		{
			try
			{
				m_process.waitFor();
			}
			catch(InterruptedException e2)
			{}
			return m_process.exitValue();
		}
	}
}
