package org.jsc.io;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

import org.jsc.Proc1;
import org.jsc.Util;

/**
 * Example to watch a directory (or tree) for changes to files.
 */
public class WatchDir {
    private final WatchService watcher;
    private final Map<WatchKey,Path> keys;
    private final boolean recursive;
    private boolean trace = false;
    private Proc1<Path> onChange;
    private Thread watchingForChanges;

    /**
     * Register the given directory with the WatchService
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Register the given directory, and all its sub-directories, with the
     * WatchService.
     */
    private void registerAll(final Path start) throws IOException {
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Creates a WatchService and registers the given directory
     */
    public WatchDir(Path dir, boolean recursive, Proc1<Path> onChange) {
    	try {
	        this.watcher = FileSystems.getDefault().newWatchService();
	        this.keys = new HashMap<WatchKey,Path>();
	        this.recursive = recursive;
	
	        if (recursive) {
	            System.out.format("Scanning %s ...\n", dir);
	            registerAll(dir);
	            System.out.println("Done.");
	        } else {
	            register(dir);
	        }
	
	        // enable trace after initial registration
	        this.trace = true;
	        this.onChange = onChange;
    	} catch(Throwable t) {
    		throw Util.asRuntime(t);
    	}
    }

    /**
     * Process all events for keys queued to the watcher
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
	public void start() {
    	if(watchingForChanges != null) {
    		throw new RuntimeException("Already running!");
    	}
		Thread t = new Thread(() -> {
	        for (;;) {
	
	            // wait for key to be signalled
	            WatchKey key;
	            try {
	                key = watcher.take();
	            } catch (InterruptedException x) {
	                return;
	            }
	
	            Path dir = keys.get(key);
	            if (dir == null) {
	                System.err.println("WatchKey not recognized!!");
	                continue;
	            }
	
	            for (WatchEvent event: key.pollEvents()) {
	                WatchEvent.Kind kind = event.kind();
	
	                // TBD - provide example of how OVERFLOW event is handled
	                if (kind == OVERFLOW) {
	                    continue;
	                }
	
	                // Context for directory entry event is the file name of entry
	                WatchEvent<Path> ev = event;
	                Path name = ev.context();
	                Path child = dir.resolve(name);
	
	                // print out event
	                System.out.format("%s: %s\n", event.kind().name(), child);
	                
	                try {
	                	onChange.exec(child);
	                } catch(Throwable e) {
	                	e.printStackTrace(); // continue watching, don't fail
	                }
	
	                // if directory is created, and watching recursively, then
	                // register it and its sub-directories
	                if (recursive && (kind == ENTRY_CREATE)) {
	                    try {
	                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
	                            registerAll(child);
	                        }
	                    } catch (IOException x) {
	                        // ignore to keep sample readable
	                    }
	                }
	            }
	
	            // reset key and remove from set if directory no longer accessible
	            boolean valid = key.reset();
	            if (!valid) {
	                keys.remove(key);
	
	                // all directories are inaccessible
	                if (keys.isEmpty()) {
	                    break;
	                }
	            }
	        }
		}, "DirModificationListener/"+System.identityHashCode(this));
		watchingForChanges = t;
		t.setDaemon(true);
		t.start();

    }

    /**
     * Stop watching
     */
	public void stop() {
		Thread t = watchingForChanges;
		watchingForChanges = null;
		t.interrupt();
	}
}