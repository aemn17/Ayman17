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

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;

import net.sf.hajdbc.cache.AbstractTableProperties;
import net.sf.hajdbc.cache.ColumnProperties;
import net.sf.hajdbc.cache.DatabaseMetaDataSupport;
import net.sf.hajdbc.cache.ForeignKeyConstraint;
import net.sf.hajdbc.cache.QualifiedName;
import net.sf.hajdbc.cache.UniqueConstraint;

/**
 * @author Paul Ferraro
 *
 */
public class EagerTableProperties extends AbstractTableProperties
{
	private Map<String, ColumnProperties> columnMap;
	private UniqueConstraint primaryKey;
	private Collection<UniqueConstraint> uniqueConstraints;
	private Collection<ForeignKeyConstraint> foreignKeyConstraints;
	private Collection<String> identityColumns;
	
	public EagerTableProperties(DatabaseMetaData metaData, DatabaseMetaDataSupport support, QualifiedName table) throws SQLException
	{
		super(support, table);
		
		this.columnMap = support.getColumns(metaData, table);
		this.primaryKey = support.getPrimaryKey(metaData, table);
		this.uniqueConstraints = support.getUniqueConstraints(metaData, table, this.primaryKey);
		this.foreignKeyConstraints = support.getForeignKeyConstraints(metaData, table);
		this.identityColumns = support.getIdentityColumns(this.columnMap.values());
	}

	@Override
	protected Map<String, ColumnProperties> getColumnMap()
	{
		return this.columnMap;
	}

	/**
	 * @see net.sf.hajdbc.cache.TableProperties#getPrimaryKey()
	 */
	@Override
	public UniqueConstraint getPrimaryKey()
	{
		return this.primaryKey;
	}

	/**
	 * @see net.sf.hajdbc.cache.TableProperties#getForeignKeyConstraints()
	 */
	@Override
	public Collection<ForeignKeyConstraint> getForeignKeyConstraints()
	{
		return this.foreignKeyConstraints;
	}

	/**
	 * @see net.sf.hajdbc.cache.TableProperties#getUniqueConstraints()
	 */
	@Override
	public Collection<UniqueConstraint> getUniqueConstraints()
	{
		return this.uniqueConstraints;
	}

	/**
	 * @see net.sf.hajdbc.cache.TableProperties#getIdentityColumns()
	 */
	@Override
	public Collection<String> getIdentityColumns() throws SQLException
	{
		return this.identityColumns;
	}
}
