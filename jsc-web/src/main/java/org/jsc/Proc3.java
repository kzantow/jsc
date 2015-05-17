package org.jsc;

/**
 * Process, does not return a value; accepts parameters
 * @author kzantow
 */
@FunctionalInterface
public interface Proc3<Arg1, Arg2, Arg3> {
	/**
	 * Execute with the given parameters
	 * @param t
	 * @throws Exception
	 */
	void exec(Arg1 v1, Arg2 v2, Arg3 v3) throws Exception;
}
