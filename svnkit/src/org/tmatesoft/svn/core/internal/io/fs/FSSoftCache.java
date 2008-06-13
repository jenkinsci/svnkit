/**
 * 
 */
package org.tmatesoft.svn.core.internal.io.fs;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

public final class FSSoftCache {

    private final Map myCache;

    public FSSoftCache() {
        myCache = new HashMap();
    }

    public void put(Object key, Object value) {
         myCache.put(key, new SoftReference(value));
    }

    public void delete(Object key) {
        myCache.remove(key);
    }

    public Object fetch(Object key) {
    	SoftReference ref = (SoftReference) myCache.get(key);
    	if(ref != null) {
    		Object obj = ref.get();
    		if(obj != null) {
    			return obj;
    		}
    		myCache.remove(key);
    	}
    	return null;
    }

    public void clear() {
        myCache.clear();
    }
}