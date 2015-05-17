package org.jsc.io;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsc.Proc1;
import org.jsc.Util;

/**
 * Simple namespace aware, non-validating xml parser/generator class
 */
public class Xml {
	/**
	 * Construct a new XML element
	 */
	public Xml() {
	}
	
	/**
	 * Construct a new Xml node with the given namespace, node name & value
	 * @param ns
	 * @param name
	 * @param value
	 */
	public Xml(String ns, String name, String value) {
		this.ns = ns;
		this.name = name;
		this.value = value;
	}

	/**
	 * Parse the XML string, returns an Xml object
	 */
	public static Xml parse(String s) {
		return parse(new StringReader(s));
	}
	
	/**
	 * Parse the XML string, returns an Xml object
	 */
	public static Xml parse(Reader r) {
		try {
			return parse(new XStream(r), new HashMap<String, String>() {
				private static final long serialVersionUID = 1L;
				@Override
				public String get(Object key) {
					String s = super.get(key);
					if(s == null && key != null) {
						throw new RuntimeException("invalid namespace: " + key);
					}
					return s;
				}
			});
		} catch(Exception e) {
			throw Util.asRuntime(e);
		}
	}
	
	/**
	 * Meat of the parsing...
	 * @param s
	 * @param nss
	 * @return
	 */
	private static Xml parse(XStream s, Map<String,String> nss) {
		Xml curr = null;
		while(s.curr == '<' || s.read()) {
			s.consumeWhitespace();
			if(s.curr == '<') {
				s.read();
				// TODO XMLPI && CDATA support && entity processing
				if(s.curr == '?') {
					while(s.read() && s.curr != '?') {
					}
					s.read(); s.read(); // skip through ?>
				}
				else if(s.curr == '!') {
					while(s.read() && s.curr != '!') {
					}
					s.read(); s.read(); // skip through --!>
				}
				else if(s.curr == '/') {
					s.read();
					// verify element
					String ns = null;
					String name = s.readIdentifier();
					if(s.curr == ':') {
						ns = nss.get(name);
						s.read();
						name = s.readIdentifier();
					} else {
						ns = nss.get(null); // may be in empty namespace
					}
					if(!curr.is(ns, name)) {
						throw new RuntimeException();
					}
					s.consumeWhitespace();
					s.read(); // skip >
					s.consumeWhitespace();
					if(curr.parent != null) {
						curr = curr.parent;
					} else {
						return curr; // root node
					}
				}
				else {
					Xml next = new Xml();
					next.parent = curr;
					if(curr != null) {
						curr.addChild(next);
					}
					curr = next;

					String ns = null;
					String name = s.readIdentifier();
					if(s.curr == ':') {
						ns = name;
						s.read();
						name = s.readIdentifier();
					}
					curr.name = name;
					
					s.consumeWhitespace();
					while(s.curr != '>') {
						s.consumeWhitespace();
						
						if(s.curr == '/') {
							// just end the element
							break;
						}
						
						String attNs = null;
						String attName = s.readIdentifier();
						if(s.curr == ':') {
							s.read();
							attNs = attName;
							attName = s.readIdentifier();
						}
						
						s.consumeWhitespace();
						if(s.curr != '=') {
							throw new RuntimeException();
						}
						s.read(); // skip =
						s.consumeWhitespace();
						String value = s.readString();
						
						if("xmlns".equals(attNs)) {
							nss.put(attName, value);
						}
						else if("xmlns".equals(attName)) { // xmlns=...
							nss.put(null, value);
							attNs = null;
						}
						else {
							Xml attr = new Xml();
							attr.name = attName;
							attr.ns = nss.get(attNs);
							attr.value = value;
							curr.addAttr(attr);
						}
						s.consumeWhitespace();
					}
					
					// defer ns lookup until all attributes have been processed
					curr.ns = nss.get(ns);
					
					if(s.curr == '/') {
						curr = curr.parent;
						s.read();
					}
					s.read(); // consume >
				}
			}
			if(s.curr != '<' && curr != null) {
				s.utility.setLength(0);
				s.utility.append(s.curr);
				while(s.read() && s.curr != '<') {
					s.utility.append(s.curr);
				}
				Xml txt = new Xml();
				txt.value = s.utility.toString();
				curr.addChild(txt);
			}
		}
		return curr;
	}
	
