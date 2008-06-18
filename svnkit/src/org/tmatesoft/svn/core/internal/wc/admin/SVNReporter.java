/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
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

import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNEventFactory;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.ISVNDebugLog;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNReporter implements ISVNReporterBaton {

    private SVNAdminAreaInfo myInfo;
    private SVNDepth myDepth;
    private boolean myIsRestore;
    private boolean myUseDepthCompatibilityTrick;
    private File myTarget;
    private ISVNDebugLog myLog;

    public SVNReporter(SVNAdminAreaInfo info, File file, boolean restoreFiles, 
            boolean useDepthCompatibilityTrick, SVNDepth depth, ISVNDebugLog log) {
        myInfo = info;
        myDepth = depth;
        myIsRestore = restoreFiles;
        myUseDepthCompatibilityTrick = useDepthCompatibilityTrick;
        myLog = log;
        myTarget = file;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNAdminArea targetArea = myInfo.getTarget();
            SVNWCAccess wcAccess = myInfo.getWCAccess();
            SVNEntry targetEntry = wcAccess.getEntry(myTarget, false);
            if (targetEntry == null || (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                SVNEntry parentEntry = wcAccess.getVersionedEntry(myTarget.getParentFile(), false);
                long revision = parentEntry.getRevision();
                if (myDepth == SVNDepth.UNKNOWN) {
                    myDepth = SVNDepth.INFINITY;
                }
                reporter.setPath("", null, revision, myDepth, targetEntry == null || targetEntry.isIncomplete());
                if (targetEntry == null || targetEntry.isIncomplete()) {
                    myInfo.addIncompleteEntry("");
                }
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            
            SVNEntry parentEntry = null;
            boolean startEmpty = targetEntry.isIncomplete();
            if (myUseDepthCompatibilityTrick && targetEntry.getDepth().compareTo(SVNDepth.IMMEDIATES) <= 0 &&
                    myDepth.compareTo(targetEntry.getDepth()) > 0) {
                startEmpty = true;
            }
            long revision = targetEntry.getRevision();
            if (!SVNRevision.isValidRevisionNumber(revision)) {
                parentEntry = wcAccess.getVersionedEntry(myTarget.getParentFile(), false);
                revision = parentEntry.getRevision();
            }
            reporter.setPath("", null, revision, targetEntry.getDepth(), startEmpty);
            if (startEmpty) {
                myInfo.addIncompleteEntry("");
            }
            boolean missing = false; 
            if (!targetEntry.isScheduledForDeletion()) {
                SVNFileType fileType = SVNFileType.getType(myTarget);
                missing = fileType == SVNFileType.NONE;
            }
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else if (myDepth != SVNDepth.EMPTY) {
                    reportEntries(reporter, targetArea, "", revision, startEmpty, myDepth);
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
                    reporter.linkPath(svnURL, "", targetEntry.getLockToken(), targetEntry.getRevision(), targetEntry.getDepth(), false);
                } else if (targetEntry.getRevision() != revision || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), targetEntry.getDepth(), false);
                }
            }
            reporter.finishReport();
        } catch (SVNException e) {
            try {
                reporter.abortReport();
            } catch (SVNException inner) {
                myLog.logInfo(inner);
            }
            throw e;
        } catch (Throwable th) {
            myLog.logInfo(th);
            try {
                reporter.abortReport();
            } catch (SVNException e) {
                myLog.logInfo(e);
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "WC report failed: {0}", th.getMessage());
            SVNErrorManager.error(err, th);
        }
    }

    private void reportEntries(ISVNReporter reporter, SVNAdminArea adminArea, String dirPath, long dirRevision, 
            boolean reportAll, SVNDepth depth) throws SVNException {
        SVNWCAccess wcAccess = myInfo.getWCAccess();
        String externalsProperty = adminArea.getProperties(adminArea.getThisDirName()).getStringPropertyValue(SVNProperty.EXTERNALS);
        SVNEntry thisEntry = adminArea.getEntry(adminArea.getThisDirName(), true);
        if (externalsProperty != null) {
            // use owners path as a key.
            String areaPath = adminArea.getRelativePath(myInfo.getAnchor());
            myInfo.addExternal(areaPath, externalsProperty, externalsProperty);
            myInfo.addDepth(areaPath, thisEntry.getDepth());
        }

        String parentURL = thisEntry.getURL();
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
            String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(entry.getName()));

            if (entry.isFile()) {
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    restoreFile(adminArea, entry.getName());
                }
                String url = entry.getURL();
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                    }
                } else if (!entry.isScheduledForAddition() && !entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, entry.getLockToken(), entry.getRevision(), entry.getDepth(), false);
                } else if (entry.getRevision() != dirRevision || 
                           entry.getLockToken() != null || 
                           thisEntry.getDepth() == SVNDepth.EMPTY) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), 
                                     entry.getDepth(), false);
                }
            } else if (entry.isDirectory() && (depth.compareTo(SVNDepth.FILES) > 0 || 
                                               depth == SVNDepth.UNKNOWN)) {
                if (missing) {
                    if (myIsRestore && entry.isScheduledForDeletion() || entry.isScheduledForReplacement()) {
                        // remove dir schedule if it is 'scheduled for deletion' but missing.
                        entry.setSchedule(null);
                        adminArea.saveEntries(false);
                    }
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    continue;
                }
                
                if (wcAccess.isMissing(adminArea.getFile(entry.getName()))) {
                    continue;
                }
                
                SVNAdminArea childArea = wcAccess.retrieve(adminArea.getFile(entry.getName()));
                SVNEntry childEntry = childArea.getEntry(childArea.getThisDirName(), true);
                String url = childEntry.getURL();
                boolean startEmpty = childEntry.isIncomplete();
                if (myUseDepthCompatibilityTrick && childEntry.getDepth().compareTo(SVNDepth.FILES) <= 0 && 
                        depth.compareTo(childEntry.getDepth()) > 0) {
                    startEmpty = true;
                    myInfo.addIncompleteEntry(path);
                }
                
                if (reportAll) {
                    if (!url.equals(expectedURL)) {
                        SVNURL svnURL = SVNURL.parseURIEncoded(url);
                        reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), 
                                childEntry.getDepth(), startEmpty);
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), 
                                childEntry.getDepth(), startEmpty);
                    }
                } else if (!url.equals(expectedURL)) {
                    SVNURL svnURL = SVNURL.parseURIEncoded(url);
                    reporter.linkPath(svnURL, path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.getDepth(), startEmpty);
                } else if (childEntry.getLockToken() != null || 
                           childEntry.getRevision() != dirRevision ||
                           childEntry.isIncomplete() ||
                           thisEntry.getDepth() == SVNDepth.EMPTY ||
                           thisEntry.getDepth() == SVNDepth.FILES ||
                           (thisEntry.getDepth() == SVNDepth.IMMEDIATES && 
                            childEntry.getDepth() != SVNDepth.EMPTY)) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.getDepth(), startEmpty);
                }

                if (depth == SVNDepth.INFINITY || depth == SVNDepth.UNKNOWN) {
                    reportEntries(reporter, childArea, path, childEntry.getRevision(), startEmpty, depth);
                }
            }
        }
    }
    
    private void restoreFile(SVNAdminArea adminArea, String name) throws SVNException {
        if (!myIsRestore) {
            return;
        }
        adminArea.restoreFile(name);
        SVNEntry entry = adminArea.getEntry(name, true);
        myInfo.getWCAccess().handleEvent(SVNEventFactory.createSVNEvent(adminArea.getFile(entry.getName()), entry.getKind(), null, entry.getRevision(), SVNEventAction.RESTORE, null, null, null));
    }    
}
