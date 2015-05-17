package org.jsc.web.auth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Util;
import org.jsc.app.Service;

/**
 * HTTP basic & digest authentication support.
 * In order to support digest, must store the digest of the form:
 * md5([username]:[realm]:[password])
 * 
 * From: https://gist.github.com/usamadar/2912088
 * @author kzantow
 */
@Service
public class HttpAuthenticator {
	private String authMethod = "auth";
    private String realm = "hive";
    public String nonce;
    
    @Inject Authenticator authenticator;

    /**
     * Default constructor to initialize stuff
     *
     */
    public HttpAuthenticator() throws IOException, Exception {
        nonce = calculateNonce();
    }
    
    void basicAuth(HttpServletRequest request, HttpServletResponse response) {
		String authHeader = request.getHeader("authorization");
		String encodedValue = authHeader.substring(authHeader.indexOf(' '));
		String decodedValue = new String(Base64.getDecoder().decode(encodedValue));
		String[] parts = decodedValue.split(":");
		
		Credential<?> user = authenticator.authenticate(new Credentials(new UsernameCredential(parts[0]), new PasswordCredential(parts[1])));
		if(user == null) {
			throw new SecurityException("");
		}
	}

    protected String authenticate(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
        	// authorization = Digest username="asdg", realm="example.com", nonce="84aacba7ecfa01c32368793102834bc0", 
        	// uri="/dav/files", response="eb6776f38d9684986d8555130bff6154", opaque="e63804180dea4292c92657b3a3ad3edd", 
        	// qop=auth, nc=00000001, cnonce="367460247b55f136"

            String authHeader = request.getHeader("Authorization");
            if (Util.isBlank(authHeader)) {
                response.setContentType("text/html;charset=UTF-8");
                response.addHeader("WWW-Authenticate", getAuthenticateHeader());
            } else {
                if (authHeader.startsWith("Digest")) {
                    // parse the values of the Authentication header into a hashmap
                    HashMap<String, String> headerValues = parseHeader(authHeader);
                    
                    String username = headerValues.get("username");

                    String method = request.getMethod();

            		Credential<?> passwordAuthDigest = authenticator.authenticate(new Credentials(new PasswordAuthDigestLookup(username)));
            		
                    String ha1 = passwordAuthDigest instanceof PasswordAuthDigestLookup ? ((PasswordAuthDigestLookup)passwordAuthDigest).getValue() : null;
                    //		Util.md5hex(userName + ":" + realm + ":" + password);
                    
                    if(ha1 == null) {
                    	throw new SecurityException();
                    }

                    String qop = headerValues.get("qop");

                    String ha2;

                    String reqURI = headerValues.get("uri");

                    if (!Util.isBlank(qop) && qop.equals("auth-int")) {
                        String requestBody = readRequestBody(request);
                        String entityBodyMd5 = Util.md5hex(requestBody);
                        ha2 = Util.md5hex(method + ":" + reqURI + ":" + entityBodyMd5);
                    } else {
                        ha2 = Util.md5hex(method + ":" + reqURI);
                    }

                    String serverResponse;

                    if (Util.isBlank(qop)) {
                        serverResponse = Util.md5hex(ha1 + ":" + nonce + ":" + ha2);

                    } else {
                        //String domain = headerValues.get("realm");

                        String nonceCount = headerValues.get("nc");
                        String clientNonce = headerValues.get("cnonce");

                        serverResponse = Util.md5hex(ha1 + ":" + nonce + ":"
                                + nonceCount + ":" + clientNonce + ":" + qop + ":" + ha2);

                    }
                    String clientResponse = headerValues.get("response");

                    if (!serverResponse.equals(clientResponse)) {
                        response.addHeader("WWW-Authenticate", getAuthenticateHeader());
                        throw new SecurityException();
                    }
                    
                    return username;
                }

            }

            /*
             * out.println("<head>"); out.println("<title>Servlet
             * HttpDigestAuth</title>"); out.println("</head>");
             * out.println("<body>"); out.println("<h1>Servlet HttpDigestAuth at
             * " + request.getContextPath () + "</h1>"); out.println("</body>");
             * out.println("</html>");
             */
        } finally {
        }
        
        throw new SecurityException();
    }

    /**
     * Gets the Authorization header string minus the "AuthType" and returns a
     * hashMap of keys and values
     *
     * @param headerString
     * @return
     */
    private HashMap<String, String> parseHeader(String headerString) {
        // seperte out the part of the string which tells you which Auth scheme is it
        String headerStringWithoutScheme = headerString.substring(headerString.indexOf(" ") + 1).trim();
        HashMap<String, String> values = new HashMap<String, String>();
        String keyValueArray[] = headerStringWithoutScheme.split(",");
        for (String keyval : keyValueArray) {
            if (keyval.contains("=")) {
                String key = keyval.substring(0, keyval.indexOf("="));
                String value = keyval.substring(keyval.indexOf("=") + 1);
                values.put(key.trim(), value.replaceAll("\"", "").trim());
            }
        }
        return values;
    }

    private String getAuthenticateHeader() {
        String header = "";

        header += "Digest realm=\"" + realm + "\",";
        if (!Util.isBlank(authMethod)) {
            header += "qop=" + authMethod + ",";
        }
        header += "nonce=\"" + nonce + "\",";
        header += "opaque=\"" + getOpaque(realm, nonce) + "\"";

        return header;
    }

    /**
     * Calculate the nonce based on current time-stamp upto the second, and a
     * random seed
     *
     * @return
     */
    public String calculateNonce() {
        Date d = new Date();
        SimpleDateFormat f = new SimpleDateFormat("yyyy:MM:dd:hh:mm:ss");
        String fmtDate = f.format(d);
        Random rand = new Random(100000);
        Integer randomInt = rand.nextInt();
        return Util.md5hex(fmtDate + randomInt.toString());
    }

    private String getOpaque(String domain, String nonce) {
        return Util.md5hex(domain + nonce);
    }

    /**
     * Returns the request body as String
     *
     * @param request
     * @return
     * @throws IOException
     */
    private String readRequestBody(HttpServletRequest request) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader bufferedReader = null;
        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                bufferedReader = new BufferedReader(new InputStreamReader(
                        inputStream));
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            } else {
                stringBuilder.append("");
            }
        } catch (IOException ex) {
            throw ex;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    throw ex;
                }
            }
        }
        String body = stringBuilder.toString();
        return body;
    }
  
}