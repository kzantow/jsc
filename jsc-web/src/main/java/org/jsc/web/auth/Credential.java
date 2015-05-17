package org.jsc.web.auth;

/**
 * Interface to provide one or more credentials to a context
 * @author kzantow
 */
public interface Credential<T> {
	/**
	 * Gets the value of this credential
	 * @return
	 */
	public T getValue();
}
