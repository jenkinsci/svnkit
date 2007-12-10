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
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.internal.io.fs.FSID;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public interface ISVNDBProcessor {
    
    public void openDB(File dbDir) throws SVNException;
    
    public void closeDB() throws SVNException;
    
    public long getMaxRevisionForPathFromMergeInfoChangedTable(String path, long upperRevision) throws SVNException;

    public Map parseMergeInfoFromDB(String path, long lastMergedRevision) throws SVNException;

    public Map getMergeInfoForChildren(String path, long revision, Map mergeInfo, ISVNMergeInfoFilter filter) throws SVNException;
        
    public void beginTransaction() throws SVNException;
    
    public void commitTransaction() throws SVNException;

    public void cleanUpFailedTransactionsInfo(long revision) throws SVNException;

    public void insertMergeInfo(long revision, String mergedFrom, String mergedTo, SVNMergeRange[] ranges) throws SVNException;
    
    public void updateMergeInfoChanges(long newRevision, String path) throws SVNException;

    public String getNodeOrigin(String nodeID) throws SVNException;

    public void setNodeOrigins(Map nodeOrigins) throws SVNException;

}
