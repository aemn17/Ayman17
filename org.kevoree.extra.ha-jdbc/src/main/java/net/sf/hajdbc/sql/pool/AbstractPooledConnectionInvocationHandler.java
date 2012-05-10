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
package net.sf.hajdbc.sql.pool;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.sql.ConnectionEvent;
import javax.sql.ConnectionEventListener;
import javax.sql.PooledConnection;
import javax.sql.StatementEvent;
import javax.sql.StatementEventListener;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.invocation.InvocationStrategy;
import net.sf.hajdbc.invocation.InvocationStrategyEnum;
import net.sf.hajdbc.invocation.Invoker;
import net.sf.hajdbc.sql.ChildInvocationHandler;
import net.sf.hajdbc.sql.ConnectionInvocationHandlerFactory;
import net.sf.hajdbc.sql.InvocationHandlerFactory;
import net.sf.hajdbc.sql.SQLProxy;
import net.sf.hajdbc.sql.TransactionContext;
import net.sf.hajdbc.util.reflect.Methods;

/**
 * @author Paul Ferraro
 * @param <D> 
 * @param <C> 
 */
@SuppressWarnings("nls")
public abstract class AbstractPooledConnectionInvocationHandler<Z, D extends Database<Z>, C extends PooledConnection> extends ChildInvocationHandler<Z, D, Z, C, SQLException>
{
	private static final Method addConnectionEventListenerMethod = Methods.getMethod(PooledConnection.class, "addConnectionEventListener", ConnectionEventListener.class);
	private static final Method addStatementEventListenerMethod = Methods.getMethod(PooledConnection.class, "addStatementEventListener", StatementEventListener.class);
	private static final Method removeConnectionEventListenerMethod = Methods.getMethod(PooledConnection.class, "removeConnectionEventListener", ConnectionEventListener.class);
	private static final Method removeStatementEventListenerMethod = Methods.getMethod(PooledConnection.class, "removeStatementEventListener", StatementEventListener.class);
	
	private static final Set<Method> eventListenerMethodSet = new HashSet<Method>(Arrays.asList(addConnectionEventListenerMethod, addStatementEventListenerMethod, removeConnectionEventListenerMethod, removeStatementEventListenerMethod));
	
	private static final Method getConnectionMethod = Methods.getMethod(PooledConnection.class, "getConnection");
	private static final Method closeMethod = Methods.getMethod(PooledConnection.class, "close");
	
	private Map<Object, Invoker<Z, D, C, ?, SQLException>> connectionEventListenerInvokerMap = new HashMap<Object, Invoker<Z, D, C, ?, SQLException>>();
	private Map<Object, Invoker<Z, D, C, ?, SQLException>> statementEventListenerInvokerMap = new HashMap<Object, Invoker<Z, D, C, ?, SQLException>>();
	
