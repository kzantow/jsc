package org.jsc.web.servlet;

import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.EventListener;
import java.util.Map;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.FilterRegistration;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;
import javax.servlet.ServletRegistration.Dynamic;
import javax.servlet.SessionCookieConfig;
import javax.servlet.SessionTrackingMode;
import javax.servlet.descriptor.JspConfigDescriptor;

/**
 * Wraps a ServletContext..
 * @author kzantow
 */
@SuppressWarnings("deprecation")
public class ServletContextWrapper implements ServletContext {
	ServletContext delegate;
	
	public ServletContextWrapper(ServletContext delegate) {
		this.delegate = delegate;
	}

	@Override
	public String getContextPath() {
		return delegate.getContextPath();
	}

	@Override
	public ServletContext getContext(String uripath) {
		return delegate.getContext(uripath);
	}

	@Override
	public int getMajorVersion() {
		return delegate.getMajorVersion();
	}

	@Override
	public int getMinorVersion() {
		return delegate.getMinorVersion();
	}

	@Override
	public int getEffectiveMajorVersion() {
		return delegate.getEffectiveMajorVersion();
	}

	@Override
	public int getEffectiveMinorVersion() {
		return delegate.getEffectiveMinorVersion();
	}

	@Override
	public String getMimeType(String file) {
		return delegate.getMimeType(file);
	}

	@Override
	public Set<String> getResourcePaths(String path) {
		return delegate.getResourcePaths(path);
	}

	@Override
	public URL getResource(String path) throws MalformedURLException {
		return delegate.getResource(path);
	}

	@Override
	public InputStream getResourceAsStream(String path) {
		return delegate.getResourceAsStream(path);
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		return delegate.getRequestDispatcher(path);
	}

	@Override
	public RequestDispatcher getNamedDispatcher(String name) {
		return delegate.getNamedDispatcher(name);
	}

	@Override
	public Servlet getServlet(String name) throws ServletException {
		return delegate.getServlet(name);
	}

	@Override
	public Enumeration<Servlet> getServlets() {
		return delegate.getServlets();
	}

	@Override
	public Enumeration<String> getServletNames() {
		return delegate.getServletNames();
	}

	@Override
	public void log(String msg) {
		delegate.log(msg);
	}

	@Override
	public void log(Exception exception, String msg) {
		delegate.log(exception, msg);
	}

	@Override
	public void log(String message, Throwable throwable) {
		
		delegate.log(message, throwable);
	}

	@Override
	public String getRealPath(String path) {
		return delegate.getRealPath(path);
	}

	@Override
	public String getServerInfo() {
		return delegate.getServerInfo();
	}

	@Override
	public String getInitParameter(String name) {
		return delegate.getInitParameter(name);
	}

	@Override
	public Enumeration<String> getInitParameterNames() {
		return delegate.getInitParameterNames();
	}

	@Override
	public boolean setInitParameter(String name, String value) {
		return delegate.setInitParameter(name, value);
	}

	@Override
	public Object getAttribute(String name) {
		return delegate.getAttribute(name);
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		return delegate.getAttributeNames();
	}

	@Override
	public void setAttribute(String name, Object object) {
		delegate.setAttribute(name, object);
	}

	@Override
	public void removeAttribute(String name) {
		delegate.removeAttribute(name);
	}

	@Override
	public String getServletContextName() {
		return delegate.getServletContextName();
	}

	@Override
	public Dynamic addServlet(String servletName, String className) {
		return delegate.addServlet(servletName, className);
	}

	@Override
	public Dynamic addServlet(String servletName, Servlet servlet) {
		return delegate.addServlet(servletName, servlet);
	}

	@Override
	public Dynamic addServlet(String servletName, Class<? extends Servlet> servletClass) {
		return delegate.addServlet(servletName, servletClass);
	}

	@Override
	public <T extends Servlet> T createServlet(Class<T> clazz) throws ServletException {
		return delegate.createServlet(clazz);
	}

	@Override
	public ServletRegistration getServletRegistration(String servletName) {
		return delegate.getServletRegistration(servletName);
	}

	@Override
	public Map<String, ? extends ServletRegistration> getServletRegistrations() {
		return delegate.getServletRegistrations();
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, String className) {
		return delegate.addFilter(filterName, className);
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Filter filter) {
		return delegate.addFilter(filterName, filter);
	}

	@Override
	public javax.servlet.FilterRegistration.Dynamic addFilter(String filterName, Class<? extends Filter> filterClass) {
		return delegate.addFilter(filterName, filterClass);
	}

	@Override
	public <T extends Filter> T createFilter(Class<T> clazz) throws ServletException {
		return delegate.createFilter(clazz);
	}

	@Override
	public FilterRegistration getFilterRegistration(String filterName) {
		return delegate.getFilterRegistration(filterName);
	}

	@Override
	public Map<String, ? extends FilterRegistration> getFilterRegistrations() {
		return delegate.getFilterRegistrations();
	}

	@Override
	public SessionCookieConfig getSessionCookieConfig() {
		return delegate.getSessionCookieConfig();
	}

	@Override
	public void setSessionTrackingModes(Set<SessionTrackingMode> sessionTrackingModes) {
		delegate.setSessionTrackingModes(sessionTrackingModes);
	}

	@Override
	public Set<SessionTrackingMode> getDefaultSessionTrackingModes() {
		return delegate.getDefaultSessionTrackingModes();
	}

	@Override
	public Set<SessionTrackingMode> getEffectiveSessionTrackingModes() {
		return delegate.getEffectiveSessionTrackingModes();
	}

	@Override
	public void addListener(String className) {
		delegate.addListener(className);
	}

	@Override
	public <T extends EventListener> void addListener(T t) {
		delegate.addListener(t);
	}

	@Override
	public void addListener(Class<? extends EventListener> listenerClass) {
		delegate.addListener(listenerClass);
	}

	@Override
	public <T extends EventListener> T createListener(Class<T> clazz) throws ServletException {
		return delegate.createListener(clazz);
	}

	@Override
	public JspConfigDescriptor getJspConfigDescriptor() {
		return delegate.getJspConfigDescriptor();
	}

	@Override
	public ClassLoader getClassLoader() {
		return delegate.getClassLoader();
	}

	@Override
	public void declareRoles(String... roleNames) {
		delegate.declareRoles(roleNames);
	}
	
	@Override
	public String getVirtualServerName() {
		return delegate.getVirtualServerName();
	}
}