package org.jsc;

/**
 * Executes some code, provides a result, takes no arguments
 * @author kzantow
 */
@FunctionalInterface
public interface Fn<ReturnType> {
	/**
	 * Provide the thing
	 */
	public ReturnType exec() throws Exception;
}
