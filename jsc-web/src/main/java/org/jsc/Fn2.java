package org.jsc;

/**
 * Accepts two parameters, returns a value
 * @author kzantow
 */
@FunctionalInterface
public interface Fn2<ReturnType, Arg1, Arg2> {
	/**
	 * Accept two parameters, return a value
	 */
	public ReturnType exec(Arg1 v1, Arg2 v2) throws Exception;
}
