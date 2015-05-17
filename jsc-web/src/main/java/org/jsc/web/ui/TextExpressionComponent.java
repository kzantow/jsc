package org.jsc.web.ui;

import java.io.Writer;

import org.jsc.Expr;

public final class TextExpressionComponent extends BaseComponent {
	Expr ve;
	
	public TextExpressionComponent(Expr ve) {
		this.ve = ve;
	}

	public void render(Context ctx, Writer w) throws Throwable {
		Object val = ve.getValue(ctx);
		if(val != null) {
			w.write(val.toString());
		}
	}
}