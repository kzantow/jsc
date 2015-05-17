package org.jsc.web.ui;

import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javassist.ClassPool;
import javassist.CtClass;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.jsc.Expr;
import org.jsc.ExpressionService;
import org.jsc.Fn1;
import org.jsc.MapWrapper;
import org.jsc.Proc;
import org.jsc.Proc1;
import org.jsc.Proc2;
import org.jsc.Util;
import org.jsc.ExpressionService.BindingResolver;
import org.jsc.app.App;
import org.jsc.web.StaticResourceHandler;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;


/**
 * Creates component trees from html components
 * @author kzantow
 */
@Singleton
public class TemplateProcessor {
	@Inject private StaticResourceHandler res;
	@Inject private App app;
	
	private Map<String, Fn1<Component,Component>> jsc = new HashMap<String, Fn1<Component,Component>>() {
		private static final long serialVersionUID = 1L;
		@Override
		public Fn1<Component,Component> get(Object key) {
			Fn1<Component,Component> u = super.get(key);
			if(u == null) {
				String name = (String)key;
				for(String s : app.findResources("*"+name+".html")) {
					int idx = s.lastIndexOf('/') + 1;
					String n = (idx > 0 ? s.substring(idx) : s).replace(".html", "");
					if(n.equals(name)) {
						URL js = app.getResource(n + ".js");
						if(js != null) {
							u = new Fn1<Component, Component>() {
								public Component exec(Component c) throws Exception {
									return new JavascriptComponent(js, expr.parseScript(new String(Util.readFully(js), Util.UTF8C)), c);
								}
							};
							super.put(name, u);
						}
					}
				}
			}
			return u;
		}
		@Override
		public boolean containsKey(Object key) {
			return get(key) != null;
		}
	};

	private Map<String, URL> templates = new HashMap<String, URL>() {
		private static final long serialVersionUID = 1L;
		@Override
		public URL get(Object key) {
			URL u = super.get(key);
			if(u == null) {
				String name = (String)key;
				for(String s : app.findResources("*"+name+".html")) {
					int idx = s.lastIndexOf('/') + 1;
					String n = (idx > 0 ? s.substring(idx) : s).replace(".html", "");
					if(n.equals(name)) {
						super.put(name, u = app.getResource(s));
					}
				}
			}
			return u;
		}
		@Override
		public boolean containsKey(Object key) {
			return get(key) != null;
		}
	};
	
	private Map<String, Class<Component>> components = new HashMap<String, Class<Component>>() {
		private static final long serialVersionUID = 1L;
		@SuppressWarnings("unchecked")
		@Override
		public Class<Component> get(Object key) {
			Class<Component> c = super.get(key);
			if(c == null) {
				String name = (String)key;
				for(Class<? extends Component> typ : app.findSubtypesOf(Component.class)) {
					if(!Util.isInstantiable(typ)) {
						continue;
					}
					String s = typ.getSimpleName().toLowerCase(); // all tags are lower case from jsoup
					put(s, (Class<Component>)typ);
				}
				c = super.get(name);
				if(c == null) {
					put(name, null); // prevent duplicate lookups
				}
			}
			return c;
		}
		@Override
		public boolean containsKey(Object key) {
			return get(key) != null;
		}
	};
	
	/**
	 * Parse template into a render tree
	 * @param page
	 * @param template
	 * @return
	 * @throws Exception
	 */
	public Component parseTemplate(String path, URL template) throws Exception {
		Map<String,Proc2<Component,Component>> regions = new HashMap<>();
		return parseTemplate(path, template, regions);
	}
	
