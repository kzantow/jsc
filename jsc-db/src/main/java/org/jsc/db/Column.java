package org.jsc.db;

import java.sql.PreparedStatement;


/**
 * Db column
 * @author kzantow
 */
public interface Column<T> extends Bindable<T>, Mappable<T> {
	/**
	 * Get the quoted name of the column
	 * @return
	 */
	String getName();
	
	/**
	 * Map this column to the object, starting at the given resultSet column
	 * @param rs
	 * @param idx
	 * @param o
	 */
	void mapProperty(ResultSetReader rdr, Object o) throws Exception;
	
	/**
	 * Bind from a parent object
	 * @param rs
	 * @param idx
	 * @param o
	 */
	int bindProperty(PreparedStatement ps, int idx, Object o) throws Exception;
	
	/**
	 * Get the db data type - e.g. varchar2(255), etc...
	 * @return
	 */
	String getDataType();
	
	/**
	 * Indicates this column is part of the identity
	 * @return
	 */
	boolean id();
}
