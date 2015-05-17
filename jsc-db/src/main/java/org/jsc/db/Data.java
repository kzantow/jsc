package org.jsc.db;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * used by data objects; annotate this to make sure tables are created, etc
 * @author kzantow
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Data {
}
