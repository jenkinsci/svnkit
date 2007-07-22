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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCompositePathList implements ISVNPathList, Iterator {
    private File[] myPaths;
    private Map myPathsToPegRevisions;
    private SVNRevision myPegRevision;
    private Iterator myPathsIterator;
    private int myIterateIndex; 

    public static SVNCompositePathList create(SVNPathList pathList, SVNChangeList changeList, boolean noDuplicates) throws SVNException {
        if (pathList == null && changeList == null) {
            return null;
        }
        
        Map combinedPathsToPegRevisions = new HashMap();
        Collection combinedPaths = new LinkedList(); 
        if (pathList != null) {
            File[] paths = pathList.getPaths();
            for (int i = 0; i < paths.length; i++) {
                File path = paths[i];
                combinedPathsToPegRevisions.put(path, pathList.getPegRevision(path));
                combinedPaths.add(path);
            }
        }
        if (changeList != null) {
            File[] paths = changeList.getPaths();
            for (int i = 0; i < paths.length; i++) {
                File path = paths[i];
                if (!noDuplicates || !combinedPathsToPegRevisions.containsKey(path)) {
                    SVNRevision pathListPegRev = (SVNRevision) combinedPathsToPegRevisions.get(path);
                    SVNRevision changeListPegRev = changeList.getPegRevision(path);
                    if (pathListPegRev == null || pathListPegRev == SVNRevision.UNDEFINED) {
                        combinedPathsToPegRevisions.put(path, changeListPegRev);
                    }
                    combinedPaths.add(path);
                }
            }            
        }
        
        SVNCompositePathList list = new SVNCompositePathList();
        list.myPathsToPegRevisions = combinedPathsToPegRevisions;
        list.myPaths = (File[]) combinedPaths.toArray(new File[combinedPaths.size()]);
        list.myPegRevision = pathList != null ?  pathList.getPegRevision() : SVNRevision.UNDEFINED;
        if (list.myPegRevision == SVNRevision.UNDEFINED && changeList != null &&
                changeList.getPegRevision() != SVNRevision.UNDEFINED) {
            list.myPegRevision = changeList.getPegRevision();
        }
        return list;
    }

    public File[] getPaths() throws SVNException {
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
        return (SVNRevision) myPathsToPegRevisions.get(path);
    }

    public SVNRevision getPegRevision() {
        return myPegRevision;
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
