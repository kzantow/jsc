package org.jsc.web;

import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jsc.ExpressionService;
import org.jsc.InitializingMap;
import org.jsc.Log;
import org.jsc.Util;
import org.jsc.ExpressionService.BindingResolver;
import org.jsc.app.App;
import org.jsc.app.Service;
import org.jsc.app.OnStartup;
import org.jsc.web.AppRequestDispatcher.RequestHandler;

/**
 * Filter for static resources, allows for far future expires headers
 * and automatic hashing of resources like CSS/JS
 */
@Singleton
@Service
public class StaticResourceHandler implements RequestHandler {
	Log log;
	
	@Inject
	private ExpressionService expr;
	
	@Inject
	private org.jsc.web.MimeType mime;
	
	@Inject
	private AppRequestDispatcher dispatcher;
	
	public static final boolean checkForModifiedResources = Util.env("checkForModifiedResources", App.development);
	public static final long resourceModificationCheckInterval = Util.env("resourceModificationCheckMinutes", App.development ? 0 : 5) * 60l * 1000l;
	public static final long expiresMillisFromNow = Util.env("expiresHeaderMinutesFromNow", App.development ? 0 : (365*(24*60))) * 60l * 1000l; // default to 1 year
	public static final long futureExpiresTime = System.currentTimeMillis() + expiresMillisFromNow;
	public static final String paramName = "v";
	
	private String filterPath = Util.env("resource.path", "/r");
	private String contextPath = filterPath;
	
	@OnStartup
	public void init() {
		contextPath = dispatcher.getContextPath() + getFilterPath();
	}
	
	@Override
	public String getFilterPath() {
		return filterPath;
	}
	
	/**
	 * Holds reference to a single resource
	 */
	public abstract static class Resource {
		public String resourcePath;
		public String externalPath;
		public String contentType;
		public byte[] content;
		public long lastModified;
		public long nextCheck;
		public long expiresTime;
		public abstract long getLastModified() throws Exception;
	}
	
	/**
	 * Handles binary resources - e.g. images
	 */
	public class BinaryResource extends Resource {
		protected final URL u;
		public BinaryResource(String path) throws Exception {
			while(path.startsWith("/")) {
				path = path.substring(1);
			}
			u = Thread.currentThread().getContextClassLoader().getResource(path);
			if(u == null) {
				throw new NoSuchFileException(path);
			}
			this.resourcePath = path;
			this.content = getContent();
			this.externalPath = contextPath + '/' + path + "?" + paramName + (App.development ? "" : ("=" + Util.hex(Util.sha1(content)).substring(0,10).toLowerCase()));
			this.contentType = mime.getType(path);
			this.lastModified = getLastModified();
			this.nextCheck = System.currentTimeMillis() + resourceModificationCheckInterval;
			this.expiresTime = futureExpiresTime;
		}
		byte[] getContent() throws Exception {
			return Util.readFully(u);
		}
		@Override
		public long getLastModified() throws Exception {
			URLConnection c = u.openConnection();
			long lastMod = c.getLastModified();
			lastMod = lastMod - (lastMod%1000);
			return lastMod;
		}
	}
	
	static Pattern urlPattern = Pattern.compile("url\\(['\"]?([^\\)'\"]+)");
	
	/**
	 * Handles text-base resources, e.g. css
	 */
	public class TextResource extends BinaryResource {
		long lastModified = -1;
		List<Resource> subResources;
		public TextResource(String path) throws Exception {
			super(path);
		}
		@Override
		byte[] getContent() throws Exception {
			int idx = this.resourcePath.lastIndexOf('/');
			String thisPath = idx >= 0 ? this.resourcePath.substring(0, idx) : "";
			byte[] b = super.getContent();
			String content = new String(b, "utf-8");
			
			// Process EL expressions e.g. path
			if(content.indexOf("${") >= 0) {
				content = content.replace("\\", "\\\\"); // leave single escapes as-is
				content = expr.coerce(expr.parseExpr(content).getValue(new BindingResolver(Util.map("path", externalPath))), String.class);
				content = content.replace("\\\\", "\\"); // tomcat 7 not escaping things, this only applies to a couple files...
			}
			
			Matcher m = urlPattern.matcher(content);
			StringBuilder out = new StringBuilder();
			int start = 0;
			while(m.find()) {
				String url = m.group(1);
				if(handlesUrl(url)) {
					while(url.startsWith("./")) {
						url = url.substring(2);
					}
					if(url.startsWith("../")) {
						String prefix = thisPath;
						while(url.startsWith("../")) {
							url = url.substring(3);
							prefix = prefix.substring(0, prefix.lastIndexOf('/'));
						}
						url = prefix + '/' + url;
					}
					if(!url.startsWith("/")) {
						url = thisPath + '/' + url;
					}
				
					out.append(content.substring(start, m.start(1)));
					Resource r = getResource(url);
					synchronized(this) {
						if(subResources == null) {
							subResources = new ArrayList<Resource>();
							subResources.add(r);
						}
					}
					out.append(r.externalPath);
					start = m.end(1);
				}
			}
			out.append(content.substring(start));
			return out.toString().getBytes("utf-8");
		}
		
