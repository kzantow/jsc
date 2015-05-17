package org.jsc.web;

import java.io.OutputStream;

/**
 * A stream that will set name and content type
 * @author kzantow
 */
public interface ContentStream {
	/**
	 * Call this before getStream() to set the name
	 * @param name
	 */
	void setName(String name);
	
	/**
	 * Call this before getStream() to set the contentType
	 * @param name
	 */
	void setContentType(String contentType);
	
	/**
	 * If content length is known, send it
	 * @param length
	 */
	void setContentLength(long length);
	
	/**
	 * Call this to get a stream to write data
	 * @param name
	 */
	OutputStream getOutputStream();
}
