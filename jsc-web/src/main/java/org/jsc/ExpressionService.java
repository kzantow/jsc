package org.jsc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import javax.inject.Singleton;
import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

/**
 * Mostly wrapper for expression functionality
 * 
 * @author kzantow
 */
@Singleton
public class ExpressionService {
	private static final String SETTER_CTX_PLACEHOLDER = "__ctx__";
	private static final String SETTER_VAL_PLACEHOLDER = "__val__";

	public static class BindingResolver implements Bindings {
		private Map<String, Object> vars;

		public BindingResolver(Map<String, Object> vars) {
			this.vars = vars;
		}

		@Override
		public Object get(Object key) {
			return vars.get(key);
		}

		@Override
		public boolean containsKey(Object key) {
			return vars.containsKey(key);
		}

		@Override
		public boolean containsValue(Object value) {
			return vars.containsValue(value);
		}

		@Override
		public int size() {
			return vars.size();
		}

		@Override
		public boolean isEmpty() {
			return vars.isEmpty();
		}

		@Override
		public void clear() {
			vars.clear();
		}

		@Override
		public Set<String> keySet() {
			return vars.keySet();
		}

		@Override
		public Collection<Object> values() {
			return vars.values();
		}

		@Override
		public Set<Entry<String, Object>> entrySet() {
			return vars.entrySet();
		}

		@Override
		public Object put(String name, Object value) {
			return vars.put(name, value);
		}

		@Override
		public void putAll(Map<? extends String, ? extends Object> toMerge) {
			vars.putAll(toMerge);
		}

		@Override
		public Object remove(Object key) {
			return vars.remove(key);
		}
		
		@Override
		public String toString() {
			return vars.getClass().getSimpleName() + Util.stringify(vars);
		}
	}

	private final ScriptEngineManager manager = new ScriptEngineManager();
	private final ScriptEngine engine = manager.getEngineByName("nashorn");

	/**
	 * Call this to treat the entire string as a script and get a compiled version
	 * @param js
	 * @return
	 */
	public Expr parseScript(String js) {
		try {
			CompiledScript script = ((Compilable) engine).compile(js);
			return new Expr() {
				@Override
				public void setValue(BindingResolver ctx, Object val) {
					throw new UnsupportedOperationException("Full scripts do not support setting values");
				}
				
				@Override
				public Object getValue(BindingResolver ctx) {
					try {
						return script.eval(ctx);
					} catch (Exception e) {
						throw Util.asRuntime(e);
					}
				}
			};
		} catch(Exception e) {
			throw Util.asRuntime(e);
		}
	}

