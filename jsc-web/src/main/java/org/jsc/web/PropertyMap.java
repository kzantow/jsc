package org.jsc.web;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.jsc.Util;

/**
 * Gets values based on properties in the provided object
 * @author kzantow
 */
public class PropertyMap extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;
	
	private Object base;
	private Map<String,Method> getters;
	
	public PropertyMap(Object base) {
		this.base = base;
		getters = Util.getters(base.getClass());
	}
	
	public Object get(Object key) {
		Object o = super.get(key);
		if(o == null) {
			String k = key.toString();
			try {
				super.put(k, o = getters.get(k).invoke(base));
			} catch (Exception e) {
				throw Util.asRuntime(e);
			}
		}
		return o;
	}
}
