package org.jsc.web.ui;

import java.io.Writer;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import org.jsc.Expr;

/**
 * This is an included template, manages expression mappings
 * @author kzantow
 */
public class TemplateComponent extends BaseComponent implements ReloadableCompoent {
	URL src;
	Map<String,Expr> attributes;
	Component base;
	
	public TemplateComponent(URL src, Map<String,Expr> attributes, Component base) {
		this.src = src;
		this.attributes = attributes;
		this.base = base;
		children = Collections.singletonList(base);
	}
	
	@Override
	public URL getSrc() {
		return src;
	}
	
	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		ExpressionResolvingMap vars = new ExpressionResolvingMap(attributes, ctx);
		base.render(new Context(vars, ctx), w);
	}

	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		ExpressionResolvingMap vars = new ExpressionResolvingMap(attributes, ctx);
		base.postback(new PostbackContext(vars, ctx));
	}
}