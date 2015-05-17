package org.jsc.app;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import javax.enterprise.inject.Default;
import javax.inject.Singleton;

import org.jsc.Fn1;
import org.jsc.Fn2;
import org.jsc.Log;
import org.jsc.Proc;
import org.jsc.Util;
import org.jsc.io.WatchDir;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.MembersInjector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;

/**
 * Basic central class to the app
 * @author kzantow
 */
public class App {
	/**
	 * Whether to run the app in development mode
	 */
	public static boolean development = Util.env("development", false);
	
	static {
		if(development) {
			Log.log.info("  ----  Running in development mode...");
		}
	}
	
	private final ClassLoader baseClassLoader = App.class.getClassLoader() != null ? App.class.getClassLoader() : Thread.currentThread().getContextClassLoader();
	
	private Injector injector;
	private String[] packages;
	private ConfigurationBuilder cfg;
	private Reflections reflections;
	private ClassLoader cl = baseClassLoader;
	private List<Proc> startupHooks = new ArrayList<Proc>();
	private List<Proc> shutdownHooks = new ArrayList<Proc>();
	
	public App(String ... packages) {
		Log.log.info("New app with packages: ", packages);
		this.packages = Util.append(packages, "org.jsc");
		init();
	}
	
	private WatchDir watchingForChanges = null;
	@SuppressWarnings("unused")
	private void watchForResourceChanges() {
		if(watchingForChanges != null) {
			// already watching, reloading
			return;
		}
		try {
			CodeSource cs = getClass().getProtectionDomain().getCodeSource();
			URL thisSource = cs.getLocation();
			
			File f = new File(thisSource.toURI());
			if (!f.isDirectory()) {
				f = f.getParentFile();
			}
			Path dir = f.toPath();
			watchingForChanges = new WatchDir(dir, true, p -> {
				Log.log.debug(p, " changed, reloading app...");
				//if(true) return; // todo real reloading
				// Simulate the shutdown() call:
				for(Method m : findAnnotatedMethods(OnShutdown.class)) {
					try {
						m.invoke(get(m.getDeclaringClass()));
					} catch(Throwable t) {
						t.printStackTrace();
					}
				}
				init();
			});
			watchingForChanges.start();
		} catch(Throwable t) {
			throw Util.asRuntime(t);
		}
	}
	
	/**
	 * Get subtypes of something
	 * @param typ
	 * @return
	 */
	public <T> Collection<Class<? extends T>> findSubtypesOf(Class<T> typ) {
		return sort(reflections.getSubTypesOf(typ));
	}
	
	/**
	 * Get types annotated
	 * @param annotation
	 * @return
	 */
	public Collection<Class<?>> findAnnotatedClasses(Class<? extends Annotation> annotation) {
		return sort(reflections.getTypesAnnotatedWith(annotation));
	}
	
	/**
	 * Get methods annotated
	 * @param annotation
	 * @return
	 */
	public Collection<Method> findAnnotatedMethods(Class<? extends Annotation> annotation) {
		return sort(reflections.getMethodsAnnotatedWith(annotation));
	}
	
