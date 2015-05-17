package org.jsc.web.ui;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;

public class PostbackContext extends Context {
	HttpServletRequest req;
	
	public PostbackContext(HttpServletRequest req, Map<String,Object> context) {
		super(context);
		this.req = req;
	}
	
	public PostbackContext(Map<String, Object> context, PostbackContext parent) {
		super(context, parent);
		this.req = parent.req;
	}
	
	public HttpServletRequest getRequest() {
		return req;
	}
}
