package org.jsc.web.ui;

import java.io.Writer;
import java.util.List;

/**
 * Top-level html document, outputs html 5 doctype
 * @author kzantow
 */
public final class HtmlDocumentComponent extends BaseComponent {
	public HtmlDocumentComponent(List<Component> children) {
		super(children);
	}

	public void render(Context ctx, Writer w) throws Throwable {
		w.write("<!doctype html>");
		super.render(ctx, w);
	}
}
