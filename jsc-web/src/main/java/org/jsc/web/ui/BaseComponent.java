package org.jsc.web.ui;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.jsc.Util;

/**
 * Basic component with child list
 * @author kzantow
 */
public class BaseComponent implements Component {
	protected List<Component> children;
	
	public BaseComponent() {
	}
	
	public BaseComponent(List<Component> children) {
		this.children = children;
	}
	
	/*
	 * (non-Javadoc)
	 * @see util.web.ui.Component#getChildren()
	 */
	@Override
	public List<Component> getChildren() {
		if(children == null) {
			children = new ArrayList<Component>();
		}
		return children;
	}
	
	/*
	 * (non-Javadoc)
	 * @see util.web.ui.Component#hasChildren()
	 */
	@Override
	public boolean hasChildren() {
		return children != null && !children.isEmpty();
	}
	
	/*
	 * (non-Javadoc)
	 * @see util.web.ui.Component#render(util.web.ui.Context, java.io.Writer)
	 */
	@Override
	public void render(Context ctx, Writer w) throws Throwable {
		if(children != null) {
			for(Component c : children) {
				c.render(ctx, w);
			}
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see util.web.ui.Component#postback(util.web.ui.PostbackContext)
	 */
	@Override
	public void postback(PostbackContext ctx) throws Throwable {
		if(children != null) {
			for(Component c : children) {
				c.postback(ctx);
			}
		}
	}
	
	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return Util.stringify(this);
	}
}
