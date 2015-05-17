package org.jsc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.servlet.ServletException;

import org.apache.commons.codec.binary.Hex;

/**
 * Useful static utility methods & constants
 */
@SuppressWarnings("unchecked")
public class Util {
	/**
	 * UTF-8 constants
	 */
	public static final String UTF8 = "utf-8";
	/**
	 * UTF constant
	 */
	public static final Charset UTF8C;
	
	/**
	 * Indicates to output debugging information
	 */
	public static final boolean debug = true;
	
	/**
	 * Convenience to get an empty Class<?> array
	 */
	public static final Class<?>[] EMPTY_CLASS_ARRAY = new Class<?>[0];
	
	/**
	 * Convenience to get an empty object
	 */
	public static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
	
	public static final boolean logIgnoredExceptions = false;

	public static final Pattern WHITESPACE = Pattern.compile("\\s+");
	
	private static final Decoder base64decoder = Base64.getDecoder();
	private static final Encoder base64encoder = Base64.getEncoder();
	
	static {
		try {
			UTF8C = Charset.forName("utf-8");
		}
		catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Read the contents of the URL fully as a byte array
	 * @param u
	 * @return
	 */
	public static byte[] readFully(URL u) {
		try {
			URLConnection conn = u.openConnection();
			conn.setUseCaches(false);
			conn.setDefaultUseCaches(false);
			return readFully(conn.getInputStream());
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}
	
	/**
	 * Read an input stream, get the bytes; closes the input stream
	 */
	public static byte[] readFully(InputStream in) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte [] buffer = new byte[8192];
			int read = 0;
			while((read = in.read(buffer)) >= 0) {
				baos.write(buffer, 0, read);
			}
			return baos.toByteArray();
		} catch(Exception e) {
			throw asRuntime(e);
		} finally {
			try { in.close(); } catch(Exception e) { ignore(e); }
		}
	}

