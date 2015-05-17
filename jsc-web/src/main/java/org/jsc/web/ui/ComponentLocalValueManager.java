package org.jsc.web.ui;

import java.io.Writer;
import java.util.List;

import org.jsc.Proc2;

public final class ComponentLocalValueManager extends BaseComponent {
	Component base;
	
	List<Proc2<Object, Context>> set;
	List<Proc2<Object, Context>> update;
	
	public ComponentLocalValueManager(Component base, List<Proc2<Object, Context>> set, List<Proc2<Object, Context>> update) {
		this.base = base;
		this.set = set;
		this.update = update;
	}

	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		for(Proc2<Object, Context> p : set) {
			p.exec(base, ctx);
		}
		base.render(ctx, w);
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		for(Proc2<Object, Context> p : set) {
			p.exec(base, ctx);
		}
		base.postback(ctx);
		for(Proc2<Object, Context> p : update) {
			p.exec(base, ctx);
		}
	}
}