package org.jsc.web.ui;

import java.io.Writer;
import java.util.Map;

import org.jsc.Expr;

/**
 * Ouptuts an element
 * @author kzantow
 */
public final class ElementOutputComponent extends BaseComponent {
	String name;
	Map<String, Expr> attributes;
	
	public ElementOutputComponent(String name, Map<String, Expr> attributes) {
		this.name = name;
		this.attributes = attributes;
	}

	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		w.append('<').append(name);
		for(String k : attributes.keySet()) {
			Object v = attributes.get(k);
			if(v instanceof Expr) {
				v = ((Expr)v).getValue(ctx);
			}
			w.append(' ').append(k).append("=\"").append(v == null ? "" : v.toString()).append('"');
		}
		w.append('>');
		super.render(ctx, w);
		w.append("</").append(name).append('>');
	}
}