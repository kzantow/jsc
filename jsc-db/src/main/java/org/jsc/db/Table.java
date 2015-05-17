package org.jsc.db;

import java.sql.PreparedStatement;
import java.util.List;

/**
 * Db table
 * @author kzantow
 */
public interface Table<T> extends Mappable<T> {
	/**
	 * Get the quoted name of the table
	 * @return
	 */
	String getName();
	
	/**
	 * Bind parameters
	 * @param ps
	 * @param idx
	 * @param t
	 */
	void bind(PreparedStatement ps, int idx, T t) throws Exception;
	
	/**
	 * Get an ordered list of the columns in this table
	 * @return
	 */
	List<Column<?>> getColumns();
}