	/**
	 * Indicates this is a matching node with the same namespace & local name
	 * @param ns
	 * @param name
	 * @return
	 */
	public boolean is(String ns, String name) {
		return Util.nullEquals(this.ns, ns) && Util.nullEquals(this.name, name);
	}

	/**
	 * Add an attribute
	 * @param attr
	 */
	public void addAttr(Xml attr) {
		if(attrs == null) {
			attrs = new ArrayList<Xml>();
		}
		attrs.add(attr);
	}
	
	/**
	 * Add a child node
	 * @param child
	 */
	public void addChild(Xml child) {
		if(children == null) {
			children = new ArrayList<Xml>();
		}
		children.add(child);
	}
	
	/**
	 * Simple character reader, keeps track of current character & previous,
	 * provides utility methods to read XML
	 */
	public static final class XStream {
		Reader r;
		int idx = -1;
		int sz = 0;
		char[] buf = new char[1024];
		public char prev;
		public char curr;
		
		final StringBuilder utility = new StringBuilder(1024);
		
		public XStream(Reader r) {
			this.r = r;
		}
		
		/**
		 * Moves to the next character; returns true if more characters, false if at the end of stream
		 */
		public boolean read() {
			if(sz < 0) {
				return false;
			}
			
			idx++;
			if(idx >= sz) {
				idx = 0;
				try {
					sz = r.read(buf);
				} catch(Exception e) {
					throw Util.asRuntime(e);
				}
			}

			prev = curr;
			if(idx < sz) {
				curr = buf[idx];
				return true;
			}
			else {
				// at the end
				curr = 0x0;
			}
			
			return false;
		}
		
		public boolean hasNext() {
			return idx < sz;
		}
		
		public void consumeWhitespace() {
			while(Character.isWhitespace(curr)) {
				read();
			}
		}
		
		public String readIdentifier() {
			utility.setLength(0);
			consumeWhitespace();
			char bound = 0x0;
			if(curr == '"' || curr == '\'') {
				bound = curr;
				read();
			}
			while(true) {
				if(curr == '\\') {
					read(); // blindly read escaped
				} else {
					if(bound == 0x0) {
						// not in a string, must stop at non-identifier chars:
						if(!Character.isJavaIdentifierPart(curr) && curr != '-') {
							break;
						}
					} else {
						if(curr == bound) {
							read();
							break; // when reached the end
						}
					}
				}
				utility.append(curr);
				if(!read()) {
					break;
				}
			}
			return utility.toString();
		}
		
		public String readString() {
			return readIdentifier();
		}
	}
	
	private String ns;
	private Xml parent;
	private List<Xml> children;
	private List<Xml> attrs;
	private String name;
	private String value;
	
	/**
	 * Add an attribute no namespace
	 * @param name
	 * @param value
	 * @return
	 */
	public Xml attr(String name, String value) {
		return attr(ns, name, value);
	}
	
	/**
	 * Add an attribute, with namespace
	 * @param ns
	 * @param name
	 * @param value
	 * @return
	 */
	public Xml attr(String ns, String name, String value) {
		Xml attr = new Xml(ns, name, value);
		addAttr(attr);
		return this;
	}
	
	/**
	 * Add an element, with the same namespace as the parent
	 * @param name
	 * @return
	 */
	public Xml el(String name) {
		return el(ns, name);
	}
	
	/**
	 * Add an element as a child, return that element
	 * @param ns
	 * @param name
	 * @return
	 */
	public Xml el(String ns, String name) {
		Xml el = new Xml(ns, name, null);
		addChild(el);
		return el;
	}
	
	/**
	 * Add an element, with callback to operate on it, returns this; operates in
	 * the same namespace as this 
	 * @param ns
	 * @param name
	 * @return
	 */
	public Xml el(String name, Proc1<Xml> withElement) {
		return el(ns, name, withElement);
	}
	
	/**
	 * Add an element, with callback to operate on it, returns this
	 * @param ns
	 * @param name
	 * @return
	 */
	public Xml el(String ns, String name, Proc1<Xml> withElement) {
		try {
			withElement.exec(el(ns, name));
		} catch(Throwable t) {
			throw Util.asRuntime(t);
		}
		return this;
	}
	
	/**
	 * Append a text node
	 * @param text
	 */
	public Xml text(String text) {
		addChild(new Xml(null, null, text));
		return this;
	}
	
	/**
	 * Gets parent
	 */
	public Xml up() {
		return parent;
	}
	
