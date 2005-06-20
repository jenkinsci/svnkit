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
package org.tmatesoft.svn.examples.wc;

import org.tmatesoft.svn.core.io.SVNLock;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;

public class StatusHandler implements ISVNStatusHandler {
    private boolean myIsRemote;

    public StatusHandler(boolean isRemote) {
        myIsRemote = isRemote;
    }

    public void handleStatus(SVNStatus status) {
        SVNStatusType contentsStatus = status.getContentsStatus();
        String pathChangeType = " ";
        boolean isAddedWithHistory = status.isCopied();
        if (contentsStatus == SVNStatusType.STATUS_MODIFIED) {
            pathChangeType = "M";
        } else if (contentsStatus == SVNStatusType.STATUS_CONFLICTED) {
            pathChangeType = "C";
        } else if (contentsStatus == SVNStatusType.STATUS_MERGED) {
            pathChangeType = "G";
        } else if (contentsStatus == SVNStatusType.STATUS_DELETED) {
            pathChangeType = "D";
        } else if (contentsStatus == SVNStatusType.STATUS_ADDED) {
            pathChangeType = "A";
        } else if (contentsStatus == SVNStatusType.STATUS_UNVERSIONED) {
            pathChangeType = "?";
        } else if (contentsStatus == SVNStatusType.STATUS_EXTERNAL) {
            pathChangeType = "X";
        } else if (contentsStatus == SVNStatusType.STATUS_IGNORED) {
            pathChangeType = "I";
        } else if (contentsStatus == SVNStatusType.STATUS_MISSING
                || contentsStatus == SVNStatusType.STATUS_INCOMPLETE) {
            pathChangeType = "!";
        } else if (contentsStatus == SVNStatusType.STATUS_OBSTRUCTED) {
            pathChangeType = "~";
        } else if (contentsStatus == SVNStatusType.STATUS_REPLACED) {
            pathChangeType = "R";
        } else if (contentsStatus == SVNStatusType.STATUS_NONE
                || contentsStatus == SVNStatusType.STATUS_NORMAL) {
            pathChangeType = " ";
        }

        SVNStatusType propertiesStatus = status.getPropertiesStatus();
        String propertiesChangeType = " ";
        if (propertiesStatus == SVNStatusType.STATUS_MODIFIED) {
            propertiesChangeType = "M";
        } else if (propertiesStatus == SVNStatusType.STATUS_CONFLICTED) {
            propertiesChangeType = "C";
        }

        boolean isLocked = status.isLocked();
        boolean isSwitched = status.isSwitched();
        SVNLock localLock = status.getLocalLock();
        SVNLock remoteLock = status.getRemoteLock();
        String lockLabel = " ";
        if (!myIsRemote) {
            if (localLock != null) {
                /*
                 * finds out if the file is locally locKed or not
                 */
                lockLabel = (localLock.getID() != null && !localLock.getID()
                        .equals("")) ? "K" : " ";
            }
        } else {
            if (localLock != null) {
                /*
                 * at first suppose the file is locally locKed as well as in the
                 * repository
                 */
                lockLabel = "K";
                if (remoteLock != null) {
                    /*
                     * author of the local lock differs from the author of the
                     * remote lock - the lock was sTolen!
                     */
                    if (!remoteLock.getOwner().equals(localLock.getOwner())) {
                        lockLabel = "T";
                    }
                } else {
                    /*
                     * the local lock presents but there's no lock in the
                     * repository - the lock was Broken.
                     */
                    lockLabel = "B";
                }
            } else if (remoteLock != null) {
                /*
                 * the file is not locally locked but locked in the repository -
                 * the lock token is in some Other working copy.
                 */
                lockLabel = "O";
            }
        }
        long workingRevision = status.getRevision().getNumber();
        long lastChangedRevision = status.getCommittedRevision().getNumber();
        String offset = "                    ";
        String[] offsets = new String[3];
        offsets[0] = offset.substring(0, 6 - String.valueOf(workingRevision)
                .length());
        offsets[1] = offset.substring(0, 6 - String
                .valueOf(lastChangedRevision).length());
        //status
        offsets[2] = offset.substring(0,
                offset.length()
                        - (status.getAuthor() != null ? status.getAuthor()
                                .length() : 1));
        /*
         * status is shown in the manner of the native Subversion command line
         * client's command "svn status"
         */
        System.out.println(pathChangeType
                + propertiesChangeType
                + (isLocked ? "L" : " ")
                + (isAddedWithHistory ? "+" : " ")
                + (isSwitched ? "S" : " ")
                + lockLabel
                + "  "
                + ((myIsRemote && pathChangeType.equals("M")) ? "*" : " ")
                + "  "
                + workingRevision
                + offsets[0]
                + (lastChangedRevision >= 0 ? String
                        .valueOf(lastChangedRevision) : "?") + offsets[1]
                + (status.getAuthor() != null ? status.getAuthor() : "?")
                + offsets[2] + status.getFile().getPath());
    }

}