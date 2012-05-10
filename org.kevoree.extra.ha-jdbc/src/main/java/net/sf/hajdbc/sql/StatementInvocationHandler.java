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

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.invocation.Invoker;

/**
 * @author Paul Ferraro
 * @param <D> 
 */
public class StatementInvocationHandler<Z, D extends Database<Z>> extends AbstractStatementInvocationHandler<Z, D, Statement>
{
	/**
	 * @param connection the connection that created this statement
	 * @param proxy the invocation handler of the connection that created this statement
	 * @param invoker the invoker used to create this statement
	 * @param statementMap a map of database to underlying statement
	 * @param transactionContext 
	 * @param fileSupport support for streams
	 * @throws Exception
	 */
	public StatementInvocationHandler(Connection connection, SQLProxy<Z, D, Connection, SQLException> proxy, Invoker<Z, D, Connection, Statement, SQLException> invoker, Map<D, Statement> statementMap, TransactionContext<Z, D> transactionContext, FileSupport<SQLException> fileSupport)
	{
		super(connection, proxy, invoker, Statement.class, statementMap, transactionContext, fileSupport);
	}
}
