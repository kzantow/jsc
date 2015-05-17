package org.jsc;

/**
 * Accepts a single parameter, returns a value
 * @author kzantow
 */
@FunctionalInterface
public interface Fn1<ReturnType, T> {
	/**
	 * Accept a parameter, return a value
	 */
	public ReturnType exec(T t) throws Exception;
}
