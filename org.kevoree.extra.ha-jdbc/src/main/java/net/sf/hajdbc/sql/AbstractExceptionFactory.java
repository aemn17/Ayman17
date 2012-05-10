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

import net.sf.hajdbc.ExceptionFactory;

/**
 * @author paul
 *
 */
public abstract class AbstractExceptionFactory<E extends Exception> implements ExceptionFactory<E>
{
	private Class<E> targetClass;
	
	protected AbstractExceptionFactory(Class<E> targetClass)
	{
		this.targetClass = targetClass;
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.ExceptionFactory#createException(java.lang.Throwable)
	 */
	@Override
	public E createException(Throwable e)
	{
		if (this.targetClass.isInstance(e))
		{
			return this.targetClass.cast(e);
		}
		
		E exception = this.createException();
		
		exception.initCause(e);
		
		return exception;
	}
	
	protected abstract E createException();
}
