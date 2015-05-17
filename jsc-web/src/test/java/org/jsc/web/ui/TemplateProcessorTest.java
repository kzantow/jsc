package org.jsc.web.ui;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jsc.Util;
import org.jsc.app.App;
import org.junit.Assert;
import org.junit.Test;

public class TemplateProcessorTest {
	public static class Page {
		String name;
		List<Page> pages = new ArrayList<>();
		public Page(String name) {
			this.name = name;
		}
		public String getName() {
			return name;
		}
		public void setName(String name) {
			this.name = name;
		}
		public List<Page> getPages() {
			return pages;
		}
		public void setPages(List<Page> pages) {
			this.pages = pages;
		}
	}
	
	@Test
	public void testDefines() throws Throwable {
		
	}
	@Test
	public void testIf() throws Throwable {
		TemplateProcessor tp = new App().get(TemplateProcessor.class);
		
		Page pg = new Page("parent");
		pg.pages = Arrays.asList(new Page("page 1"), new Page("page 2"));
		StringWriter sw = new StringWriter();
		
		Context ctx = new Context(new ObjectContext(pg), new Context(Util.map("pool", false)));
		
		Component r = null;
		
		r = tp.parseTemplate("htl", Util.toUrl("html", 0,
			"<html><body><div @if=\"${pool}\">${page.name}</div></body></html>".getBytes()));
		
		r.render(ctx, sw);
		
		Assert.assertEquals("<!doctype html><html><head></head><body></body></html>", sw.toString());
	}
	
	@Test
	public void testLoop() throws Throwable {
		TemplateProcessor tp = new App().get(TemplateProcessor.class);
		
		Page pg = new Page("parent");
		pg.pages = Arrays.asList(new Page("page 1"), new Page("page 2"));
		StringWriter sw = new StringWriter();
		
		Context ctx = new Context(new ObjectContext(pg), new Context(Collections.emptyMap()));
		
		Component r = null;
		
		r = tp.parseTemplate("", Util.toUrl("html", 0, 
				"<div @loop:p=${pages}>${p.name}</div>".getBytes()));
		
		r.render(ctx, sw);
		
		Assert.assertEquals("<div>page 1</div><div>page 2</div>", sw.toString());
	}
}
