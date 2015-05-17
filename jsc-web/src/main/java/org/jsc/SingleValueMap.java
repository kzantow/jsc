package org.jsc;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Map that contains exactly one key->value mapping. Operations that may modify the size of the map will fail.
 * Using {@link #put(Object, Object)} will result in both the single value and key being updated with the new value.
 * 
 * @author kzantow
 *
 * @param <K>
 * @param <V>
 */
public class SingleValueMap<K, V> implements Map<K, V>, Map.Entry<K,V>, Serializable {
	private static final long serialVersionUID = 1L;
	
	private K k;
	private V v;
	
	public SingleValueMap() {
	}

	public SingleValueMap(K key, V value) {
		k = key;
		v = value;
	}

	@Override
	public int size() {
		return 1;
	}

	@Override
	public boolean isEmpty() {
		return false;
	}

	@Override
	public boolean containsKey(Object key) {
		return Util.nullEquals(k, key);
	}

	@Override
	public boolean containsValue(Object value) {
		return Util.nullEquals(v, value);
	}

	@Override
	public V get(Object key) {
		return Util.nullEquals(k, key) ? v : null;
	}

	@Override
	public V put(K key, V value) {
		k = key;
		V o = v;
		v = value;
		return o;
	}

	@Override
	public V remove(Object key) {
		throw new IllegalArgumentException("remove() not supported for SingleElementMap");
	}

	@Override
	public void putAll(Map<? extends K, ? extends V> m) {
		throw new IllegalArgumentException("putAll() not supported for SingleElementMap");
	}

	@Override
	public void clear() {
		throw new IllegalArgumentException("clear() not supported for SingleElementMap");
	}

	@Override
	public Set<K> keySet() {
		return Collections.singleton(k);
	}

	@Override
	public Collection<V> values() {
		return Collections.singleton(v);
	}

	@Override
	public Set<Entry<K, V>> entrySet() {
		return Collections.singleton(this);
	}
	
	@Override
	public K getKey() {
		return k;
	}
	
	@Override
	public V getValue() {
		return v;
	}
	
	@Override
	public V setValue(V value) {
		V o = v;
		v = value;
		return o;
	}
	
	@Override
	public String toString() {
		return Util.stringify(this);
	}
}