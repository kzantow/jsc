package org.jsc.web.auth;

import org.jsc.Util;

/**
 * A set of credentials
 * @author kzantow
 */
public class Credentials {
	Credential<?>[] credentials;
	
	/**
	 * Construct this set of credentials
	 * @param credentials
	 */
	public Credentials(Credential<?> ... credentials) {
		this.credentials = credentials;
	}
	
	/**
	 * Append a credential to this set; e.g. multi-factor authentication
	 * @param credential
	 */
	public void append(Credential<?> credential) {
		credentials = Util.append(credentials, credential);
	}
	
	/**
	 * Gets a matching credential. NOTE: if multiple credentials exist
	 * that are assignable from the given type, this method will return the first to
	 * match, not the most specific.
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends Credential<?>> T get(Class<T> type) {
		for(int i = 0; i < credentials.length; i++) {
			if(type.isInstance(credentials[i])) {
				return (T)credentials[i];
			}
		}
		return null;
	}
}
