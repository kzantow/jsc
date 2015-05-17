package org.jsc;

/**
 * Process, does not return a value; does not accept parameters
 * @author kzantow
 */
@FunctionalInterface
public interface Proc {
	/**
	 * Execute whatever
	 */
	void exec();
}