	/**
	 * Call this to treat the string as an expression; e.g. parse out "asdf${asdf}" to evaluate within script blocks
	 * @param expr
	 * @return
	 */
	public Expr parseExpr(String expr) {
		ArrayList<Expr> parts = new ArrayList<Expr>();
		StringBuilder text = new StringBuilder();
		char last = ' ';
		int bracketDepth = 0;
		Proc addString = () -> {
			if (text != null && text.length() > 0) {
				parts.add(new Expr() {
					String val = text.toString();
					@Override
					public Object getValue(BindingResolver ctx) {
						return val;
					}
					@Override
					public void setValue(BindingResolver ctx, Object val) {
						throw new UnsupportedOperationException("Cannot set literal text within an expression!");
					}
					@Override
					public String toString() {
						return Util.stringify(val);
					}
				});
				text.setLength(0);
			}
		};
		for (int i = 0; i < expr.length(); i++) {
			char c = expr.charAt(i);
			if (c == '\\') { // escaped chars go right to the stream
				i++;
				if (i < expr.length()) {
					text.append(expr.charAt(i));
				}
				last = c; // backslash so \${ doesn't match
				continue;
			}
			if (bracketDepth > 0) { // in expression
				if (c == '{') {
					bracketDepth++;
				}
				if (c == '}') {
					bracketDepth--;
				}
				if (bracketDepth == 0) { // end of expression
					if (text == null || text.length() == 0) {
						throw new RuntimeException("Illegal expression: " + expr);
					}

					try {
						CompiledScript getter = ((Compilable) engine).compile(text.toString()); // implicit return value
						CompiledScript _setter = null;
						try {
							// This is a big hack because there is literally no way in nashorn to set the global context to
							// set values, and all the classes are final
							// awesome.
							// ...
							_setter = ((Compilable) engine).compile(SETTER_CTX_PLACEHOLDER + "." + text.toString() + "=" + SETTER_VAL_PLACEHOLDER);
						} catch (Exception e) {
							// may not be a settable expression
						}
						CompiledScript setter = _setter;
						parts.add(new Expr() {
							@Override
							public Object getValue(BindingResolver ctx) {
								Object out;
								try {
									out = getter.eval(ctx);
								} catch (Exception e) {
									throw new RuntimeException("Error evaluating: " + expr, e);
								}
								return out;
							}

							@Override
							public void setValue(BindingResolver ctx, Object val) {
								BindingResolver vals = new BindingResolver(ctx) {
									public boolean containsKey(Object key) {
										return SETTER_VAL_PLACEHOLDER.equals(key) || SETTER_CTX_PLACEHOLDER.equals(key) || super.containsKey(key);
									}
									public Object get(Object key) {
										if(SETTER_VAL_PLACEHOLDER.equals(key)) {
											return val;
										}
										if(SETTER_CTX_PLACEHOLDER.equals(key)) {
											return ctx;
										}
										return super.get(key);
									}
									@Override
									public Object put(String name, Object value) {
										return super.put(name, value);
									}
								};
								try {
									SimpleScriptContext ctxt = new SimpleScriptContext() {
										public void setAttribute(String name, Object value, int scope) {
											super.setAttribute(name, value, scope);
										}
									};
									ctxt.setBindings(vals, ScriptContext.ENGINE_SCOPE);
									setter.eval(ctxt);
								} catch (NullPointerException e) {
									if (setter == null) {
										throw new RuntimeException("Not a settable expression: " + expr);
									}
								} catch (Exception e) {
									throw new RuntimeException("Error evaluatong: " + expr, e);
								}
							}
							
							@Override
							public String toString() {
								return Util.stringify(expr);
							}

						});
					} catch (Exception e) {
						throw Util.asRuntime(e);
					}

					text.setLength(0);
					continue;
				}
			} else {
				if (last == '$' && c == '{') {
					text.setLength(text.length() - 1); // get rid of the '$'
					addString.exec();
					bracketDepth++;
					continue;
				}
			}
			text.append(c);
			last = c;
		}
		addString.exec();
		if (parts.size() == 1) {
			return parts.get(0);
		}
		return new Expr() {
			Expr[] pt = parts.toArray(new Expr[parts.size()]);

			@Override
			public Object getValue(BindingResolver ctx) {
				StringBuilder sb = new StringBuilder();
				for (int i = 0; i < pt.length; i++) {
					Object val = pt[i].getValue(ctx);
					if (val != null) {
						sb.append(val.toString());
					}
				}
				return sb.toString();
			}

			@Override
			public void setValue(BindingResolver ctx, Object val) {
				throw new RuntimeException("Cannot set value for complex expression: " + expr);
			}
			
			@Override
			public String toString() {
				return Util.stringify(pt);
			}
		};
	}

	/**
	 * Attempt to coerce a value to a specific type
	 * 
	 * @param value
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T> T coerce(Object value, Class<T> type) {
		if (type == String.class) {
			return (T) (value == null ? "" : value.toString());
		}
		if (type == Boolean.class) {
			if (value instanceof Boolean) {
				return (T) value;
			}
			if (value instanceof Number) {
				return (T) (Boolean.valueOf(((Number) value).intValue() != 0));
			}
		}
		return (T) type;
	}

	public Map<String,Object> getGlobals() {
		ScriptContext myNewContext = new SimpleScriptContext();
		// ENGINE_SCOPE is a SimpleBindings instance
		try {
			engine.eval("1", myNewContext); // init globals!
		} catch (ScriptException e) {
			throw Util.asRuntime(e);
		}
		// nashorn engine associates a fresh nashorn Global with the
		// ENGINE_SCOPE
		Bindings obj = (Bindings)myNewContext.getBindings(ScriptContext.ENGINE_SCOPE);
		return obj;
	}
}