		@Override
		public long getLastModified() throws Exception {
			synchronized(this) {
				if(subResources == null) {
					try {
						getContent();
					} catch(RuntimeException e) {
						throw e;
					} catch(Exception e) {
						throw new RuntimeException(e);
					}
					if(subResources == null) {
						subResources = Collections.emptyList();
					}
				}
			}
			long lastMod = super.getLastModified();
			for(Resource r : subResources) {
				long rmod = r.getLastModified();
				if(rmod > lastMod) {
					lastMod = rmod;
				}
			}
			return lastMod;
		}
	}
	
	/**
	 * Returns an appropriate resource object for the given path, this may be binary for images or
	 * text for CSS/JS files, which may minimize or otherwise modify the files.
	 * 
	 * @param path
	 * @return
	 * @throws Exception
	 */
	public Resource getResourceFromPath(final String path) throws Exception {
		final String ctype = mime.getType(path);
		if(ctype != null) {
			if(ctype.startsWith("text/")) {
				return new TextResource(path);
			}
			return new BinaryResource(path);
		}
		return null;
	}
	
	/**
	 * Method indicates that resources filter will handle the URL
	 * @param url
	 * @return
	 */
	public boolean handlesUrl(String url) {
		return url != null
			&& !url.startsWith("data:")
			&& !url.startsWith("http")
			&& !url.startsWith("//")
			&& mime.getType(url) != null;
	}
	
	/**
	 * Cached images...
	 */
	private InitializingMap<String,Resource> resources = new InitializingMap<String,Resource>(
	(path) -> {
		try {
			Resource r = getResourceFromPath(path);
			if(r == null) {
				throw new NullPointerException();
			}
			return r;
		} catch(Throwable t) {
			throw Util.asRuntime(t);
		}
	});	
	
	/**
	 * Call this to get the URL to send to the browser, based on the local resource name
	 * @param resource
	 * @return
	 */
	public String getExternalURL(String resource) {
		if(!handlesUrl(resource)) {
			return resource;
		}
		return getResource(resource).externalPath;
	}
	
	@Override
	public void handle(HttpServletRequest req, HttpServletResponse res) throws Throwable {
		// MAKE SURE this is called before a parameter request is made
		req.setCharacterEncoding("utf-8");
		
		String path = req.getRequestURI(); // path has already been normalized for this handler
		if(handlesUrl(path)
		&& req.getParameter(paramName) != null) { // only handle resources w/ mod indicator
			Resource r = getResource(path);
			if(r != null && r.content != null) {
				long modSince = req.getDateHeader("If-Modified-Since");
				if(modSince > 0) {
					if(r.lastModified <= modSince) {
						res.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
						return;
					}
				}
				
				res.setContentType(r.contentType);
				res.addDateHeader("Expires", r.expiresTime);
				res.addHeader("Cache-Control", "public");
				res.setDateHeader("Last-Modified", r.lastModified);
				OutputStream out = res.getOutputStream();
				try {
					out.write(r.content);
				} finally {
					out.close();
				}
				return;
			}
		}
	}

	/**
	 * Gets the resource...
	 * @param path
	 * @return
	 */
	public Resource getResource(String path) {
		if(path == null) {
			return null;
		}
		Resource r = resources.get(path);
		if(checkForModifiedResources) {
			long currentTime = System.currentTimeMillis();
			if(r.nextCheck < currentTime) {
				log.debug("Checking modification of resource: " + r.resourcePath);
				
				long rmod;
				try {
					rmod = r.getLastModified();
				} catch(RuntimeException e) {
					throw e;
				} catch(Exception e) {
					throw new RuntimeException(e);
				}
				if(r.lastModified < rmod) { // re-fetch
					log.info("Resource changed, reloading: " + r.resourcePath + " with rmod: " + rmod + " and r.lastModified: " + r.lastModified);
					resources.remove(path);
					r = resources.get(path);
				} else {
					r.nextCheck = currentTime + resourceModificationCheckInterval;
				}
			}
		}
		return r;
	}
}
