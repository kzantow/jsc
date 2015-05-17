package org.jsc.db;

import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.sql.DataSource;

import org.jsc.Fn1;
import org.jsc.Proc1;
import org.jsc.Util;
import org.jsc.app.App;
import org.jsc.app.OnShutdown;
import org.jsc.app.OnStartup;

@Singleton
public class Db {
	@Inject App app;
	
	@OnStartup
	public void checkDB() {
		for(Class<?> c : app.findAnnotatedClasses(Data.class)) {
			Table<?> t = getTable(c);
			try {
				sql("select count(*) from ").append(t.getName()).getInt();
			} catch(Exception e) {
				createTable(c);
			}
			
			for(Column<?> col : t.getColumns()) {
				try {
					sql("select count(").append(col.getName()).append(") from ").append(t.getName()).getInt();
				} catch(Exception e) {
					sql("alter table ").append(t.getName()).append(" add column ").append(col.getName()).append(" ").append(col.getDataType()).execute();
				}
			}
		}
	}
	
	@OnShutdown
	public void shutdownDataSource(){
		maxConnections = 0;
		pool.forEach(conn -> {
			try { conn.close(); } catch(Throwable t) { Util.ignore(t); }
		});
		pool.clear();
		open.forEach(conn -> {
			try { conn.close(); } catch(Throwable t) { Util.ignore(t); }
		});
		open.clear();
		ds = null;
	}
	
	public interface Dialect {
		public void createTable(Object o, Writer w);
	}
	
	private final ThreadLocal<Connection> currentConnection = new ThreadLocal<Connection>();
	private final ThreadLocal<Object> currentTransaction = new ThreadLocal<Object>();
	private long timeoutMillis = 1000;
	private int maxConnections = 10;
	private Queue<Connection> pool = new ConcurrentLinkedQueue<>();
	private Collection<Connection> open = Collections.synchronizedSet(new HashSet<>());
	private DataSource ds;
	
	{
		try {
			DriverManager.registerDriver(new org.h2.Driver());
			
			@SuppressWarnings("unused")
			String testQuery = "select 1";
			String //url = "postgres://drjyysjxjvtvdu:TzqZahajn2wN2hDtqlIk3TcCP2@ec2-54-83-204-85.compute-1.amazonaws.com:5432/ddi25k20gsq3f9";
			//url = "jdbc:h2:mem:temp;MVCC=TRUE;DB_CLOSE_DELAY=-1";
			url = Util.env("dbUrl", "jdbc:h2:~/hive-db;MVCC=TRUE");
			
//			new java.io.File(System.getProperty("user.home") + "/hive-db.mv.db").delete();
//			new java.io.File(System.getProperty("user.home") + "/hive-db.trace.db").delete();
			
			Driver driver = DriverManager.getDriver(url);
			ds = new DataSource() {
				@Override
				public <T> T unwrap(Class<T> iface) throws SQLException {
					return null;
				}
				
				@Override
				public boolean isWrapperFor(Class<?> iface) throws SQLException {
					return false;
				}
				
				@Override
				public void setLoginTimeout(int seconds) throws SQLException {
				}
				
				@Override
				public void setLogWriter(PrintWriter out) throws SQLException {
				}
				
				@Override
				public Logger getParentLogger() throws SQLFeatureNotSupportedException {
					return null;
				}
				
				@Override
				public int getLoginTimeout() throws SQLException {
					return 0;
				}
				
				@Override
				public PrintWriter getLogWriter() throws SQLException {
					return null;
				}
				
				@Override
				public Connection getConnection(String username, String password) throws SQLException {
					return null;
				}
				
				@Override
				public Connection getConnection() throws SQLException {
					synchronized(pool) {
						Connection c = pool.poll();
						if(c == null) {
							if(open.size() < maxConnections) {
								c = driver.connect(url, new Properties());
							} else {
								try { pool.wait(timeoutMillis); } catch(Throwable t) { Util.asRuntime(t); }
								c = pool.poll();
							}
						}
						c = new ConnectionWrapper(c) {
							public void close() throws java.sql.SQLException {
								synchronized(pool) {
									pool.add(getConnection());
									setConnection(null);
									open.remove(this);
									pool.notify();
								}
							}
						};
						open.add(c);
						return c;
					}
				}
			};
		} catch(Throwable e) {
			throw Util.asRuntime(e);
		}
	}
	
