package org.jsc.web;

import java.io.Writer;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Util;
import org.jsc.app.OnStartup;
import org.jsc.app.Service;
import org.jsc.web.AppRequestDispatcher.HandlesRequests;
import org.jsc.web.AppRequestDispatcher.RequestHandler;
import org.jsc.web.ui.Component;
import org.jsc.web.ui.Context;
import org.jsc.web.ui.PostbackContext;

import com.google.inject.Provider;

@Service
@HandlesRequests
public class JavascriptComponentHandler extends JavaClassHandlers {
	private final Map<String,Object> bindings = new HashMap<String,Object>() {
		private static final long serialVersionUID = 1L;

		public Object get(Object key) {
			Object out = super.get(key);
			if(out instanceof Provider<?>) {
				return ((Provider<?>)out).get();
			}
			return out;
		}
	};
	
	@Override
	@OnStartup
	public void init() {
		bindings.putAll(expr.getGlobals());
		for(Class<?> typ : app.findAnnotatedClasses(Service.class)) {
			// add all service-level bindings to the global scope based on simple class name
			String serviceName = typ.getSimpleName();
			bindings.put(serviceName, app.get(typ));
		}
		for(Class<?> typ : app.findAnnotatedClasses(Named.class)) {
			// add all named bindings to the global scope
			String serviceName = typ.getAnnotation(Named.class).value();
			bindings.put(serviceName, new Provider<Object>() {
				@Override
				public Object get() {
					return app.get(typ);
				}
			});
		}
		bindings.put("console", new Console());
	}
	
	public static class Console {
		public void log(String s) {
			System.out.println(s);
		}
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
					public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
						if(u != null) {
							res.setContentType(TEXT_HTML);
							res.setCharacterEncoding(Util.UTF8);
							Writer w = res.getWriter();
							try {
								Component cmp = tpl.getComponent(file, u);
								if("POST".equals(req.getMethod())) {
									PostbackContext ctx = new PostbackContext(req, bindings);
									try {
										cmp.postback(ctx);
										
										// TODO won't always redirect... might download, etc..
										res.sendRedirect(getFilterPath() + req.getRequestURI());
									} catch(Exception e) {
										// In case of an error, re-render with error context
										ctx.put("error", Util.getRootCause(e));
										cmp.render(ctx, w);
									}
								}
								else {
									Context ctx = new Context(bindings);
									ctx.put("error", null); // FIXME remove this; need JS to be able to handle missing values like JSF EL does...
									cmp.render(ctx, w);
								}
							} catch(Exception e) {
								renderErrorPage(e, w);
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
