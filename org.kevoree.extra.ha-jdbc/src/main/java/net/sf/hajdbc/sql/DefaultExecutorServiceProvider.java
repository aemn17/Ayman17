/*
 * HA-JDBC: High-Availability JDBC
 * Copyright 2004-2009 Paul Ferraro
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.sf.hajdbc.sql;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.xml.bind.annotation.XmlAttribute;

import net.sf.hajdbc.ExecutorServiceProvider;

/**
 * @author paul
 *
 */
public class DefaultExecutorServiceProvider implements ExecutorServiceProvider, Serializable
{
	private static final long serialVersionUID = 5781743869682086889L;
	
	@XmlAttribute(name = "min-threads")
	private int minThreads = 0;
	@XmlAttribute(name = "max-threads")
	private int maxThreads = 100;
	@XmlAttribute(name = "max-idle")
	private int maxIdle = 60;
	
	/**
	 * @return the minThreads
	 */
	public int getMinThreads()
	{
		return this.minThreads;
	}

	/**
	 * @param minThreads the minThreads to set
	 */
	public void setMinThreads(int minThreads)
	{
		this.minThreads = minThreads;
	}

	/**
	 * @return the maxThreads
	 */
	public int getMaxThreads()
	{
		return this.maxThreads;
	}

	/**
	 * @param maxThreads the maxThreads to set
	 */
	public void setMaxThreads(int maxThreads)
	{
		this.maxThreads = maxThreads;
	}

	/**
	 * @return the maxIdle
	 */
	public int getMaxIdle()
	{
		return this.maxIdle;
	}

	/**
	 * @param maxIdle the maxIdle to set
	 */
	public void setMaxIdle(int maxIdle)
	{
		this.maxIdle = maxIdle;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.ExecutorServiceProvider#getExecutor(java.util.concurrent.ThreadFactory)
	 */
	@Override
	public ExecutorService getExecutor(ThreadFactory threadFactory)
	{
		return new ThreadPoolExecutor(this.minThreads, this.maxThreads, this.maxIdle, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), threadFactory, new ThreadPoolExecutor.CallerRunsPolicy());
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.ExecutorServiceProvider#release(java.util.concurrent.ExecutorService)
	 */
	@Override
	public void release(ExecutorService executor)
	{
		executor.shutdown();
	}
}
