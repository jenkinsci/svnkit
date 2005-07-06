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

package org.tmatesoft.svn.core.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNWorkspace;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */

class SVNReporterBaton implements ISVNReporterBaton {
    
    private final ISVNEntry myRoot;
    private boolean myIsRecursive;
    private String myTarget;
    private Collection myExternals;
    private Collection myMissingEntries;
    private ISVNWorkspace myWorkspace;

    public SVNReporterBaton(ISVNWorkspace workspace, ISVNEntry root, String target, boolean recursive) {
        myRoot = root;
        myWorkspace = workspace;
        myIsRecursive = recursive;
        myTarget = target;
        myMissingEntries = new HashSet();
		myExternals = new HashSet();
    }

    protected void reportEntry(ISVNEntry entry, ISVNReporter reporter, String parentURL, long parentRevision) throws SVNException {
        long revision = SVNProperty.longValue(entry.getPropertyValue(SVNProperty.REVISION));
        String locktoken = entry.getPropertyValue(SVNProperty.LOCK_TOKEN);
        if (revision < 0 && !entry.isMissing()) {
            return;
        }
        String path = getEntryPath(entry);
        if (SVNReporterBaton.isSwitched(parentURL, entry)) {
            DebugLog.log("REPORT.LINK: " + entry.getPropertyValue(SVNProperty.URL) + " : " + path + " : " + revision);
            String url = entry.getPropertyValue(SVNProperty.URL);
            url = PathUtil.decode(url);
            reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, locktoken, revision, false); 
        } else if (entry.isMissing()) {
            if (myWorkspace != null && !entry.isDirectory()) {
                entry.asFile().restoreContents();
                entry.asFile().markResolved(true);
                ((SVNWorkspace) myWorkspace).fireEntryModified(entry, SVNStatus.RESTORED, false);
                if (revision != parentRevision) {
                    reporter.setPath(path, locktoken, revision, false);
                }
            } else {
                DebugLog.log("REPORT.MISSING: " + path);
                reporter.deletePath(path);
                myMissingEntries.add(entry);
            }
        } else if (revision != parentRevision || locktoken != null) {
            DebugLog.log("REPORT: " + path + " : " + revision);
            reporter.setPath(path, locktoken, revision, false);
        }
    }

    public void report(ISVNReporter reporter) throws SVNException {
        if (myTarget != null) {
            ISVNEntry targetEntry = myRoot.asDirectory().getChild(myTarget);
            long revision = 0;
            String locktoken = null;
            if (targetEntry != null) {
                revision = SVNProperty.longValue(targetEntry.getPropertyValue(SVNProperty.REVISION));
                locktoken = targetEntry.getPropertyValue(SVNProperty.LOCK_TOKEN);
            }
            if (revision >= 0) {
                if (myWorkspace != null && targetEntry != null && targetEntry.isMissing() && !targetEntry.isDirectory()) {
                    // restore before reporting
                    targetEntry.asFile().restoreContents();
                    targetEntry.asFile().markResolved(true);
                    ((SVNWorkspace) myWorkspace).fireEntryModified(targetEntry, SVNStatus.RESTORED, false);
                }
                String parentURL = myRoot.getPropertyValue(SVNProperty.URL);
                DebugLog.log("REPORT.TARGET: " + myTarget + " : " + revision);
                reporter.setPath("", locktoken, revision, revision == 0);
                if (SVNReporterBaton.isSwitched(parentURL, targetEntry)) {
                    DebugLog.log("REPORT.TARGET.LINK: " + targetEntry.getPropertyValue(SVNProperty.URL) + " : " + revision);
                    String url = targetEntry.getPropertyValue(SVNProperty.URL);
                    url = PathUtil.decode(url);
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), "", locktoken, revision, false); 
                } 
            } else {
                revision = SVNProperty.longValue(myRoot.getPropertyValue(SVNProperty.REVISION)); 
                DebugLog.log("REPORT.MISSING.TARGET: " + myTarget + " : " + revision);
                reporter.setPath("", locktoken, revision, false);
                reporter.deletePath("");
                if (targetEntry != null) {
                    myMissingEntries.add(targetEntry);
                }
            }
        } else {
            doReport(myRoot, reporter, null, -1);
        }
        reporter.finishReport();
    }
    
    public Iterator missingEntries() {
        return myMissingEntries.iterator();
    }

    private void doReport(ISVNEntry r, ISVNReporter reporter, String parentURL, long parentRevision) throws SVNException {
        long revision = SVNProperty.longValue(r.getPropertyValue(SVNProperty.REVISION));
        if (revision < 0 && !r.isMissing()) {
            return;
        }
        if (r.isScheduledForAddition()) {
            return;
        }
        reportEntry(r, reporter, parentURL, parentRevision);
        if (r.isDirectory()) {
            myExternals = SVNExternal.create(r.asDirectory(), myExternals);
        }
        if (r.isDirectory()) {
            ISVNDirectoryEntry dir = r.asDirectory();
            String target = myTarget;
            myTarget = null;
            parentURL = dir.getPropertyValue(SVNProperty.URL);
            for(Iterator entries = dir.childEntries(); entries.hasNext();) {
                ISVNEntry child = (ISVNEntry) entries.next();
                if (target != null && !target.equals(child.getName())) {
                    continue;
                }
                if (myIsRecursive) {
                    doReport(child, reporter,parentURL, revision);
                } else if (!child.isScheduledForAddition()) {
                    // just report this entry
                    reportEntry(child, reporter, parentURL, revision);
                }
                if (target != null) {
                    return;
                }
            }
            // deleted entries.
            for(Iterator deleted = dir.deletedEntries(); deleted.hasNext();) {
                Map deletedEntry = (Map) deleted.next();
                String name = (String) deletedEntry.get(SVNProperty.NAME);
                if (target != null && !target.equals(name)) {
                    continue;
                }
                String path = PathUtil.append(dir.getPath(), name);
                path = path.substring(myRoot.getPath().length());
                path = PathUtil.removeLeadingSlash(path);
                DebugLog.log("REPORT.DELETE: " + path);
                reporter.deletePath(path);
            }
        }
    }
    
    protected String getEntryPath(ISVNEntry entry) {
        String path = entry.getPath();
        path = path.substring(myRoot.getPath().length());
        return PathUtil.removeLeadingSlash(path);
    }
    
    public Collection getExternals() {
        return myExternals;
    }
    
    public static boolean isSwitched(String parentURL, ISVNEntry entry) throws SVNException {
        if (parentURL == null || entry == null || entry.getPropertyValue(SVNProperty.URL) == null ||
                entry.isObstructed()) {
            return false;
        }
        String expectedURL = PathUtil.append(parentURL, PathUtil.encode(entry.getName()));
        return !expectedURL.equals(entry.getPropertyValue(SVNProperty.URL));
    }
} 