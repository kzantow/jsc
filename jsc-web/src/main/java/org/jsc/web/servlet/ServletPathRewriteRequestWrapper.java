package org.jsc.web.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * Simple servlet request wrapper used to provide an alternate path
 * @author kzantow
 */
public class ServletPathRewriteRequestWrapper extends HttpServletRequestWrapper {
	String reqURI;
	public ServletPathRewriteRequestWrapper(HttpServletRequest request, String reqURI) {
		super(request);
		this.reqURI = reqURI;
	}

	@Override
	public String getRequestURI() {
		return reqURI;
	}
	
	public void setRequestURI(String reqURI) {
		this.reqURI = reqURI;
	}
}
