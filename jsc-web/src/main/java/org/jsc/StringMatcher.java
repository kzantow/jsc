package org.jsc;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple, efficient-ish startsWith branching engine:
 * @author Keith.Zantow
 */
public class StringMatcher<T> {
	private static class Node<T> {
		final char branch;
		@SuppressWarnings("unused")
		String match;
		T result;
		Node<T>[] nodes;
		
		public Node(char branch) {
			this.branch = branch;
		}
	}
	
	private final Node<T> root = new Node<T>(' ');
	private final Map<String,T> entries = new HashMap<>();
	
	/**
	 * Add a matcher:
	 * 
	 * * check each child node from current
	 * 		- if child matches char, continue
	 * 		- if no child matches, add one
	 * * if end, set current to result
	 * 
	 * @param s
	 * @param result
	 */
	@SuppressWarnings("unchecked")
	public synchronized void add(String s, T result) {
		entries.put(s,result);
		
		Node<T> curr = this.root;
		int len = s.length();
		nextChar: for(int charNum = 0; charNum < len; charNum++) {
			char c = s.charAt(charNum);
			if(curr.nodes != null) {
				for(int nodeNum = 0; nodeNum < curr.nodes.length; nodeNum++) {
					Node<T> n = curr.nodes[nodeNum];
					if(c == n.branch) {
						curr = n;
						if(charNum == len-1) {
							break nextChar;
						}
						continue nextChar;
					}
				}
			}
			// Didn't find a matching node...
			if(curr.nodes == null) {
				curr.nodes = new Node[] { curr = new Node<T>(c) };
			} else {
				int sz = curr.nodes.length;
				Node<T>[] next = new Node[sz + 1];
				System.arraycopy(curr.nodes, 0, next, 0, sz);
				curr.nodes = next;
				next[sz] = curr = new Node<T>(c);
			}
		}
		
		curr.match = s;
		curr.result = result;
	}
	
	public T get(String s) {
		// Interesting!
		// Fastest way to iterate over a string < 256 chars is charAt:
		// http://stackoverflow.com/questions/8894258/fastest-way-to-iterate-over-all-the-chars-in-a-string
		// also, this avoids creating a new array
		Node<T> curr = this.root;
		int len = s.length();
		T result = null;
		for(int charNum = 0; charNum < len; charNum++) {
			char c = s.charAt(charNum);
			if(curr.nodes == null || curr.nodes.length == 0) {
				break;
			}
			int nodeLen = curr.nodes.length;
			Node<T> n = null;
			for(int nodeNum = 0; nodeNum < nodeLen; nodeNum++) {
				Node<T> child = curr.nodes[nodeNum];
				if(c == child.branch) {
					n = child;
					break;
				}
			}
			if(n == null) {
				break;
			} else {
				curr = n;
			}
			if(curr.result != null) {
				result = curr.result;
			}
		}
		return result;
	}
	
	@Override
	public String toString() {
		return entries.toString();
	}
}
