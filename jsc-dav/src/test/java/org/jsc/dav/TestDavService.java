package org.jsc.dav;

import java.util.List;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

public class TestDavService {
	Sardine sardine = SardineFactory.begin("kz", "woot");
	
	//@Test
	public void testDav() throws Exception {
		dumpCol("http://localhost:8080/dav/files", "/");
	}
	
	private void dumpCol(String pfx, String sub) throws Exception {
		List<DavResource> resources = sardine.list(pfx + sub);
		for (DavResource res : resources) {
			System.out.println(res);
			if(res.isDirectory()) {
				dumpCol(pfx, sub + res.getName() + "/");
			}
		}
	}
}