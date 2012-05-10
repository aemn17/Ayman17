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

import java.lang.reflect.InvocationHandler;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.Map;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.invocation.Invoker;

public class SQLXMLInvocationHandlerFactory<Z, D extends Database<Z>, P> extends LocatorInvocationHandlerFactory<Z, D, P, SQLXML>
{
	/**
	 * Constructs a new SQLXMLInvocationHandlerFactory
	 * @param connection
	 */
	public SQLXMLInvocationHandlerFactory(Connection connection)
	{
		super(SQLXML.class, connection);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.LocatorInvocationHandlerFactory#createInvocationHandler(java.lang.Object, net.sf.hajdbc.sql.SQLProxy, net.sf.hajdbc.invocation.Invoker, java.util.Map, boolean)
	 */
	@Override
	protected InvocationHandler createInvocationHandler(P parent, SQLProxy<Z, D, P, SQLException> proxy, Invoker<Z, D, P, java.sql.SQLXML, SQLException> invoker, Map<D, java.sql.SQLXML> objectMap, boolean updateCopy) throws SQLException
	{
		return new SQLXMLInvocationHandler<Z, D, P>(parent, proxy, invoker, objectMap, updateCopy);
	}
}
