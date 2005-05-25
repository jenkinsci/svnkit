package org.tmatesoft.svn.core.internal.wc;

import java.io.InputStream;

public interface ISVNPropertyComparator {
    
    public void propertyAdded(String name, InputStream value, int length);

    public void propertyDeleted(String name);

    public void propertyChanged(String name, InputStream newValue, int length);
}