	public <T> T inTransaction(Fn1<T, Connection> fn) {
		return apply(conn -> {
			Object tx = currentTransaction.get();
			if(tx == null) {
				currentTransaction.set(conn);
				conn.setAutoCommit(false);
				try {
					T out = fn.exec(conn);
					conn.commit();
					return out;
				} catch(Throwable e) {
					try { conn.rollback(); } catch(Throwable ex) { /* this didn't cause the error... */ }
					throw Util.asRuntime(e);
				} finally {
					currentTransaction.remove();
					try { conn.setAutoCommit(true); } catch(Throwable ex) { /* don't fail this */ }
				}
			}
			// Already in a tx
			return fn.exec(conn);
		});
	}
	public void accept(Proc1<Connection> fn) {
		apply(conn -> {
			fn.exec(conn);
			return null;
		});
	}
	public <T> T apply(Fn1<T, Connection> fn) {
		Connection conn = currentConnection.get();
		if(conn == null) {
			try {
				conn = ds.getConnection();
				currentConnection.set(conn);
				return fn.exec(conn);
			} catch(Throwable e) {
				throw Util.asRuntime(e);
			} finally {
				currentConnection.remove();
				try { if(conn != null) conn.close(); } catch(Throwable e) { Util.ignore(e); }
			}
		}
		try {
			// Already a current connection
			return fn.exec(conn);
		} catch(Throwable e) {
			throw Util.asRuntime(e);
		}
	}
	
