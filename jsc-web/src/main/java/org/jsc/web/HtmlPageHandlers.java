package org.jsc.web;

import java.net.URL;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Util;
import org.jsc.app.Service;
import org.jsc.app.OnStartup;
import org.jsc.web.AppRequestDispatcher.RequestHandler;
import org.jsc.web.ui.Component;
import org.jsc.web.ui.Context;

@Service
//@HandlesRequests
public class HtmlPageHandlers extends JavaClassHandlers {
	@Override
	@OnStartup
	public void init() {
	}
	
	@Override
	public RequestHandler getRequestHandler(String reqURI, HttpServletRequest req) {
		URL u  = app.getResource(reqURI + ".html");
		if(u != null) {
			try {
				Component c = tpl.parseTemplate(reqURI, u);
				return new RequestHandler() {
					@SuppressWarnings("unchecked")
					@Override
					public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
						c.render(new Context(Collections.EMPTY_MAP), res.getWriter());
					}
					
					@Override
					public String getFilterPath() {
						return null;
					}
				};
			} catch (Exception e) {
				throw Util.asRuntime(e);
			}
		}
		return null;
	}
}
