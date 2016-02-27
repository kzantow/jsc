package org.jsc.vfs.dav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.Log;
import org.jsc.Util;
import org.jsc.app.OnStartup;
import org.jsc.app.Service;
import org.jsc.io.Xml;
import org.jsc.vfs.ContentResource;
import org.jsc.vfs.ContentResourceLock;
import org.jsc.vfs.ContentResourceLockProvider;
import org.jsc.vfs.ContentResourceProvider;
import org.jsc.web.AppRequestDispatcher;
import org.jsc.web.AppRequestDispatcher.RequestHandler;
import org.jsc.web.auth.HttpAuthenticator;
import org.jsc.web.servlet.LoggingServletWrappers;

/**
 * Provides basic, fast WebDav services given a {@link ContentResourceProvider}, see: {@link org.jsc.vfs.FilesystemContentResourceProvider}
 * @author kzantow
 */
@Service
public class WebDavHttpServletService implements RequestHandler {
	private static final InputStream EMPTY_STREAM = new InputStream() {
		public int read() throws IOException {
			return -1;
		}
	};
	
	/**
	 * Need to use RFC_1123 with WebDav, per spec:
	 * http://www.webdav.org/specs/rfc4918.html#PROPERTY_getlastmodified
	 */
	private static final DateTimeFormatter DAV_DATE_TIME_FORMAT = DateTimeFormatter.RFC_1123_DATE_TIME
        .withLocale(Locale.US).withZone(ZoneOffset.UTC); // Need this for using Instant
	
	Log log;
	@Inject ContentResourceProvider resources;
	@Inject ContentResourceLockProvider resourcesLock;
	@Inject HttpAuthenticator auth;
	@Inject AppRequestDispatcher dispatcher;
	
	private final String pfx = Util.env("dav.prefix", "/files");
	private String urlPrefix = pfx;
	
	@OnStartup
	public void init() {
		urlPrefix = dispatcher.getContextPath() + pfx;
	}
	
	private String[] getpath(String uri) throws Exception {
		while(uri.startsWith("/")) {
			uri = uri.substring(1);
		}
		while(uri.endsWith("/")) {
			uri = uri.substring(0,uri.length()-1);
		}
		if(uri.contains("..")) {
			throw new IllegalArgumentException("Bad path: " + uri);
		}
		String[] path = uri.split("/");
		for(int i = 0; i < path.length; i++) {
			path[i] = Util.urlDecode(path[i]);
		}
		return path;
	}
	
	private String[] fullpath(ContentResource c) {
		ContentResource p = c;
		int i = 0;
		while(p != null) {
			i++;
			p = p.getParent();
		}
		p = c;
		String[] parts = new String[i];
		for(i--; i >= 0; i--) {
			parts[i] = p.getName();
			p = p.getParent();
		}
		return parts;
	}
	
	@Override
	public String getFilterPath() {
		return pfx;
	}
	
	@Override
	public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
		if(Util.debug) {
			req = new LoggingServletWrappers.HttpRequestWrapper(req, new OutputStreamWriter(System.out));
			res = new LoggingServletWrappers.HttpResponseWrapper(res, req, req.getRequestURI(), new OutputStreamWriter(System.out));
		}
		
		String method = req.getMethod();
		if("OPTIONS".equals(method)) {
			res.setStatus(200);
			res.setHeader("Allow", "HEAD, OPTIONS, GET, PUT, DELETE, COPY, MOVE, MKCOL, PROPFIND, PROPPATCH, LOCK, UNLOCK");
			res.setHeader("DAV", "1, 2, ordered-collections, value");
//			res.setHeader("Allow", "OPTIONS, GET, HEAD, PUT, DELETE, MKCOL, PROPFIND, LOCK, UNLOCK");
//			res.setHeader("DAV", "1, 2");
			return;
		}

		// authentication
		String user = "system";//auth.authenticate(req, res);
		
		Writer w = new OutputStreamWriter(res.getOutputStream(), Util.UTF8C);
		
