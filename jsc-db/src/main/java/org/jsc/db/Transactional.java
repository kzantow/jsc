package org.jsc.db;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.inject.Inject;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jsc.Util;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;

/**
 * Execute in a transaction
 * @author kzantow
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transactional {
	/**
	 * Interceptor to actually apply the annotation behavior
	 * @author kzantow
	 */
	public static class Interceptor implements Module {
		@Override
		public void configure(Binder binder) {
	    	binder.bindInterceptor(Matchers.any(), Matchers.annotatedWith(Transactional.class), new MethodInterceptor() {
	    		@Inject Db db;
				@Override
				public Object invoke(MethodInvocation mi) throws Throwable {
					return db.inTransaction(conn -> {
						try {
							return mi.proceed();
						} catch (Throwable e) {
							throw Util.asRuntime(e);
						}
					});
				}
	    	});
	    }
	}
}
