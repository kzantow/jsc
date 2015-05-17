package org.jsc.io;

import java.io.IOException;
import java.io.StringReader;

import org.jsc.Util;
import org.junit.Assert;
import org.junit.Test;

public class TestXml {
	@Test
	public void testBasicParsing() {
		Xml xml = Xml.parse(new StringReader("<doc><node asdf='foo'>text</node><node>text2</node></doc>"));
		Assert.assertTrue(xml.find("node") != null);
	}
	@Test
	public void testBasicParsingNS() {
		Xml xml = Xml.parse(new StringReader("<doc xmlns='doc' xmlns:x='asdf'><node/><x:node asdf='foo'>text</x:node><node>text2</node></doc>"));
		Assert.assertTrue(xml.find("node") != null);
	}
	@Test(expected=RuntimeException.class)
	public void testNoNamespace() {
		Xml.parse(new StringReader("<doc xmlns='doc'><x:node asdf='foo'>text</x:node><node>text2</node></doc>"));
	}
	@Test(expected=RuntimeException.class)
	public void testNoEnd() {
		Xml.parse(new StringReader("<doc xmlns='doc'><x:node asdf='foo'>text</x:node><node>text2</node>"));
	}
	@Test
	public void testSpace() {
		Xml.parse(new StringReader("\n<D:doc xmlns:D='DAV:'><D:node D:asdf='foo'>text</D:node><D:node>text2</D:node></D:doc>\n"));
	}
	@Test
	public void testDav() {
		Xml xml = Xml.parse(" <?xml version=\"1.0\" encoding=\"utf-8\"?>\n"+
							"<D:propfind xmlns:D=\"DAV:\">\n"+
							"<D:prop>\n"+
							"<D:getlastmodified/>\n"+
							"<D:getcontentlength/>\n"+
							"<D:creationdate/>\n"+
							"<D:resourcetype/>\n"+
							"</D:prop>\n"+
							"</D:propfind>\n"+
							"\n"+
							"\n");
		Assert.assertTrue(xml.find("prop").find("getlastmodified") != null);
	}
	@Test
	public void testOut() throws IOException {
		Xml xml = new Xml("DAV", "blah", null)
			.el("musician", x -> {
				x.el("name").text("jeff");
				x.el("touring");
			});
		
		Assert.assertEquals(
			"<blah xmlns=\"DAV\"><musician><name>jeff</name><touring/></musician></blah>",
			xml.toString()
		);
		
		Assert.assertEquals(
			"<D:blah xmlns:D=\"DAV\"><D:musician><D:name>jeff</D:name><D:touring/></D:musician></D:blah>",
			xml.toString(Util.map("DAV", "D"))
		);
	}
	@Test
	public void testOut2() throws IOException {
		String ns2 = "http://foo";
		Xml xml = new Xml("DAV", "blah", null)
			.el("musician", x -> {
				x.el("name").text("jeff");
				x.el("touring");
			}).el("musician", x -> {
				x.el("name").text("james");
				x.el("touring").attr("active","no");
				x.el(ns2, "bandsite").attr(ns2,"url","www.awesomeband.com");
			});
		
		Assert.assertEquals(
			"<blah xmlns=\"DAV\">"
			+ "<musician><name>jeff</name><touring/></musician>"
			+ "<musician><name>james</name><touring active=\"no\"/><ns1:bandsite xmlns:ns1=\"http://foo\" ns1:url=\"www.awesomeband.com\"/></musician>"
			+ "</blah>",
			xml.toString()
		);
	}
}
