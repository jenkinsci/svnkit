/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test.wc;

import java.io.File;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNWCDescriptor implements ISVNWorkingCopy {

    private Map myPaths;

    protected Map getPaths() {
        if (myPaths == null) {
            myPaths = new SVNHashMap();
            myPaths.put("", new SVNTestFileDescriptor(""));
        }
        return myPaths;
    }

    public void addFile(SVNTestFileDescriptor fileDescriptor) {
        getPaths().put(fileDescriptor.getPath(), fileDescriptor);
    }

    public AbstractSVNTestFile getTestFile(String path) {
        return getDescriptor(path);
    }

    public AbstractSVNTestFile getRoot() {
        return getDescriptor("");
    }

    public AbstractSVNTestFile[] getChildren(AbstractSVNTestFile file) {
        Collection result = new LinkedList();
        for (Iterator iterator = getPaths().entrySet().iterator(); iterator.hasNext();) {
            SVNHashMap.Entry entry = (Map.Entry) iterator.next();
            String childPath = (String) entry.getKey();
            if (SVNPathUtil.removeTail(childPath).equals(file.getPath())) {
                result.add(entry.getKey());                
            }
        }
        AbstractSVNTestFile[] children = new SVNTestFileDescriptor[result.size()];
        return (AbstractSVNTestFile[]) result.toArray(children);
    }

    public void walk(ISVNWorkingCopyWalker walker) throws SVNException {
        for (Iterator iterator = getPaths().values().iterator(); iterator.hasNext();) {
            SVNTestFileDescriptor descriptor = (SVNTestFileDescriptor) iterator.next();
            walker.handleEntry(descriptor);
        }
    }

    private SVNTestFileDescriptor getDescriptor(String path) {
        return (SVNTestFileDescriptor) getPaths().get(path);        
    }

    public void dump(File wcRoot) throws SVNException {
        for (Iterator iterator = getPaths().values().iterator(); iterator.hasNext();) {
            AbstractSVNTestFile fd = (AbstractSVNTestFile) iterator.next();
            fd.dump(wcRoot);
        }
    }
}