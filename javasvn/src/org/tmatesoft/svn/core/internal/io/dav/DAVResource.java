/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class DAVResource {
    
    private String myWURL;
    private String myVURL;
    private String myURL;
    private String myPath;
    private ISVNWorkspaceMediator myMediator;
    private long myRevision;
    private boolean myIsCopy;
    
    private DAVConnection myConnection;
    private List myDeltaFiles;
    private Map myProperties;
    private boolean myIsAdded;

    public DAVResource(ISVNWorkspaceMediator mediator, DAVConnection connection, String path, long revision) {
        this(mediator, connection, path, revision, false);
    }
    
    public DAVResource(ISVNWorkspaceMediator mediator, DAVConnection connection, String path, long revision, boolean isCopy) {
        myPath = path;
        myMediator = mediator;
        myURL = PathUtil.append(connection.getLocation().getPath(), path);
        if (myURL.endsWith("/")) {
            myURL = PathUtil.removeTrailingSlash(myURL);
        }
        myRevision = revision;
        myConnection = connection;
        myIsCopy = isCopy;
    }

    public void setAdded(boolean added) {
        myIsAdded = added;
    }

    public boolean isAdded() {
        return myIsAdded;
    }
    
    public boolean isCopy() {
        return myIsCopy;
    }

    public String getURL() {
        return myURL;
    }   
    
    public String getPath() {
        return myPath;
    }
    
    public String getVersionURL() throws SVNException {
        // do fetch from server if empty...
        if (myVURL == null) {
            if (myMediator != null) {
                myVURL = myMediator.getWorkspaceProperty(myPath, "svn:wc:ra_dav:version-url");
                DebugLog.log("cached vURL for " + myPath + " : " + myVURL);
                if (myVURL != null) {
                    return myVURL;
                }
            }
            String path = myURL;
            if (myRevision >= 0) {
                // get baseline collection url for revision from public url.
                DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, path, myRevision, false, false, null);
                DebugLog.log("base line path: " + info.baselineBase + " + " + info.baselinePath);
                path = PathUtil.append(info.baselineBase, info.baselinePath);
            }
            // get "checked-in" property from baseline collection or from HEAD, this will be vURL.
            // this shouldn't be called for copied urls.
            myVURL = (String) DAVUtil.getPropertyValue(myConnection, path, null, DAVElement.CHECKED_IN);
        }
        return myVURL;
    }    

    public String getWorkingURL() {
        return myWURL;
    }
    
    public OutputStream addTextDelta() throws IOException {
        if (myDeltaFiles == null) {
            myDeltaFiles = new LinkedList();
        }
        // try to get from mediator.
        if (myMediator != null) {
            Object id = new Integer(myDeltaFiles.size());
            myDeltaFiles.add(id);
            return myMediator.createTemporaryLocation(PathUtil.decode(myPath), id);
        }
        File tempFile = File.createTempFile("svn", "temp");
        tempFile.deleteOnExit();
        myDeltaFiles.add(tempFile);
        return new BufferedOutputStream(new FileOutputStream(tempFile));
    }
    
    public int getDeltaCount() {
        return myDeltaFiles == null ? 0 : myDeltaFiles.size();        
    }
    
    public InputStream getTextDelta(int i) throws IOException {
        if (myMediator != null) {
            return myMediator.getTemporaryLocation(new Integer(i));
        }
        File file = (File) myDeltaFiles.get(i);
        return new BufferedInputStream(new FileInputStream(file));
    }
    
    public void dispose() {
        if (myDeltaFiles != null) {
            for(Iterator values = myDeltaFiles.iterator(); values.hasNext();) {
                if (myMediator != null) {
                    myMediator.deleteTemporaryLocation(values.next());
                    continue;
                }
                File file = (File) values.next();
                file.delete();
            }
        }
        myDeltaFiles = null;
        myProperties = null;
    }

    public void setWorkingURL(String location) {
        myWURL = location;
    }
    
    public void putProperty(String name, String value) {
        if (myProperties == null) {
            myProperties = new HashMap();
        }
        myProperties.put(name, value);       
    }
    
    public Map getProperties() {
        return myProperties;
    }
    
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append("[");
        sb.append(myURL);
        sb.append("][");
        sb.append(myVURL);
        sb.append("][");
        sb.append(myWURL);
        sb.append("][");
        sb.append(myPath);
        sb.append("]");
        return sb.toString();
    }

}
