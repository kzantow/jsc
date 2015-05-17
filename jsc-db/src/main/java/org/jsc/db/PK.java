package org.jsc.db;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates this is part of the primary key
 * @author kzantow
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface PK {
}
