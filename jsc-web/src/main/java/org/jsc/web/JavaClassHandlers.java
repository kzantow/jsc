package org.jsc.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.ExpressionService;
import org.jsc.Fn2;
import org.jsc.StringMatcher;
import org.jsc.Util;
import org.jsc.app.App;
import org.jsc.app.Before;
import org.jsc.app.Service;
import org.jsc.app.OnStartup;
import org.jsc.io.Json;
import org.jsc.web.AppRequestDispatcher.HandlesRequests;
import org.jsc.web.AppRequestDispatcher.RequestHandler;
import org.jsc.web.ui.Component;
import org.jsc.web.ui.Context;
import org.jsc.web.ui.ObjectContext;
import org.jsc.web.ui.PostbackContext;
import org.jsc.web.ui.TemplateProcessor;

@Service
@HandlesRequests
public class JavaClassHandlers implements AppRequestDispatcher.RequestHandlerProvider {
	public static final String TEXT_HTML = "text/html";
	
	@Inject protected App app;
	@Inject protected ExpressionService expr;
	@Inject protected Json json;
	@Inject protected TemplateProcessor tpl;
	
	protected StringMatcher<RequestHandler> handlers = new StringMatcher<RequestHandler>();
	
	@Override
	public RequestHandler getRequestHandler(String reqURI, HttpServletRequest req) {
		return handlers.get(reqURI);
	}
	
