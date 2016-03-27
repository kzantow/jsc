package org.jsc.web.ui;

import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;

import org.jsc.Util;
import org.junit.Assert;
import org.junit.Test;

import com.google.common.base.Charsets;

public class UtilTest {
	@Test
	public void testStringFunctions() {
		Assert.assertTrue("thisSucks".equals(Util.toCamelCase("this_sucks")));
		Assert.assertTrue("ThisSucks".equals(Util.toCamelCase("_this_sucks")));
		Assert.assertTrue("thisSucks".equals(Util.toCamelCase("this_sucks_")));
		
		Assert.assertTrue("this_sucks".equals(Util.toUnderscore("thisSucks")));
		Assert.assertTrue("_this_sucks".equals(Util.toUnderscore("ThisSucks")));
		Assert.assertTrue("this_suck_s".equals(Util.toUnderscore("thisSuckS")));
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
	
	@Test
	public void testInpuStreamRange() {
		byte[] bytes = "_his was a fancy array".getBytes(Charsets.US_ASCII);
		
		byte[] r2_5 = Util.readFully(Util.inputStreamRange(new ByteArrayInputStream(bytes), 2, 5));
		
		Assert.assertTrue(r2_5.length == 4);
		Assert.assertTrue(r2_5[0] == 'i');
		Assert.assertTrue(r2_5[3] == 'w');
		
		byte[] r0_7 = Util.readFully(Util.inputStreamRange(new ByteArrayInputStream(bytes), 0, 7));
		
		Assert.assertTrue(r0_7.length == 8);
		Assert.assertTrue(r0_7[0] == '_');
		Assert.assertTrue(r0_7[7] == 's');
		
		byte[] r0_last = Util.readFully(Util.inputStreamRange(new ByteArrayInputStream(bytes), 0, bytes.length-1));
		
		Assert.assertTrue(r0_last.length == bytes.length);
		Assert.assertTrue(r0_last[0] == '_');
		Assert.assertTrue(r0_last[bytes.length-1] == bytes[bytes.length-1]);
		
		byte[] r0_all = Util.readFully(Util.inputStreamRange(new ByteArrayInputStream(bytes), 0, -1));
		
		Assert.assertTrue(r0_all.length == bytes.length);
		Assert.assertTrue(r0_all[0] == '_');
		Assert.assertTrue(r0_all[bytes.length-1] == bytes[bytes.length-1]);
	}
}
