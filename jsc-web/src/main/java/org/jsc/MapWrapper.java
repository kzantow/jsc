package org.jsc;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Delegates to parent map for value lookups if not found locally
 * @author kzantow
 */
public class MapWrapper<K, V> extends HashMap<K, V> {
	private static final long serialVersionUID = 1L;
	
	private final Map<K, V> delegate;
	
	public MapWrapper(Map<K, V> delegate) {
		this.delegate = delegate;
	}
	
	@Override
	public V get(Object key) {
		V v = super.get(key);
		if(v == null) {
			v = delegate.get(key);
		}
		return v;
	}
	
	@Override
	public boolean containsKey(Object key) {
		return super.containsKey(key) || delegate.containsKey(key);
	}
	
	@Override
	public Set<K> keySet() {
		return new AbstractSet<K>() {
			@Override
			public Iterator<K> iterator() {
				return Util.merge(MapWrapper.super.keySet(), delegate.keySet()).iterator();
			}
			@Override
			public int size() {
				return MapWrapper.super.size() + delegate.size();
			}
		};
	}
	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return new AbstractSet<Map.Entry<K,V>>() {
			@Override
			public Iterator<java.util.Map.Entry<K, V>> iterator() {
				return Util.merge(MapWrapper.super.entrySet(), delegate.entrySet()).iterator();
			}
			@Override
			public int size() {
				return MapWrapper.super.size() + delegate.size();
			}
		};
	}
}
