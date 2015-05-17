package org.jsc.web;

import javax.inject.Singleton;

/**
 * Best guesses for mime types
 * @author kzantow
 */
@Singleton
public class MimeType {
	/**
	 * Returns the content type based on file extension
	 * @param path
	 * @return
	 */
	public String getType(String path) {
		if(path == null) {
			return null;
		}
		if(path.endsWith(".css")) {
			return "text/css";
		}
		if(path.endsWith(".js")) {
			return "text/javascript"; // most compatible 
			//return "application/javascript"; // correct
		}
		if(path.endsWith(".png")) {
			return "image/png";
		}
		if(path.endsWith(".jpg") || path.endsWith(".jpeg")) {
			return "image/jpeg";
		}
		if(path.endsWith(".gif")) {
			return "image/gif";
		}
		if(path.endsWith(".ico")) {
			return "image/x-icon";
		}
		if(path.endsWith(".otf")) {
			return "application/x-font-otf";
		}
		if(path.endsWith(".eot")) {
			return "application/vnd.ms-fontobject";
		}
		if(path.endsWith(".svg")) {
			return "image/svg+xml";
		}
		if(path.endsWith(".ttf")) {
			return "application/x-font-ttf";
		}
		if(path.endsWith(".woff")) {
			return "application/x-font-woff";
		}
		if(path.endsWith("less")) {
			return "text/less";
		}
		
		return null;
	}
}
