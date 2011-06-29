package org.tmatesoft.svn.core.internal.wc;

import java.util.HashMap;
import java.util.Map;

public class SVNObjectsPool {
    
    private Map objectsPool = new HashMap();
    
    public Object getObject(Object value) {
        if (value != null) {
            Object existingValue = objectsPool.get(value);
            if (existingValue != null) {
                value = existingValue;
            } else {
                objectsPool.put(value, value);
            }
        }
        return value;
    }
    
    public void clear() {
        objectsPool = new HashMap();
    }

    public int size() {
        return objectsPool.size();
    }
}