	/**
	 * Sorts the elements here based on @Before and @After annotations; can only be Types or Methods
	 * @param items
	 * @return
	 */
	private <T extends AnnotatedElement> Collection<T> sort(Collection<T> items) {
		if(items.size() == 0) {
			return items;
		}
		List<T> out = new ArrayList<T>();
		List<T> remaining = new ArrayList<T>(items);
		
		final Fn1<Class<?>, Object> sortClass;
		if(items.iterator().next() instanceof Method) {
			sortClass = (t) -> ((Method)t).getDeclaringClass();
		} else {
			sortClass = (t) -> (Class<?>)t;
		}
		
		final Fn2<Boolean, List<T>, Class<?>> containsClass = (l, v) -> {
			for(T t : l) {
				Class<?> c = sortClass.exec(t);
				if(c == v) {
					return true;
				}
			}
			return false;
		};
		
		final Fn1<Boolean, T> canInsert = (v) -> {
			Class<?> vc = sortClass.exec(v);
			// check if this must occur after another class
			for(Class<?> c : after(v)) {
				if(containsClass.exec(remaining, c)) {
					return false;
				}
			}
			// check if this must occur before another class
			for(T t : remaining) {
				for(Class<?> c : before(t)) {
					if(c == vc) {
						return false;
					}
				}
			}
			return true;
		};
		
		try {
			while(true) {
				int startSize = remaining.size();
				for(Iterator<T> i = remaining.iterator(); i.hasNext();) {
					T t = i.next();
					if(canInsert.exec(t)) {
						out.add(t);
						i.remove();
					}
				}
				if(startSize == remaining.size()) {
					throw new RuntimeException("Unable to fulfil ordering requirements for: " + items);
				}
				if(remaining.size() == 0) {
					break;
				}
			}
		} catch (Throwable e) {
			throw Util.asRuntime(e);
		}
		
		return out;
	}
	
	private Class<?>[] before(AnnotatedElement e) {
		Before b = e.getAnnotation(Before.class);
		if(b != null) {
			return b.value();
		}
		return Util.EMPTY_CLASS_ARRAY;
	}

	private Class<?>[] after(AnnotatedElement e) {
		After b = e.getAnnotation(After.class);
		if(b != null) {
			return b.value();
		}
		return Util.EMPTY_CLASS_ARRAY;
	}

	
	/**
	 * Finds resources matching the given wildcard pattern,
	 * e.g.: *.html -> .*\\Q.html\\E
	 * @param pattern
	 * @return
	 */
	public Collection<String> findResources(String pattern) {
		pattern = "\\Q" + pattern.replace("*", "\\E.*\\Q") + "\\E";
		pattern = pattern.replace("\\Q\\E", "");
		return findResources(Pattern.compile(pattern));
	}
	
	/**
	 * Finds resources matching the given pattern
	 * @param pattern
	 * @return
	 */
	public Collection<String> findResources(Pattern pattern) {
		return reflections.getResources(pattern);
	}
	
	/**
	 * Get an instance of the particular type
	 * @param typ
	 * @return
	 */
	public <T> T get(Class<T> typ) {
		return injector.getInstance(typ);
	}
	
	/**
	 * Register explicit startup hook
	 * @param hook
	 */
	public void addStartupHook(Proc hook) {
		startupHooks.add(hook);
	}
	
	/**
	 * Register explicit shutdown hook
	 * @param hook
	 */
	public void addShutdownHook(Proc hook) {
		shutdownHooks.add(hook);
	}
	
	/**
	 * Process startup hooks
	 */
	public void startup() {
		if(App.development) {
			// watch resources for changes
			// FIXME make this work, it would be awesome. for now, we will just reload html & js components; that's a lot!
			//watchForResourceChanges();
		}
		
		for(Proc hook : startupHooks) {
			hook.exec();
		}
		
		for(Method m : findAnnotatedMethods(OnStartup.class)) {
			try {
				Class<?> typ = m.getDeclaringClass();
				if(!isSingleton(typ)) {
					throw new IllegalArgumentException(typ.getName() + "#" + m.getName() + "() is annotated with @OnStartup but class is not a singleton type.");
				}
				if(m.getParameterCount() > 0) {
					throw new IllegalArgumentException(typ.getName() + "#" + m.getName() + "() is annotated with @OnStartup but must be a no-arg method.");
				}
				m.invoke(get(typ));
			} catch(Throwable t) {
				throw Util.asRuntime(t);
			}
		}
	}

	/**
	 * Process shutdown hooks
	 */
	public void shutdown() {
		if(watchingForChanges != null) {
			watchingForChanges.stop();
			watchingForChanges = null;
		}
		
		for(Method m : findAnnotatedMethods(OnShutdown.class)) {
			try {
				m.invoke(get(m.getDeclaringClass()));
			} catch(Throwable t) {
				t.printStackTrace();
			}
		}
		
		for(Proc hook : shutdownHooks) {
			hook.exec();
		}

		// TODO kill the injector threads... how?
	}

