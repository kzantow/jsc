package org.jsc.web.ui;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;

import org.jsc.Util;
import org.junit.Test;

public class UtilTest {
	@Test
	public void testStringFunctions() {
		assert "thisSucks".equals(Util.toCamelCase("this_sucks"));
		assert "ThisSucks".equals(Util.toCamelCase("_this_sucks"));
		assert "thisSucks".equals(Util.toCamelCase("this_sucks_"));
		
		assert "this_sucks".equals(Util.toUnderscore("thisSucks"));
		assert "_this_sucks".equals(Util.toUnderscore("ThisSucks"));
		assert "this_suck_s".equals(Util.toUnderscore("thisSuckS"));
	}
	
	public static void main(String... args) {
		ArrayList<String> strings = new ArrayList<String>();
		for(int i = 0; i < 1000000; i++) {
			strings.add(""+(Math.random()*100000000d));
		}
		
		long start = System.currentTimeMillis();
		for(String s : strings) {
			Util.sha1(s);
		}
		
		System.out.println("1: " + (System.currentTimeMillis() - start));
		start = System.currentTimeMillis();
		
		for(String s : strings) {
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				Base64.getEncoder().encode(md.digest(s.getBytes(Util.UTF8C)));
			} catch(Exception e) {
				throw new RuntimeException(e);
			}
		}
		
		System.out.println("1: " + (System.currentTimeMillis() - start));
	}
}
