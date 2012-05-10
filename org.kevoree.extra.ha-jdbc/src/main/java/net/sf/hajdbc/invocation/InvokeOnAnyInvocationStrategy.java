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
package net.sf.hajdbc.invocation;

import net.sf.hajdbc.*;
import net.sf.hajdbc.sql.SQLProxy;
import net.sf.hajdbc.state.StateManager;
import net.sf.hajdbc.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.SortedMap;

/**
 * @author Paul Ferraro
 */
public class InvokeOnAnyInvocationStrategy extends InvokeOnNextInvocationStrategy
{
	private static Logger logger = LoggerFactory.getLogger(InvokeOnExistingInvocationStrategy.class);

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.invocation.InvocationStrategy#invoke(net.sf.hajdbc.sql.SQLProxy, net.sf.hajdbc.invocation.Invoker)
	 */
	@Override
	public <Z, D extends Database<Z>, T, R, E extends Exception> SortedMap<D, R> invoke(SQLProxy<Z, D, T, E> proxy, Invoker<Z, D, T, R, E> invoker) throws E
	{
		DatabaseCluster<Z, D> cluster = proxy.getDatabaseCluster();
		Dialect dialect = cluster.getDialect();
		StateManager stateManager = cluster.getStateManager();
		ExceptionFactory<E> exceptionFactory = proxy.getExceptionFactory();

		for (Map.Entry<D, T> entry: proxy.entries())
		{
			D database = entry.getKey();
			
			try
			{
				R result = invoker.invoke(database, entry.getValue());
				
				return Collections.singletonSortedMap(database, result);
			}
			catch (Exception e)
			{
				E exception = exceptionFactory.createException(e);
				
				if (exceptionFactory.indicatesFailure(exception, dialect) && (cluster.getBalancer().size() > 1))
				{
					if (cluster.deactivate(database, stateManager))
					{
						logger.error( Messages.DATABASE_DEACTIVATED.getMessage(), database, cluster);
					}
				}
				else
				{
					throw exception;
				}
			}
		}
		
		return super.invoke(proxy, invoker);
	}
}