	/**
	 * Finds any descendant matching element by name
	 * @param name
	 * @return
	 */
	public Xml find(String name) {
		if(name.equals(name)) {
			return this;
		}
		if(children != null) {
			for(Xml child : children) {
				Xml x = child.find(name);
				if(x != null) {
					return x;
				}
			}
		}
		return null;
	}
	
	/**
	 * Finds direct child element by name
	 * @param name
	 * @return
	 */
	public Xml child(String name) {
		if(children != null) {
			for(Xml child : children) {
				if(name.equals(child.name)) {
					return child;
				}
			}
		}
		return null;
	}
	
	/**
	 * Gets an attribute value
	 * @param name
	 * @return
	 */
	public String attr(String name) {
		if(attrs != null) {
			for(Xml attr : attrs) {
				if(name.equals(attr.name)) {
					return attr.value;
				}
			}
		}
		return null;
	}
	
	/**
	 * Gets a value of an attribute on the given path
	 * @param path
	 * @return
	 */
	public String valueOf(String ... path) {
		Xml x = this;
		for(int i = 0; i < path.length; i++) {
			if(i == path.length-1) {
				return x.attr(name);
			}
			x = x.child(path[i]);
			if(x == null) {
				return null;
			}
		}
		return null;
	}
	
	/**
	 * Write this XML element to the writer, using a default namespace
	 * convention
	 * @param w
	 */
	public void write(Writer w) throws IOException {
		write(w, Util.map(ns, null));
	}
	
	/**
	 * Write this XML element to the writer with the given specific namespace -> prefix mapping
	 * @param w
	 * @param nss namespace-to-prefix mapping
	 * @throws IOException
	 */
	public void write(Writer w, Map<String, String> nss) throws IOException {
		Map<String, String> s = new HashMap<String, String>() {
			private static final long serialVersionUID = 1L;
			int num = 0;
			@Override
			public String get(Object key) {
				if(!containsKey(key)) {
					String s;
					if(nss.containsKey(key)) {
						s = nss.get(key);
					} else {
						s = "ns"+(++num);
					}
					super.put((String)key, s);
					return s;
				}
				return super.get(key);
			}
		};
		writeXml(w, s);
	}
	
	/**
	 * Write this XML element to the writer with the given namespace -> prefix mapping
	 * @param w
	 * @param nss namespace-to-prefix mapping
	 * @throws IOException
	 */
	private void writeXml(Writer w, Map<String,String> nss) throws IOException {
		if(name != null) {
			boolean appendNs = ns != null && !nss.containsKey(ns);
			try {
				if(value != null) { // attributes
					if(appendNs) {
						writeNamespaceDecl(w, ns, nss);
					}
					w.append(' ');
					writeNsPrefix(w, ns, nss);
					w.append(name).append('=').append('"').append(value).append('"');
					return;
				}
				// elements
				w.append('<');
				writeNsPrefix(w, ns, nss);
				w.append(name);
				if(appendNs) {
					writeNamespaceDecl(w, ns, nss);
				}
				if(attrs != null) {
					for(Xml attr : attrs) {
						attr.writeXml(w, nss);
					}
				}
				if(children == null) {
					w.append('/').append('>');
					return;
				}
				w.append('>');
				for(Xml child : children) {
					child.writeXml(w, nss);
				}
				w.append('<').append('/');
				writeNsPrefix(w, ns, nss);
				w.append(name);
				w.append('>');
				return;
			} finally {
				if(appendNs) {
					nss.remove(ns);
				}
			}
		}
		// text
		w.append(value);
	}
	
	private static void writeNamespaceDecl(Writer w, String ns, Map<String,String> nss) throws IOException {
		String pfx = nss.get(ns);
		if(pfx == null) {
			w.append(" xmlns");
		} else {
			w.append(" xmlns:").append(pfx);
		}
		w.append('=').append('"').append(ns).append('"');
	}
	
	private static void writeNsPrefix(Writer w, String ns, Map<String,String> nss) throws IOException {
		String pfx = nss.get(ns);
		if(pfx != null) {
			w.append(pfx).append(':');
		}
	}
	
	@Override
	public String toString() {
		StringWriter w = new StringWriter();
		try {
			write(w);
		} catch(Exception e) {
			throw Util.asRuntime(e);
		}
		return w.toString();
	}
	
	/**
	 * Write to string, with the given namespace mapping
	 * @param nss
	 * @return
	 */
	public String toString(Map<String,String> nss) throws IOException {
		StringWriter w = new StringWriter();
		write(w, nss);
		return w.toString();
	}
}