	/**
	 * Constructs a new AbstractPooledConnectionInvocationHandler
	 * @param dataSource
	 * @param proxy
	 * @param invoker
	 * @param proxyClass
	 * @param objects
	 */
	protected AbstractPooledConnectionInvocationHandler(Z dataSource, SQLProxy<Z, D, Z, SQLException> proxy, Invoker<Z, D, Z, C, SQLException> invoker, Class<C> proxyClass, Map<D, C> objects)
	{
		super(dataSource, proxy, invoker, proxyClass, SQLException.class, objects);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#getInvocationHandlerFactory(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected InvocationHandlerFactory<Z, D, C, ?, SQLException> getInvocationHandlerFactory(C object, Method method, Object[] parameters) throws SQLException
	{
		if (method.equals(getConnectionMethod))
		{
			return new ConnectionInvocationHandlerFactory<Z, D, C>(this.createTransactionContext());
		}
		
		return super.getInvocationHandlerFactory(object, method, parameters);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#getInvocationStrategy(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected InvocationStrategy getInvocationStrategy(C connection, Method method, Object[] parameters) throws SQLException
	{
		if (eventListenerMethodSet.contains(method))
		{
			return InvocationStrategyEnum.INVOKE_ON_EXISTING;
		}

		return super.getInvocationStrategy(connection, method, parameters);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#postInvoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected void postInvoke(C connection, Method method, Object[] parameters)
	{
		if (method.equals(closeMethod))
		{
			this.getParentProxy().removeChild(this);
		}
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#close(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected void close(Z dataSource, C connection) throws SQLException
	{
		connection.close();
	}
	
	protected abstract TransactionContext<Z, D> createTransactionContext();

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#record(net.sf.hajdbc.invocation.Invoker, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected void record(Invoker<Z, D, C, ?, SQLException> invoker, Method method, Object[] parameters)
	{
		if (method.equals(addConnectionEventListenerMethod))
		{
			synchronized (this.connectionEventListenerInvokerMap)
			{
				this.connectionEventListenerInvokerMap.put(parameters[0], invoker);
			}
		}
		else if (method.equals(removeConnectionEventListenerMethod))
		{
			synchronized (this.connectionEventListenerInvokerMap)
			{
				this.connectionEventListenerInvokerMap.remove(parameters[0]);
			}
		}
		else if (method.equals(addStatementEventListenerMethod))
		{
			synchronized (this.statementEventListenerInvokerMap)
			{
				this.statementEventListenerInvokerMap.put(parameters[0], invoker);
			}
		}
		else if (method.equals(removeStatementEventListenerMethod))
		{
			synchronized (this.statementEventListenerInvokerMap)
			{
				this.statementEventListenerInvokerMap.remove(parameters[0]);
			}
		}
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#replay(net.sf.hajdbc.Database, java.lang.Object)
	 */
	@Override
	protected void replay(D database, C connection) throws SQLException
	{
		synchronized (this.connectionEventListenerInvokerMap)
		{
			for (Invoker<Z, D, C, ?, SQLException> invoker: this.connectionEventListenerInvokerMap.values())
			{
				invoker.invoke(database, connection);
			}
		}

		synchronized (this.statementEventListenerInvokerMap)
		{
			for (Invoker<Z, D, C, ?, SQLException> invoker: this.statementEventListenerInvokerMap.values())
			{
				invoker.invoke(database, connection);
			}
		}
	}
	
	@SuppressWarnings("unused")
	private static class ConnectionEventListenerFilter<Z, D extends Database<Z>, C extends PooledConnection> implements ConnectionEventListener
	{
		private SQLProxy<Z, D, C, SQLException> proxy;
		private final D database;
		private final ConnectionEventListener listener;
		
		ConnectionEventListenerFilter(SQLProxy<Z, D, C, SQLException> proxy, D database, ConnectionEventListener listener)
		{
			this.proxy = proxy;
			this.database = database;
			this.listener = listener;
		}
		
		@Override
		public void connectionClosed(ConnectionEvent event)
		{
			ConnectionEvent e = this.getEvent(event);
			
			if (e != null)
			{
				this.listener.connectionClosed(e);
			}
		}

		@Override
		public void connectionErrorOccurred(ConnectionEvent event)
		{
			ConnectionEvent e = this.getEvent(event);
			
			if (e != null)
			{
				this.listener.connectionErrorOccurred(e);
			}
		}
		
		private ConnectionEvent getEvent(ConnectionEvent event)
		{
			Object source = event.getSource();
			C connection = this.proxy.getObject(this.database);
			
			if (Proxy.isProxyClass(source.getClass()) && Proxy.getInvocationHandler(source).equals(this.proxy))
			{
				return new ConnectionEvent(connection, event.getSQLException());
			}
			
			return event.getSource().equals(connection) ? event : null;
		}
	}
	
	@SuppressWarnings("unused")
	private static class StatementEventListenerFilter<Z, D extends Database<Z>, C extends PooledConnection> implements StatementEventListener
	{
		private SQLProxy<Z, D, C, SQLException> proxy;
		private final D database;
		private final StatementEventListener listener;
		
		StatementEventListenerFilter(SQLProxy<Z, D, C, SQLException> proxy, D database, StatementEventListener listener)
		{
			this.proxy = proxy;
			this.database = database;
			this.listener = listener;
		}
		
		@Override
		public void statementClosed(StatementEvent event)
		{
			StatementEvent e = this.getEvent(event);
			
			if (e != null)
			{
				this.listener.statementClosed(e);
			}
		}

		@Override
		public void statementErrorOccurred(StatementEvent event)
		{
			StatementEvent e = this.getEvent(event);
			
			if (e != null)
			{
				this.listener.statementErrorOccurred(e);
			}
		}
		
		private StatementEvent getEvent(StatementEvent event)
		{
			Object source = event.getSource();
			C connection = this.proxy.getObject(this.database);
			
			if (Proxy.isProxyClass(source.getClass()) && Proxy.getInvocationHandler(source).equals(this.proxy))
			{
				return new StatementEvent(connection, event.getStatement(), event.getSQLException());
			}
			
			return source.equals(connection) ? event : null;
		}
	}
}
