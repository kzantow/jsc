package org.jsc.web;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.app.App;

/**
 * Bootstrap to hive app
 */
public class AppFilter implements Filter {
	private App app;
	private AppRequestDispatcher dispatcher;

	@Override
	public void init(FilterConfig cfg) throws ServletException {
		app = new App(cfg.getInitParameter("packages").split(","));
		app.addStartupHook(() -> {
			dispatcher = app.get(AppRequestDispatcher.class);
			dispatcher.setContextPath(cfg.getServletContext().getContextPath());
		});
		app.startup();
	}
	
	@Override
	public void destroy() {
		app.shutdown();
	}

	@Override
	public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
		if(req instanceof HttpServletRequest) {
			dispatcher.handle((HttpServletRequest)req, (HttpServletResponse)res, chain);
		} else {
			chain.doFilter(req, res);
		}
	}

	/**
	 * Send an error, suppress server HTML
	 * @param res
	 * @param e
	 * @throws IOException
	 */
	public static void sendError(HttpServletResponse res, int status, Throwable e) throws IOException {
		if(!res.isCommitted()) {
			if(e != null) {
				res.sendError(status, e.getMessage());
			} else {
				res.sendError(status);
			}
		}
		res.getOutputStream().close(); // prevent tomcat error pages
	}
}
