package org.jsc.web.auth;

/**
 * For HTTP digest authentication, we need the password auth digest; this provides a method to request it from the
 * authentication source
 * @author kzantow
 */
public class PasswordAuthDigestLookup implements Credential<String> {
	private String name;
	
	public PasswordAuthDigestLookup() {
	}
	
	public PasswordAuthDigestLookup(String name) {
		this.name = name;
	}

	@Override
	public String getValue() {
		return name;
	}
}
