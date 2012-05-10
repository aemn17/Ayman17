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
package net.sf.hajdbc.cache.eager;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.Dialect;
import net.sf.hajdbc.cache.DatabaseMetaDataCache;
import net.sf.hajdbc.cache.DatabaseMetaDataSupport;
import net.sf.hajdbc.cache.DatabaseMetaDataSupportFactory;
import net.sf.hajdbc.cache.DatabaseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;

/**
 * Per-database {@link DatabaseMetaDataCache} implementation that populates itself eagerly.
 * @author Paul Ferraro
 */
public class EagerDatabaseMetaDataCache<Z, D extends Database<Z>> implements DatabaseMetaDataCache<Z, D>
{
	private static final Logger logger = LoggerFactory.getLogger(EagerDatabaseMetaDataCache.class);
	
	private final Map<D, DatabaseProperties> map = new TreeMap<D, DatabaseProperties>();
	private final DatabaseCluster<Z, D> cluster;
	private final DatabaseMetaDataSupportFactory factory;
	
	public EagerDatabaseMetaDataCache(DatabaseCluster<Z, D> cluster, DatabaseMetaDataSupportFactory factory)
	{
		this.cluster = cluster;
		this.factory = factory;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.cache.DatabaseMetaDataCache#flush()
	 */
	@Override
	public void flush() throws SQLException
	{
		Map<D, DatabaseProperties> map = new TreeMap<D, DatabaseProperties>();
		
		for (D database: this.cluster.getBalancer())
		{
			Connection connection = database.connect(database.createConnectionSource(), database.decodePassword(this.cluster.getCodec()));
			
			try
			{
				map.put(database, this.createDatabaseProperties(connection));
			}
			finally
			{
				try
				{
					connection.close();
				}
				catch (SQLException e)
				{
					logger.warn(e.toString());
				}
			}
		}
		
		synchronized (this.map)
		{
			this.map.clear();
			this.map.putAll(map);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.cache.DatabaseMetaDataCache#getDatabaseProperties(net.sf.hajdbc.Database, java.sql.Connection)
	 */
	@Override
	public DatabaseProperties getDatabaseProperties(D database, Connection connection) throws SQLException
	{
		synchronized (this.map)
		{
			DatabaseProperties properties = this.map.get(database);
			
			if (properties == null)
			{
				properties = this.createDatabaseProperties(connection);
				
				this.map.put(database, properties);
			}
			
			return properties;
		}
	}
	
	private DatabaseProperties createDatabaseProperties(Connection connection) throws SQLException
	{
		DatabaseMetaData metaData = connection.getMetaData();
		Dialect dialect = this.cluster.getDialect();
		DatabaseMetaDataSupport support = this.factory.createSupport(metaData, dialect);
		return new EagerDatabaseProperties(metaData, support, dialect);
	}
}
