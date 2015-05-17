package org.jsc.web.ui.core;

import java.io.Writer;

import org.jsc.Proc;
import org.jsc.web.ui.BaseComponent;
import org.jsc.web.ui.Context;
import org.jsc.web.ui.PostbackContext;

public class Button extends BaseComponent {
	Proc action;
	
	String id;
	Object value;
	String label;
	
	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		w.append("<input type=\"submit\" id=\"")
			.append(ctx.getId(this, id))
			.append("\" name=\"")
			.append(ctx.getId(this, id))
			.append("\" value=\"")
			.append(label)
			.append("\"");
		
		
		w.append("/>");
	}
	
	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		if(null != ctx.getRequest().getParameter(ctx.getId(this,id))) {
			action.exec();
		}
	}
}
