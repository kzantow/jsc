package org.jsc.web.ui;

import java.io.Writer;
import java.util.Collections;

import org.jsc.Expr;
import org.jsc.SingleValueMap;

public final class LoopComponent extends BaseComponent {
	int idx = 0;
	Expr ve;
	String var;
	
	public LoopComponent(String var, Expr ve) {
		this.var = var;
		this.ve = ve;
	}
	
	@SuppressWarnings("restriction")
	Iterable<?> getIterable(Object o) {
		if(o == null) {
			return Collections.EMPTY_LIST;
		}
		if(o instanceof Iterable) {
			return (Iterable<?>)o;
		}
		if(o instanceof jdk.nashorn.api.scripting.ScriptObjectMirror) {
			jdk.nashorn.api.scripting.ScriptObjectMirror m = (jdk.nashorn.api.scripting.ScriptObjectMirror)o;
			if(m.isArray()) {
				return m.to(java.util.List.class);
			}
		}
		throw new IllegalArgumentException("Not an Iterable: " + o); 
	}

	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		Component child = getChildren().get(0);
		Object o = ve.getValue(ctx);
		Iterable<?> i = getIterable(o);
		SingleValueMap<String,Object> vars = new SingleValueMap<>();
		vars.put(var, null);
		Context c = new Context(vars, ctx) {
			@Override
			public String getId(Component c, String id) {
				return super.getId(LoopComponent.this, var)
					+ ':' + idx + ':' + super.getId(c, id);
			}
			@Override
			public Object put(String key, Object value) {
				return ctx.put(key, value); // bypass attempting to set the singlevaluemap
			}
		};
		idx = 0;
		for(Object v : i) {
			vars.setValue(v);
			child.render(c, w);
			idx++;
		}
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		Component child = getChildren().get(0);
		Object o = ve.getValue(ctx);
		Iterable<?> i = getIterable(o);
		SingleValueMap<String,Object> vars = new SingleValueMap<>();
		vars.put(var, null);
		PostbackContext c = new PostbackContext(vars, ctx) {
			@Override
			public String getId(Component c, String id) {
				return super.getId(LoopComponent.this, var)
					+ ':' + idx + ':' + super.getId(c, id);
			}
		};
		idx = 0;
		for(Object v : i) {
			vars.setValue(v);
			child.postback(c);
			idx++;
		}
	}
}