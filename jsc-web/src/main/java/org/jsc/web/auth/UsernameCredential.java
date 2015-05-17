package org.jsc.web.auth;

/**
 * Simple credential for plaintext username
 * @author kzantow
 */
public class UsernameCredential implements Credential<String> {
	private String name;
	
	public UsernameCredential() {
	}
	
	public UsernameCredential(String name) {
		this.name = name;
	}

	@Override
	public String getValue() {
		return name;
	}
}
