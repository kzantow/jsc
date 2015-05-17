package org.jsc.web.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Enumeration;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import org.jsc.Util;

public class LoggingServletWrappers {
	static void dumpRequest(HttpServletRequest req, Writer out) throws IOException {
		out.append("----- REQUEST -----\n");
		out.append(req.getMethod()).append(" ")
			.append(req.getRequestURI())
			.append(req.getQueryString() == null ? "" : req.getQueryString())
			.append("\n");
		for(Enumeration<String> s = req.getHeaderNames(); s.hasMoreElements();) {
			String k = s.nextElement();
			out.append(k + ": " + req.getHeader(k) + "\n");
		}
	}
	
	static void dumpResponse(HttpServletResponse res, String requestId, Writer out) throws IOException {
		out.append("----- RESPONSE for: ").append(requestId).append(" -----\n");
		out.append(Integer.toString(res.getStatus())).append("\n");
		for(String k : res.getHeaderNames()) {
			out.append(k + ": " + res.getHeader(k) + "\n");
		}
	}
	public static class HttpRequestWrapper extends HttpServletRequestWrapper {
		Writer out;
		
		BufferedReader r;
		ServletInputStream in; 
		
		public HttpRequestWrapper(HttpServletRequest req, Writer out) {
			super(req);
			this.out = out;
			try {
				dumpRequest(this, out);
				out.flush();
			} catch(Exception e) {
				Util.ignore(e);
			}
		}
		
		public BufferedReader getReader() throws IOException {
			if(r == null) {
				
				r = super.getReader();
				r = new BufferedReader(r) {
					@Override
					public int read(char[] cbuf, int off, int len) throws IOException {
						int read = super.read(cbuf, off, len);
						if(read > 0) {
							out.write(cbuf, off, read);
							out.flush();
						}
						return read;
					}
				};
			}
			return r;
		}
		
		@Override
		public ServletInputStream getInputStream() throws IOException {
			if(in == null) {
				in = super.getInputStream();
				
				String ct = getHeader("Content-Type");
				if(ct != null && ct.startsWith("text")) {
					ServletInputStream s = in;
					in = new ServletInputStream() {
						@Override
						public int read() throws IOException {
							int i = s.read();
							if(i >= 0) {
								// TODO dangling utf 8 chars... ugh
								out.write((char)i);
							}
							return i;
						}
						@Override
						public int readLine(byte[] b, int off, int len) throws IOException {
							int read = s.readLine(b, off, len);
							out.flush();
							return read;
						}
						@Override
						public int read(byte[] b, int off, int len) throws IOException {
							int read = s.read(b, off, len);
							out.flush();
							return read;
						}
						
						@Override
						public boolean isFinished() {
							return s.isFinished();
						}
						
						@Override
						public boolean isReady() {
							return s.isReady();
						}
						
						@Override
						public void setReadListener(ReadListener l) {
							s.setReadListener(l);
						}
					};
				}
			}
			return in;
		}
	}
	
	public static class HttpResponseWrapper extends HttpServletResponseWrapper {
		Writer out;
		
		String requestId;
		HttpServletRequest originRequest;
		StringWriter sw = new StringWriter();
		PrintWriter w;
		ServletOutputStream os;
		boolean isText = false;
		
		public HttpResponseWrapper(HttpServletResponse res, HttpServletRequest originRequest, String requestId, Writer out) {
			super(res);
			this.out = out;
			this.originRequest = originRequest;
			this.requestId = requestId;
		}
		
		@Override
		public PrintWriter getWriter() throws IOException {
			if(w == null) {
				if(os != null) {
					throw new RuntimeException("OutputStream already accessed.");
				}
				Writer s = super.getWriter();
				w = new PrintWriter(s) {
					@Override
					public void write(char[] buf, int off, int len) {
						sw.write(buf, off, len);
						super.write(buf, off, len);
					}
					@Override
					public void close() {
						try {
							dumpResponse(HttpResponseWrapper.this, requestId, out);
							out.write(sw.toString());
							out.write("\n");
							out.flush();
						} catch(Exception e) {
							Util.ignore(e);
						}
						super.close();
					}
				};
			}
			return w;
		}
		
		@Override
		public ServletOutputStream getOutputStream() throws IOException {
			if(os == null) {
				if(w != null) {
					throw new RuntimeException("Writer already accessed.");
				}
				ServletOutputStream s = super.getOutputStream();
				os = new ServletOutputStream() {
					@Override
					public void write(int b) throws IOException {
						s.write(b);
					}
					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						if(isText) {
							sw.write(new String(b, off, len, Util.UTF8C));
						}
						s.write(b, off, len);
					}
					@Override
					public void close() throws IOException {
						try {
							dumpResponse(HttpResponseWrapper.this, requestId, out);
							if(isText) {
								out.write(sw.toString());
							}
							else {
								out.write("<binary data>");
							}
							out.write("\n");
							out.flush();
						} catch(Exception e) {
							Util.ignore(e);
						}
						s.close();
					}
					@Override
					public boolean isReady() {
						return s.isReady();
					}
					@Override
					public void setWriteListener(WriteListener l) {
						s.setWriteListener(l);
					}
				};
			}
			return os;
		}
		
		@Override
		public void setContentType(String type) {
			if(type.startsWith("text")) {
				isText = true;
			}
			super.setContentType(type);
		}
	}
}