	/**
	 * Process initialization
	 */
	@SuppressWarnings("unchecked")
	public void init() {
		URL thisPath = App.class.getProtectionDomain().getCodeSource().getLocation();
		URL[] jars = { thisPath };
		
		
		cl = new URLClassLoader(jars, baseClassLoader) {
			@Override
			public Class<?> loadClass(String name) throws ClassNotFoundException {
				Class<?> c = null;
				try {
					c = findClass(name); // load first
					resolveClass(c);
				} catch(ClassNotFoundException e) {
					// Uhh
				}
				if(c != null) {
					return c;
				}
				return super.loadClass(name);
			}
		};
		
		ClassLoader orig = Thread.currentThread().getContextClassLoader();
		//Thread.currentThread().setContextClassLoader(cl); // for reflections
		
		try {
			cfg = new ConfigurationBuilder()
				//.addClassLoader(cl)
				.addUrls(ClasspathHelper.forPackage("util"))
	        	.setScanners(new SubTypesScanner(), new TypeAnnotationsScanner(), new MethodAnnotationsScanner(), new ResourcesScanner());
			
			for(String pkg : packages) {
				cfg.addUrls(ClasspathHelper.forPackage(pkg));
			}
		
	    	reflections = new Reflections(cfg);
	    	
			injector = Guice.createInjector(
			(binder) -> {
				binder.bind(App.class).toInstance(this);
				try {
					//Thread.currentThread().setContextClassLoader(cl); // for reflections
					for(Class<? extends Module> c : findSubtypesOf(Module.class)) {
						Log.log.info("Found module: ", c);
						c.newInstance().configure(binder);
					}
				} catch(Exception e) {
					throw Util.asRuntime(e);
				}
				for(Class<?> c : findAnnotatedClasses(Service.class)) {
					binder.bind(c).in(Scopes.SINGLETON);
				}
				for(Class<?> c : findAnnotatedClasses(Default.class)) {
					// TODO fix this somehow
					binder.bind((Class<Object>)c.getInterfaces()[0]).to(c);
				}
			});
		} finally {
			Thread.currentThread().setContextClassLoader(orig);
		}
	}

	/**
	 * Indicates this class should be a "singleton", currently: @Singleton or @Service
	 * @param typ
	 * @return
	 */
	public boolean isSingleton(Class<?> typ) {
		return typ.isAnnotationPresent(Singleton.class)
		|| typ.isAnnotationPresent(Service.class);
	}

	/**
	 * Get a named resource from the app
	 * @param name
	 * @return
	 */
	public URL getResource(String name) {
		return cl.getResource(name);
	}
	
	/**
	 * Provide named logs to the services...
	 * @author kzantow
	 */
	public static class LogProviderModule implements Module {
		private class LogMembersInjector<T> implements MembersInjector<T> {
			private final Field field;
			private final Log log;

			LogMembersInjector(Field field) {
				this.field = field;
				this.log = new Log(field.getDeclaringClass());
				field.setAccessible(true);
			}

			public void injectMembers(T t) {
				try {
					field.set(t, log);
				} catch (IllegalAccessException e) {
					throw Util.asRuntime(e);
				}
			}
		}

		private class LogTypeListener implements TypeListener {
			public <T> void hear(TypeLiteral<T> typeLiteral, TypeEncounter<T> typeEncounter) {
				Class<?> clazz = typeLiteral.getRawType();
				while (clazz != null) {
					for (Field field : clazz.getDeclaredFields()) {
						if (field.getType() == Log.class) {
							typeEncounter.register(new LogMembersInjector<T>(field));
						}
					}
					clazz = clazz.getSuperclass();
				}
			}
		}

		@Override
		public void configure(Binder binder) {
			binder.bindListener(Matchers.any(), new LogTypeListener());
		}
	}

	/**
	 * Get the current classloader
	 * @return
	 */
	public ClassLoader getClassLoader() {
		return cl;
	}
}
