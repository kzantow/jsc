package org.jsc.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Singleton;

import org.jsc.Util;

@Singleton
public class FilesystemContentResourceProvider implements ContentResourceProvider {
	private static final File root;
	static {
		String dir = Util.env("fsRootDir", "~/file-root-dir");
		dir = dir.replace("~", System.getProperty("user.home"));
		root = new File(dir);
		if(!root.isDirectory()) {
			root.mkdirs();
		}
	}
	
	private ContentResource ROOT = new RootResource();
	
	private File getfile(String[] path) {
		File f = root;
		for(String dir : path) {
			f = new File(f, dir);
		}
		return f;
	}
	
	private String[] fullpath(ContentResource c) {
		ContentResource parent = c.getParent();
		if(parent != null) {
			return Util.append(fullpath(parent), c.getName());
		}
		return new String[] { c.getName() };
	}
	
	@Override
	public void mkdir(String... path) {
		File f = getfile(path);
		if(!f.exists()) {
			f.mkdirs();
		}
	}
	
	@Override
	public void rename(ContentResource c, String ... path) {
		File f = getfile(path);
		File parent = f.getParentFile();
		if(!parent.exists()) {
			parent.mkdirs();
		}
		if(f.exists()) {
			f.delete();
		}
		File src = getfile(fullpath(c));
		src.renameTo(f);
	}
	
	@Override
	public void put(InputStream in, String ... path) {
		File f = getfile(path);
		if(f.exists()) {
			f.delete();
		}
		File parent = f.getParentFile();
		if(parent.exists()) {
			if(!parent.isDirectory()) {
				throw new IllegalArgumentException(fullpath(fromFile(parent)) + " is not a directory.");
			}
		}
		else {
			parent.mkdirs();
		}
		try {
			Util.streamFully(in, new FileOutputStream(f));
		} catch(Exception e) {
			throw Util.asRuntime(e);
		}
	}
	
	@Override
	public void delete(String... path) {
		File f = getfile(path);
		if(f.exists()) {
			f.delete();
		}
	}
	
	@Override
	public ContentResource get(String ... path) {
		File f = getfile(path);
		if(f.exists()) {
			return fromFile(f);
		}
		return null; // nothing found
	}
	
	private ContentResource fromFile(File f) {
		if(root.equals(f)) {
			return ROOT;
		}

		return new FileResource(f);
	}
	
	public class RootResource implements ContentResource {
		public boolean isContainer() {
			return true;
		}
		public String getName() {
			return "";
		}
		public ContentResource getParent() {
			return null;
		}
		public Instant getLastModified() {
			return Instant.ofEpochMilli(root.lastModified());
		}
		public InputStream getInputStream() throws IOException {
			return null;
		}
		public Instant getCreationTime() {
			return Instant.ofEpochMilli(root.lastModified());
		}
		public String getContentType() {
			return "";
		}
		public List<ContentResource> getChildResources() {
			List<ContentResource> out = new ArrayList<>();
			for(File c : root.listFiles()) {
				out.add(fromFile(c));
			}
			return out;
		}
		public long getContentLength() {
			return 0;
		}
		public int hashCode() {
			return 0;
		}
		public boolean equals(Object obj) {
			return obj instanceof RootResource;
		}
	}
	
	public class FileResource implements ContentResource {
		File f;
		Path path;
		BasicFileAttributes attr;
		
		public FileResource(File f) {
			this.f = f;
			path = f.toPath();
			try {
				attr = Files.readAttributes(path, BasicFileAttributes.class);
			} catch (IOException e) {
				throw Util.asRuntime(e);
			}
		}
		
		@Override
		public boolean isContainer() {
			return f.isDirectory();
		}
		
		@Override
		public String getName() {
			return f.getName();
		}
		
		@Override
		public ContentResource getParent() {
			return fromFile(f.getParentFile());
		}
		
		@Override
		public Instant getLastModified() {
			return Instant.ofEpochMilli(f.lastModified());
		}
		
		@Override
		public InputStream getInputStream() throws IOException {
			return new FileInputStream(f);
		}
		
		@Override
		public Instant getCreationTime() {
			return attr.creationTime().toInstant();
		}
		
		@Override
		public String getContentType() {
			try {
				return Files.probeContentType(path);
			} catch (IOException e) {
				throw Util.asRuntime(e);
			}
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public List<ContentResource> getChildResources() {
			if(!f.isDirectory()) {
				return Collections.EMPTY_LIST;
			}
			List<ContentResource> out = new ArrayList<>();
			for(File c : f.listFiles()) {
				out.add(fromFile(c));
			}
			return out;
		}
		
		@Override
		public long getContentLength() {
			return f.length();
		}
		
		@Override
		public int hashCode() {
			return f.hashCode();
		}
		
		@Override
		public boolean equals(Object obj) {
			if(obj instanceof FileResource) {
				return ((FileResource)obj).f.equals(f);
			}
			return false;
		}
	}
}