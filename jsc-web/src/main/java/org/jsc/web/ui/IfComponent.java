package org.jsc.web.ui;

import java.io.Writer;

import org.jsc.Expr;

/**
 * Conditionally renders/etc.
 * @author kzantow
 *
 */
public final class IfComponent extends BaseComponent {
	Expr test;
	
	public IfComponent(Expr test) {
		this.test = test;
	}

	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		Component child = getChildren().get(0);
		Object v = test.getValue(ctx);
		if(Boolean.TRUE.equals(v)) {
			child.render(ctx, w);
		}
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		Component child = getChildren().get(0);
		Object v = test.getValue(ctx);
		if(Boolean.TRUE.equals(v)) {
			child.postback(ctx);
		}
	}
}