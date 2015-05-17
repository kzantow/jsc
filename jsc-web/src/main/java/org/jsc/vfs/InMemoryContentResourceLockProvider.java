package org.jsc.vfs;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;

@Singleton
public class InMemoryContentResourceLockProvider implements ContentResourceLockProvider {
	Map<ContentResource, ContentResourceLock> locks = new ConcurrentHashMap<>();
	
	@Override
	public ContentResourceLock lock(ContentResource r, String owner, long millis) {
		ContentResourceLock l = getLock(r);
		if(l != null) {
			if(owner.equals(l.getLockOwner())) {
				throw new SecurityException("Not lock owner.");
			}
		}
		Instant lockTime = Instant.now();
		String id = UUID.randomUUID().toString();
		locks.put(r, l = new ContentResourceLock() {
			public Instant getLockTime() {
				return lockTime;
			}
			public String getLockOwner() {
				return owner;
			}
			public Instant getLockExpirationTime() {
				return Instant.ofEpochMilli(System.currentTimeMillis() - millis);
			}
			public String getId() {
				return id;
			}
		});
		return l;
	}
	
	@Override
	public void unlock(ContentResource r, String owner) {
		ContentResourceLock l = getLock(r);
		if(l != null) {
			if(owner.equals(l.getLockOwner())) {
				throw new SecurityException("Not lock owner.");
			}
		}
		locks.remove(r);
	}
	
	@Override
	public ContentResourceLock getLock(ContentResource r) {
		return locks.get(r);
	}
}
