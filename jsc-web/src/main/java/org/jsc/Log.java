package org.jsc;

import java.util.function.Supplier;
import java.util.logging.Logger;

import org.jsc.app.App;

/**
 * Simple logging wrapper; currently using JDK logging
 * @author kzantow
 */
public class Log {
	/**
	 * Shared general log, available to the application
	 */
	
	public static enum Level {
		DEBUG,
		INFO,
		WARN,
		ERROR
	}
	
	static final boolean isDebugEnabled = App.development;
	private final Logger logger;
	
	public Log(Class<?> typ) {
		logger = Logger.getLogger(typ.getName());
	}
	
	public void debug(Object ... args) {
		if(isDebugEnabled) {
			log(Level.DEBUG, msg(true, args));
		}
	}
	
	public void debug(Supplier<String> message) {
		if(isDebugEnabled) {
			log(Level.DEBUG, message);
		}
	}
	
	public void info(Object ... args) {
		log(Level.INFO, msg(false, args));
	}
	
	public void info(Supplier<String> message) {
		log(Level.INFO, message);
	}
	
	public void warn(Object ... args) {
		log(Level.WARN, msg(false, args));
	}
	
	public void warn(Supplier<String> message) {
		log(Level.WARN, message);
	}
	
	public void error(Object ... args) {
		log(Level.ERROR, msg(true, args));
	}
	
	public void error(Supplier<String> message) {
		log(Level.ERROR, message);
	}
	
	public Supplier<String> msg(boolean traceExceptions, Object ... args) {
		return () -> {
			StringBuilder sb = new StringBuilder();
			for(Object o : args) {
				if(o == null) {
					sb.append("null");
				}
				else if(o instanceof Throwable) {
					Throwable t = (Throwable)o;
					if(traceExceptions) {
						java.io.StringWriter sw = new java.io.StringWriter(1024);
						t.printStackTrace(new java.io.PrintWriter(sw));
						sb.append(sw.getBuffer());
					}
					else {
						sb.append(t.getClass()).append(": ").append(t.getMessage());
					}
				}
				else if(o.getClass().isArray()) {
					sb.append(Util.stringify(o));
				}
				else {
					sb.append(o.toString());
				}
			}
			return sb.toString();
		};
	}
	
	public void log(Level level, Supplier<String> msg) {
		switch(level) {
		case DEBUG:
			logger.log(java.util.logging.Level.INFO, msg.get());
			return;
		case INFO:
			logger.log(java.util.logging.Level.INFO, msg.get());
			return;
		case WARN:
			logger.log(java.util.logging.Level.WARNING, msg.get());
			return;
		case ERROR:
			logger.log(java.util.logging.Level.SEVERE, msg.get());
			return;
		}
	}
}