		try {
			String[] path = getpath(req.getRequestURI());
			
			// OSX puts .DSStore files in every directory
			// and ._ files all over the place. this is normal.
			// potentially disable them from being uploaded.
			
			ContentResource c = resources.get(path);
			
			// I'm a dummy, good quick info:
			// http://blog.coralbits.com/2011/07/webdav-protocol-for-dummies.html
			
			if("GET".equals(method) || "HEAD".equals(method)) {
				if(c == null) {
					res.sendError(HttpServletResponse.SC_NOT_FOUND);
					return;
				}
				
				//res.setHeader("Content-Disposition", "attachment; filename=\"" + c.getName().replace("\"", "&quot;") + "\"");
				//res.setContentType(c.getContentType());
				if(!c.isContainer()) {
					int length = (int)c.getContentLength();
					if(length == 0) {
						res.setStatus(204); // No content
					}
					else {
						res.setContentLength(length);
						res.setDateHeader("Last-Modified", c.getLastModified().toEpochMilli());
						if("GET".equals(method)) {
							try {
								Util.streamFully(c.getInputStream(), res.getOutputStream());
							} catch(Exception e) {
								log.warn("Transfer of: ", path, " failed: ", e);
							}
						}
					}
				}
			}
			else if("MKCOL".equals(method)) {
				resources.mkdir(path);
				res.setStatus(201);
			}
			else if("PUT".equals(method)) {
				resources.put(req.getInputStream(), path);
			}
			else if("DELETE".equals(method)) {
				resources.delete(path);
			}
			else if("MOVE".equals(method)) {
				if(c == null) {
					res.setStatus(404);
					return;
				}
				String dst = req.getHeader("Destination");
				int idx = dst.indexOf(urlPrefix);
				if(idx < 0) {
					throw new IllegalArgumentException("Destination: " + dst);
				}
				dst = dst.substring(idx + urlPrefix.length() + 1);
				dst = Util.urlDecode(dst);
				try {
					resources.rename(c, getpath(dst));
				} catch(Exception e) {
					res.sendError(412); // if header: Overwrite: F
					return;
				}
			}
			else if("LOCK".equals(method)) {
				Xml.parse(req.getReader());

				if(c == null) {
					resources.put(EMPTY_STREAM, path);
					c = resources.get(path);
				}
				
				//Timeout: Infinite, Second-4100000000 
				//String timeout = req.getHeader("Timeout");
				//String depth = req.getHeader("Depth");
				
				ContentResourceLock lock = resourcesLock.lock(c, user, 10000);
				
				res.setStatus(200);
				res.setHeader("Lock-Token", "<urn:uuid:"+lock.getId()+">");
				
				res.setContentType("text/xml");
				res.setCharacterEncoding("utf-8");
				
				w.write("<?xml version=\"1.0\" encoding=\"utf-8\" ?>");
				w.write("<D:prop xmlns:D=\"DAV:\">");
			    w.write("<D:lockdiscovery>");
				w.write("<D:activelock>");
				w.write("<D:locktype><D:write/></D:locktype>");
				w.write("<D:lockscope><D:exclusive/></D:lockscope>");
				w.write("<D:depth>infinity</D:depth>");
				w.write("<D:owner>");
				w.append("<D:href>").append(lock.getLockOwner()).append("</D:href>");
				w.write("</D:owner>");
				w.append("<D:timeout>Second-")
				.append(Long.toString((lock.getLockExpirationTime().toEpochMilli()-System.currentTimeMillis())/1000))
				.append("</D:timeout>");
				w.write("<D:locktoken>");
				w.append("<D:href>urn:uuid:").append(lock.getId()).append("</D:href>");
				w.write("</D:locktoken>");
				w.write("<D:lockroot>");
				w.append("<D:href>/").append(Util.join(fullpath(c),"/")).append("</D:href>");
				w.write("</D:lockroot>");
				w.write("</D:activelock>");
			    w.write("</D:lockdiscovery>");
				  w.write("</D:prop>");
			}
			else if("UNLOCK".equals(method)) {
				Xml.parse(req.getReader());

				
			}
			else if("PROPFIND".equals(method)) {
				if(c == null) {
					res.setStatus(404);
					return;
				}
				// http://stackoverflow.com/questions/10144148/example-of-a-minimal-request-response-cycle-for-webdav
				res.setStatus(207);
				res.setContentType("text/xml");
				res.setCharacterEncoding("utf-8");
				
				if(!c.isContainer()) {
					res.setDateHeader("Last-Modified", c.getLastModified().toEpochMilli());
				}
				
				String depth = req.getHeader("Depth");

				// Windows doesn't like empty xmlns, using D:...
				w.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
				w.append("<D:multistatus xmlns:D=\"DAV:\">");
				
				if("".equals(c.getName())) {
					w.append("<D:responsedescription>");
					w.append("Your Awesome Repository");
					w.append("</D:responsedescription>");
				}
				
				proplist(c, "1".equals(depth) ? 1 : 0, urlPrefix, w);
				
				w.append("</D:multistatus>");
			}
		} finally {
			w.close();
		}
	}
	
	/**
	 * Appends the path, separated by / -- does not begin with a slash
	 * @param r
	 * @param w
	 * @throws IOException
	 */
	static void appendPath(ContentResource r, Writer w) throws IOException {
		ContentResource parent = r.getParent();
		if(parent != null) {
			appendPath(parent, w);
			w.append('/');
		}
		streamEncoded(r.getName(), w);
	}
	
	/**
	 * Streams an XML-safe encoded stream
	 */
	public static void streamEncoded(CharSequence chars, Writer w) throws IOException {
		// URL encoding just doesn't work quite right for windows, for some reason...
		int sz = chars.length();
		for(int i = 0; i < sz; i++) {
			char c = chars.charAt(i);
			switch(c) {
			case ' ':
				w.append("%20");
				break;
			case '<':
				w.append("%3C");
				break;
			case '>':
				w.append("%3E");
				break;
			case '&':
				w.append("%26");
				break;
			default:
				w.append(chars.charAt(i));
			}
		}
	}
	
	void proplist(ContentResource r, int depth, String urlPrefix, Writer w) throws IOException {
		if(Util.debug) {
			w.append("\n");
		}
		
		w.append("<D:response>");
		w.append("<D:href>").append(urlPrefix);
		appendPath(r, w);
		w.append("</D:href>");
		
		w.append("<D:propstat>");
		w.append("<D:prop>");
		
		w.append("<D:creationdate>");
		DAV_DATE_TIME_FORMAT.formatTo(r.getCreationTime(), w);
		w.append("</D:creationdate>");
		
		w.append("<D:displayname>");
		if("".equals(r.getName())) {
			w.append("Your Awesome Repository");
		} else {
			streamEncoded(r.getName(), w);
		}
		w.append("</D:displayname>");
		
		if("".equals(r.getName())) {
			w.append("<D:responsedescription>");
			w.append("Your Awesome Repository");
			w.append("</D:responsedescription>");
		}
		
		if(r.isContainer()) {
			w.append("<D:getcontenttype>httpd/unix-directory</D:getcontenttype>");
			
			w.append("<D:resourcetype><D:collection/></D:resourcetype>");
			
		} else {
			w.append("<D:getcontentlength>");
			w.append(Long.toString(r.getContentLength()));
			w.append("</D:getcontentlength>");
			
			w.append("<D:getcontenttype>");
			w.append(r.getContentType());
			w.append("</D:getcontenttype>");
			
			w.append("<D:resourcetype/>");
		}
		
		w.append("<D:getlastmodified>");
		DAV_DATE_TIME_FORMAT.formatTo(r.getLastModified(), w);
		w.append("</D:getlastmodified>");
		
		w.append("<D:getetag>");
		w.append(Long.toHexString(r.getLastModified().getEpochSecond()));
		w.append("</D:getetag>");
		
        w.append(
    		"<D:supportedlock>"+
				"<D:lockentry><D:lockscope><D:exclusive/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>"+
		        //"<D:lockentry><D:lockscope><D:shared/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>"+
	        "</D:supportedlock>");
		
		w.append("</D:prop>");
		w.append("<D:status>HTTP/1.1 200 OK</D:status>");
		w.append("</D:propstat>");
		
		w.append("</D:response>");
		
		if(depth > 0 && r.isContainer()) {
			for(ContentResource c : r.getChildResources()) {
				proplist(c, depth-1, urlPrefix, w);
			}
		}
	}
}
