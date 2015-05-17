package org.jsc.io;

import java.io.Reader;
import java.io.Writer;

/**
 * Simple interface to read/write JSON
 */
public interface Json {
	/**
	 * Convert the given object to a JSON string
	 * @param o
	 * @return
	 */
	String toJson(Object o);
	
	/**
	 * Write the give object as a JSON string to the provided writer
	 * @param o
	 * @param w
	 */
	void toJson(Object o, Writer w);
	
	/**
	 * Read an object from the given JSON string. If known, provide the expected type
	 * for the object or collection of objects.
	 * @param type
	 * @param json
	 * @return
	 */
	Object fromJson(Class<?> type, String json);
	
	/**
	 * Read an object as JSON data from the given reader. If known, provide the expected type
	 * for the object or collection of objects.
	 * @param type
	 * @param r
	 * @return
	 */
	Object fromJson(Class<?> type, Reader r);
}
