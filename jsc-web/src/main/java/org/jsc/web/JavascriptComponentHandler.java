package org.jsc.web;

import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Util;
import org.jsc.app.Service;
import org.jsc.app.OnStartup;
import org.jsc.web.AppRequestDispatcher.HandlesRequests;
import org.jsc.web.AppRequestDispatcher.RequestHandler;
import org.jsc.web.ui.Component;
import org.jsc.web.ui.Context;
import org.jsc.web.ui.PostbackContext;

@Service
@HandlesRequests
public class JavascriptComponentHandler extends JavaClassHandlers {
	Map<String,Object> bindings = new HashMap<String,Object>();
	
	@Override
	@OnStartup
	public void init() {
		bindings.putAll(expr.getGlobals());
	}
	
	@Override
	public RequestHandler getRequestHandler(String reqURI, HttpServletRequest req) {
		String file = reqURI.substring(1).replace('.', '/');
		URL u = app.getResource(file + ".html");
		if(u != null) {
			URL js = app.getResource(file + ".js");
			if(js != null) {
				RequestHandler handler = new RequestHandler() {
					public String getFilterPath() {
						return reqURI;
					}
					@SuppressWarnings("unchecked")
					public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
						if(u != null) {
							res.setContentType(TEXT_HTML);
							res.setCharacterEncoding(Util.UTF8);
							Writer w = res.getWriter();
							Component cmp = tpl.getComponent(file, u);
							if("POST".equals(req.getMethod())) {
								PostbackContext ctx = new PostbackContext(req, bindings);
								cmp.postback(ctx);
								res.sendRedirect(getFilterPath() + req.getRequestURI());
							}
							else {
								Context ctx = new Context(bindings);
								try {
									cmp.render(ctx, w);
								} catch(Exception e) {
									renderErrorPage(e, w);
								}
							}
						}			
					}
				};
				
				return handler;
			}
		}
		
		return null;
	}
}
