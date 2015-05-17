package org.jsc.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Enumeration;
import java.util.zip.GZIPOutputStream;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.jsc.Util;
import org.jsc.web.servlet.ServletContextWrapper;

/**
 * Simple Jetty server, is awesome; automatic gzip compression (& HEAD requests)
 * @author kzantow
 */
@Singleton
public class Server {
	final GZipServletFilter gzipFilter;
	final AppFilter filter;
	final FilterChain chainTerminator;
	final FilterChain appFilterChain;
	
	public static void main(String[] args) throws Exception {
		InetAddress addr = InetAddress.getByName(Util.env("serverIp","0.0.0.0"));
		int port = Util.env("serverPort", 8080);
		boolean useJetty = Util.env("useJetty", true);
		System.out.println("Running server on: " + addr + " : " + port);
		InetSocketAddress bind = new InetSocketAddress(addr, port);
		
		Server s = new Server();
		
		if(useJetty) {
			org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server(bind);
			for(Connector y : server.getConnectors()) {
			    for(ConnectionFactory x  : y.getConnectionFactories()) {
			        if(x instanceof HttpConnectionFactory) {
			            ((HttpConnectionFactory)x).getHttpConfiguration().setSendServerVersion(false);
			        }
			    }
			}
	        server.setHandler(new AbstractHandler() {
	        	final GZipServletFilter gzipFilter = s.gzipFilter;
	        	final FilterChain appFilterChain = s.appFilterChain;
	        	@Override
	        	public void handle(String s, Request r, HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
	        		gzipFilter.doFilter(req, res, appFilterChain);
	        		r.setHandled(true);
	        	}
	        });
	        server.start();
	        server.join();
		}
		else {
			JavaServer srv = new JavaServer() {
	        	final GZipServletFilter gzipFilter = s.gzipFilter;
	        	final FilterChain appFilterChain = s.appFilterChain;
				public void doFilter(HttpServletRequest req, HttpServletResponse res) throws Exception {
	        		gzipFilter.doFilter(req, res, appFilterChain);
				}
			};
			srv.start(port);
		}
	}
	

	
	public Server() throws Exception {
		filter = new AppFilter();
		filter.init(new FilterConfig() {
			public ServletContext getServletContext() {
				return new ServletContextWrapper(null) {
					@Override
					public String getContextPath() {
						return "";
					}
				};
			}
			public Enumeration<String> getInitParameterNames() {
				return null;
			}
			public String getInitParameter(String name) {
				return Util.env("packages", "");
			}
			public String getFilterName() {
				return null;
			}
		});
		chainTerminator = new FilterChain() {
			@Override
			public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
				// Requests that make it all the way through are not found
				((HttpServletResponse)response).sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		};
		appFilterChain = new FilterChain() {
			@Override
			public void doFilter(ServletRequest req, ServletResponse res) throws IOException, ServletException {
				filter.doFilter(req, res, chainTerminator);
			}
		};
		gzipFilter = new GZipServletFilter();
	}

	private final class GZipServletFilter implements Filter {
		public void init(FilterConfig filterConfig) throws ServletException {
		}
		public void destroy() {
		}
		public void doFilter(ServletRequest request, ServletResponse response,
				FilterChain chain) throws IOException, ServletException {

			HttpServletRequest httpRequest = (HttpServletRequest) request;
			HttpServletResponse httpResponse = (HttpServletResponse) response;

			if (acceptsGZipEncoding(httpRequest)) {
				GZipServletResponseWrapper gzipResponse = new GZipServletResponseWrapper(
						httpResponse);
				chain.doFilter(request, gzipResponse);
				gzipResponse.close();
			} else {
				chain.doFilter(request, response);
			}
		}

		private boolean acceptsGZipEncoding(HttpServletRequest httpRequest) {
			String acceptEncoding = httpRequest.getHeader("Accept-Encoding");
			return acceptEncoding != null
					&& acceptEncoding.indexOf("gzip") != -1;
		}
	}

	private final class GZipServletResponseWrapper extends HttpServletResponseWrapper {
		private ServletOutputStream gzipOutputStream = null;
		private PrintWriter printWriter = null;

		public GZipServletResponseWrapper(HttpServletResponse response) throws IOException {
			super(response);
		}

		public void close() throws IOException {
			// PrintWriter.close does not throw exceptions. Thus, the call does
			// not need
			// be inside a try-catch block.
			if(printWriter != null) {
				printWriter.close();
			}

			if(gzipOutputStream != null) {
				gzipOutputStream.close();
			}
		}

		/**
		 * Flush OutputStream or PrintWriter
		 *
		 * @throws IOException
		 */

		@Override
		public void flushBuffer() throws IOException {
			// PrintWriter.flush() does not throw exception
			if(printWriter != null) {
				printWriter.flush();
			}

			IOException exception1 = null;
			try {
				if(gzipOutputStream != null) {
					gzipOutputStream.flush();
				}
			} catch(IOException e) {
				exception1 = e;
			}

			IOException exception2 = null;
			try {
				super.flushBuffer();
			} catch(IOException e) {
				exception2 = e;
			}

			if(exception1 != null)
				throw exception1;
			if(exception2 != null)
				throw exception2;
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			if(gzipOutputStream != null) {
				return gzipOutputStream;
			}
			if(getStatus() > 299 || getContentType() == null || !getContentType().startsWith("text/")) {
				gzipOutputStream = super.getOutputStream();
			}
			else {
				if(printWriter != null) {
					throw new IllegalStateException("PrintWriter obtained already - cannot get OutputStream");
				}
				if(gzipOutputStream == null) {
					addHeader("Content-Encoding", "gzip");
					gzipOutputStream = new GZipServletOutputStream(getResponse().getOutputStream());
				}
			}
			return gzipOutputStream;
		}

		@Override
		public PrintWriter getWriter() throws IOException {
			if(printWriter != null) {
				return printWriter;
			}
			if(getStatus() > 299 || getContentType() == null || !getContentType().startsWith("text/")) {
				printWriter = super.getWriter();
			}
			else {
				if(printWriter == null && gzipOutputStream != null) {
					throw new IllegalStateException(
							"OutputStream obtained already - cannot get PrintWriter");
				}
				if(printWriter == null) {
					addHeader("Content-Encoding", "gzip");
					gzipOutputStream = new GZipServletOutputStream(getResponse().getOutputStream());
					printWriter = new PrintWriter(new OutputStreamWriter(gzipOutputStream, getResponse().getCharacterEncoding()));
				}
			}
			return printWriter;
		}
		
		@Override
		public void setContentLength(int len) {
			// ignore, since content length of zipped content
			// does not match content length of unzipped content.
		}
	}

	private final class GZipServletOutputStream extends ServletOutputStream {
		private GZIPOutputStream gzipOutputStream = null;

		public GZipServletOutputStream(OutputStream output) throws IOException {
			super();
			gzipOutputStream = new GZIPOutputStream(output);
		}

		@Override
		public void close() throws IOException {
			gzipOutputStream.close();
		}

		@Override
		public void flush() throws IOException {
			gzipOutputStream.flush();
		}

		@Override
		public void write(byte b[]) throws IOException {
			gzipOutputStream.write(b);
		}

		@Override
		public void write(byte b[], int off, int len) throws IOException {
			gzipOutputStream.write(b, off, len);
		}

		@Override
		public void write(int b) throws IOException {
			gzipOutputStream.write(b);
		}
		
		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setWriteListener(WriteListener wl) {
		}
	}
}
