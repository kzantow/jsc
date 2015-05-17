package org.jsc.web.auth;

/**
 * Credential to contain a plaintext password
 * @author kzantow
 */
public class PasswordCredential implements Credential<String> {
	private String password;
	
	public PasswordCredential() {
	}
	
	public PasswordCredential(String password) {
		this.password = password;
	}

	@Override
	public String getValue() {
		return password;
	}
}
