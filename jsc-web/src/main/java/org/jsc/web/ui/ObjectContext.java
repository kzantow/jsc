package org.jsc.web.ui;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.jsc.Util;

public class ObjectContext extends HashMap<String,Object> {
	private static final long serialVersionUID = 1L;
	
	final Object o;
	final Map<String,Method> getters;
	final Map<String,Method> setters;
	final Map<String,Method> methods;
	
	public ObjectContext(Object o) {
		this.o = o;
		getters = Util.getters(o.getClass());
		setters = Util.setters(o.getClass());
		methods = Util.methods(o.getClass());
	}
	
	@Override
	public Object get(Object key) {
		Method m = getters.get(key);
		if(m != null) {
			try {
				return m.invoke(o);
			} catch(InvocationTargetException e) {
				throw Util.asRuntime(e.getCause());
			} catch (Exception e) {
				throw Util.asRuntime(e);
			}
		}
		final Method mt = methods.get(key);
		if(mt != null) {
			@SuppressWarnings("restriction")
			jdk.nashorn.api.scripting.JSObject fn = new jdk.nashorn.api.scripting.AbstractJSObject() {
				@Override
				public boolean isFunction() {
					return true;
				}
				@Override
				public Object call(Object thiz, Object... args) {
					try {
						return mt.invoke(o, args);
					} catch (Exception e) {
						throw Util.asRuntime(e);
					}
				}
			};
			return fn;
		}
		return null;
	}
	
	@Override
	public Object put(String key, Object value) {
		Method m = setters.get(key);
		if(m != null) {
			try {
				m.invoke(o, value);
			} catch(InvocationTargetException e) {
				throw Util.asRuntime(e.getCause());
			} catch (Exception e) {
				throw Util.asRuntime(e);
			}
		}
		return null;
	}
	
	@Override
	public boolean containsKey(Object key) {
		return getters.containsKey(key) || methods.containsKey(key);
	}
}
