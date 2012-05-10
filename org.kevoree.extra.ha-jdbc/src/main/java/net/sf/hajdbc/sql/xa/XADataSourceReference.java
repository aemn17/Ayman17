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
package net.sf.hajdbc.sql.xa;

import javax.naming.StringRefAddr;
import javax.sql.XADataSource;

import net.sf.hajdbc.DatabaseClusterConfigurationFactory;
import net.sf.hajdbc.sql.CommonDataSourceReference;

/**
 * @author Paul Ferraro
 *
 */
public class XADataSourceReference extends CommonDataSourceReference<XADataSource>
{
	public static final String FORCE_2PC = "force2PC";
	
	private static final long serialVersionUID = -1333879245815171042L;
	
	/**
	 * Constructs a reference to an <code>XADataSource</code> for the specified cluster
	 * @param cluster a cluster identifier
	 */
	public XADataSourceReference(String cluster)
	{
		this(cluster, (String) null);
	}

	/**
	 * Constructs a reference to an <code>XADataSource</code> for the specified cluster
	 * @param cluster a cluster identifier
	 * @param config the uri of the configuration file
	 */
	public XADataSourceReference(String cluster, String config)
	{
		this(cluster, config, false);
	}

	public XADataSourceReference(String cluster, boolean force2PC)
	{
		this(cluster, (String) null, force2PC);
	}
	
	public XADataSourceReference(String cluster, String config, boolean force2PC)
	{
		super(XADataSource.class, XADataSourceFactory.class, cluster, XADataSourceDatabaseClusterConfiguration.class, config);

		this.setForce2PC(force2PC);
	}
	
	public XADataSourceReference(String cluster, DatabaseClusterConfigurationFactory<XADataSource, XADataSourceDatabase> factory)
	{
		this(cluster, factory, false);
	}
	
	public XADataSourceReference(String cluster, DatabaseClusterConfigurationFactory<XADataSource, XADataSourceDatabase> factory, boolean force2PC)
	{
		super(XADataSource.class, XADataSourceFactory.class, cluster, factory);

		this.setForce2PC(force2PC);
	}
	
	private void setForce2PC(boolean force2PC)
	{
		this.add(new StringRefAddr(FORCE_2PC, Boolean.toString(force2PC)));
	}
}
