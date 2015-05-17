package org.jsc;

/**
 * Process, does not return a value; accepts a single parameter
 * @author kzantow
 */
@FunctionalInterface
public interface Proc1<T> {
	/**
	 * Execute with the given parameter
	 * @param t
	 * @throws Exception
	 */
	void exec(T t) throws Exception;
}
