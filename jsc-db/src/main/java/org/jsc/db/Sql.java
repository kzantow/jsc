package org.jsc.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.sql.DataSource;

import org.jsc.Proc1;
import org.jsc.Util;

/**
 * Sql utility
 * @author kzantow
 */
public class Sql {
	Proc1<Proc1<Connection>> exec;
	StringBuilder sql = new StringBuilder();
	List<Binding<?>> bindings = new ArrayList<>();
	
	public Sql(Db db) {
		this.exec = db::accept;
	}
	
	public Sql(Connection conn) {
		this.exec = (fn) -> { fn.exec(conn); };
	}
	
	public Sql(DataSource ds) {
		this.exec = (fn) -> { fn.exec(ds.getConnection()); };
	}
	
	/**
	 * Execute a select query, callback for each row
	 * @param eachRow
	 */
	public void execute(Proc1<ResultSet> eachRow) {
		try {
			exec.exec(conn -> {
				PreparedStatement ps = null;
				ResultSet rs = null;
				try {
					ps = conn.prepareStatement(getSql());
					bindParameters(ps);
					rs = ps.executeQuery();
					while(rs.next()) {
						eachRow.exec(rs);
					}
				} finally {
					try { if(rs != null) rs.close(); } catch(Exception e) { /* can't do much with this one */ }
					try { if(ps != null) ps.close(); } catch(Exception e) { throw Util.asRuntime(e); }
				}
			});
		} catch(Throwable e) {
			throw Util.asRuntime(e);
		}
	}
	
	/**
	 * List the results of the query, all results mapped to strings
	 * @return
	 */
	public List<Object> list() {
		ArrayList<Object> out = new ArrayList<Object>();
		execute(new Proc1<ResultSet>() {
			ResultSetMetaData rsmd;
			@Override
			public void exec(ResultSet rs) throws Exception {
				if(rsmd == null) {
					rsmd = rs.getMetaData();
				}
				HashMap<String,String> s = new HashMap<>();
				for(int i = 1; i <= rsmd.getColumnCount(); i++) {
					s.put(rsmd.getColumnName(i), rs.getString(i));
				}
				out.add(s);
			}
		});
		return out;
	}

	/**
	 * Get a single int value from the query
	 * @return
	 */
	public int getInt() {
		int[] out = { 0 };
		execute((r) -> out[0] = r.getInt(1));
		return out[0];
	}
	
	/**
	 * Get a single long value from the query
	 * @return
	 */
	public long getLong() {
		long[] out = { 0 };
		execute((r) -> out[0] = r.getLong(1));
		return out[0];
	}
	
	/**
	 * Get a single string value from the query
	 * @return
	 */
	public String getString() {
		String[] out = { null };
		execute((r) -> out[0] = r.getString(1));
		return out[0];
	}
	
	/**
	 * Bind parameters to the prepared statement
	 * @param ps
	 * @throws Exception
	 */
	private void bindParameters(PreparedStatement ps) throws Exception {
		int idx = 1;
		for(Binding<?> b : bindings) {
			idx += b.bind(ps, idx);
		}
	}

	/**
	 * Execute an update query
	 */
	public int execute() {
		int[] out = { 0 };
		try {
			exec.exec(conn -> {
				PreparedStatement ps = null;
				try {
					String sql = getSql();
					System.out.println(sql);
					ps = conn.prepareStatement(sql);
					bindParameters(ps);
					out[0] = ps.executeUpdate();
				} finally {
					try { if(ps != null) ps.close(); } catch(Exception e) { throw Util.asRuntime(e); }
				}
			});
		} catch(Throwable e) {
			throw Util.asRuntime(e);
		}
		return out[0];
	}

	private String getSql() {
		return sql.toString();
	}

	public Sql append(String sql) {
		this.sql.append(sql);
		return this;
	}
	
	public Sql bind(String value) {
		bind((ps, idx) -> { ps.setString(idx, value); return 1; });
		return this;
	}
	
	public Sql bind(int value) {
		bind((ps, idx) -> { ps.setInt(idx, value); return 1; });
		return this;
	}
	
	public Sql bind(long value) {
		bind((ps, idx) -> { ps.setLong(idx, value); return 1; });
		return this;
	}
	
	public Sql bind(Instant value) {
		bind((ps, idx) -> { ps.setTimestamp(idx, new java.sql.Timestamp(value.toEpochMilli())); return 1; });
		return this;
	}
	
	public Sql bind(Binding<?> o) {
		append(o);
		return append("?");
	}
	
	/**
	 * Append a value binding only, does not add to the query
	 * @param o
	 * @return
	 */
	public Sql append(Binding<?> o) {
		bindings.add(o);
		return this;
	}
	
	@Override
	public String toString() {
		return getSql();
	}
}
