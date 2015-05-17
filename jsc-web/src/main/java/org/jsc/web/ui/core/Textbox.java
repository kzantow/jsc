package org.jsc.web.ui.core;

import java.io.Writer;

import org.jsc.web.ui.BaseComponent;
import org.jsc.web.ui.Context;
import org.jsc.web.ui.PostbackContext;

public class Textbox extends BaseComponent {
	String id;
	Object value;
	boolean readonly;
	boolean disabled;
	String s;
	
	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		String id = ctx.getId(this, this.id);
		w.append("<input type=\"text\" id=\"")
			.append(id)
			.append("\" name=\"")
			.append(id)
			.append("\"");
		
		if(value != null) {
			w.append(" value=\"");
			w.append((String)value);
			
			//.append(s.getClass().getName());
			w.append("\"");
		}
		
		w.append("/>");
	}
	
	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		String val = ctx.getRequest().getParameter(ctx.getId(this, id));
		value = val;
	}
}
