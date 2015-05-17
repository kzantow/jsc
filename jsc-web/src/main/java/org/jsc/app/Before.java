package org.jsc.app;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Ensure this method is executed before other methods in the given classes
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Before {
	Class<?>[] value() default {};
}
