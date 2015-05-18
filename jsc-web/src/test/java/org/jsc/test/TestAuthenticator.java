package org.jsc.test;

import javax.enterprise.inject.Default;

import org.jsc.web.auth.Authenticator;
import org.jsc.web.auth.Credential;
import org.jsc.web.auth.Credentials;

@Default
public class TestAuthenticator implements Authenticator {
	@Override
	public Credential<?> authenticate(Credentials credentials) {
		return null;
	}
}