	/**
	 * Stream the contents of input to the output stream using a small buffer; closes both streams
	 * @param in
	 * @param out
	 */
	public static void streamFully(InputStream in, OutputStream out) {
		try {
			byte[] buffer = new byte[8192];
			int read = 0;
			while((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
		} catch(Exception e) {
			throw asRuntime(e);
		} finally {
			try { in.close(); } catch(Exception e) { ignore(e); }
			try { out.close(); } catch(Exception e) { ignore(e); }
		}
	}

	/**
	 * Stream the contents of input to the output stream using a small buffer; closes both streams
	 * @param in
	 * @param out
	 */
	public static void streamFully(Reader in, Writer out) {
		try {
			char[] buffer = new char[8192];
			int read = 0;
			while((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
		} catch(Exception e) {
			throw asRuntime(e);
		} finally {
			try { in.close(); } catch(Exception e) { ignore(e); }
			try { out.close(); } catch(Exception e) { ignore(e); }
		}
	}
	
	/**
	 * Hash with secure algorithm (sha-256, currently)
	 * @param s
	 * @return
	 */
	public static byte[] sha256(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return digest.digest(data);
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}
	
	/**
	 * Hash with secure algorithm (sha-256, currently)
	 * @param s
	 * @return
	 */
	public static String sha256(String s) {
		return base64(sha256(s.getBytes(UTF8C)));
	}
	
	/**
	 * Encode bytes to a base64 string
	 * @param bytes
	 * @return
	 */
	public static String base64(byte[] bytes) {
		return base64encoder.encodeToString(bytes);
	}

	/**
	 * Decodes a base64 encoded string to bytes
	 * @param encoded
	 * @return
	 */
	public static byte[] base64(String encoded) {
		return base64decoder.decode(encoded);
	}
	
	/**
	 * UrlEncode to a stream, with %20 instead of + for spaces
	 * @param s
	 * @param out
	 */
	public static void urlEncode(String s, Appendable out) {
		try {
			for (char ch : s.toCharArray()) {
				if(ch == ' ') {
					out.append('+');
				}
				else if (urlEncodeIsUnsafeChar(ch)) {
					out.append('%');
					out.append(urlEncodeToHex(ch / 16));
					out.append(urlEncodeToHex(ch % 16));
				} else {
					out.append(ch);
				}
			}
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}
	
	/**
	 * URL encode with %20 instead of + for spaces
	 * @param s
	 * @return
	 */
	public static String urlEncode(String s) {
		StringBuilder resultStr = new StringBuilder();
		urlEncode(s, resultStr);
		return resultStr.toString();
	}

	/**
	 * urlEncode helper
	 * @param ch
	 * @return
	 */
	private static char urlEncodeToHex(int ch) {
		return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
	}
	
	/**
	 * urlEncode helper
	 * @param ch
	 * @return
	 */
	private static boolean urlEncodeIsUnsafeChar(char ch) {
		if (ch > 128 || ch < 0)
			return true;
		return " %$&+,/:;=?@<>#%".indexOf(ch) >= 0;
	}
	
	/**
	 * URL decode matching {@link #urlEncode(String)}
	 * @param s
	 * @return
	 */
	public static String urlDecode(String s) {
		try {
			return URLDecoder.decode(s, UTF8);
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}

	/**
	 * Gets the given throwable as a runtime exception
	 * @param t
	 * @return
	 */
	public static RuntimeException asRuntime(Throwable t) {
		if(t instanceof RuntimeException) {
			return (RuntimeException)t;
		}
		RuntimeException e = new RuntimeException(t.getMessage(), t);
		e.setStackTrace(t.getStackTrace());
		return e;
	}
	
	/**
	 * Ignore the exception: ALWAYS use this to ignore exceptions, as logging may be enabled
	 * @param t
	 */
	public static void ignore(Throwable t) {
		if(logIgnoredExceptions) {
			t.printStackTrace();
		}
	}
	
	/**
	 * Get the cause of this throwable, as best we can
	 * @param t
	 * @return
	 */
	public static Throwable getCause(Throwable t) {
		if(t instanceof InvocationTargetException) {
			return getCause(t.getCause());
		}
		if(t instanceof ServletException) {
			Throwable cause = ((ServletException)t).getRootCause();
			return cause == null ? t : cause;
		}
		return t;
	}
	
	/**
	 * Get the root cause of this throwable, as best we can; this
	 * differs from getCause() that it will follow all causes, not just
	 * seemingly pertinent ones.
	 * @param t
	 * @return
	 */
	public static Throwable getRootCause(Throwable t) {
		if(t.getCause() != null && t.getCause() != t) {
			return t.getCause();
		}
		if(t instanceof InvocationTargetException) {
			return getCause(t.getCause());
		}
		if(t instanceof ServletException) {
			Throwable cause = ((ServletException)t).getRootCause();
			return cause == null ? t : cause;
		}
		return t;
	}

	/**
	 * Throws an exception that something is not implemented
	 * @return
	 */
	public static RuntimeException notImplemented(String reason) {
		throw new RuntimeException("Operation not implemented: " + reason);
	}

	/**
	 * Lower-cases the first character in the string; returns the same string if it is not upper
	 * @param s
	 * @return
	 */
	public static String lowerFirst(String s) {
		if(s == null || s.length() < 1) {
			return s;
		}
		char c = s.charAt(0);
		if(Character.isLowerCase(c)) {
			return s;
		}
		return Character.toLowerCase(c) + s.substring(1);
	}

	/**
	 * Upper-cases the first character in the string; returns the same string if it is not lower
	 * @param s
	 * @return
	 */
	public static String upperFirst(String s) {
		if(s == null || s.length() < 1) {
			return s;
		}
		char c = s.charAt(0);
		if(Character.isUpperCase(c)) {
			return s;
		}
		return Character.toUpperCase(c) + s.substring(1);
	}
	
	/**
	 * Returns an underscore-style version of a camel-case string
	 * @param s
	 */
	public static String toUnderscore(String s) {
		if(s == null || s.length() == 0) {
			return s;
		}
		StringBuilder out = null;
		int start = 0;
		int len = s.length();
		for(int i = 0; i < len; i++) {
			char c = s.charAt(i);
			if(Character.isUpperCase(c)) {
				if(out == null) {
					out = new StringBuilder(len+4);
				}
				if(start < i) {
					out.append(s, start, i);
				}
				out.append('_');
				out.append(Character.toLowerCase(c));
				start = i+1;
			}
		}
		if(out == null) {
			return s;
		}
		if(start < len) {
			out.append(s, start, len);
		}
		return out.toString();
	}
	
	/**
	 * Returns a camel-case version of an underscore-style string
	 * @param s
	 */
	public static String toCamelCase(String s) {
		if(s == null || s.length() == 0) {
			return s;
		}
		StringBuilder out = null;
		int start = 0;
		int len = s.length();
		for(int i = 0; i < len; i++) {
			if(s.charAt(i) == '_') {
				if(out == null) {
					out = new StringBuilder(len+4);
				}
				out.append(s, start, i);
				i++;
				if(i < len) {
					out.append(Character.toUpperCase(s.charAt(i)));
				}
				start = i+1;
			}
		}
		if(out == null) {
			return s;
		}
		if(start < len) {
			out.append(s, start, len);
		}
		return out.toString();
	}

	/**
	 * Return true if the string is null or empty string
	 * @param trim
	 * @return
	 */
	public static boolean isEmpty(String s) {
		return s == null || s.length() == 0;
	}

	/**
	 * Return true if the string is empty or whitespace
	 * @param trim
	 * @return
	 */
	public static boolean isBlank(String s) {
		return isEmpty(s) || WHITESPACE.matcher(s).matches();
	}

	/**
	 * Concatenates items to the end of the given array
	 * @param array
	 * @param items
	 * @return
	 */
	@SafeVarargs
	public static <T> T[] concat(T[] array, T ... items) {
		T[] out = (T[])Array.newInstance(array.getClass().getComponentType(), array.length + items.length);
		System.arraycopy(array, 0, out, 0, array.length);
		System.arraycopy(items, 0, out, array.length, items.length);
		return out;
	}

	/**
	 * Close a closable item, swallow exceptions
	 * @param c
	 */
	public static void close(Closeable c) {
		if(c != null) try { c.close(); } catch(Throwable t) { ignore(t); }
	}

	/**
	 * Gets the extension from the filename
	 * @param fileName
	 * @return
	 */
	public static String getExtension(String fileName) {
		if(fileName == null) {
			return null;
		}
		int idx = fileName.lastIndexOf('.');
		if(idx < 0) {
			return null;
		}
		return fileName.substring(idx);
	}
	
	/**
	 * Hash bytes with sha1
	 * @param bytes
	 * @return
	 */
	public static byte[] sha1(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-1");
			return digest.digest(data);
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}
	
	/**
	 * Hash bytes with md5
	 * @param bytes
	 * @return
	 */
	public static byte[] md5(byte[] data) {
		try {
			MessageDigest digest = MessageDigest.getInstance("MD5");
			return digest.digest(data);
		} catch(Exception e) {
			throw asRuntime(e);
		}
	}
	
	/**
	 * sha-1 hash and base64 encode a UTF-8 string
	 * @param s
	 * @return
	 */
	public static String sha1(String s) {
		return base64(sha1(s.getBytes(UTF8C)));
	}
	
	/**
	 * md5 hash and base64 encode a UTF-8 string
	 * @param s
	 * @return
	 */
	public static String md5(String s) {
		return base64(md5(s.getBytes(UTF8C)));
	}
	
	/**
	 * md5 hash and hex encode a UTF-8 string
	 * @param s
	 * @return
	 */
	public static String md5hex(String s) {
		return hex(md5(s.getBytes(UTF8C)));
	}
	
	/**
	 * Hex encode bytes
	 * @param s
	 * @return
	 */
	public static String hex(byte[] data) {
		return Hex.encodeHexString(data);
	}

	/**
	 * Returns true if both strings are null or are .equals
	 * @param o1
	 * @param o2
	 * @return
	 */
	public static boolean nullEquals(Object o1, Object o2) {
		if(o1 == null) {
			if(o2 == null) {
				return true;
			}
			return false;
		}
		return o1.equals(o2);
	}

	/**
	 * Appends elements to an array. Does not support primitive array types.
	 * @param array
	 * @param values
	 * @return
	 */
	@SafeVarargs
	public static <T> T[] append(T[] array, T ... values) {
		T[] out = (T[])Array.newInstance(array.getClass().getComponentType(), array.length + values.length);
		System.arraycopy(array, 0, out, 0, array.length);
		System.arraycopy(values, 0, out, array.length, values.length);
		return out;
	}

	/**
	 * Returns a single-element map with the given key -> value mapping
	 * 
	 * @param key
	 * @param val
	 * @return
	 */
	public static <K,V> SingleValueMap<K,V> map(K key, V value) {
		return new SingleValueMap<K, V>(key, value);
	}

	/**
	 * Join elements together to a string with the given separator
	 * @param fullpath
	 * @param string
	 * @return
	 */
	public static CharSequence join(Iterable<?> iterable, String separator) {
		if(iterable == null) {
			return "";
		}
		StringBuilder sb = null;
		for(Object o : iterable) {
			if(sb == null) {
				sb = new StringBuilder();
			}
			else {
				sb.append(',');
			}
			sb.append(o);
		}
		return sb;
	}

	/**
	 * Join elements in an array together to a string with the given separator
	 * @param fullpath
	 * @param string
	 * @return
	 */
	public static CharSequence join(Object[] iterable, String separator) {
		if(iterable == null) {
			return "";
		}
		StringBuilder sb = null;
		for(Object o : iterable) {
			if(sb == null) {
				sb = new StringBuilder();
			}
			else {
				sb.append(',');
			}
			sb.append(o);
		}
		return sb;
	}
	
	/**
	 * Add any additional environment providers. Note that this
	 * may occur after some lookups have. Be aware of the execution
	 * order.
	 * 
	 * @param fn
	 */
	public static void addEnvProvider(Fn1<String,String> fn) {
		envProviders = append(envProviders, fn);
	}
	
	private static Fn1<String,String>[] envProviders = new Fn1[0];
	static {
		// Set the default: system properties
		addEnvProvider((name) -> System.getProperty(name));
	};
	
	/**
	 * Get an environment variable
	 * @param name
	 * @return
	 */
	public static String env(String name) {
		for(int i = 0; i < envProviders.length; i++) {
			try {
				String prop = envProviders[i].exec(name);
				if(prop != null) {
					return prop;
				}
			} catch(Throwable t) {
				throw asRuntime(t);
			}
		}
		return null;
	}

	/**
	 * Get a configuration value from the environment
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static String env(String name, String defaultValue) {
		String prop = env(name);
		if(prop != null) {
			return prop;
		}
		return defaultValue;
	}
	
	/**
	 * Get an integer configuration value from the environment
	 * @param name
	 * @param defaultValue
	 * @return
	 */
	public static int env(String name, int defaultValue) {
		String prop = env(name);
		if(prop != null) {
			return Integer.parseInt(prop);
		}
		return defaultValue;
	}

	/**
	 * Get a boolean configuration value from the environment
	 * @param string
	 * @param b
	 * @return
	 */
	public static boolean env(String name, boolean defaultValue) {
		String prop = env(name);
		if(prop != null) {
			return Boolean.parseBoolean(prop);
		}
		return defaultValue;
	}

	/**
	 * Reverse the order of the elements
	 * @param items
	 * @return
	 */
	public static <T> Iterable<T> reverse(Iterable<T> items) {
		ArrayList<T> out = new ArrayList<>();
		for(T t : items) {
			if(out.size() == 0) {
				out.add(t);
			}
			else {
				out.add(0, t);
			}
		}
		return out;
	}
	
	/**
	 * Stringify the object
	 * @param baseComponent
	 * @return
	 */
	public static String stringify(Object o) {
		return stringify(o, true, 2);
	}
	
	/**
	 * Stringify the object
	 * @param baseComponent
	 * @return
	 */
	public static String stringify(Object o, boolean indent, int depth) {
		StringBuilder sb = new StringBuilder();
		try {
			stringify(o, new IdentityHashMap<Object,Object>(), indent ? "" : null, depth, sb);
		} catch(Throwable t) {
			throw asRuntime(t);
		}
		return sb.toString();
	}
	
	/**
	 * Helper for stringifying objects
	 * @param o
	 * @param inst
	 * @param indent
	 * @param depth
	 * @param sb
	 * @throws Exception
	 */
	private static void stringify(Object o, IdentityHashMap<Object,Object> inst, String indent, int depth, Appendable sb) throws Exception {
		if(depth < 0) {
			sb.append("...");
			return;
		}
		if(o == null) {
			sb.append("null");
			return;
		}
		if(inst.containsKey(o)) {
			sb.append("<...>");
			return;
		}
		inst.put(o, o);
		
		String childIndent = indent == null ? null : indent + "    ";
		
		if(o instanceof String) {
			sb.append('"').append(o.toString()).append('"');
		}
		else if(o instanceof Number) {
			sb.append(o.toString());
		}
		else if(o instanceof URL) {
			sb.append("URL(").append(o.toString()).append(')');
		}
		else if(o.getClass().isArray()) {
			sb.append("[");;
			for(Object o2 : toObjectArray(o)) {
				stringify(o2, inst, indent, depth - 1, sb);
			}
			sb.append("]");
		}
		else if(o instanceof Collection) {
			sb.append("[");
			for(Object o2 : (Collection<?>)o) {
				stringify(o2, inst, indent, depth - 1, sb);
			}
			sb.append("]");
		}
		else if(o instanceof Map) {
			sb.append("{");
			Map<?,?> m = (Map<?,?>)o;
			boolean first = true;
			for(Object k : m.keySet()) {
				if(first) {
					first = false;
				}
				else {
					sb.append(',');
					if(childIndent != null) {
						sb.append(' ');
					}
				}
				sb.append(k == null ? "null" : k.toString()).append(":");
				if(childIndent != null) {
					sb.append(' ');
				}
				stringify(m.get(k), inst, childIndent, depth - 1, sb);
			}
			sb.append("}");
		}
		else if(o instanceof ClassLoader) {
			sb.append(o.toString());
		}
		else {
			Class<?> c = o.getClass();
			sb.append(c.getName()).append("(");
			while(c != Object.class) {
				try {
					for(java.lang.reflect.Field f : c.getDeclaredFields()) {
						if(Modifier.isStatic(f.getModifiers())) {
							continue;
						}
						if(childIndent != null) {
							sb.append("\n").append(childIndent);
						}
						sb.append(f.getName()).append(":");
						if(childIndent != null) {
							sb.append(' ');
						}
						f.setAccessible(true);
						Class<?> typ = f.getType();
						Object v = f.get(o);
						
						if(v == null)  {
							sb.append("null");
						}
						else if(typ.isPrimitive() || Number.class.isAssignableFrom(typ)) {
							sb.append(v.toString());
						}
						else if(f.isAnnotationPresent(Inject.class)) {
							sb.append("@Inject ").append(v.toString());
						}
						else {
							stringify(v, inst, childIndent, depth - 1, sb);
						}
					}
				}
				catch(Throwable e) {
					sb.append(e.getClass().getName()).append(": ").append(e.getMessage());
				}
				c = c.getSuperclass();
			}
			if(indent != null) {
				sb.append("\n").append(indent);
			}
			sb.append(")");
		}
	}

	/**
	 * Indicates the class may be created with newInstance()
	 * @param c
	 * @return
	 */
	public static boolean isInstantiable(Class<?> c) {
		try {
			// check modifiers
			int mod = c.getModifiers();
			if(Modifier.isPublic(mod)
			&& !Modifier.isAbstract(mod)
			&& !c.isInterface()
			&& !c.isAnonymousClass()
			&& !c.isMemberClass()
			&& !c.isLocalClass()) {
				// check constructor
				Constructor<?> cs = c.getConstructor();
				mod = cs.getModifiers();
				if(Modifier.isPublic(mod)) {
					return true;
				}
			}
		} catch(Exception e) {
			// Ignore, no public constructor
		}
		return false;
	}

	/**
	 * Get all fields
	 * @param t
	 * @return
	 */
	public static Map<String,Field> fields(Class<?> t) {
		Map<String,Field> out = new HashMap<>();
		Class<?> c = t;
		while(c != Object.class) {
			for(Field f : c.getDeclaredFields()) {
				if(Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				f.setAccessible(true);
				out.put(f.getName(), f);
			}
			c = c.getSuperclass();
		}
		return out;
	}

	/**
	 * Set a field on this object
	 * @param o
	 * @param fieldName
	 * @param value
	 */
	public static void setField(Object o, String fieldName, Object value) {
		try {
			fields(o.getClass()).get(fieldName).set(o, value);
		} catch (Exception e) {
			throw Util.asRuntime(e);
		}
	}

	/**
	 * Getter methods for the class
	 * @param c
	 * @return
	 */
	public static Map<String,Method> getters(Class<?> c) {
		Map<String,Method> out = new HashMap<>();
		Class<?> typ = c;
		while(typ != Object.class) {
			for(Method m : typ.getDeclaredMethods()) {
				if(Modifier.isPublic(m.getModifiers())
				&& !Modifier.isStatic(m.getModifiers())
				&& m.getParameterCount() == 0
				&& m.getReturnType() != Void.TYPE
				&& (m.getName().startsWith("get") || (m.getName().startsWith("is") && m.getReturnType() == Boolean.TYPE))) {
					m.setAccessible(true);
					String name = lowerFirst(m.getName().startsWith("is") ? m.getName().substring(2) : m.getName().substring(3));
					out.put(name, m);
				}
			}
			typ = typ.getSuperclass();
		}
		return out;
	}

	/**
	 * Setter methods for the given class
	 * @param c
	 * @return
	 */
	public static Map<String,Method> setters(Class<?> c) {
		Map<String,Method> out = new HashMap<>();
		Class<?> typ = c;
		while(typ != Object.class) {
			for(Method m : typ.getDeclaredMethods()) {
				if(Modifier.isPublic(m.getModifiers())
				&& !Modifier.isStatic(m.getModifiers())
				&& m.getParameterCount() == 1
				&& m.getReturnType() == Void.TYPE
				&& m.getName().startsWith("set")) {
					m.setAccessible(true);
					String name = lowerFirst(m.getName().substring(3));
					out.put(name, m);
				}
			}
			typ = typ.getSuperclass();
		}
		return out;
	}
	
	/**
	 * 
	 * @param class1
	 * @return
	 */
	public static Map<String, Method> methods(Class<?> c) {
		Map<String,Method> out = new HashMap<>();
		Class<?> typ = c;
		while(typ != Object.class) {
			for(Method m : typ.getDeclaredMethods()) {
				if(Modifier.isPublic(m.getModifiers())
				&& !Modifier.isStatic(m.getModifiers())) {
					m.setAccessible(true);
					String name = m.getName();
					out.put(name, m);
				}
			}
			typ = typ.getSuperclass();
		}
		return out;
	}

	/**
	 * Return an object array from any array type (e.g. converts primitives); will throw a exception
	 * for objects which are not arrays.
	 * @param o
	 * @return
	 */
	public static Object[] toObjectArray(Object o) {
		if(o == null) {
			return null;
		}
		if(!o.getClass().isArray()) {
			throw new IllegalArgumentException("Argument is not array...");
		}
		Object[] values;
		if(o instanceof boolean[]) {
			values = toObject((boolean[])o);
		}
		else if(o instanceof byte[]) {
			values = toObject((byte[])o);
		}
		else if(o instanceof short[]) {
			values = toObject((short[])o);
		}
		else if(o instanceof int[]) {
			values = toObject((int[])o);
		}
		else if(o instanceof long[]) {
			values = toObject((long[])o);
		}
		else if(o instanceof char[]) {
			values = toObject((char[])o);
		}
		else if(o instanceof float[]) {
			values = toObject((float[])o);
		}
		else if(o instanceof double[]) {
			values = toObject((double[])o);
		}
		else {
			values = (Object[])o;
		}
		return values;
	}
	
	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Double[] toObject(double[] o) {
		Double[] out = new Double[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Float[] toObject(float[] o) {
		Float[] out = new Float[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Character[] toObject(char[] o) {
		Character[] out = new Character[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Long[] toObject(long[] o) {
		Long[] out = new Long[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Integer[] toObject(int[] o) {
		Integer[] out = new Integer[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Short[] toObject(short[] o) {
		Short[] out = new Short[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Byte[] toObject(byte[] o) {
		Byte[] out = new Byte[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Convert primitive array to object array
	 * @param o
	 * @return
	 */
	public static Boolean[] toObject(boolean[] o) {
		Boolean[] out = new Boolean[o.length];
		for(int i = 0; i < o.length; i++) {
			out[i] = o[i];
		}
		return out;
	}

	/**
	 * Merge multiple iterable objects into 1
	 * @param iterables
	 * @return
	 */
	@SafeVarargs
	public static <T> Iterable<T> merge(Iterable<T> ... iterables) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					Iterator<T> curr = iterables[0].iterator();
					{ primeNext(); }
					int idx = 0;
					void primeNext() {
						if(curr == null) {
							return;
						}
						while(!curr.hasNext()) {
							idx++;
							if(idx >= iterables.length) {
								curr = null;
								return;
							}
							curr = iterables[idx].iterator();
						}
					}
					@Override
					public boolean hasNext() {
						return curr != null;
					}
					@Override
					public T next() {
						T next = curr.next();
						primeNext();
						return next;
					}
				};
			}
		};
	}
	
	/**
	 * Escape dangerous HTML characters: " &lt; &
	 * @param s
	 * @return
	 */
	public static String escapeHtml(String s) {
		if(s == null) {
			return null;
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace("\"","&quot;");
	}

	/**
	 * Wraps an input stream to a URL
	 * @param in
	 * @return
	 */
	public static URL toUrl(String name, long lastModified, byte[] data) {
		java.net.URLStreamHandler ush = new java.net.URLStreamHandler() {
			@Override
			protected URLConnection openConnection(URL u) throws IOException {
				return new URLConnection(u) {
					@Override
					public void connect() throws IOException { }
					@Override
					public InputStream getInputStream() throws IOException {
						return new ByteArrayInputStream(data);
					}
					@Override
					public int getContentLength() {
						return data.length;
					}
					@Override
					public long getLastModified() {
						return lastModified;
					}
				};
			}
		};
		
		try {
			return new URL("mem", "", 0, name, ush);
		} catch (MalformedURLException e) {
			throw asRuntime(e);
		}
	}

	/**
	 * Get the stack trace as a string
	 * @param string
	 * @param t
	 * @return
	 */
	public static String stackTrace(Throwable t) {
		StringWriter w = new StringWriter();
		PrintWriter pw = new PrintWriter(w);
		t.printStackTrace(pw);
		pw.flush();
		return w.toString();
	}
}
