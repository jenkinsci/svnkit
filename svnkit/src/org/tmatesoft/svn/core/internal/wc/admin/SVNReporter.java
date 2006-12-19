/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.File;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNExternalInfo;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.util.ISVNDebugLog;

/**
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class SVNReporter implements ISVNReporterBaton {

    private SVNAdminAreaInfo myInfo;
    private boolean myIsRecursive;
    private boolean myIsRestore;
    private File myTarget;
    private ISVNDebugLog myLog;

    public SVNReporter(SVNAdminAreaInfo info, File file, boolean restoreFiles, boolean recursive, ISVNDebugLog log) {
        myInfo = info;
        myIsRecursive = recursive;
        myIsRestore = restoreFiles;
        myLog = log;
        myTarget = file;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNAdminArea targetArea = myInfo.getTarget();
            SVNWCAccess wcAccess = myInfo.getWCAccess();
            SVNEntry targetEntry = wcAccess.getEntry(myTarget, false);
            if (targetEntry == null || (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                SVNEntry parentEntry = wcAccess.getEntry(myTarget.getParentFile(), false);
                long revision = parentEntry.getRevision();
                reporter.setPath("", null, revision, targetEntry != null ? targetEntry.isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            
            SVNEntry parentEntry = null;
            long revision = targetEntry.getRevision();
            if (revision < 0) {
                 parentEntry = wcAccess.getEntry(myTarget.getParentFile(), false);
                if (parentEntry == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNVERSIONED_RESOURCE, "''{0}'' is not under version control", myTarget.getParentFile());
                    SVNErrorManager.error(err);
                }
                revision = parentEntry.getRevision();
            }
            reporter.setPath("", null, revision, targetEntry.isIncomplete());
            
            SVNFileType fileType = SVNFileType.getType(myTarget);
            boolean missing = !targetEntry.isScheduledForDeletion() && fileType == SVNFileType.NONE;
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    reportEntries(reporter, targetArea, "", revision, targetEntry.isIncomplete(), myIsRecursive);
                }
            } else if (targetEntry.isFile()) {
                if (missing) {
                    restoreFile(targetArea, targetEntry.getName());
                }
                // report either linked path or entry path
                parentEntry = parentEntry == null ? wcAccess.getEntry(myTarget.getParentFile(), false) : parentEntry;
                String url = targetEntry.getURL();
                String parentURL = parentEntry.getURL();
                String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(targetEntry.getName()));
                if (parentEntry != null && !expectedURL.equals(url)) {
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, "", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                } else if (targetEntry.getRevision() != revision || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                }
            }
            reporter.finishReport();
        } catch (SVNException e) {
            try {
                reporter.abortReport();
            } catch (SVNException inner) {
                myLog.info(inner);
                SVNErrorMessage err = e.getErrorMessage().wrap("Error aborting report");
                SVNErrorManager.error(err, e);
            }
            throw e;
        } catch (Throwable th) {
            myLog.info(th);
            try {
                reporter.abortReport();
            } catch (SVNException e) {
                myLog.info(e);
                SVNErrorMessage err = e.getErrorMessage().wrap("Error aborting report");
                SVNErrorManager.error(err, th);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "WC report failed: {0}", th.getMessage());
            SVNErrorManager.error(err, th);
        }
    }

    private void reportEntries(ISVNReporter reporter, SVNAdminArea adminArea, String dirPath, long dirRevision, boolean reportAll, boolean recursive) throws SVNException {
        SVNWCAccess wcAccess = myInfo.getWCAccess();
        SVNExternalInfo[] externals = myInfo.addExternals(adminArea, adminArea.getProperties(adminArea.getThisDirName()).getPropertyValue(SVNProperty.EXTERNALS));
        for (int i = 0; externals != null && i < externals.length; i++) {
            externals[i].setOldExternal(externals[i].getNewURL(), externals[i].getNewRevision());
        }

        for (Iterator e = adminArea.entries(true); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if (adminArea.getThisDirName().equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : SVNPathUtil.append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                if (!reportAll) {
                    reporter.deletePath(path);
                }
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }
            File file = adminArea.getFile(entry.getName());
            SVNFileType fileType = SVNFileType.getType(file);
            boolean missing = fileType == SVNFileType.NONE;
            String parentURL = adminArea.getEntry(adminArea.getThisDirName(), true).getURL();
            String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(entry.getName()));

            if (entry.isFile()) {
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    restoreFile(adminArea, entry.getName());
                }
                String url = entry.getURL();
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                    }
                } else if (!entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), false);
                } else if (entry.getRevision() != dirRevision || entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                }
            } else if (entry.isDirectory() && recursive) {
                if (missing) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    continue;
                }
                SVNAdminArea childArea = wcAccess.retrieve(adminArea.getFile(entry.getName()));
                SVNEntry childEntry = childArea.getEntry(childArea.getThisDirName(), true);
                String url = childEntry.getURL();
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    }
                } else if (!url.equals(expectedURL)) {
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                } else if (childEntry.getLockToken() != null || childEntry.getRevision() != dirRevision || childEntry.isIncomplete()) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                }
                reportEntries(reporter, childArea, path, childEntry.getRevision(), childEntry.isIncomplete(), recursive);
            }
        }
    }
    
    private void restoreFile(SVNAdminArea adminArea, String name) throws SVNException {
        if (!myIsRestore) {
            return;
        }
        adminArea.restoreFile(name);
        SVNEntry entry = adminArea.getEntry(name, true);
        myInfo.getWCAccess().handleEvent(SVNEventFactory.createRestoredEvent(myInfo, adminArea, entry));
    }
    
}
