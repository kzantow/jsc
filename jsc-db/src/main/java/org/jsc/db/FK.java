package org.jsc.db;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicate a foreign key reference
 * @author kzantow
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FK {
	Class<?> type();
	String field();
}
