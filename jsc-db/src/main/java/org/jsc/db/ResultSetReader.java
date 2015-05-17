package org.jsc.db;

import java.sql.ResultSet;

/**
 * Utility to iterate through result set parameters consistently
 * @author kzantow
 */
public class ResultSetReader {
	public ResultSet rs;
	public int idx = 0;
	
	/**
	 * Gets the current result set
	 * @return
	 */
	public ResultSet getResultSet() {
		return rs;
	}
	
	/**
	 * Gets the current parameter index & increments it (this starts at 1)
	 * @return
	 */
	public int paramIndex() {
		return ++idx;
	}
}
