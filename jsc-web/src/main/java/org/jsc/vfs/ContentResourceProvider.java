package org.jsc.vfs;

import java.io.InputStream;

import com.google.inject.ImplementedBy;

/**
 * Simple interface to provide blob like data to/from webdav or other services (e.g. files)
 */
@ImplementedBy(FilesystemContentResourceProvider.class)
public interface ContentResourceProvider {
	/**
	 * Get a resource at the given path, returns null if not found
	 * @param path
	 * @return
	 */
	public ContentResource get(String ... path);
	
	/**
	 * Make a directory
	 * @param path
	 */
	public void mkdir(String ... path);
	
	/**
	 * Rename a resource to another path
	 * @param c
	 * @param path
	 */
	public void rename(ContentResource c, String ... path);
	
	/**
	 * Create or replace a resource at the given path
	 * @param in
	 * @param path
	 */
	public void put(InputStream in, String ... path);
	
	/**
	 * Create or replace a resource at the given path, all content
	 * written to the output stream before it is closed 
	 * will become the contents.
	 * 
	 * @param path
	 */
	//public OutputStream stream(String ... path);
	
	/**
	 * Delete a resource at the given path
	 * @param path
	 */
	public void delete(String ... path);
}
