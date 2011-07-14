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
package org.tmatesoft.svn.cli.svn;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.SVNTreeConflictUtil;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17;
import org.tmatesoft.svn.core.internal.wc17.SVNStatus17.ConflictInfo;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNTreeConflictDescription;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusPrinter {

    private SVNCommandEnvironment myEnvironment;

    public SVNStatusPrinter(SVNCommandEnvironment env) {
        myEnvironment = env;
    }

    public void printStatus(String path, SVNStatus status,
            boolean detailed, boolean showLastCommitted, boolean skipUnrecognized, boolean showReposLocks) throws SVNException {
        if (status == null || (skipUnrecognized && !(status.isVersioned() || status.getTreeConflict() != null)) ||
                (status.getContentsStatus() == SVNStatusType.STATUS_NONE && status.getRemoteContentsStatus() == SVNStatusType.STATUS_NONE)) {
            return;
        }

        char treeStatusCode = ' ';
        String treeDescriptionLine = "";
        if (status.getStatus17() != null) {
            if (status.isConflicted()) {
                ConflictInfo conflictedInfo = new ConflictInfo();
                boolean hasTreeConflict = false;
                if (status.isVersioned()) {
                    try {
                        conflictedInfo = status.getStatus17().getConflictInfo();
                    } catch (SVNException e) {
                        if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_UPGRADE_REQUIRED) {
                            conflictedInfo = new ConflictInfo();
                        } else {
                            throw e;
                        }
                    }
                    hasTreeConflict = conflictedInfo != null && conflictedInfo.treeConflicted;
                } else {
                    hasTreeConflict = true;
                }                
                if (hasTreeConflict) {
                    SVNTreeConflictDescription treeConflictDescription = status.getStatus17().getTreeConflict();
                    String description = SVNTreeConflictUtil.getHumanReadableConflictDescription(treeConflictDescription);
                    treeStatusCode = 'C';
                    treeDescriptionLine = "\n      >   " + description;
                }
            }
        } else {
            if (status.getTreeConflict() != null) {
                String description = SVNTreeConflictUtil.getHumanReadableConflictDescription(status.getTreeConflict());
                treeStatusCode = 'C';
                treeDescriptionLine = "\n      >   " + description;
            }
        }

        StringBuffer result = new StringBuffer();
        if (detailed) {
            String wcRevision;
            char remoteStatus;
            if (!status.isVersioned()) {
                wcRevision = "";
            } else if (status.isCopied()) {
                wcRevision = "-";
            } else if (!status.getRevision().isValid()) {
                if(status.getStatus17()!=null) {
                    SVNStatus17 status17 = status.getStatus17();
                    if(status17.getNodeStatus()==SVNStatusType.STATUS_DELETED)
                        wcRevision = Long.toString(status17.getChangedRev());
                    else
                        wcRevision = "-";
                } else
                    wcRevision = " ? ";
            } else {
                wcRevision = Long.toString(status.getRevision().getNumber());
            }
            if (status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE || status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
                remoteStatus = '*';
            } else {
                remoteStatus = ' ';
            }
            char lockStatus;
            if (showReposLocks) {
                if (status.getRemoteLock() != null) {
                    if (status.getLocalLock() != null) {
                        lockStatus = status.getLocalLock().getID().equals(status.getRemoteLock().getID()) ? 'K' : 'T';
                    } else {
                        lockStatus = 'O';
                    }
                } else if (status.getLocalLock() != null) {
                    lockStatus = 'B';
                } else {
                    lockStatus = ' ';
                }
            } else {
                lockStatus = status.getLocalLock() != null ? 'K' : ' ';
            }
            if (showLastCommitted) {
                String commitRevision = "";
                String commitAuthor = "";

                if (status.isVersioned() && status.getCommittedRevision().isValid()) {
                    commitRevision = status.getCommittedRevision().toString();
                } else if (status.isVersioned()) {
                    commitRevision = " ? ";
                }
                if (status.isVersioned() && status.getAuthor() != null) {
                    commitAuthor = status.getAuthor();
                } else if (status.isVersioned()) {
                    commitAuthor = " ? ";
                }
                result.append(combineStatus(status).getCode());
                result.append(status.getPropertiesStatus().getCode());
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(getSwitchCharacter(status));
                result.append(lockStatus);
                result.append(treeStatusCode); // tree status
                result.append(" ");
                result.append(remoteStatus);
                result.append("   ");
                result.append(SVNFormatUtil.formatString(wcRevision, 6, false, false)); // 6 chars
                result.append("   ");
                result.append(SVNFormatUtil.formatString(commitRevision, 6, false, false)); // 6 chars
                result.append(" ");
                result.append(SVNFormatUtil.formatString(commitAuthor, 12, true)); // 12 chars
                result.append(" ");
                result.append(path);
                result.append(treeDescriptionLine);
            }  else {
                result.append(combineStatus(status).getCode());
                result.append(status.getPropertiesStatus().getCode());
                result.append(status.isLocked() ? 'L' : ' ');
                result.append(status.isCopied() ? '+' : ' ');
                result.append(getSwitchCharacter(status));
                result.append(lockStatus);
                result.append(treeStatusCode); // tree status
                result.append(" ");
                result.append(remoteStatus);
                result.append("   ");
                result.append(SVNFormatUtil.formatString(wcRevision, 6, false, false)); // 6 chars
                result.append("   ");
                result.append(path);
                result.append(treeDescriptionLine);
            }
        } else {
            result.append(combineStatus(status).getCode());
            result.append(status.getPropertiesStatus().getCode());
            result.append(status.isLocked() ? 'L' : ' ');
            result.append(status.isCopied() ? '+' : ' ');
            result.append(getSwitchCharacter(status));
            result.append(status.getLocalLock() != null ? 'K' : ' ');
            result.append(treeStatusCode); // tree status
            result.append(" ");
            result.append(path);
            result.append(treeDescriptionLine);
        }
        myEnvironment.getOut().println(result);
    }
    
    private static SVNStatusType combineStatus(SVNStatus status) {
        if (status.getStatus17() == null) {
            return status.getContentsStatus();
        }
        if (status.getStatus17().getNodeStatus() == SVNStatusType.STATUS_CONFLICTED) {
            if (!status.isVersioned() && status.isConflicted()) {
                return SVNStatusType.STATUS_MISSING;
            }
        }
        if (status.getStatus17().getNodeStatus() == SVNStatusType.STATUS_MODIFIED) {
            return status.getStatus17().getTextStatus();
        }
        return status.getStatus17().getNodeStatus();
    }

    private static char getSwitchCharacter(SVNStatus status) {
        if (status == null) {
            return ' ';
        }
        return status.isSwitched() ? 'S' : (status.isFileExternal() ? 'X' : ' ');
    }
}