	/**
	 * Parse template into a render tree
	 * @param page
	 * @param template
	 * @return
	 * @throws Exception
	 */
	private Component parseTemplate(String path, URL template, Map<String,Proc2<Component,Component>> regions) throws Exception {
		java.io.StringWriter txt = new java.io.StringWriter();
		
		URLConnection c = template.openConnection();
		c.setDefaultUseCaches(false);
		c.setUseCaches(false);
		
		Util.streamFully(new java.io.InputStreamReader(c.getInputStream(), Util.UTF8C), txt);
		String html = txt.toString();
		
		Document doc = Jsoup.parse(html);
		boolean isDoc = html.contains("<html"); // todo better way to do this?
		Element el;
		if(isDoc) {
			el = doc;
		} else {
			Element body = doc.body();
			for(Node n : Util.reverse(doc.head().children())) {
				body.prependChild(n);
			}
			el = body;
		}
		
		BaseComponent base = new BaseComponent();
		for(Node child : el.children()) {
			appendNode(base, child, regions);
		}
		
		Component cmp;
		if(isDoc) {
			cmp = new HtmlDocumentComponent(base.getChildren());
		}
		else {
			// Don't wrap to a container, if only 1 node
			if(base.getChildren().size() == 1) {
				cmp = base.getChildren().get(0);
			}
			else {
				cmp = base;
			}
		}
		
		// support script-backed components
		if(jsc.containsKey(path)) {
			Fn1<Component,Component> js = jsc.get(path);
			cmp = js.exec(cmp);
		}
		
		return new TemplateComponent(template, Collections.emptyMap(), cmp);
	}
	
	javassist.ClassPool cp = ClassPool.getDefault();
	
	Class<?> makeTemplateClass(URL template) throws Exception {
		CtClass cls = cp.makeClass(template.getFile().replace('.', '_'));
		cls.addInterface(cp.getCtClass(Component.class.getName()));
		return cls.toClass();
	}
	
	@Inject ExpressionService expr;
	private Expr createExpression(Node location, String expression, Class<?> type) {
		Expr exp = expr.parseExpr(expression);
		if(type != Object.class) {
			return new Expr() {
				@Override
				public void setValue(BindingResolver ctx, Object val) {
					exp.setValue(ctx, val);
				}
				
				@Override
				public Object getValue(BindingResolver ctx) {
					return expr.coerce(exp.getValue(ctx), type);
				}
				
				@Override
				public String toString() {
					return "(" + type.getSimpleName() + ")" + Util.stringify(expr);
				}
			};
		}
		return exp;
	}
	
