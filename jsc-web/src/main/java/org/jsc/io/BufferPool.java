package org.jsc.io;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;

/**
 * Provides a pooled set of byte arrays, with output streams that automatically handle obtaining and releasing them
 * @author kzantow
 */
public final class BufferPool {
	private final char[][] available;
	private int availableLength = 0;
	
	private final char[][] used;
	private int usedLength = 0;
	
	private final BufferPoolWriter[] writersAvailable;
	private int writersAvailableLength = 0;
	
	private final BufferPoolWriter[] wused;
	private int wusedLength = 0;
	
	private final int capacity;
	private final int bufSize;
	private final long timeout;
	
	private static final Field strChr;
	static {
		try {
			strChr = String.class.getDeclaredField("value");
			strChr.setAccessible(true);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	private static final char[] getCharArray(String str) {
		try {
			return (char[])strChr.get(str);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Constructs a new bufferPool with 10k buffers, 100 capacity
	 */
	public BufferPool() {
		this(100);
	}
	
	/**
	 * Constructs a new bufferPool with 10k buffers, provided capacity
	 * @param capacity
	 */
	public BufferPool(int capacity) {
		this(capacity, 10000);
	}
	
	/**
	 * Construct a ByteBufferPool with the given capacity & buffer, and 1 second timeout wait size:
	 * approximate total memory consumed by this is: capacity * bufSize in bytes
	 * e.g.: 100 * 100000 ~= 10 MB
	 * @param capacity
	 * @param bufSize
	 */
	public BufferPool(int capacity, int bufSize) {
		this(capacity, bufSize, 1000); // default to 1 second timeout
	}
	
	
	/**
	 * Construct a ByteBufferPool with the given capacity & buffer, and specified timeout wait size:
	 * approximate total memory consumed by this is: capacity * bufSize in bytes
	 * e.g.: 100 * 100000 ~= 10 MB
	 * @param capacity
	 * @param bufSize
	 */
	public BufferPool(int capacity, int bufSize, long timeoutInMillis) {
		this.capacity = capacity;
		this.timeout = timeoutInMillis;
		this.bufSize = bufSize;
		available = new char[capacity][];
		used = new char[capacity][];
		writersAvailable = new BufferPoolWriter[capacity];
		wused = new BufferPoolWriter[capacity];
	}
	
	private void removeFromUsed(char[] o) {
		int pos = 0;
		for(; pos < usedLength; pos++) {
			if(used[pos] == o) {
				break;
			}
		}
		usedLength--;
		for(; pos < usedLength; pos++) { // can't use arraycopy on same array for src & dst
			used[pos] = used[pos+1];
		}
	}

	private void removeFromWritersUsed(BufferPoolWriter o) {
		int pos = 0;
		for(; pos < wusedLength; pos++) {
			if(wused[pos] == o) {
				break;
			}
		}
		wusedLength--;
		for(; pos < wusedLength; pos++) {
			wused[pos] = wused[pos+1];
		}
	}
	
	public class BufferPoolWriter extends Writer {
		int buf = -1;
		int pos = 0;
		char[][] bufs = new char[capacity][];
		
		@Override
		public void write(char[] cbuf, int off, int len) throws IOException {
			int spos = off;
			// source length, can we fit in current buffer? do, otherwise find length to use from cbuf
			int slen = len - spos < bufSize - pos ? len - spos : bufSize - pos;
			// while we can't fit into the buffer
			while(buf < 0 || slen < len - spos) {
				if(buf >= 0) { // first time will allocate a buffer
					System.arraycopy(cbuf, spos, bufs[buf], pos, slen);
					spos += slen;
					pos = 0;
					slen = len - spos < bufSize ? len - spos : bufSize;
				}
				
				// get a new buffer
				synchronized(available) {
					buf++;
					if(availableLength > 0) {
						availableLength--;
						bufs[buf] = available[availableLength];
					}
					else {
						if(usedLength < capacity) {
							bufs[buf] = new char[bufSize];
						} else {
							try {
								// at capacity, wait
								available.wait(timeout);
								if(availableLength > 0) {
									availableLength--;
									bufs[buf] = available[availableLength]; // get the last, no moving necessary
								} else {
									throw new RuntimeException("Unable to obtain buffer after waiting: " + timeout + "ms");
								}
							} catch(InterruptedException e) {
								throw new RuntimeException("Unable to obtain buffer after waiting: " + timeout + "ms");
							}
						}
					}
					
					used[usedLength] = bufs[buf];
					usedLength++;
				}
			}
			System.arraycopy(cbuf, spos, bufs[buf], pos, slen);
			pos += slen;
		}
		
		@Override
		public void write(char[] cbuf) throws IOException {
			write(cbuf, 0, cbuf.length);
		}
		
		@Override
		public void write(int c) throws IOException {
			super.write(c);
		}
		
		@Override
		public void write(String str) throws IOException {
			char[] chars = getCharArray(str);
			write(chars);
		}
		
		@Override
		public void write(String str, int off, int len) throws IOException {
			super.write(str, off, len);
		}
		
		@Override
		public Writer append(char c) throws IOException {
			return super.append(c);
		}
		
		@Override
		public Writer append(CharSequence csq) throws IOException {
			return super.append(csq);
		}
		
		@Override
		public Writer append(CharSequence csq, int start, int end) throws IOException {
			return super.append(csq, start, end);
		}
		
		@Override
		public void flush() throws IOException {
		}
		
		@Override
		public void close() throws IOException {
			synchronized(available) {
				for(int b = 0; b < buf; b++) {
					removeFromUsed(bufs[b]);
					available[availableLength] = bufs[b];
					availableLength++;
				}
				reset();
			}
			synchronized(writersAvailable) {
				// this may still be used after being closed erroneously. not the most safe...
				removeFromWritersUsed(this);
				writersAvailable[writersAvailableLength] = this;
				writersAvailableLength++;
				writersAvailable.notify(); // notify writer added and available
			}
		}
		
		public void reset() {
			buf = -1;
			pos = 0;
		}
		
		/**
		 * Writes all the contents of this writer to the given output stream
		 * @param s
		 * @throws IOException
		 */
		public void writeTo(Writer out) throws IOException {
			for(int b = 0; b <= buf; b++) {
				out.write(bufs[b], 0, b < buf ? bufSize : pos);
			}
		}
	}
	
	public BufferPoolWriter getWriter() {
		BufferPoolWriter w;
		synchronized(writersAvailable) {
			if(writersAvailableLength > 0) {
				writersAvailableLength--;
				w = writersAvailable[writersAvailableLength]; // get the last, no moving necessary
			}
			else if(wusedLength < capacity) {
				w = new BufferPoolWriter();
				wused[wusedLength] = w;
				wusedLength++;
			}
			else {
				try {
					// at capacity, wait
					writersAvailable.wait(timeout);
					writersAvailableLength--;
					w = writersAvailable[writersAvailableLength]; // get the last, no moving necessary
				} catch(InterruptedException e) {
					throw new RuntimeException("Unable to obtain writer after waiting: " + timeout + "ms");
				}
			}
		}
		return w;
	}
	
	public static void main(String[] args) throws Exception {
		BufferPool bp = new BufferPool(8, 4); // very small version for testing
		
		BufferPoolWriter w = bp.getWriter();
		w.write("O ");
		w.write("OMG!");
		w.write("OMG!OMG!OMG!");
		
		StringWriter sw = new StringWriter();
		w.writeTo(sw);
		sw.flush();
		
		if(!"O OMG!OMG!OMG!OMG!".equals(sw.toString())) {
			throw new RuntimeException("OMG!");
		}
		
		w.close();
		w = bp.getWriter();
		w.write("O ");
		w.write("OMG!");
		w.write("OMG!OMG!OMG!");
		
		if(!"O OMG!OMG!OMG!OMG!".equals(sw.toString())) {
			throw new RuntimeException("OMG!");
		}
		
		if(bp.writersAvailableLength != 0) {
			throw new RuntimeException("OMG!");
		}
		
		bp.getWriter().close();
		w.close();
		
		if(bp.writersAvailableLength != 2) {
			throw new RuntimeException("OMG!");
			
		}
		
		w = bp.getWriter();
		
		if(bp.writersAvailableLength != 1) {
			throw new RuntimeException("OMG!");
		}
		
		w.close();
	}
}
