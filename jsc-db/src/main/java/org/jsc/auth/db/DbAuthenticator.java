package org.jsc.auth.db;

import javax.enterprise.inject.Default;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.jsc.Util;
import org.jsc.db.Db;
import org.jsc.web.auth.Authenticator;
import org.jsc.web.auth.Credential;
import org.jsc.web.auth.Credentials;
import org.jsc.web.auth.PasswordAuthDigestLookup;
import org.jsc.web.auth.PasswordCredential;
import org.jsc.web.auth.UsernameCredential;

@Default
@Singleton
public class DbAuthenticator implements Authenticator {
	
	@Inject Db db;
	
	@Override
	public Credential<?> authenticate(Credentials credentials) {
		PasswordAuthDigestLookup digestRequest = credentials.get(PasswordAuthDigestLookup.class);
		if(digestRequest != null) {
			String authDigestHash = db.sql("select passwordAuthDigest from user where name = ").bind(digestRequest.getValue()).getString();
			return new PasswordAuthDigestLookup(authDigestHash);
		}
		UsernameCredential username = credentials.get(UsernameCredential.class);
		PasswordCredential password = credentials.get(PasswordCredential.class);
		if(username == null || password == null) {
			return null;
		}
		if(db.sql("select count(*) from user where name = ").bind(username.getValue())
				.append(" and passwordHash = ").bind(Util.sha256(password.getValue())).getInt() == 0) {
			return null;
		}
		return username;
	}
}
