/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd. All rights reserved.
 * 
 * This software is licensed as described in the file COPYING, which you should
 * have received as part of this distribution. The terms are also available at
 * http://tmate.org/svn/license.html. If newer versions of this license are
 * posted there, you may use a newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.util.Date;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNReporter implements ISVNReporterBaton {

    private SVNWCAccess myWCAccess;

    private boolean myIsRecursive;

    private boolean myIsRestore;

    public SVNReporter(SVNWCAccess wcAccess, boolean restoreFiles,
            boolean recursive) {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
        myIsRestore = restoreFiles;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNEntries targetEntries = myWCAccess.getTarget().getEntries();
            SVNEntries anchorEntries = myWCAccess.getAnchor().getEntries();
            SVNEntry targetEntry = anchorEntries.getEntry(myWCAccess
                    .getTargetName(), true);

            if (targetEntry == null
                    || targetEntry.isHidden()
                    || (targetEntry.isDirectory() && targetEntry
                            .isScheduledForAddition())) {
                long revision = anchorEntries.getEntry("", true).getRevision();
                reporter
                        .setPath("", null, revision,
                                targetEntry != null ? targetEntry
                                        .isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            long revision = targetEntry.isFile() ? targetEntry.getRevision()
                    : targetEntries.getEntry("", true).getRevision();
            if (revision < 0) {
                revision = anchorEntries.getEntry("", true).getRevision();
            }
            reporter.setPath("", null, revision, targetEntry.isIncomplete());
            boolean missing = !targetEntry.isScheduledForDeletion() &&  
                !myWCAccess.getAnchor().getFile(myWCAccess.getTargetName()).exists();
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    reportEntries(reporter, myWCAccess.getTarget(), "",
                            targetEntry.isIncomplete(), myIsRecursive);
                }
            } else if (targetEntry.isFile()) {
                if (missing) {
                    restoreFile(myWCAccess.getAnchor(), targetEntry.getName());
                }
                // report either linked path or entry path
                String url = targetEntry.getURL();
                SVNEntry parentEntry = targetEntries.getEntry("", true);
                String parentURL = parentEntry.getURL();
                String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(targetEntry.getName()));
                if (!expectedURL.equals(url)) {
                    reporter.linkPath(url, "",
                            targetEntry.getLockToken(), targetEntry
                                    .getRevision(), false);
                } else if (targetEntry.getRevision() != parentEntry
                        .getRevision()
                        || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(),
                            targetEntry.getRevision(), false);
                }
            }
            reporter.finishReport();
        } catch (Throwable th) {
            DebugLog.error(th);
            try {
                reporter.abortReport();
            } catch (SVNException e) {
                DebugLog.error(e);
            }
            if (th instanceof SVNException) {
                throw (SVNException) th;
            }
            SVNErrorManager
                    .error("svn: Working copy state was not reported properly: "
                            + th.getMessage());
        }
    }

    private void reportEntries(ISVNReporter reporter, SVNDirectory directory,
            String dirPath, boolean reportAll, boolean recursive)
            throws SVNException {
        SVNEntries entries = directory.getEntries();
        long baseRevision = entries.getEntry("", true).getRevision();

        SVNExternalInfo[] externals = myWCAccess.addExternals(directory,
                directory.getProperties("", false).getPropertyValue(
                        SVNProperty.EXTERNALS));
        for (int i = 0; externals != null && i < externals.length; i++) {
            externals[i].setOldExternal(externals[i].getNewURL(), externals[i]
                    .getNewRevision());
        }

        for (Iterator e = entries.entries(true); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : SVNPathUtil
                    .append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                if (!reportAll) {
                    reporter.deletePath(path);
                }
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }
            File file = directory.getFile(entry.getName());
            boolean missing = !file.exists();
            if (entry.isFile()) {
                if (!reportAll) {
                    // check svn:special files -> symlinks that could be
                    // directory.
                    boolean special = SVNFileUtil.isWindows
                            && directory.getProperties(entry.getName(), false)
                                    .getPropertyValue(SVNProperty.SPECIAL) != null;

                    if ((special && !file.exists())
                            || (!special && file.isDirectory())) {
                        reporter.deletePath(path);
                        continue;
                    }
                }
                if (missing && !entry.isScheduledForDeletion()
                        && !entry.isScheduledForReplacement()) {
                    restoreFile(directory, entry.getName());
                }
                String url = entry.getURL();
                String parentURL = entries
                        .getPropertyValue("", SVNProperty.URL);
                String expectedURL = SVNPathUtil.append(parentURL, SVNEncodingUtil.uriEncode(entry.getName()));
                if (reportAll) {
                    if (!url.equals(expectedURL)
                            && !entry.isScheduledForAddition()
                            && !entry.isScheduledForReplacement()) {
                        reporter.linkPath(url,
                                path, entry.getLockToken(),
                                entry.getRevision(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry
                                .getRevision(), false);
                    }
                } else if (!entry.isScheduledForReplacement()
                        && !url.equals(expectedURL)) {
                    // link path
                    reporter.linkPath(url,
                            path, entry.getLockToken(), entry.getRevision(),
                            false);
                } else if (entry.getRevision() != baseRevision
                        || entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry
                            .getRevision(), false);
                }
            } else if (entry.isDirectory() && recursive) {
                if (missing
                        || directory.getChildDirectory(entry.getName()) == null) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    return;
                }
                if (file.isFile()) {
                    SVNErrorManager.error("svn: Cannot report information on directory '" + file + "': entry is obstructed with node of another type");
                }
                SVNDirectory childDir = directory.getChildDirectory(entry
                        .getName());
                SVNEntry childEntry = childDir.getEntries().getEntry("", true);
                String url = childEntry.getURL();
                if (reportAll) {
                    if (!url.equals(entry.getURL())) {
                        reporter.linkPath(url,
                                path, childEntry.getLockToken(), childEntry
                                        .getRevision(), childEntry
                                        .isIncomplete());
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(),
                                childEntry.getRevision(), childEntry
                                        .isIncomplete());
                    }
                } else if (!url.equals(entry.getURL())) {
                    reporter.linkPath(url,
                            path, childEntry.getLockToken(), childEntry
                                    .getRevision(), childEntry.isIncomplete());
                } else if (childEntry.getLockToken() != null
                        || childEntry.getRevision() != baseRevision) {
                    reporter
                            .setPath(path, childEntry.getLockToken(),
                                    childEntry.getRevision(), childEntry
                                            .isIncomplete());
                }
                reportEntries(reporter, childDir, path, childEntry
                        .isIncomplete(), recursive);
            }
        }
    }

    private void restoreFile(SVNDirectory dir, String name) throws SVNException {
        if (!myIsRestore) {
            return;
        }
        SVNProperties props = dir.getProperties(name, false);
        SVNEntry entry = dir.getEntries().getEntry(name, true);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;

        File src = dir.getBaseFile(name, false);
        File dst = dir.getFile(name);
        SVNTranslator.translate(dir, name, SVNFileUtil.getBasePath(src),
                SVNFileUtil.getBasePath(dst), true, true);
        dir.markResolved(name, true, false);

        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
        if (executable) {
            SVNFileUtil.setExecutable(dst, true);
        }
        if (needsLock) {
            SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
        }
        long tstamp = dst.lastModified();
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = SVNTimeUtil.parseDate(entry.getCommittedDate()).getTime();
            dst.setLastModified(tstamp);
        } else {
            entry.setTextTime(SVNTimeUtil.formatDate(new Date(tstamp)));
        }
        dir.getEntries().save(false);

        myWCAccess.handleEvent(SVNEventFactory.createRestoredEvent(myWCAccess,
                dir, entry));
    }
}
