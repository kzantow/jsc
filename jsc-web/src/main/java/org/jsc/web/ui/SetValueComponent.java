package org.jsc.web.ui;

import java.io.Writer;

import org.jsc.Expr;
import org.jsc.Util;

public final class SetValueComponent extends BaseComponent {
	String var;
	Expr ve;
	public SetValueComponent(String var, Expr ve) {
		this.var = var;
		this.ve = ve;
	}
	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		Component child = getChildren().get(0);
		Object v = ve.getValue(ctx);
		if(var != null) {
			v = Util.map(var, v);
		}
		Context c = new Context(Context.getObjectBindings(v), ctx);
		child.render(c, w);
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		Component child = getChildren().get(0);
		Object v = ve.getValue(ctx);
		if(var != null) {
			v = Util.map(var, v);
		}
		PostbackContext c = new PostbackContext(Context.getObjectBindings(v), ctx);
		child.postback(c);
	}
}