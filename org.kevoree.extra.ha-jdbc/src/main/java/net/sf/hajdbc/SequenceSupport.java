/*
 * HA-JDBC: High-Availablity JDBC
 * Copyright 2010 Paul Ferraro
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
package net.sf.hajdbc;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;

import net.sf.hajdbc.cache.QualifiedName;
import net.sf.hajdbc.cache.SequenceProperties;

/**
 * @author Paul Ferraro
 */
public interface SequenceSupport
{
	/**
	 * Parses a sequence name from the specified SQL statement.
	 * @param sql a SQL statement
	 * @return the name of a sequence, or null if this SQL statement does not reference a sequence or this dialect does not support sequences
	 * @throws SQLException
	 * @since 2.0
	 */
	String parseSequence(String sql) throws SQLException;
	
	/**
	 * Returns a collection of all sequences in this database.
	 * @param metaData database meta data
	 * @return a collection of sequence names
	 * @throws SQLException
	 * @since 2.0
	 */
	Map<QualifiedName, Integer> getSequences(DatabaseMetaData metaData) throws SQLException;
	
	/**
	 * Returns a SQL statement for obtaining the next value the specified sequence
	 * @param sequence a sequence name
	 * @return a SQL statement
	 * @throws SQLException
	 * @since 2.0
	 */
	String getNextSequenceValueSQL(SequenceProperties sequence) throws SQLException;

	/**
	 * Returns a SQL statement used reset the current value of a sequence.
	 * @param sequence a sequence name
	 * @param value a sequence value
	 * @return a SQL statement
	 * @throws SQLException 
	 * @since 2.0
	 */
	String getAlterSequenceSQL(SequenceProperties sequence, long value) throws SQLException;
}
