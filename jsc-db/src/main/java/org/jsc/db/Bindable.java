package org.jsc.db;

import java.sql.PreparedStatement;

/**
 * Implement this to bind parameters to queries
 * @author kzantow
 */
public interface Bindable<T> {
	/**
	 * Bind all parameters, starting at idx; return the count of parameters bound
	 * @param ps
	 * @param column
	 * @return
	 */
	int bind(PreparedStatement ps, int idx, T o) throws Exception;
}
