package org.jsc.db;

import java.sql.PreparedStatement;

/**
 * Value binding to a query
 * @author kzantow
 */
public interface Binding<T> {
	/**
	 * Value binding for a prepared statement
	 * @param ps
	 * @param idx
	 * @return
	 */
	int bind(PreparedStatement ps, int idx) throws Exception;
}
