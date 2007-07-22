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
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNChangeList implements ISVNPathList, Iterator {
    private String myChangelistName;
    private File myRootPath;
    private File[] myPaths;
    private SVNRevision myPegRevision;
    private ISVNOptions myOptions;
    private ISVNRepositoryPool myRepositoryPool;
    private ISVNAuthenticationManager myAuthManager;    
    private SVNChangelistClient myChangelistClient;
    private Iterator myPathsIterator;
    private int myIterateIndex; 
    
    public static SVNChangeList create(String changelistName, File wcPath) {
        SVNChangeList list = new SVNChangeList();
        list.myChangelistName = changelistName;;
        list.myRootPath = wcPath;
        return list;
    }

    public String getChangelistName() {
        return myChangelistName;
    }
    
    public File getRootPath() {
        return myRootPath;
    }

    public File[] getPaths() throws SVNException {
        if (myPaths == null) {
            SVNChangelistClient client = getChangelistClient();
            Collection changelistTargets = client.getChangelist(myRootPath, myChangelistName, (Collection) null);
            if (changelistTargets != null) {
                myPaths = (File[]) changelistTargets.toArray(new File[changelistTargets.size()]);
            }
        }
        return myPaths;
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

    public int getPathsCount() throws SVNException {
        File[] paths = getPaths();
        if (paths != null) {
            return paths.length;
        }
        return 0;
    }

    public SVNRevision getPegRevision(File path) {
        return myPegRevision != null ? myPegRevision : SVNRevision.UNDEFINED;
    }

    public SVNRevision getPegRevision() {
        return myPegRevision != null ? myPegRevision : SVNRevision.UNDEFINED;
    }

    public void setPegRevision(SVNRevision pegRevision) {
        myPegRevision = pegRevision;
    }
    
    public void setAuthManager(ISVNAuthenticationManager authManager) {
        myAuthManager = authManager;
    }
    
    public void setOptions(ISVNOptions options) {
        myOptions = options;
    }
    
    public void setRepositoryPool(ISVNRepositoryPool repositoryPool) {
        myRepositoryPool = repositoryPool;
    }

    private ISVNAuthenticationManager getAuthManager() {
        if (myAuthManager == null) {
            myAuthManager = SVNWCUtil.createDefaultAuthenticationManager();
        }
        return myAuthManager;
    }
    
    private ISVNOptions getOptions() {
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        return myOptions;
    }

    private SVNChangelistClient getChangelistClient() {
        if (myChangelistClient == null) {
            if (myRepositoryPool != null) {
                myChangelistClient = new SVNChangelistClient(myRepositoryPool, getOptions());
            } else {
                myChangelistClient = new SVNChangelistClient(getAuthManager(), getOptions());
            }
        }
        return myChangelistClient;
    }

}
