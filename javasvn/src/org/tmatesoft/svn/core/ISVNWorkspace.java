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

package org.tmatesoft.svn.core;

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.io.ISVNCredentialsProvider;
import org.tmatesoft.svn.core.io.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNWorkspace {
    
    public long HEAD = -2;
    
    /* workspace settings */
    
    public String getID();
    
    public SVNRepositoryLocation getLocation() throws SVNException ;
    
    public void setAutoProperties(Map properties);
    
    public Map getAutoProperties();

    public void setGlobalIgnore(String ignore);

    public String getGlobalIgnore();
    
    public ISVNWorkspace getRootWorkspace(boolean stopOnExternals, boolean stopOnSwitch);
    
    public void addWorkspaceListener(ISVNWorkspaceListener listener);
    
    public void removeWorkspaceListener(ISVNWorkspaceListener listener);

    public void setCredentials(String userName, String password);
    
    public void setCredentials(ISVNCredentialsProvider provider);

    public ISVNCredentialsProvider getCredentialsProvider();
    
    public void setExternalsHandler(ISVNExternalsHandler handler);

        /* repository sync. operations */
    
    public long checkout(SVNRepositoryLocation location, long revision, boolean export) throws SVNException;

    public long checkout(SVNRepositoryLocation location, long revision, boolean export, boolean recurse) throws SVNException;
    
    public long commit(SVNRepositoryLocation target, String message) throws SVNException;
    
    public long update(String path, long revision, boolean recursive) throws SVNException;

    public long update(long revision) throws SVNException;
    
    public long update(SVNRepositoryLocation url, String path, long revision, boolean recursive) throws SVNException;

    public long commit(String paths[], ISVNCommitHandler handler, boolean recursive) throws SVNException;

    public long commit(String paths[], String message, boolean recursive) throws SVNException;

    public long commit(String path, String message, boolean recursive) throws SVNException;

    public long commit(String message) throws SVNException;
    
    /* RO operations */
    
    public long status(String path, boolean remote, ISVNStatusHandler handler, boolean descend, 
            boolean includeUnmodified, boolean includeIgnored) throws SVNException;

    public long status(String path, boolean remote, ISVNStatusHandler handler, boolean descend, 
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException;

    public SVNStatus status(String path, boolean remote) throws SVNException;
    
    public void log(String path, long revisionStart, long revisionEnd, boolean stopOnCopy,  boolean discoverPath, 
            ISVNLogEntryHandler handler) throws SVNException;
    
    /* working copy related operations */
    
    public void refresh() throws SVNException;
    
    public SVNRepositoryLocation getLocation(String path) throws SVNException;
    
    public void add(String path, boolean mkdir, boolean recurse) throws SVNException;

    public void delete(String path) throws SVNException;

    public void copy(String source, String destination, boolean move) throws SVNException;
    
    public void markResolved(String path, boolean recursive) throws SVNException;

    public void revert(String path, boolean recursive) throws SVNException;
    
    public void relocate(SVNRepositoryLocation newLocation, String path, boolean recursive) throws SVNException;

    public Iterator propertyNames(String path) throws SVNException;
    
    public String getPropertyValue(String path, String name) throws SVNException;
    
    public void setPropertyValue(String path, String name, String value) throws SVNException;

    public void setPropertyValue(String path, String name, String value, boolean recurse) throws SVNException;
    
    public Map getProperties(String path, boolean reposProps, boolean entryProps) throws SVNException;

    public ISVNFileContent getFileContent(String path) throws SVNException;
}
