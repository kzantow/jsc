package org.jsc.web.ui;

import org.jsc.StringMatcher;
import org.junit.Assert;
import org.junit.Test;

public class TestStringMatcher {
	@SuppressWarnings("unused")
	@Test
	public void testStringMatcher() {
		StringMatcher<Integer> sb = new StringMatcher<Integer>();
		sb.add("/a/blah?asdf",1);
		Assert.assertTrue(sb.get("/a/blah?asdf") == 1);
		
		sb.add("/a/blah?asdf2",2);
		sb.add("/b",3);
		
		sb.add("omg",17);
		sb.add("llc",18);
		sb.add("sqq",19);
		
		Assert.assertTrue(sb.get("/a/blah?asdf&foo") == 1);
		Assert.assertTrue(sb.get("/a/blah?asdf2&foo") == 2);
		Assert.assertTrue(sb.get("/a/blah?asd&foo") == null);
		Assert.assertTrue(sb.get("/a/blah?asd&foo") == null);
		
		Assert.assertTrue(sb.get("sqq") == 19);
		Assert.assertTrue(sb.get("llc") == 18);
		
		long start = System.currentTimeMillis();
		double total = 0;
		int iterations = 10000;
		String[] searches = { "/ba/blah?as", "/a/blahz?asdf", "/a/blah?asdf2", "/fb/blah?adf3" };
		for(int i = 0; i < iterations; i++) {
			int val = skip(sb.get(searches[i%searches.length]));
			total += val;
		}
		Assert.assertTrue(true);
	}
	
	public static int skip(Object o) {
		if(o == null) {
			return 0;
		} else {
			return 1;
		}
	}
}
