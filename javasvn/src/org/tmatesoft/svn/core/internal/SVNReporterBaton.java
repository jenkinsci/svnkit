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
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;


class SVNReporterBaton implements ISVNReporterBaton {
    
    private final ISVNEntry myRoot;
    private boolean myIsRecursive;
    private String myTarget;
    private Collection myExternals;

    public SVNReporterBaton(ISVNEntry root, String target, boolean recursive) {
        myRoot = root;
        myIsRecursive = recursive;
        myTarget = target;
    }

    protected void reportEntry(ISVNEntry entry, ISVNReporter reporter, String parentURL, long parentRevision) throws SVNException {
        long revision = SVNProperty.longValue(entry.getPropertyValue(SVNProperty.REVISION));
        if (revision < 0 && !entry.isMissing()) {
            return;
        }
        String path = getEntryPath(entry);
        if (SVNReporterBaton.isSwitched(parentURL, entry)) {
            DebugLog.log("REPORT.LINK: " + entry.getPropertyValue(SVNProperty.URL) + " : " + path + " : " + revision);
            String url = entry.getPropertyValue(SVNProperty.URL);
            url = PathUtil.decode(url);
            reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, revision, false); 
        } else if (entry.isMissing()) {
            reporter.deletePath(path);
            DebugLog.log("REPORT.MISSING: " + path);
        } else if (revision != parentRevision) {
            DebugLog.log("REPORT: " + path + " : " + revision);
            reporter.setPath(path, revision, false);
        }
    }

    public void report(ISVNReporter reporter) throws SVNException {
        if (myTarget != null) {
            ISVNEntry targetEntry = myRoot.asDirectory().getChild(myTarget);
            long revision = 0;
            if (targetEntry != null) {
                revision = SVNProperty.longValue(targetEntry.getPropertyValue(SVNProperty.REVISION));
            }
            DebugLog.log("REPORT.TARGET: " + myTarget + " : " + revision);
            reporter.setPath("", revision, revision <= 0);
        } else {
            doReport(myRoot, reporter, null, -1);
        }
        reporter.finishReport();
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
        return myExternals == null ? Collections.EMPTY_SET : myExternals;
    }
    
    public static boolean isSwitched(String parentURL, ISVNEntry entry) throws SVNException {
        if (parentURL == null) {
            return false;
        }
        String expectedURL = PathUtil.append(parentURL, PathUtil.encode(entry.getName()));
        return !expectedURL.equals(entry.getPropertyValue(SVNProperty.URL));
    }
} 