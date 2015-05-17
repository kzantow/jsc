package org.jsc.web.ui;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.jsc.ExpressionService.BindingResolver;

/**
 * Rendering and execution context
 * @author kzantow
 */
public class Context extends BindingResolver {
	private List<Object> state;
	private Context parent;
	
	int nextId = 0;
	private IdentityHashMap<Component, String> ids;
	
	public Context(Map<String, Object> context) {
		super(context);
	}
	
	public Context(Map<String, Object> context, Context parent) {
		this(context);
		this.parent = parent;
	}
	
	public String idFor(Component c) {
		if(parent != null) {
			return parent.idFor(c);
		}
		if(ids == null) {
			ids = new IdentityHashMap<Component, String>();
		}
		String id = ids.get(c);
		if(id == null) {
			ids.put(c, id = Integer.toString(++nextId));
		}
		return id;
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Map<String,Object> getObjectBindings(Object o) {
		if(o instanceof Map) {
			return (Map)o;
		}
		else {
			return new ObjectContext(o);
		}
	}

	public String getId(Component c, String id) {
		if(id == null) {
			id = idFor(c);
		}
		return id;
	}
	
	public Context getParent() {
		return parent;
	}
	
	@Override
	public Object get(Object key) {
		if(super.containsKey(key)) {
			return super.get(key);
		}
		if(parent != null) {
			return parent.get(key);
		}
		return null;
	}
	
	@Override
	public Object put(String key, Object value) {
		if(super.containsKey(key)) {
			return super.put(key, value);
		}
		if(parent != null && parent.containsKey(key)) {
			return parent.get(key);
		}
		return super.put(key, value);
	}
	
	@Override
	public boolean containsKey(Object key) {
		if(parent != null) {
			return super.containsKey(key) || parent.containsKey(key);
		}
		return super.containsKey(key);
	}
	
	public void pushState(Object o) {
		if(state == null) state = new ArrayList<Object>();
		state.add(o);
	}
	
	public Object popState() {
		if(state == null || state.size() == 0) throw new IllegalStateException("No state to pop.");
		return state.remove(0);
	}
	
	@Override
	public String toString() {
		return "CTX[" + super.toString() + "]";
	}
}