	private void appendNode(Component parent, Node n, Map<String,Proc2<Component,Component>> regions) throws Exception {
		if(n instanceof Element) {
			Element el = (Element)n;
			
			String name = el.tagName();
			
			try {
				if("script".equals(name) && el.hasAttr("src")) {
					el.attr("src", res.getExternalURL(el.attr("src")));
				}
				else if("link".equals(name) && el.hasAttr("href")) {
					el.attr("href", res.getExternalURL(el.attr("href")));
				}
				else if("img".equals(name) && el.hasAttr("src")) {
					el.attr("src", res.getExternalURL(el.attr("src")));
				}
			} catch(Exception e) {
				throw new IllegalArgumentException("Error setting src/href from: " + el.outerHtml());
			}
			
			List<Component> out = new ArrayList<Component>();
			
			Proc1<Component> setOut = (c) -> {
				if(!out.isEmpty()) {
					out.get(out.size()-1).getChildren().add(c);
				}
				else {
					out.add(c);
				}
			};
			
			Map<String,Expr> attributes = new HashMap<>();
			for(Attribute attr : el.attributes()) {
				String key = attr.getKey();
				String value = attr.getValue();
				if(key.startsWith("@")) {
					key = key.substring(1);
					String[] sp = key.split(":");
					String op = sp[0];
					if("loop".equals(op)) {
						Expr ve = createExpression(el, value, Object.class);
						String var = sp[1];
						Component c = new LoopComponent(var, ve);
						setOut.exec(c);
					}
					else if("set".equals(op)) {
						// wrap with a setValue component
						Expr ve = createExpression(el, value, Object.class);
						String var = sp.length > 1 ? sp[1] : null;
						
						Component c = new SetValueComponent(var, ve);
						setOut.exec(c);
					}
					else if("if".equals(op)) {
						Expr test = createExpression(el, value, Boolean.class);
						Component c = new IfComponent(test);
						setOut.exec(c);
					}
					else if("insert".equals(op)) {
						String var = sp.length > 1 ? sp[1] : null;
						// this is tricky: put placeholder callbacks for insert areas
						regions.put(var, (regionParent, newRegion) -> {
							// TODO better way?
							// This must capture the current context, NOT be executed in
							// the child context...
							String randomId = UUID.randomUUID().toString();
							BaseComponent ProvideParentContext = new BaseComponent() {
								public void render(Context ctx, Writer w) throws Throwable {
									ctx.put(randomId, ctx);
									super.render(ctx, w);
								}
								public void postback(PostbackContext ctx) throws Throwable {
									ctx.put(randomId, ctx);
									super.postback(ctx);
								}
							};
							regionParent.getChildren().add(0, ProvideParentContext); // TODO wrapper style?
							
							BaseComponent ExecuteInParentContext = new BaseComponent(Collections.singletonList(newRegion)) {
								public void render(Context ctx, Writer w) throws Throwable {
									Context pctx = (Context)ctx.get(randomId);
									newRegion.render(pctx, w);
								}
								public void postback(PostbackContext ctx) throws Throwable {
									PostbackContext pctx = (PostbackContext)ctx.get(randomId);
									newRegion.postback(pctx);
								}
							};
							
							int idx = parent.getChildren().indexOf(out.get(0));
							out.set(0, ExecuteInParentContext); // replace for further changes...
							parent.getChildren().set(idx, ExecuteInParentContext);
						});
					}
					else if("define".equals(op)) {
						// ignore here
					}
					else {
						throw new RuntimeException("Invalid directive: " + op);
					}
				}
				else {
					Expr ve = createExpression(el, value, Object.class);
					attributes.put(key, ve);
				}
			};
			
			Component c = null;
			if(components.containsKey(name)) {
				Class<Component> t = components.get(name);
				
				regions = new MapWrapper<String,Proc2<Component,Component>>(regions);
				List<Proc2<Object, Context>> set = new ArrayList<Proc2<Object, Context>>();
				List<Proc2<Object, Context>> update = new ArrayList<Proc2<Object, Context>>();
				
				Map<String,Field> fields = Util.fields(t);
				Map<String,Method> getters = Util.getters(t);
				Map<String,Method> setters = Util.setters(t);
				
				for(Attribute attr : el.attributes()) {
					Field field = fields.get(attr.getKey());
					Method setter = setters.get(attr.getKey());
					Method getter = getters.get(attr.getKey());
					
					if(setter == null && field == null) {
						throw new RuntimeException("Invalid property: " + attr);
					}
					
					Class<?> ftyp = setter == null ? field.getType() : setter.getParameterTypes()[0];
					
					if(attr.getValue().contains("${")) {
						if(Proc.class.equals(ftyp)) {
							Expr ve = createExpression(el, attr.getValue(), ftyp);
							if(setter != null) {
								set.add((cmp, ctx) -> {
									Proc p = () -> {
										ve.getValue(ctx);
									};
									setter.invoke(cmp, p);
								});
							}
							else {
								set.add((cmp, ctx) -> {
									Proc p = () -> {
										ve.getValue(ctx);
									};
									field.set(cmp, p);
								});
							}
						}
						else {
							Expr ve = createExpression(el, attr.getValue(), ftyp);
							if(setter != null) {
								set.add((cmp, ctx) -> {
									setter.invoke(cmp, ve.getValue(ctx));
								});
								update.add((cmp, ctx) -> {
									Object o = getter.invoke(cmp);
									ve.setValue(ctx, o);
								});
							}
							else {
								set.add((cmp, ctx) -> {
									field.set(cmp, ve.getValue(ctx));
								});
								update.add((cmp, ctx) -> {
									Object o = field.get(cmp);
									ve.setValue(ctx, o);
								});
							}
						}
					}
					else {
						Object v = expr.coerce(attr.getValue(), ftyp);
						if(setter != null) {
							set.add((cmp, ctx) -> {
								setter.invoke(cmp, v);
							});
						}
						else {
							set.add((cmp, ctx) -> {
								field.set(cmp, v);
							});
						}
					}
				}
				
				Component base = app.get(t);
				
				// For code components, wrap in value-setter component
				c = new ComponentLocalValueManager(base, set, update);
			}
			else if(templates.containsKey(name)) {
				regions = new MapWrapper<String,Proc2<Component,Component>>(regions);
				
				URL u = templates.get(name);
				Component base = parseTemplate(name, u, regions);
				
				c = new TemplateComponent(u, attributes, base);
				
				// Unnamed region replaces the entire contents with the caller's children
				Proc2<Component,Component> all = regions.get(null);
				if(all != null) {
					BaseComponent comp = new BaseComponent();
					for(Node child : el.childNodes()) {
						appendNode(comp, child, regions);
					};
					all.exec(parent, comp.getChildren().size() == 1 ? comp.getChildren().get(0) : comp);
				}
			}
			else {
				c = new ElementOutputComponent(name, attributes);
				
				// childNodes includes text nodes
				for(Node child : el.childNodes()) {
					appendNode(c, child, regions);
				};
			}
			
			setOut.exec(c);
			
			// if this was a definition, push to regions
			for(Attribute attr : el.attributes()) {
				String key = attr.getKey();
				if(key.startsWith("@")) {
					key = key.substring(1);
					String[] sp = key.split(":");
					String op = sp[0];
					if("define".equals(op)) {
						String var = sp.length > 1 ? sp[1] : null;
						if(var != null) {
							Proc2<Component,Component> p = regions.get(var);
							if(p == null) {
								// FIXME: line numbers, better error reporting
								throw new RuntimeException("No @insert for: " + var + " @ " + el);
							}
							p.exec(parent, out.get(0));
						}
						else {
							throw new RuntimeException("Must define name for @define, e.g. @define:header");
						}
						
						// Don't add this as a child
						return;
					}
				}
			}
			
			// add it
			parent.getChildren().add(out.get(0));
		}
		else if(n instanceof TextNode) {
			if(Util.isBlank(n.outerHtml().trim())) {
				return;
			}
			Expr ve = createExpression(n, ((TextNode)n).getWholeText().trim(), String.class);
			Component c = new TextExpressionComponent(ve);
			parent.getChildren().add(c);
		}
		else {
			String html = n.outerHtml().trim();
			if(Util.isBlank(html)) {
				return;
			}
			Component c = new BaseComponent() {
				public void render(Context ctx, Writer w) throws Throwable {
					w.write(html);
				}
			};
			parent.getChildren().add(c);
		}
	}

