package org.jsc.web;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Log;
import org.jsc.Util;
import org.jsc.app.App;
import org.jsc.app.OnStartup;
import org.jsc.web.servlet.ServletPathRewriteRequestWrapper;

/**
 * Handles dispatch to json services & pages
 * @author kzantow
 */
@Singleton
public class AppRequestDispatcher {
	/**
	 * Implement this to handle a particular request... could be for JSON HTML or other
	 */
	public interface RequestHandler {
		void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable;
		String getFilterPath();
	}
	
	/**
	 * Annotate a {@link RequestHandlerProvider} to be picked up by the AppRequestDispatcher
	 */
	public @interface HandlesRequests {
	}
	
	/**
	 * Used to find a handler for a paritcular request
	 */
	public interface RequestHandlerProvider {
		RequestHandler getRequestHandler(String reqURI, HttpServletRequest req);
	}
	
	@Inject private App app;
	
	private RequestHandlerProvider[] handlers = new RequestHandlerProvider[0];
	
	
	public String contextPath = "";
	private int contextPathLength = 0;
	private RequestHandler indexHandler;
	
	public String getContextPath() {
		return contextPath;
	}
	
	public void setContextPath(String contextPath) {
		this.contextPath = contextPath;
		contextPathLength = contextPath.length();
	}
	
	@OnStartup
	public void build() throws Exception {
		for(Class<?> c : app.findAnnotatedClasses(HandlesRequests.class)) {
			handlers = Util.append(handlers, (RequestHandlerProvider)app.get(c));
		}
		for(RequestHandlerProvider prov : handlers) {
			RequestHandler h = prov.getRequestHandler("/index/index", null); // look for java code first
			if(h != null) {
				indexHandler = h;
				break;
			}
		}
		if(indexHandler == null) {
			for(RequestHandlerProvider prov : handlers) {
				RequestHandler h = prov.getRequestHandler("/index", null);
				if(h != null) {
					indexHandler = h;
					break;
				}
			}
		}
	}

	/**
	 * Get the first matching request handler
	 */
	private RequestHandler getHandler(String reqURI, HttpServletRequest req) {
		for(int i = 0; i < handlers.length; i++) {
			RequestHandler h = handlers[i].getRequestHandler(reqURI, req);
			if(h != null) {
				return h;
			}
		}
		return null;
	}

	public void handle(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws IOException, ServletException {
		long start = System.currentTimeMillis();
		String reqURI = req.getRequestURI();
		try {
			if(contextPathLength > 0) {
				reqURI = reqURI.substring(contextPathLength); // normalize req uri based on context path
			}
			req.setCharacterEncoding(Util.UTF8);
			RequestHandler handler = getHandler(reqURI, req);
			if(handler != null) {
				reqURI = reqURI.substring(handler.getFilterPath().length());
				try {
					req = new ServletPathRewriteRequestWrapper(req, reqURI);
					handler.handle(req, res);
				} catch(Throwable t) {
					Util.ignore(t);
					t = Util.getCause(t);
					if(t instanceof SecurityException) {
						AppFilter.sendError(res, HttpServletResponse.SC_UNAUTHORIZED, t);
						return;
					}
					t.printStackTrace();
					AppFilter.sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t);
					Log.log.error(t);
					return;
				}
			} else {
				if(reqURI.length() == 1) {
					try {
						indexHandler.handle(req, res);
						return;
					} catch(Throwable t) {
						t.printStackTrace();
						Util.ignore(t);
						AppFilter.sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t);
					}
				}
				chain.doFilter(req, res);
			}
		} finally {
			if(App.development) {
				Log.log.debug(req.getRemoteAddr(), " ", res.getStatus(), " ", req.getMethod(), " ", reqURI, " -- " , System.currentTimeMillis()-start, "ms");
			}
		}
	}
}
