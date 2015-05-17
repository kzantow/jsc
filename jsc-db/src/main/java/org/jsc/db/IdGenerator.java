package org.jsc.db;

import java.util.UUID;

/**
 * Generates unique ids
 * @author kzantow
 */
public class IdGenerator {
	/**
	 * Creates a unique id; currently this is using UUID
	 * @return
	 */
	public static String newId() {
		return UUID.randomUUID().toString();
	}
}
