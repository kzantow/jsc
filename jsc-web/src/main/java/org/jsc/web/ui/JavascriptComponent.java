package org.jsc.web.ui;

import java.io.Writer;
import java.net.URL;
import java.util.Collections;

import javax.script.Bindings;

import org.jsc.Expr;

public final class JavascriptComponent extends BaseComponent implements ReloadableCompoent {
	URL src;
	Component base;
	Expr script;
	
	public JavascriptComponent(URL src, Expr script, Component base) {
		this.script = script;
		this.base = base;
		children = Collections.singletonList(base);
		this.src = src;
	}

	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		Bindings b = (Bindings)script.getValue(ctx);
		base.render(new Context(b, ctx), w);
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		Bindings b = (Bindings)script.getValue(ctx);
		base.postback(new PostbackContext(b, ctx));
	}
	
	@Override
	public URL getSrc() {
		return src;
	}
}
