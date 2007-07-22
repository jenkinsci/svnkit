/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNPathList implements ISVNPathList, Iterator {
    private File[] myPaths;
    private SVNRevision myPegRevision;
    private Map myPathsToPegRevisions;
    private Iterator myPathsIterator;
    private int myIterateIndex; 

    public static SVNPathList create(File[] paths, SVNRevision pegRevision) {
        if (paths == null || paths.length == 0) {
            return null;
        }
        
        SVNPathList list = new SVNPathList();
        list.myPaths = paths;
        list.myPegRevision = pegRevision;
        return list;
    }
    
    public static SVNPathList create(File[] paths, SVNRevision[] pegRevisions) {
        if (paths == null || paths.length == 0) {
            return null;
        }

        SVNPathList list = new SVNPathList();
        list.myPaths = paths;
        list.myPathsToPegRevisions = new HashMap();
        for (int i = 0; i < paths.length; i++) {
            File path = paths[i];
            SVNRevision pegRevision = pegRevisions != null && i < pegRevisions.length ? 
                                      pegRevisions[i] : SVNRevision.UNDEFINED;
            
            list.myPathsToPegRevisions.put(path, pegRevision);
        }

        return list;
    }

    public File[] getPaths() throws SVNException {
        if (myPaths == null) {
            myPaths = (File[]) myPathsToPegRevisions.
                                            keySet().
                                            toArray(new File[myPathsToPegRevisions.size()]);        
        }
        return myPaths;
    }

    public int getPathsCount() throws SVNException {
        File[] paths = getPaths();
        if (paths != null) {
            return paths.length;
        }
        return 0;
    }

    public SVNRevision getPegRevision(File path) {
        if (myPathsToPegRevisions != null) {
            SVNRevision rev = (SVNRevision) myPathsToPegRevisions.get(path);
            return rev != null ? rev : getPegRevision();
        }
        return getPegRevision();
    }

    public SVNRevision getPegRevision() {
        if (myPegRevision != null) {
            return myPegRevision;
        }
        return SVNRevision.UNDEFINED;
    }

    public Iterator getPathsIterator() throws SVNException {
        if (myPathsIterator != null) {
            return myPathsIterator;
        }
        getPaths();
        myIterateIndex = -1;
        myPathsIterator = this;
        return myPathsIterator;
    }

    public boolean hasNext() {
        if (myPathsIterator != null) {
            boolean hasNext = (myIterateIndex + 1) < myPaths.length;  
            if (!hasNext) {
                myPathsIterator = null;
            }
            return hasNext;
        }
        return false;
    }

    public Object next() {
        if (myPathsIterator != null && (myIterateIndex + 1) < myPaths.length) {
            return myPaths[++myIterateIndex];
        }
        return null;
    }

    public void remove() {
        //do nothing
    }
    
}
