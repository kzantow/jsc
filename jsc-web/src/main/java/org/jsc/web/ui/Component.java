package org.jsc.web.ui;

import java.io.Writer;
import java.util.List;

/**
 * Base web component
 * @author kzantow
 */
public interface Component {
	/**
	 * Components form a hierarchy like most any component frameworks; this will always return a collection.
	 * Call {@link #hasChildren()} to determine if there are any children and avoid possible object creation.
	 * @return
	 */
	List<Component> getChildren();
	
	/**
	 * Call this to determine if there are children,
	 * @return
	 */
	boolean hasChildren();
	
	/**
	 * Render this component tree
	 * @param ctx
	 * @param w
	 * @throws Throwable
	 */
	void render(Context ctx, Writer w) throws Throwable;
	
	/**
	 * Handle postbacks to this component tree
	 * @param ctx
	 * @throws Throwable
	 */
	void postback(PostbackContext ctx) throws Throwable;
}
