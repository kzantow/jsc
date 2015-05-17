package org.jsc.app;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Ensure this method is executed after other methods in the given classes
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface After {
	Class<?>[] value() default {};
}
