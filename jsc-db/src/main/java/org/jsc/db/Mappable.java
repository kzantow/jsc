package org.jsc.db;


/**
 * Implement this to map a resultSet
 * @author kzantow
 */
public interface Mappable<T> {
	/**
	 * Map data starting at idx, return the number of values read
	 * @param rs
	 * @param idx
	 * @return
	 */
	T map(ResultSetReader rdr) throws Exception;
}
