/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.omg.CORBA.RepositoryIdHelper;
import org.tmatesoft.sqljet.core.table.SqlJetDb;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.wc.db.SVNEntryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNRepositoryInfo;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbKind;
import org.tmatesoft.svn.core.internal.wc.db.SVNWCDbStatus;
import org.tmatesoft.svn.core.internal.wc.db.SVNWorkingCopyDB17;
import org.tmatesoft.svn.core.wc.SVNConflictDescription;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea17 extends SVNAdminArea {
    private SVNWorkingCopyDB17 myWCDb;
    
    public SVNAdminArea17(File dir) {
        super(dir);
        myWCDb = new SVNWorkingCopyDB17();
    }

    public void addTreeConflict(SVNTreeConflictDescription conflict) throws SVNException {
    }

    public SVNAdminArea createVersionedDirectory(File dir, String url, String rootURL, String uuid, long revNumber, boolean createMyself, SVNDepth depth) throws SVNException {
        return null;
    }

    public SVNTreeConflictDescription deleteTreeConflict(String name) throws SVNException {
        return null;
    }

    protected Map fetchEntries() throws SVNException {
        File path = getRoot();
        
        SqlJetDb sdb = myWCDb.getDBTemp(path, false);
        List childNames = myWCDb.gatherChildren(path, false);
        childNames.add(getThisDirName());
        
        for (ListIterator iterator = childNames.listIterator(childNames.size()); iterator.hasPrevious();) {
            String name = (String) iterator.previous();
            Map entryAttributes = new HashMap();
            SVNEntry entry = new SVNEntry(entryAttributes, null, name);
            SVNEntryInfo info = myWCDb.readInfo(path, true, true, true, false, false, true, true, false, true, true, true, false);
            entry.setRevision(info.getRevision());
            entry.setRepositoryRoot(info.getReposURL());
            entry.setUUID(info.getUUID());
            entry.setCommittedRevision(info.getCommittedRevision());
            entry.setAuthor(info.getCommittedAuthor());
            entry.setTextTime(SVNDate.formatDate(info.getLastTextTime()));
            entry.setDepth(info.getDepth());
            entry.setChangelistName(info.getChangeList());
            entry.setCopyFromRevision(info.getCopyFromRevision());
            
            if (getThisDirName().equals(name)) {
                Map treeConflicts = null;
                Collection conflictVictims = myWCDb.readConflictVictims(path);
                for (Iterator conflictVictimsIter = conflictVictims.iterator(); conflictVictimsIter.hasNext();) {
                    String childName = (String) conflictVictimsIter.next();
                    File childFile = new File(path, childName);
                    Collection childConflicts = myWCDb.readConflicts(childFile);
                    for (Iterator childConflictsIter = childConflicts.iterator(); childConflictsIter.hasNext();) {
                        SVNConflictDescription conflict = (SVNConflictDescription) childConflictsIter.next();
                        if (conflict instanceof SVNTreeConflictDescription) {
                            SVNTreeConflictDescription treeConflict = (SVNTreeConflictDescription) conflict;
                            if (treeConflicts == null) {
                                treeConflicts = new HashMap();
                            }
                            treeConflicts.put(childName, treeConflict);
                        }
                    }
                }
                
                if (treeConflicts != null) {
                    entry.setTreeConflicts(treeConflicts);
                }
            }
            
            SVNWCDbStatus status = info.getWCDBStatus();
            SVNWCDbKind kind = info.getWCDBKind();
            String reposRelPath = info.getReposRelPath();
            if (status == SVNWCDbStatus.NORMAL || status == SVNWCDbStatus.INCOMPLETE) {
                boolean notPresent = false;
                if (kind == SVNWCDbKind.DIR) {
                    notPresent = myWCDb.checkIfIsNotPresent(sdb, 1, name);
                }
                if (notPresent) {
                    entry.setSchedule(null);
                    entry.setDeleted(true);
                } else {
                    entry.setSchedule(null);
                    if (reposRelPath == null) {
                        SVNRepositoryInfo reposInfo = myWCDb.scanBaseRepos(path);
                        entry.setRepositoryRoot(reposInfo.getRootURL());
                        entry.setUUID(reposInfo.getUUID());
                    }
                    entry.setIncomplete(true);
                }
            } else if (status == SVNWCDbStatus.DELETED || status == SVNWCDbStatus.OBSTRUCTED_DELETE) {
                entry.scheduleForDeletion();
                if (entry.isThisDir()) {
                    entry.setKeepLocal(myWCDb.determineKeepLocal(sdb, 1, entry.getName()));
                }
            }
        }
        return null;
    }

    protected SVNVersionedProperties formatBaseProperties(SVNProperties srcProperties) {
        return null;
    }

    protected SVNVersionedProperties formatProperties(SVNEntry entry, SVNProperties srcProperties) {
        return null;
    }

    public SVNVersionedProperties getBaseProperties(String name) throws SVNException {
        return null;
    }

    public int getFormatVersion() {
        return 0;
    }

    public SVNVersionedProperties getProperties(String name) throws SVNException {
        return null;
    }

    public SVNVersionedProperties getRevertProperties(String name) throws SVNException {
        return null;
    }

    public String getThisDirName() {
        return "";
    }

    public SVNTreeConflictDescription getTreeConflict(String name) throws SVNException {
        return null;
    }

    public SVNVersionedProperties getWCProperties(String name) throws SVNException {
        return null;
    }

    public void handleKillMe() throws SVNException {
    }

    public boolean hasPropModifications(String entryName) throws SVNException {
        return false;
    }

    public boolean hasProperties(String entryName) throws SVNException {
        return false;
    }

    public boolean hasTreeConflict(String name) throws SVNException {
        return false;
    }

    public void installProperties(String name, SVNProperties baseProps, SVNProperties workingProps, SVNLog log, boolean writeBaseProps, boolean close) throws SVNException {
    }

    protected boolean isEntryPropertyApplicable(String name) {
        return false;
    }

    public boolean isLocked() throws SVNException {
        return false;
    }

    public boolean isVersioned() {
        return false;
    }

    public boolean lock(boolean stealLock) throws SVNException {
        return false;
    }

    public void postCommit(String fileName, long revisionNumber, boolean implicit, SVNErrorCode errorCode) throws SVNException {
    }

    protected boolean readExtraOptions(BufferedReader reader, Map entryAttrs) throws SVNException, IOException {
        return false;
    }

    public void saveEntries(boolean close) throws SVNException {
    }

    public void saveVersionedProperties(SVNLog log, boolean close) throws SVNException {
    }

    public void saveWCProperties(boolean close) throws SVNException {
    }

    public void setFileExternalLocation(String name, SVNURL url, SVNRevision pegRevision, SVNRevision revision, SVNURL reposRootURL) throws SVNException {
    }

    public boolean unlock() throws SVNException {
        return false;
    }

    protected void writeEntries(Writer writer) throws IOException, SVNException {
    }

    protected int writeExtraOptions(Writer writer, String entryName, Map entryAttrs, int emptyFields) throws SVNException, IOException {
        return 0;
    }

}
