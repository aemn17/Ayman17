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

import java.util.Map;
import java.util.Set;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.ExceptionFactory;

/**
 * @author Paul Ferraro
 * @since 2.0
 */
public interface SQLProxy<Z, D extends Database<Z>, T, E extends Exception>
{
	DatabaseCluster<Z, D> getDatabaseCluster();
	
	Set<Map.Entry<D, T>> entries();
	
	void addChild(SQLProxy<Z, D, ?, ? extends Exception> child);

	void removeChild(SQLProxy<Z, D, ?, ? extends Exception> child);
	
	void removeChildren();

	RootSQLProxy<Z, D, ? extends Exception> getRoot();
	
	T getObject(D database);
	
	ExceptionFactory<E> getExceptionFactory();
	
	void retain(Set<D> databaseSet);	
}
