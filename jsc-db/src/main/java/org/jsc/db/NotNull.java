package org.jsc.db;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Indicates a field is to be not null
 * @author kzantow
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface NotNull {
}
