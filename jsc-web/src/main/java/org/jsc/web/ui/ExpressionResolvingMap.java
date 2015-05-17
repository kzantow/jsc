package org.jsc.web.ui;

import java.util.HashMap;
import java.util.Map;

import org.jsc.Expr;

public class ExpressionResolvingMap extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;

	Map<String,Expr> attributes;
	Context ctx;
	
	public ExpressionResolvingMap(Map<String, Expr> attributes, Context ctx) {
		this.attributes = attributes;
		this.ctx = ctx;
	}

	public Object get(Object key) {
		Expr ve = attributes.get(key);
		if(ve == null) {
			return null;
		}
		return ve.getValue(ctx);
	}
	
	@Override
	public Object put(String key, Object value) {
		Expr ve = attributes.get(key);
		if(ve != null) {
			ve.setValue(ctx, value);
		}
		return null;
	}
	
	@Override
	public boolean containsKey(Object key) {
		return attributes.containsKey(key);
	}
	
	@Override
	public String toString() {
		return attributes.toString();
	}
}