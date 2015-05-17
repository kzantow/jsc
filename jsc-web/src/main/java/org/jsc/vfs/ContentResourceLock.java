package org.jsc.vfs;

import java.time.Instant;

/**
 * Defines information about a lock owner
 */
public interface ContentResourceLock {
	/**
	 * Get the unique ID for this lock
	 * @return
	 */
	String getId();
	
	/**
	 * Get the lock owner identifier
	 * @return
	 */
	String getLockOwner();
	
	/**
	 * Gets the time the lock was created
	 * @return
	 */
	Instant getLockTime();
	
	/**
	 * The time the lock will expire
	 * @return
	 */
	Instant getLockExpirationTime();
}
