package org.jsc;

import java.util.HashMap;

/**
 * A map which initializes missing values when first requestsed, using the provided fn
 * @author kzantow
 *
 * @param <K>
 * @param <V>
 */
public class InitializingMap<K,V> extends HashMap<K,V> {
	private static final long serialVersionUID = 1L;
	
	private final Fn1<V,K> provider;
	
	public InitializingMap(Fn1<V,K> provider) {
		this.provider = provider;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V get(Object key) {
		V v = super.get(key);
		if(v == null) {
			K k = (K)key;
			try {
				super.put(k, v = provider.exec(k));
			} catch(Throwable t) {
				throw Util.asRuntime(t);
			}
		}
		return v;
	}
}
