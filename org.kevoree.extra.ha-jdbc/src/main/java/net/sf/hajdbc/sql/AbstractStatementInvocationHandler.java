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

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.IdentityColumnSupport;
import net.sf.hajdbc.SequenceSupport;
import net.sf.hajdbc.cache.DatabaseProperties;
import net.sf.hajdbc.cache.TableProperties;
import net.sf.hajdbc.invocation.InvocationStrategy;
import net.sf.hajdbc.invocation.InvocationStrategyEnum;
import net.sf.hajdbc.invocation.Invoker;
import net.sf.hajdbc.lock.LockManager;
import net.sf.hajdbc.util.reflect.Methods;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * @author Paul Ferraro
 * @param <D> 
 * @param <S> 
 */
@SuppressWarnings("nls")
public abstract class AbstractStatementInvocationHandler<Z, D extends Database<Z>, S extends Statement> extends ChildInvocationHandler<Z, D, Connection, S, SQLException>
{
	private static final Set<Method> driverReadMethodSet = Methods.findMethods(Statement.class, "getFetchDirection", "getFetchSize", "getGeneratedKeys", "getMaxFieldSize", "getMaxRows", "getQueryTimeout", "getResultSetConcurrency", "getResultSetHoldability", "getResultSetType", "getUpdateCount", "getWarnings", "isClosed", "isPoolable");
	private static final Set<Method> driverWriteMethodSet = Methods.findMethods(Statement.class, "clearWarnings", "setCursorName", "setEscapeProcessing", "setFetchDirection", "setFetchSize", "setMaxFieldSize", "setMaxRows", "setPoolable", "setQueryTimeout");
	private static final Set<Method> executeMethodSet = Methods.findMethods(Statement.class, "execute(Update)?");
	
	private static final Method getConnectionMethod = Methods.getMethod(Statement.class, "getConnection");
	private static final Method executeQueryMethod = Methods.getMethod(Statement.class, "executeQuery", String.class);
	private static final Method clearBatchMethod = Methods.getMethod(Statement.class, "clearBatch");
	private static final Method executeBatchMethod = Methods.getMethod(Statement.class, "executeBatch");
	private static final Method getMoreResultsMethod = Methods.getMethod(Statement.class, "getMoreResults", Integer.TYPE);
	private static final Method getResultSetMethod = Methods.getMethod(Statement.class, "getResultSet");
	private static final Method addBatchMethod = Methods.getMethod(Statement.class, "addBatch", String.class);
	private static final Method closeMethod = Methods.getMethod(Statement.class, "close");
	
	protected TransactionContext<Z, D> transactionContext;
	protected FileSupport<SQLException> fileSupport;
	
	private List<Invoker<Z, D, S, ?, SQLException>> batchInvokerList = new LinkedList<Invoker<Z, D, S, ?, SQLException>>();
	private List<String> sqlList = new LinkedList<String>();
	
