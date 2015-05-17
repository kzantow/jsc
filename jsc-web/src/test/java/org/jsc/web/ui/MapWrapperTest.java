package org.jsc.web.ui;

import java.util.HashMap;

import org.jsc.MapWrapper;
import org.junit.Assert;
import org.junit.Test;

public class MapWrapperTest {
	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Test
	public void mapWrap() {
		HashMap m = new HashMap();
		m.put("mykey", "two");
		MapWrapper mw = new MapWrapper(m);
		Assert.assertEquals("two", mw.get("mykey"));
	}
}
