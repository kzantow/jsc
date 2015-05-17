package org.jsc.io.json;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

import javax.enterprise.inject.Default;
import javax.inject.Singleton;

import org.jsc.Util;
import org.jsc.io.Json;

/**
 * Serializes/deserializes json with object types; this allows for explicit type
 * registration or will automatically add all types to the deserialization
 * context that have been previously been passed to serialization
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
@Singleton
@Default
public class JsonService implements Json {
	/**
	 * Maintains deserialization context
	 */
	public static final class JsonContext {
		public Map<Object,Object> processed = new IdentityHashMap<Object, Object>();
		public long reference = 0;
		public boolean failOnCycle = false;
		public boolean maintainReferences = false;
		public boolean failMissingTypes = false;
		// 2012-04-23T18:25:43.511Z
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // TODO pool this?
		{
			sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // TODO is this correct? it should be, as it's 'Z'
		}
		public CharSequence formatDate(Date d) {
			return sdf.format(d);
		}
	}
	
	/**
	 * Used to materialize objects given expected types
	 */
	public static interface KMaterializer {
		public Object materialize(Type t, Object json, JsonContext ctx) throws Exception;
	}
	
	/**
	 * Handles materialization of properties
	 */
	public static interface KPropertyMaterializer {
		public void materialize(Object o, JsonObject json, JsonContext ctx) throws Exception;
	}
	
	/**
	 * Implement this for custom JSON serialization
	 */
	public static interface KSerializer {
		public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception;
	}
	
	/**
	 * Implement this for custom JSON deserialization
	 */
	public static interface KDeserializer {
		public Object read(Type t, KStream stream, JsonContext ctx) throws Exception;
	}
	
	/**
	 * Thrown when an exception occurs during Json serialization/deserialization
	 */
	public static class JsonException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		int line;
		int col;
		
		public JsonException(int line, int col, String message) {
			super(message);
			this.line = line;
			this.col = col;
		}
		
		public JsonException(int line, int col, Exception cause) {
			this(line, col, cause.getClass().getName() + ": " + cause.getMessage(), cause);
		}
		
		public JsonException(KStream stream, String message) {
			this(stream.line, stream.col, message);
		}
		
		public JsonException(KStream stream, char ... expected) {
			this(stream, "Invalid character  " + stream.curr + "  expected one of  " + toString(expected) + " ");
		}

		private static String toString(char ... expected) {
			String s = "";
			for(char c : expected) {
				if(s.length() > 0) {
					s += " ";
				}
				s += c;
			}
			return s;
		}

		public JsonException(int line, int col, String string, Exception cause) {
			super(string + cause.getClass().getName() + ": " + cause.getMessage(), cause);
			this.line = line;
			this.col = col;
		}
		
		@Override
		public String getMessage() {
			return super.getMessage() + " @ line: " + line + ", char: " + col;
		}
	}
	
	/**
	 * A map of name/value properties
	 */
	public static class JsonObject extends HashMap {
		private static final long serialVersionUID = 1L;
		int line;
		int col;
		
		public JsonObject() {
		}
		
		public JsonObject(int line, int character) {
			this.line = line;
			this.col = character;
		}

		@Override
		public String toString() {
			return new JsonService().toJson(this);
		}
	}
	
	/**
	 * Simple character reader, keeps track of current character & previous,
	 * provides utility methods to read JSON primitives
	 */
	public static final class KStream {
		Reader r;
		int idx = -1;
		int sz = 0;
		char[] buf = new char[1024];
		public char prev;
		public char curr;
		int line = 1;
		int col = 0;
		
		private StringBuilder utility = new StringBuilder(1024);
		
		public KStream(Reader r) {
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
					throw new JsonException(line, col, e);
				}
			}
			
			if(prev == '\n') {
				line++;
				col = 1;
			} else {
				col++;
			}
			
			prev = curr;
			if(idx < sz) {
				curr = buf[idx];
				return true;
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
						// not in a string, must stop at JSON chars:
						//if(curr == ',' || curr == ':'
						//|| curr == '[' || curr == '{'
						//|| curr == ']' || curr == '}') {
						if(!Character.isJavaIdentifierPart(curr)
						&& curr != '-' && curr != '.' //also allow numbers here
						) {
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

	private final Map<Class, String> classToType = new ConcurrentHashMap<Class, String>();
	private final Map<String, Class> typeToClass = new ConcurrentHashMap<String, Class>();

	// invalid java identifier, so... use this as our JSON object's type name
	private static final String typeFieldName = "class";
	private static final String idFieldName = "$id";

	/**
	 * SHOULD register all types that allow serialization / deserialization;
	 * these will use the simpleclassname
	 */
	public void registerTypes(Class<?>... types) {
		for (Class c : types) {
			registerType(c.getSimpleName(), c);
			if(c.getSuperclass() != Object.class) {
				registerTypes(c.getSuperclass());
			}
		}
	}

	/**
	 * Register a named type; the name is what is exposed to JSON ojbects
	 * 
	 * @param name
	 * @param c
	 */
	public void registerType(String name, Class<?> c) {
		if(classToType.containsKey(c)) {
			return;
		}
		classToType.put(c, name);
		typeToClass.put(name, c);
	}
	
	private final Object deserialize(Type t, Object json, JsonContext ctx) throws Exception {
		if(json == null) {
			return null;
		}
		if(json instanceof Collection) {
			Collection data = (Collection)json;
			Class<?> c = (Class<?>)t;
			if(c.isArray()) {
				c = c.getComponentType();
				Object arr = Array.newInstance(c, data.size());
				int idx = 0;
				for(Object o : data) {
					o = deserialize(c, o, ctx);
					Array.set(arr, idx, o);
					idx++;
				}
				return arr;
			}
			else {
				List out = new ArrayList();
				for(Object o : data) {
					o = deserialize(t, o, ctx);
					if(o != null) {
						out.add(o);
					}
				}
				return out;
			}
		}
		if(json instanceof JsonObject) {
			JsonObject props = (JsonObject)json;
			Object typeName = props.get(typeFieldName);
			if(typeName != null) {
				Class typ = typeToClass.get(typeName);
				if(typ != null) {
					t = typ;
				} else {
					// TODO Try to match the type based on fields & base type
					// Non-public type, unable to construct
					if(ctx.failMissingTypes) {
						throw new JsonException(props.line, props.col, "Unknown type for: " + typeName);
					}
					if(!isInstantiable(t)) {
						return null;
					}
				}
			} else {
				Class typ = t instanceof Class ? (Class)t : null;
				// No type defined, make sure we're not trying to deserialize general unsupported stuff:
				if(typ == null
				|| Collection.class.isAssignableFrom(typ)) {
					throw new UnsupportedOperationException("Unable to deserialize for type: " + typ);
				}
			}
			KMaterializer materializer = mappers.get(t);
			try {
				return materializer.materialize(t, json, ctx);
			} catch(JsonException e) {
				throw e;
			} catch(Exception e) {
				throw new JsonException(props.line, props.col, e);
			}
		}
		KMaterializer materializer = mappers.get(t);
		return materializer.materialize(t, json, ctx);
	}
	
	/**
	 * Returns true if t is a class && newInstance() will succeed
	 */
	private boolean isInstantiable(Type t) {
		try {
			if(t instanceof Class) {
				Class c = (Class)t;
				Constructor cs = c.getConstructor();
				if(Modifier.isPublic(c.getModifiers())
				&& !Modifier.isAbstract(c.getModifiers())
				&& Modifier.isPublic(cs.getModifiers())) {
					return true;
				}
			}
			//((Class)t).newInstance();
			//return true;
		} catch(Exception e) {
			// Ignore, no public constructor
		}
		return false;
	}

	private final Map<Class, KMaterializer> mappers = new ConcurrentHashMap<Class, KMaterializer>() {
		private static final long serialVersionUID = 1L;
		
		@Override
		public KMaterializer get(Object key) {
			KMaterializer out = super.get(key);
			if(out == null) {
				if(key == null) {
					throw new IllegalArgumentException("Null mapper key not supported");
				}
				final Class c = (Class)key;
				if(c.isEnum()) {
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						if(json == null) {
							return null;
						}
						return Enum.valueOf(c, json.toString());
					}};
				}
				else if(c == Class.class) {
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						if(json == null) {
							return null;
						}
						return typeToClass.get(json.toString());
					}};
				}
				else if(Collection.class.isAssignableFrom(c) || c.isArray()) {
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						return deserialize(t, json, ctx);
					}};
				}
				else if(Date.class.isAssignableFrom(c)) {
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						if(json == null) {
							return null;
						}
						try {
							return ctx.sdf.parse(json.toString());
						} catch(Exception e) {
							throw new RuntimeException("Illegal date format: " + json);
						}
					}};
				}
				else if(Calendar.class.isAssignableFrom(c)) {
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						if(json == null) {
							return null;
						}
						Calendar c = Calendar.getInstance();
						try {
							c.setTime(ctx.sdf.parse(json.toString()));
						} catch(Exception e) {
							throw new RuntimeException("Illegal date format: " + json);
						}
						return c;
					}};
				}
				else {
					Class parent = c;
					ArrayList<KPropertyMaterializer> _fields = new ArrayList<KPropertyMaterializer>();
					while(parent != null && parent != Object.class) {
						for(final java.lang.reflect.Field f : parent.getDeclaredFields()) {
							if(shouldSkipField(f)) {
								continue;
							}
							f.setAccessible(true);
							final String fieldName = f.getName();
							Type t = f.getGenericType();
							if(t instanceof ParameterizedType) {
								while(t instanceof ParameterizedType) {
									ParameterizedType pt = (ParameterizedType)t;
									Class cls = (Class)pt.getRawType();
									if(Collection.class.isAssignableFrom(cls)) { // collection is 1-arg generic type
								        Type[] genericTypes = pt.getActualTypeArguments();
								        if(genericTypes.length == 1) {
								        	// Might be typed bounds, this will fail ...
								        	t = genericTypes[0];
								        	break;
								        }
									} else {
										throw new IllegalArgumentException("Unsupported generic type for field: " + f.toGenericString());
									}
								}
							} else {
								t = f.getType();
							}
							final Type fieldType = t;
							if(fieldType == Object.class) {
								_fields.add(new KPropertyMaterializer() { public void materialize(Object o, JsonObject props, JsonContext ctx) throws Exception {
									Object v = null;
									Object json = props.get(fieldName);
									if(json instanceof Collection) {
										v = deserialize(fieldType, json, ctx);
									}
									else if(json instanceof Map) {
										// no type information in "Object" fields; must use incoming JSON
										Map m = (Map)(v = json);
										Class declared = typeToClass.get(m.get(typeFieldName));
										if(declared != null) {
											v = deserialize(declared, json, ctx);
										}
									} else if(json != null) {
										v = deserialize(json.getClass(), json, ctx);
									}
									// This may be set to a Map, if no type information is available on the incoming JSON
									f.set(o, v);
								}});
							} else {
								_fields.add(new KPropertyMaterializer() { public void materialize(Object o, JsonObject props, JsonContext ctx) throws Exception {
									Object v = props.get(fieldName);
									if(v != null) {
										try {
											v = deserialize(fieldType, v, ctx);
											f.set(o, v);
										} catch(JsonException e) {
											throw e;
										} catch(Exception e) {
											throw new JsonException(props.line, props.col, "Error attempting to set field: " + f.toGenericString() + ": ", e);
										}
									}
								}});
							}
						}
						parent = parent.getSuperclass();
					}
					final KPropertyMaterializer[] fields = _fields.toArray(new KPropertyMaterializer[_fields.size()]);
					
					out = new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
						JsonObject m = (JsonObject)json;
						if(t == Object.class) {
							// No type information
							throw new JsonException(m.line, m.col, "Unknown type for JSON: " + m);
						}
						Object o = c.newInstance();
						for(int i = 0; i < fields.length; i++) {
							fields[i].materialize(o, m, ctx);
						}
						return o;
					}};
				}
				
				// Don't redo this a bunch
				put(c, out);
			}
			return out;
		}
	};
	
	/**
	 * Base deserializer; dispatchers to appropriate deserializers
	 */
	private final KDeserializer anyObject  = new KDeserializer() { public Object read(Type t, KStream stream, JsonContext ctx) throws Exception {
		stream.consumeWhitespace();
		if(stream.curr == '[') {
			return deserializeList.read(t, stream, ctx);
		}
		if(stream.curr == '{') {
			return deserializeMap.read(t, stream, ctx);
		}
		if(Character.isDigit(stream.curr)) {
			return stream.readIdentifier();//new BigDecimal(stream.readIdentifier());
		}
		String val = stream.readIdentifier();
		if("null".equals(val)) { // handle nulls here
			return null;
		}
		return val;
	}};
	
	private final KDeserializer deserializeList = new KDeserializer() { public Object read(Type t, KStream stream, JsonContext ctx) throws Exception {
		stream.consumeWhitespace();
		if(stream.curr != '[') {
			throw new JsonException(stream, '[');
		}
		stream.read();
		List out = new ArrayList();
		while(stream.curr != ']') {
			Object v = anyObject.read(t, stream, ctx);
			out.add(v);
			stream.consumeWhitespace();
			if(stream.curr == ',') {
				stream.read();
			} else if(stream.curr != ']') {
				throw new JsonException(stream, ',', ']');
			}
		}
		stream.read();
		return out;
	}};
	
	private final KDeserializer deserializeMap = new KDeserializer() { public Object read(Type t, KStream stream, JsonContext ctx) throws Exception {
		stream.consumeWhitespace();
		if(stream.curr != '{') {
			throw new JsonException(stream, '{');
		}
		stream.read();
		JsonObject js = new JsonObject(stream.line, stream.col);
		while(stream.curr != '}') {
			String k = stream.readIdentifier();
			stream.consumeWhitespace();
			if(stream.curr != ':') {
				throw new JsonException(stream, ':');
			}
			stream.read();
			Object v = anyObject.read(t, stream, ctx);
			js.put(k, v);
			stream.consumeWhitespace();
			if(stream.curr == ',') {
				stream.read();
				stream.consumeWhitespace();
			} else if(stream.curr != '}') {
				throw new JsonException(stream, ',', '}');
			}
		}
		stream.read();
		return js;
	}};
	
	{
		mappers.put(boolean.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Boolean.parseBoolean(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal boolean format: " + json);
			}
		}});
		mappers.put(Boolean.class, mappers.get(boolean.class));
		
		mappers.put(byte.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Byte.parseByte(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal byte format: " + json);
			}
		}});
		mappers.put(Byte.class, mappers.get(byte.class));
		
		mappers.put(short.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Short.parseShort(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal short format: " + json);
			}
		}});
		mappers.put(Short.class, mappers.get(short.class));
		
		mappers.put(int.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Integer.parseInt(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal integer format: " + json);
			}
		}});
		mappers.put(Integer.class, mappers.get(int.class));
		
		mappers.put(long.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Long.parseLong(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal long format: " + json);
			}
		}});
		mappers.put(Long.class, mappers.get(long.class));
		
		mappers.put(float.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Float.parseFloat(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal float format: " + json);
			}
		}});
		mappers.put(Float.class, mappers.get(float.class));
		
		mappers.put(double.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return Double.parseDouble(json.toString());
			} catch(Exception e) {
				throw new RuntimeException("Illegal double format: " + json);
			}
		}});
		mappers.put(Double.class, mappers.get(double.class));
		
		mappers.put(char.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			try {
				return json.toString().charAt(0);
			} catch(Exception e) {
				throw new RuntimeException("Illegal char format: " + json);
			}
		}});
		mappers.put(Character.class, mappers.get(char.class));
		
		mappers.put(String.class, new KMaterializer() { public Object materialize(Type t, Object json, JsonContext ctx) throws Exception {
			return json; // this has already been serialized to a string
		}});
	}
	
	private final Map<Class,KSerializer> writers = new ConcurrentHashMap<Class, KSerializer>() {
		private static final long serialVersionUID = 1L;

		@Override
		public KSerializer get(Object key) {
			KSerializer ser = super.get(key);
			if(ser == null) {
				Class c = (Class)key;
				if(c.isEnum()) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						Enum e = (Enum)o;
						w.append('"').append(e.name()).append('"');
					}};
				}
				else if(c == Class.class) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						Class c = (Class)o;
						String typ = classToType.get(c);
						if(typ == null) {
							registerTypes(c);
							typ = classToType.get(c);
						}
						w.append('"').append(typ).append('"');
					}};
				}
				else if(Number.class.isAssignableFrom(c)) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						if(o == null) {
							w.append("0");
						} else {
							w.append(o.toString());
						}
					}};
				}
				else if(Calendar.class.isAssignableFrom(c) || Date.class.isAssignableFrom(c)) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						Date d;
						if(o instanceof Calendar) {
							Calendar c = (Calendar)o;
							d = c.getTime();
						} else {
							d = (Date)o;
						}
						w.append('"').append(ctx.formatDate(d)).append('"');
					}};
				}
				else if(c.isArray()) {
					final Class<?> ctype = c.getComponentType();
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						Object[] values;
						if(boolean.class == ctype) {
							values = Util.toObject((boolean[])o);
						}
						else if(byte.class == ctype) {
							values = Util.toObject((byte[])o);
						}
						else if(short.class == ctype) {
							values = Util.toObject((short[])o);
						}
						else if(int.class == ctype) {
							values = Util.toObject((int[])o);
						}
						else if(long.class == ctype) {
							values = Util.toObject((long[])o);
						}
						else if(char.class == ctype) {
							throw new IllegalArgumentException("char[] not supported until ArrayUtils updated.");
							//values = ArrayUtils.toObject((char[])o);
						}
						else if(float.class == ctype) {
							values = Util.toObject((float[])o);
						}
						else if(double.class == ctype) {
							values = Util.toObject((double[])o);
						}
						else {
							values = (Object[])o;
						}
						
						boolean first = true;
						w.append('[');
						for(Object val : values) {
							if(first) {
								first = false;
							}
							else {
								w.append(',');
							}
							serialize(val, w, ctx);
						}
						w.append(']');
					}};
				}
				else if(Map.class.isAssignableFrom(c)) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						Map m = (Map)o;
						boolean first = true;
						w.append('[');
						for(Object key : m.keySet()) {
							if(first) {
								first = false;
							}
							else {
								w.append(',');
							}
							serialize(key, w, ctx); // TODO
							w.append(':');
							serialize(m.get(key), w, ctx);
						}
						w.append(']');
					}};
				}
				else if(Iterable.class.isAssignableFrom(c)) {
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						w.append('[');
						boolean first = true;
						Iterable i = (Iterable)o;
						for(Object val : i) {
							if(first) {
								first = false;
							}
							else {
								w.append(',');
							}
							serialize(val, w, ctx);
						}
						w.append(']');
					}};
				}
				else {
					ArrayList<KSerializer> _fields = new ArrayList<KSerializer>();
					String type = classToType.get(c);
					if(type == null) {
						registerTypes(c);
						type = classToType.get(c);
					}
					while(c != null && c != Object.class) {
						for(final java.lang.reflect.Field f : c.getDeclaredFields()) {
							if(shouldSkipField(f)) {
								continue;
							}
							f.setAccessible(true);
							final String fieldPrefix = "\"" + f.getName() + "\":";
							_fields.add(new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
								w.append(fieldPrefix);
								Object val = f.get(o);
								serialize(val, w, ctx);
							}});
						}
						c = c.getSuperclass();
					}
					final KSerializer[] fields = _fields.toArray(new KSerializer[_fields.size()]);
					final String typeOut = "{\"" + typeFieldName + "\":\"" + type + "\"";
					ser = new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
						// TODO don't bother with types by default, if not needed??
						//w.append('{');
						w.append(typeOut);
						
						// Prevent cycles; support object references by id
						if(ctx.processed.containsKey(o)) {
							Object id = ctx.processed.get(o);
							if(id == null) {
								ctx.processed.put(o, id = (ctx.reference++));
							}
							if(ctx.maintainReferences) {
								w.append(",\"").append(idFieldName).append("\":").append(id.toString());
							} else {
								if(ctx.failOnCycle) {
									throw new IllegalArgumentException("Object cycle detected, not allowed: " + o);
								} else {
									w.append(",\"CYCLE\":").append(id.toString());
								}
							}
							// Done with cycle/reference
							w.append("}");
							return;
						} else {
							if(ctx.maintainReferences) {
								Object id = (ctx.reference++);
								ctx.processed.put(o, id);
								w.append(",\"").append(idFieldName).append("\":").append(id.toString());
							} else {
								ctx.processed.put(o, null);
							}
						}
						
						for(int i = 0; i < fields.length; i++) {
							w.append(',');
							fields[i].serializeObject(o, w, ctx);
						}
						w.append('}');
						
						if(!ctx.maintainReferences) { // don't worry about processing the same object again; we're no longer in a cycle
							ctx.processed.remove(o);
						}
					}};
				}
				
				put(c, ser);
			}
			return ser;
		}
	};
	
	{
		writers.put(boolean.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Boolean.toString((Boolean)o));
		}});
		writers.put(Boolean.class, writers.get(boolean.class));
		writers.put(byte.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Byte.toString((Byte)o));
		}});
		writers.put(Byte.class, writers.get(byte.class));
		writers.put(short.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Short.toString((Short)o));
		}});
		writers.put(Short.class, writers.get(short.class));
		writers.put(int.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Integer.toString((Integer)o));
		}});
		writers.put(Integer.class, writers.get(int.class));
		writers.put(long.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Long.toString((Long)o));
		}});
		writers.put(Long.class, writers.get(long.class));
		writers.put(float.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Float.toString((Float)o));
		}});
		writers.put(Float.class, writers.get(float.class));
		writers.put(double.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Double.toString((Double)o));
		}});
		writers.put(Double.class, writers.get(double.class));
		writers.put(char.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			w.append(Character.toString((Character)o));
		}});
		writers.put(Character.class, writers.get(char.class));
		writers.put(String.class, new KSerializer() { public void serializeObject(Object o, Writer w, JsonContext ctx) throws Exception {
			String s = o.toString();
			if(s.indexOf('\\')>0) { // replace backslashes first
				s = s.replace("\\", "\\\\");
			}
			if(s.indexOf('"')>0) {
				// TODO make this efficient - e.g. just append substrings
				// & what should this really be escaped to?
				//s = s.replace("\"", "&quot;");
				s = s.replace("\"", "\\\"");
			}
			w.append('"').append(s).append('"');
		}});
	}
	
	private void serialize(Object o, Writer w, JsonContext ctx) throws Exception {
		if(o == null) {
			w.append("null");
			return;
		}
		
		writers.get(o.getClass()).serializeObject(o, w, ctx);
	}
	
	/**
	 * Indicate this type of field should be skipped (static, final, transient, annotated with?)
	 * @param f
	 * @return
	 */
	protected boolean shouldSkipField(Field f) {
		int modifiers = f.getModifiers();
		// don't handle certain types of fields:
		if(Modifier.isStatic(modifiers)
		|| Modifier.isFinal(modifiers)
		|| Modifier.isTransient(modifiers)) {
			return true;
		}
		return false;
	}

	/**
	 * Converts an object to a JSON string, including type information
	 */
	@Override
	public String toJson(Object o) {
		StringWriter w = new StringWriter();
		toJson(o, w);
		return w.toString();
	}

	/**
	 * Converts an object to a JSON, including type information
	 */
	@Override
	public void toJson(Object o, Writer w) {
		try {
			serialize(o, w, new JsonContext());
		} catch(JsonException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Converts to an object from a JSON string, preserving types
	 */
	@Override
	public Object fromJson(Class<?> type, String json) {
		return fromJson(type, new StringReader(json));
	}

	/**
	 * Converts to an object from a JSON reader, preserving types
	 */
	@Override
	public Object fromJson(Class<?> type, Reader json) {
		return fromJson(type, new KStream(json));
	}
	
	private Object fromJson(Type t, KStream stream) {
		try {
			stream.read(); // prime
			Object json = anyObject.read(t, stream, new JsonContext());
			return deserialize(t, json, new JsonContext());
		} catch(JsonException e) {
			throw e;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns a quoted string or null: e.g. this is "a string" -> "this is &quot;a string&quot;"
	 * @param s
	 * @return
	 */
	public static CharSequence quoteOrNull(CharSequence s) {
		if(s == null) {
			return "null";
		}
		
		StringBuilder sb = null;
		int lastIndex = 0;
		int len = s.length();
		for(int i = 0; i < len; i++) {
			if(s.charAt(i) == '"') {
				if(sb == null) {
					sb = new StringBuilder(len*2).append("\""); // a bit much
				}
				sb.append(s.subSequence(lastIndex, i));
				sb.append("&quot;");
				lastIndex = i+1;
			}
		}
		return sb == null ? ("\"" + s + "\"") : sb.append("\"");
	}
}
