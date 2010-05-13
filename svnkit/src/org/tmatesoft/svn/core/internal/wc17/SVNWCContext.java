/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;


/**
 * @version 1.4
 * @author  TMate Software Ltd.
 */
public class SVNWCContext {
    
    private SVNWorkingCopyDB17 db;
    private boolean closeDb;
    
    public void close() throws SVNException
    {
      if (closeDb)
        {
          db.closeDB();
        }
    }

    
    public SVNWCContext() {
        this.db = new SVNWorkingCopyDB17();
        this.closeDb = true;
    }

    public SVNWCContext(SVNWorkingCopyDB17 db) {
        this.db = db;
        this.closeDb = false;
    }


    public SVNNodeKind getNodeKind(String absPath, boolean showHidden) throws SVNException {
        return null;
    }


    public SVNURL getUrlFromPath(String dirAbsPath) {
        return null;
    }


    public boolean isNodeAdded(String dirAbsPath) {
        return false;
    }


    public boolean isNodeReplaced(String dirAbsPath) {
        return false;
    }


    public long getRevisionNumber(SVNRevision revision, SVNRepository repository, File path) {
        return 0;
    }


    public void checkCancelled() {
    }


    public List getChildNodes(String localAbsPath) {
        return null;
    }


    public SVNEntryInfo getEntry(String localAbsPath, boolean allow_unversioned, SVNNodeKind kind, 
            boolean need_parent_stub) throws SVNException {
        return null;
    }


    public List readConfilctVictims(String localAbsPath) {
        return null;
    }


    public SVNConflictDescription readTreeConflict(String localAbsPath) {
        return null;
    }


    public boolean isNodeHidden(String localAbsPath) {
        return false;
    }


    public List collectIgnorePatterns(String localAbsPath, Collection ignorePatterns) {
        return null;
    }


    public boolean isAdminDirectory(String name) {
            return name != null && (SVNFileUtil.isWindows) ?
                    SVNFileUtil.getAdminDirectoryName().equalsIgnoreCase(name) :
                    SVNFileUtil.getAdminDirectoryName().equals(name);
    }

}