	public <T> Table<T> getTable(Class<T> typ) {
		List<Column<?>> columns = new ArrayList<>();
		
		Class<?> t = typ;
		while(t != Object.class) {
			for(Field f : t.getDeclaredFields()) {
				if(Modifier.isTransient(f.getModifiers())) {
					continue;
				}
				f.setAccessible(true);
				String name = dbIdentifier(f.getName());
				boolean isId = f.isAnnotationPresent(PK.class);
				if(f.getType() == String.class) {
					columns.add(new Column<String>() {
						public String getName() {
							return name;
						}
						public boolean id() {
							return isId;
						}
						public String getDataType() {
							return "varchar(255)";
						}
						public String map(ResultSetReader rdr) throws Exception {
							return rdr.getResultSet().getString(rdr.paramIndex());
						}
						public void mapProperty(ResultSetReader rdr, Object o) throws Exception {
							f.set(o, map(rdr));
						}
						public int bind(PreparedStatement ps, int idx, String o) throws Exception {
							if(o == null) {
								ps.setNull(idx, Types.VARCHAR);
							}
							else {
								ps.setString(idx, o);
							}
							return 1;
						}
						public int bindProperty(PreparedStatement ps, int idx, Object o) throws Exception {
							bind(ps, idx, ((String)f.get(o)));
							return 1;
						}
					});
				}
				else if(f.getType() == Instant.class) {
					columns.add(new Column<Instant>() {
						public String getName() {
							return name;
						}
						public boolean id() {
							return isId;
						}
						public String getDataType() {
							return "timestamp";
						}
						public Instant map(ResultSetReader rdr)  throws Exception{
							java.sql.Timestamp ts = rdr.getResultSet().getTimestamp(rdr.paramIndex());
							if(ts != null) {
								return ts.toInstant();
							}
							return null;
						}
						public void mapProperty(ResultSetReader rdr, Object o) throws Exception {
							f.set(o, map(rdr));
						}
						public int bind(PreparedStatement ps, int idx, Instant o) throws Exception {
							if(o == null) {
								ps.setNull(idx, Types.TIMESTAMP);
							}
							else {
								ps.setTimestamp(idx, new java.sql.Timestamp(o.toEpochMilli()));
							}
							return 1;
						}
						public int bindProperty(PreparedStatement ps, int idx, Object o) throws Exception {
							bind(ps, idx, ((Instant)f.get(o)));
							return 1;
						}
					});
				}
				else if(f.getType() == Integer.TYPE) {
					columns.add(new Column<Integer>() {
						public String getName() {
							return name;
						}
						public boolean id() {
							return isId;
						}
						public String getDataType() {
							return "int not null default 0";
						}
						public Integer map(ResultSetReader rdr)  throws Exception{
							int v = rdr.getResultSet().getInt(rdr.paramIndex());
							if(rdr.getResultSet().wasNull()) {
								return null;
							}
							return v;
						}
						public void mapProperty(ResultSetReader rdr, Object o) throws Exception {
							f.set(o, map(rdr));
						}
						public int bind(PreparedStatement ps, int idx, Integer o) throws Exception {
							if(o == null) {
								ps.setNull(idx, Types.INTEGER);
							}
							else {
								ps.setInt(idx, o);
							}
							return 1;
						}
						public int bindProperty(PreparedStatement ps, int idx, Object o) throws Exception {
							bind(ps, idx, ((Integer)f.get(o)));
							return 1;
						}
					});
				}
				else if(f.getType() == Long.TYPE) {
					columns.add(new Column<Long>() {
						public String getName() {
							return name;
						}
						public boolean id() {
							return isId;
						}
						public String getDataType() {
							return "long not null default 0";
						}
						public Long map(ResultSetReader rdr)  throws Exception{
							long v = rdr.getResultSet().getLong(rdr.paramIndex());
							if(rdr.getResultSet().wasNull()) {
								return null;
							}
							return v;
						}
						public void mapProperty(ResultSetReader rdr, Object o) throws Exception {
							f.set(o, map(rdr));
						}
						public int bind(PreparedStatement ps, int idx, Long o) throws Exception {
							if(o == null) {
								ps.setNull(idx, Types.INTEGER);
							}
							else {
								ps.setLong(idx, o);
							}
							return 1;
						}
						public int bindProperty(PreparedStatement ps, int idx, Object o) throws Exception {
							bind(ps, idx, ((Long)f.get(o)));
							return 1;
						}
					});
				}
				else {
					throw Util.notImplemented("Unknown field: " + f);
				}
			}
			t = t.getSuperclass();
		}

		String tableName = dbIdentifier(typ.getSimpleName());
		Table<T> table = new Table<T>() {
			@Override
			public String getName() {
				return tableName;
			}
			@Override
			public List<Column<?>> getColumns() {
				return columns;
			}
			@Override
			public void bind(PreparedStatement ps, int idx, T t) throws Exception {
				for(Column<?> c : columns) {
					idx += c.bindProperty(ps, idx, t);
				}
			}
			@SuppressWarnings("unchecked")
			@Override
			public T map(ResultSetReader rdr) throws Exception {
				Object out = typ.newInstance();
				for(Column<?> c : columns) {
					c.mapProperty(rdr, out);
				}
				return (T)out;
			}
		};
		
		return table;
	}
	
	/**
	 * Get a database identifier from java name
	 * @param simpleName
	 * @return
	 */
	public String dbIdentifier(String simpleName) {
		return simpleName.toLowerCase();
	}
	