	/**
	 * @param connection the parent connection of this statement
	 * @param proxy the parent invocation handler
	 * @param invoker the invoker that created this statement
	 * @param statementClass 
	 * @param statementMap a map of database to underlying statement
	 * @param transactionContext 
	 * @param fileSupport support object for streams
	 * @throws Exception
	 */
	protected AbstractStatementInvocationHandler(Connection connection, SQLProxy<Z, D, Connection, SQLException> proxy, Invoker<Z, D, Connection, S, SQLException> invoker, Class<S> statementClass, Map<D, S> statementMap, TransactionContext<Z, D> transactionContext, FileSupport<SQLException> fileSupport)
	{
		super(connection, proxy, invoker, statementClass, SQLException.class, statementMap);
		
		this.transactionContext = transactionContext;
		this.fileSupport = fileSupport;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#getParentMethod()
	 */
	@Override
	protected Method getParentMethod()
	{
		return getConnectionMethod;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#getInvocationHandlerFactory(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected InvocationHandlerFactory<Z, D, S, ?, SQLException> getInvocationHandlerFactory(S object, Method method, Object[] parameters) throws SQLException
	{
		if (method.equals(executeQueryMethod) || method.equals(getResultSetMethod))
		{
			return new ResultSetInvocationHandlerFactory<Z, D, S>(this.transactionContext, this.fileSupport);
		}
		
		return super.getInvocationHandlerFactory(object, method, parameters);
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#getInvocationStrategy(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected InvocationStrategy getInvocationStrategy(S statement, Method method, Object[] parameters) throws SQLException
	{
		if (driverReadMethodSet.contains(method))
		{
			return InvocationStrategyEnum.INVOKE_ON_ANY;
		}
		
		if (driverWriteMethodSet.contains(method) || method.equals(closeMethod))
		{
			return InvocationStrategyEnum.INVOKE_ON_EXISTING;
		}
		
		if (executeMethodSet.contains(method))
		{
			List<Lock> lockList = this.extractLocks((String) parameters[0]);
			
			return this.transactionContext.start(new LockingInvocationStrategy(InvocationStrategyEnum.TRANSACTION_INVOKE_ON_ALL, lockList), this.getParent());
		}
		
		if (method.equals(executeQueryMethod))
		{
			String sql = (String) parameters[0];
			
			List<Lock> lockList = this.extractLocks(sql);
			
			int concurrency = statement.getResultSetConcurrency();
			boolean lockingSelect = this.isLockingSelect(sql) || (statement.getConnection().getTransactionIsolation() >= Connection.TRANSACTION_REPEATABLE_READ);
			
			if (lockList.isEmpty() && (concurrency == ResultSet.CONCUR_READ_ONLY) && !lockingSelect)
			{
				return InvocationStrategyEnum.INVOKE_ON_NEXT;
			}
			
			InvocationStrategy strategy = new LockingInvocationStrategy(InvocationStrategyEnum.TRANSACTION_INVOKE_ON_ALL, lockList);
			
			return lockingSelect ? this.transactionContext.start(strategy, this.getParent()) : strategy;
		}
		
		if (method.equals(executeBatchMethod))
		{
			List<Lock> lockList = this.extractLocks(this.sqlList);
			
			return this.transactionContext.start(new LockingInvocationStrategy(InvocationStrategyEnum.TRANSACTION_INVOKE_ON_ALL, lockList), this.getParent());
		}
		
		if (method.equals(getMoreResultsMethod))
		{
			if (parameters[0].equals(Statement.KEEP_CURRENT_RESULT))
			{
				return InvocationStrategyEnum.INVOKE_ON_EXISTING;
			}
		}
		
		if (method.equals(getResultSetMethod))
		{
			if (statement.getResultSetConcurrency() == ResultSet.CONCUR_READ_ONLY)
			{
				return InvocationStrategyEnum.INVOKE_ON_EXISTING;
			}

			return InvocationStrategyEnum.INVOKE_ON_ALL;
		}
		
		return super.getInvocationStrategy(statement, method, parameters);
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#isSQLMethod(java.lang.reflect.Method)
	 */
	@Override
	protected boolean isSQLMethod(Method method)
	{
		return method.equals(addBatchMethod) || method.equals(executeQueryMethod) || executeMethodSet.contains(method);
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#postInvoke(java.lang.Object, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected void postInvoke(S statement, Method method, Object[] parameters)
	{
		if (method.equals(addBatchMethod))
		{
			this.sqlList.add((String) parameters[0]);
		}
		else if (method.equals(clearBatchMethod) || method.equals(executeBatchMethod))
		{
			this.sqlList.clear();
		}
		else if (method.equals(closeMethod))
		{
			this.fileSupport.close();
			
			this.getParentProxy().removeChild(this);
		}
	}
	
	protected boolean isLockingSelect(String sql) throws SQLException
	{
		Connection connection = this.getParent();
		
		return (connection.getTransactionIsolation() >= Connection.TRANSACTION_REPEATABLE_READ) || (this.getDatabaseProperties().supportsSelectForUpdate() ? this.getDatabaseCluster().getDialect().isSelectForUpdate(sql) : false);
	}
	
	protected List<Lock> extractLocks(String sql) throws SQLException
	{
		return this.extractLocks(Collections.singletonList(sql));
	}
	
	private List<Lock> extractLocks(List<String> sqlList) throws SQLException
	{
		Set<String> identifierSet = new TreeSet<String>();
		DatabaseCluster<Z, D> cluster = this.getDatabaseCluster();
		
		for (String sql: sqlList)
		{
			if (cluster.isSequenceDetectionEnabled())
			{
				SequenceSupport support = cluster.getDialect().getSequenceSupport();
				
				if (support != null)
				{
					String sequence = support.parseSequence(sql);
					
					if (sequence != null)
					{
						identifierSet.add(sequence);
					}
				}
			}
			
			if (cluster.isIdentityColumnDetectionEnabled())
			{
				IdentityColumnSupport support = cluster.getDialect().getIdentityColumnSupport();
				
				if (support != null)
				{
					String table = support.parseInsertTable(sql);
					
					if (table != null)
					{
						TableProperties tableProperties = this.getDatabaseProperties().findTable(table);
						
						if (!tableProperties.getIdentityColumns().isEmpty())
						{
							identifierSet.add(tableProperties.getName());
						}
					}
				}
			}
		}

		List<Lock> lockList = new ArrayList<Lock>(identifierSet.size());
		
		if (!identifierSet.isEmpty())
		{
			LockManager lockManager = cluster.getLockManager();
			
			for (String identifier: identifierSet)
			{
				lockList.add(lockManager.writeLock(identifier));
			}
		}
		
		return lockList;
	}

	protected DatabaseProperties getDatabaseProperties() throws SQLException
	{
		DatabaseCluster<Z, D> cluster = this.getDatabaseCluster();
		
		return cluster.getDatabaseMetaDataCache().getDatabaseProperties(cluster.getBalancer().next(), this.getParent());
	}
	
	/**
	 * @see net.sf.hajdbc.sql.AbstractChildInvocationHandler#close(java.lang.Object, java.lang.Object)
	 */
	@Override
	protected void close(Connection connection, S statement) throws SQLException
	{
		statement.close();
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#record(net.sf.hajdbc.invocation.Invoker, java.lang.reflect.Method, java.lang.Object[])
	 */
	@Override
	protected void record(Invoker<Z, D, S, ?, SQLException> invoker, Method method, Object[] parameters)
	{
		if (this.isBatchMethod(method))
		{
			synchronized (this.batchInvokerList)
			{
				//this.logger.log(Level.TRACE, "Recording batch method: {0}", invoker);

				this.batchInvokerList.add(invoker);
			}
		}
		else if (this.isEndBatchMethod(method))
		{
			synchronized (this.batchInvokerList)
			{
				//this.logger.log(Level.TRACE, "Clearing recorded batch methods");
				
				this.batchInvokerList.clear();
			}
		}
		else
		{
			super.record(invoker, method, parameters);
		}
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#isRecordable(java.lang.reflect.Method)
	 */
	@Override
	protected boolean isRecordable(Method method)
	{
		return driverWriteMethodSet.contains(method);
	}

	protected boolean isBatchMethod(Method method)
	{
		return method.equals(addBatchMethod);
	}

	protected boolean isEndBatchMethod(Method method)
	{
		return method.equals(clearBatchMethod) || method.equals(executeBatchMethod);
	}

	/**
	 * @see net.sf.hajdbc.sql.AbstractInvocationHandler#replay(net.sf.hajdbc.Database, java.lang.Object)
	 */
	@Override
	protected void replay(D database, S statement) throws SQLException
	{
		super.replay(database, statement);
		
		synchronized (this.batchInvokerList)
		{
			for (Invoker<Z, D, S, ?, SQLException> invoker: this.batchInvokerList)
			{
				this.logger.trace( "Replaying against database {0}: {1}.{2}");

				invoker.invoke(database, statement);
			}
		}
	}
}