	static class TemplateContainer {
		Component cmp;
		long lastMod;
		static long lastMod(Component cmp) throws Exception {
			long lastMod = 0;
			if(cmp instanceof ReloadableCompoent) {
				URLConnection c = ((ReloadableCompoent)cmp).getSrc().openConnection();
				c.setDefaultUseCaches(false);
				c.setUseCaches(false);
				long l = c.getLastModified();
				if(l > lastMod) {
					lastMod = l;
				}
			}
			if(cmp.hasChildren()) {
				for(Component child : cmp.getChildren()) {
					long l = lastMod(child);
					if(l > lastMod) {
						lastMod = l;
					}
				}
			}
			return lastMod;
		}
	}
	
	Map<URL,TemplateContainer> comps = new HashMap<URL, TemplateContainer>();
	
	public Component getComponent(String path, URL u) throws Throwable {
		TemplateContainer cnt = comps.get(u);
		// TODO next check timeout
		if(cnt == null || cnt.lastMod < TemplateContainer.lastMod(cnt.cmp)) {
			Component cmp = parseTemplate(path, u);
			cnt = new TemplateContainer();
			cnt.cmp = cmp;
			cnt.lastMod = TemplateContainer.lastMod(cnt.cmp);
			comps.put(u, cnt);
		}
		
		return cnt.cmp;
	};
}