	/**
	 * Create a table for the type
	 * @param typ
	 */
	public void createTable(Class<?> typ) {
		Table<?> t = getTable(typ);
		accept(conn -> {
			Sql sql = new Sql(conn)
				.append("create table ").append(t.getName()).append(" (");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(first) {
					first = false;
				} else {
					sql.append(",");
				}
				sql.append(c.getName())
					.append(" ")
					.append(c.getDataType());
			}
			
			sql.append(")");
			
			sql.execute();
		});
	}
	
	/**
	 * Use this database to create an sql statement
	 * @param sql
	 * @return
	 */
	public Sql sql(String sql) {
		return new Sql(this).append(sql);
	}
	
	/**
	 * List matching items for the given type
	 * @param typ
	 * @return
	 */
	public <T> List<T> list(Class<T> typ) {
		Table<T> t = getTable(typ);
		
		List<T> out = new ArrayList<>();
		
		accept(conn -> {
			Sql sql = new Sql(conn)
				.append("select ");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(first) first = false; else sql.append(",");
				sql.append(c.getName());
			}
			
			sql.append(" from ").append(t.getName());
			
			ResultSetReader rdr = new ResultSetReader();
			sql.execute((rs) -> {
				rdr.rs = rs;
				rdr.idx = 0;
				T o = t.map(rdr);
				out.add(o);
			});
		});
		
		return out;
	}
	
	/**
	 * Update object
	 * @param o
	 * @return
	 */
	public int update(Object o) {
		Table<?> t = getTable(o.getClass());
		
		return apply(conn -> {
			Sql sql = new Sql(conn)
				.append("update ").append(t.getName()).append(" set ");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(c.id()) continue;
				if(first) first = false; else sql.append(",");
				
				sql.append(c.getName())
					.append("=")
					.bind((ps, idx) -> c.bindProperty(ps, idx, o));
			}
			
			sql.append(" where ");
			
			first = true;
			for(Column<?> c : t.getColumns()) {
				if(!c.id()) continue;
				if(first) first = false; else sql.append(" and ");
				
				sql.append(c.getName())
					.append("=")
					.bind((ps, idx) -> c.bindProperty(ps, idx, o));
			}
			
			return sql.execute();
		});
	}
	
	/**
	 * Insert object
	 * @param o
	 * @return
	 */
	public int insert(Object o) {
		Table<?> t = getTable(o.getClass());
		
		return apply(conn -> {
			Sql sql = new Sql(conn)
				.append("insert into ").append(t.getName()).append(" (");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(first) first = false; else sql.append(",");
				sql.append(c.getName());
			}
			
			sql.append(") values (");
			
			first = true;
			for(Column<?> c : t.getColumns()) {
				if(first) first = false; else sql.append(",");
				sql.bind((ps, idx) -> c.bindProperty(ps, idx, o));
			}

			sql.append(")");

			return sql.execute();
		});
	}
	
	/**
	 * Update or insert object by id
	 * @param o
	 */
	public void save(Object o) {
		if(0 == update(o)) {
			insert(o);
		}
	}
	
	/**
	 * delete object by id
	 * @param o
	 * @return
	 */
	public int delete(Object o) {
		Table<?> t = getTable(o.getClass());
		
		return apply(conn -> {
			Sql sql = new Sql(conn)
				.append("delete from ").append(t.getName());
			
			sql.append(" where ");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(!c.id()) continue;
				if(first) first = false; else sql.append(" and ");
				
				sql.append(c.getName())
					.append("=")
					.bind((ps, idx) -> c.bindProperty(ps, idx, o));
			}
			
			return sql.execute();
		});		
	}

	/**
	 * Finds an object by the set id properties
	 * @param id
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T findById(T id) {
		Class<T> typ = (Class<T>)id.getClass();
		Table<T> t = getTable(typ);
		
		Object[] out = { null };
		
		accept(conn -> {
			Sql sql = new Sql(conn)
				.append("select ");
			
			boolean first = true;
			for(Column<?> c : t.getColumns()) {
				if(first) first = false; else sql.append(",");
				sql.append(c.getName());
			}
			
			sql.append(" from ").append(t.getName());

			sql.append(" where ");

			first = true;
			for(Column<?> c : t.getColumns()) {
				if(!c.id()) continue;
				if(first) first = false; else sql.append(" and ");
				
				sql.append(c.getName())
					.append("=")
					.bind((ps, idx) -> c.bindProperty(ps, idx, id));
			}
			
			ResultSetReader rdr = new ResultSetReader();
			sql.execute((rs) -> {
				rdr.rs = rs;
				rdr.idx = 0;
				T o = t.map(rdr);
				out[0] = o;
			});
		});
		
		return (T)out[0];
	}
}
