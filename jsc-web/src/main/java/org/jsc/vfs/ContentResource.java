package org.jsc.vfs;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;

/**
 * Abstraction to the File, allowing implementations to remain independent of
 * filesystem/other content storage type
 */
public interface ContentResource {
	/**
	 * Gets the name of the resource
	 * @return
	 */
	public String getName();
	
	/**
	 * Get the parent resource; returns null if this is the top level
	 * @return
	 */
	public ContentResource getParent();

	/**
	 * Indicates this is a container of other resources, e.g. a directory
	 * @return
	 */
	public boolean isContainer();
	
	/**
	 * Get the child resources, only applicable if this is a container
	 * @return
	 */
	public List<ContentResource> getChildResources();
	
	/**
	 * Get last modified time
	 * @return
	 */
	public Instant getLastModified();
	
	/**
	 * Get creation time
	 * @return
	 */
	public Instant getCreationTime();
	
	/**
	 * Get a stream for the contents of the resource, only applicable if not a container
	 * @return
	 * @throws IOException
	 */
	public InputStream getInputStream() throws IOException;
	
	/**
	 * Get the content type of the resource, not applicable if a container
	 * @return
	 */
	public String getContentType();
	
	/**
	 * Get the content length of the resource; not applicable if a container
	 * @return
	 */
	public long getContentLength();
}