	@Before(AppRequestDispatcher.class)
	@OnStartup
	@SuppressWarnings("unchecked")
	public void init() {
		for(Class<?> typ : app.findAnnotatedClasses(Service.class)) {
			// Directly map RequestHandlers
			if(RequestHandler.class.isAssignableFrom(typ)) {
				// explicitly add this to our filter path
				RequestHandler h = (RequestHandler)app.get(typ);
				handlers.add(h.getFilterPath(), h);
				continue;
			}
			
			String serviceName;
			if(typ.isAnnotationPresent(Named.class)) {
				serviceName = typ.getAnnotation(Named.class).value();
			} else {
				serviceName = typ.getSimpleName().replace("Service", "").toLowerCase();
			}
			
			for(Method m : typ.getDeclaredMethods()) {
				if(Modifier.isStatic(m.getModifiers())
				|| m.isSynthetic()
				|| m.isBridge()
				|| Modifier.isAbstract(m.getModifiers())
				|| !Modifier.isPublic(m.getModifiers())) {
					continue;
				}
				
				Annotation[][] annotations = m.getParameterAnnotations();
				
				String name = m.getName();
				if(name.startsWith("get") && name.length() > 3) {
					name = name.substring(3);
				}
				
				// might rather Util.lowerFirst this
				name = name.toLowerCase();
				
				String filterPath = '/' + serviceName + '/' + name;
				
				m.setAccessible(true);
				
				Class<?>[] paramTypes = m.getParameterTypes();
				Fn2<Object, HttpServletRequest, HttpServletResponse>[] paramHandlers = new Fn2[paramTypes.length];
				
				for(int i = 0; i < paramTypes.length; i++) {
					Class<?> paramType = paramTypes[i];
					if(paramType == HttpServletRequest.class) {
						paramHandlers[i] = (req, res) -> req;
					}
					else if(paramType == HttpServletResponse.class) {
						paramHandlers[i] = (req, res) -> res;
					}
					else if(paramType == InputStream.class) {
						paramHandlers[i] = (req, res) -> req.getInputStream();
					}
					else if(paramType == OutputStream.class) {
						paramHandlers[i] = (req, res) -> res.getOutputStream();
					}
					else if(paramType == Reader.class) {
						paramHandlers[i] = (req, res) -> req.getReader();
					}
					else if(paramType == Writer.class) {
						paramHandlers[i] = (req, res) -> {
							res.setContentType("text/html");
							return res.getWriter();
						};
					}
					else if(paramType == ContentWriter.class) {
						paramHandlers[i] = (req, res) -> new ContentWriter() {
							public void setName(String name) {
								res.setHeader("Content-Disposition","attachment;filename=\""+name+"\"");
							}
							public void setContentType(String contentType) {
								res.setContentType(contentType);
							}
							public void setContentLength(long length) {
								res.setContentLength((int)length);
							}
							public Writer getWriter() {
								try {
									return res.getWriter();
								} catch(Exception e) {
									throw Util.asRuntime(e);
								}
							}
						};
					}
					else if(paramType == ContentStream.class) {
						paramHandlers[i] = (req, res) -> new ContentStream() {
							public void setName(String name) {
								res.setHeader("Content-Disposition","attachment;filename=\""+name+"\"");
							}
							public void setContentType(String contentType) {
								res.setContentType(contentType);
							}
							public void setContentLength(long length) {
								res.setContentLength((int)length);
							}
							public OutputStream getOutputStream() {
								try {
									return res.getOutputStream();
								} catch(Exception e) {
									throw Util.asRuntime(e);
								}
							}
						};
					}
					else if(annotations[i] != null && annotations[i].length > 0 && annotations[i][0] instanceof Named) {
						String parameterName = ((Named)annotations[i][0]).value();
						paramHandlers[i] = (req, res) -> {
							return req.getParameter(parameterName);
						};
					}
					else {
						int getIdx = i;
						paramHandlers[i] = (req, res) -> {
							String requestType = req.getMethod(); // This should be upper case
							if("POST".equals(requestType)) {
								return json.fromJson(paramType, req.getReader());
							}
							else if("GET".equals(requestType)) {
								String path = req.getRequestURI();
								if(path.startsWith("/")) {
									path = path.substring(1);
								}
								String[] parts = path.split("\\/");
								return json.fromJson(paramType, parts[getIdx]);
							}
							return null;
						};
					}
				}
				
				URL u = app.getResource(typ.getPackage().getName().replace('.', '/') + '/' + m.getName() + ".html");
				
				RequestHandler handler = new RequestHandler() {
					public String getFilterPath() {
						return filterPath;
					}
					public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
						Object[] args = new Object[paramHandlers.length];
						try {
							for(int i = 0; i < paramHandlers.length; i++) {
								args[i] = paramHandlers[i].exec(req, res);
							}
						} catch(Exception e) {
							Util.ignore(e);
							AppFilter.sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e);
							return;
						}
						
						Object o = app.get(typ);
						Map<String,Object> objectContext = new ObjectContext(o);
						Object out = m.invoke(o, args);
						
						if(u != null) {
							res.setContentType(TEXT_HTML);
							res.setCharacterEncoding(Util.UTF8);
							Writer w = res.getWriter();
							Component cmp = tpl.getComponent(req.getRequestURI(), u);
							if("POST".equals(req.getMethod())) {
								PostbackContext ctx = new PostbackContext(objectContext, new PostbackContext(req, Collections.EMPTY_MAP));
								cmp.postback(ctx);
								res.sendRedirect(getFilterPath() + req.getRequestURI());
							}
							else {
								Context ctx = new Context(objectContext);
								cmp.render(ctx, w);
							}
						}
						else {
							if(m.getReturnType() != Void.TYPE) { // send JSON response for non-void methods
								res.setCharacterEncoding("utf-8");
								res.setContentType("application/json");
								json.toJson(out, res.getWriter());
							}
						}
					}
				};
				
				handlers.add(handler.getFilterPath(), handler);
			}
		}
	}
	
	public void renderErrorPage(Exception e, Writer w) {
		Throwable t = Util.getCause(e);
		try {
			w.append("<!doctype html><html>")
			.append("<head><style type='text/css'>body{color:red;font:Arial,sans}h1{border-bottom:1px solid #400}</style></head>")
			.append("<body><h1>").append(t.getMessage()).append("</h1><pre>")
			.append(Util.stackTrace(t))
			.append("</pre>");
			
			// TODO if special exception with metadata somewhere in there - e.g. template locations, print all that out
			
			w.append("</body></html>");
		} catch (IOException ex) {
			// Uhh... bollox
			Util.ignore(ex);
		}
	}
}
