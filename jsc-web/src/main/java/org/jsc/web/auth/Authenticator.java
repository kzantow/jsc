package org.jsc.web.auth;

/**
 * Provides a pluggable simple authentication mechanism.
 * @author kzantow
 */
public interface Authenticator {
	/**
	 * Authenticate based on the user and the given principal. Implementations MUST return null when unable to authenticate.
	 * @param principal
	 */
	public Credential<?> authenticate(Credentials credentials);
}
