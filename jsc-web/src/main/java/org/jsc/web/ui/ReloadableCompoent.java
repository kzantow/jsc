package org.jsc.web.ui;

import java.net.URL;

/**
 * For components that may cause a reload during development/update implement this with a method to
 * obtain the URL
 * @author kzantow
 */
public interface ReloadableCompoent {
	/**
	 * Url to the component, mostly used for lastModified presently
	 * @return
	 */
	public URL getSrc();
}
