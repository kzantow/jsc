package org.jsc.vfs;

import com.google.inject.ImplementedBy;

/**
 * Interface to manage locks on content resources
 * @author kzantow
 *
 */
@ImplementedBy(InMemoryContentResourceLockProvider.class)
public interface ContentResourceLockProvider {
	/**
	 * Get any current lock information for the content resource
	 * @param r
	 * @return
	 */
	public ContentResourceLock getLock(ContentResource r);
	
	/**
	 * Lock a resource, with requested time
	 * @param r
	 * @return
	 */
	public ContentResourceLock lock(ContentResource r, String owner, long millis);
	
	/**
	 * Unlock the specified resource
	 * @param r
	 */
	public void unlock(ContentResource r, String owner);
}
