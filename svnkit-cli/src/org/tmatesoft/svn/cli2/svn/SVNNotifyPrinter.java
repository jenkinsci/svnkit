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
package org.tmatesoft.svn.cli2.svn;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNNotifyPrinter implements ISVNEventHandler {
    
    private SVNCommandEnvironment myEnvironment;
    
    private boolean myIsInExternal;
    private boolean myIsChangesReceived;
    private boolean myIsDeltaSent;

    private boolean myIsCheckout;
    private boolean myIsExport;
    private boolean myIsSuppressLastLine;

    public SVNNotifyPrinter(SVNCommandEnvironment env) {
        this(env, false, false, false);
    }

    public SVNNotifyPrinter(SVNCommandEnvironment env, boolean isCheckout, boolean isExport, boolean suppressLastLine) {
        myEnvironment = env;
        myIsCheckout = isCheckout;
        myIsExport = isExport;
        myIsSuppressLastLine = suppressLastLine;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        File file = event.getFile();
        String path = null;
        if (file != null) {
            path = myEnvironment.getRelativePath(file);
            path = SVNCommandUtil.getLocalPath(path);
        } 
        PrintStream out = myEnvironment.getOut();
        StringBuffer buffer = new StringBuffer();
        if (event.getAction() == SVNEventAction.STATUS_EXTERNAL) {
            buffer.append("\nPerforming status on external item at '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
            String revStr = Long.toString(event.getRevision());
            buffer.append("Status against revision: " + SVNFormatUtil.formatString(revStr, 6, false) + "\n");
        } else if (event.getAction() == SVNEventAction.SKIP) {
            if (event.getErrorMessage() != null && event.getExpectedAction() == SVNEventAction.UPDATE_EXTERNAL) {
                // hack to let external test #14 work.
                myEnvironment.getErr().println(event.getErrorMessage());
            }
            if (event.getContentsStatus() == SVNStatusType.MISSING) {
                buffer.append("Skipped missing target: '" + path + "'\n");
            } else {
                buffer.append("Skipped '" + path + "'\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
            myIsChangesReceived = true;
            buffer.append("D    " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_REPLACE) {
            myIsChangesReceived = true;
            buffer.append("R    " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
            myIsChangesReceived = true;
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                buffer.append("C    " + path + "\n");
            } else {
                buffer.append("A    " + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_EXISTS) {
            myIsChangesReceived = true;
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                buffer.append('C');
            } else {
                buffer.append('E');
            }
            if (event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
                buffer.append('C');
            } else if (event.getPropertiesStatus() == SVNStatusType.MERGED) {
                buffer.append('G');
            } else {
                buffer.append(' ');
            }
            buffer.append("   " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_UPDATE) {
            SVNStatusType propStatus = event.getPropertiesStatus();
            if (event.getNodeKind() == SVNNodeKind.DIR && 
                    (propStatus == SVNStatusType.INAPPLICABLE || propStatus == SVNStatusType.UNKNOWN || propStatus == SVNStatusType.UNCHANGED)) {
                return;
            }
            if (event.getNodeKind() == SVNNodeKind.FILE) {
                if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                    buffer.append('C');
                } else if (event.getContentsStatus() == SVNStatusType.MERGED){
                    buffer.append('G');
                } else if (event.getContentsStatus() == SVNStatusType.CHANGED){
                    buffer.append('U');
                } else {
                    buffer.append(' ');
                }
            } else {
                buffer.append(' ');
            }
            if (event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
                buffer.append('C');
            } else if (event.getPropertiesStatus() == SVNStatusType.MERGED){
                buffer.append('G');
            } else if (event.getPropertiesStatus() == SVNStatusType.CHANGED){
                buffer.append('U');
            } else {
                buffer.append(' ');
            }
            if (buffer.toString().trim().length() > 0) {
                myIsChangesReceived = true;
            }
            if (event.getLockStatus() == SVNStatusType.LOCK_UNLOCKED) {
                buffer.append('B');
            } else {
                buffer.append(' ');
            }
            if (buffer.toString().trim().length() == 0) {
                return;
            }
            buffer.append("  " + path + "\n");
        } else if (event.getAction() == SVNEventAction.MERGE_BEGIN) {
            SVNMergeRange range = event.getMergeRange();
            if (range == null) {
                buffer.append("--- Merging differences between repository URLs into '" + path + "':\n");
            } else {
                long start = range.getStartRevision();
                long end = range.getEndRevision();
                if (start == end || start == end - 1) {
                    buffer.append("--- Merging r" + end + " into '" + path + "':\n");
                } else if (start - 1 == end) {
                    buffer.append("--- Reverse-merging r" + start + " into '" + path + "':\n");
                } else if (start < end) {
                    buffer.append("--- Merging r" + (start + 1) + " through r" + end + " into '" + path + "':\n");
                } else {
                    buffer.append("--- Reverse-merging r" + start + " through r" + (end + 1) + " into '" + path + "':\n");
                }
            }
        } else if (event.getAction() == SVNEventAction.RESTORE) {
            buffer.append("Restored '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.RESTORE) {
            buffer.append("Restored '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
            myIsInExternal = true;
            buffer.append("\nFetching external item into '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_COMPLETED) {
            if (!myIsSuppressLastLine) {
                long rev = event.getRevision();
                if (rev >= 0) {
                    if (myIsExport) {
                        buffer.append(myIsInExternal ? "Exported external at revision " + rev + ".\n" : "Exported revision " + rev + ".\n");
                    } else if (myIsCheckout) {
                        buffer.append(myIsInExternal ? "Checked out external at revision " + rev + ".\n" : "Checked out revision " + rev + ".\n");
                    } else {
                        if (myIsChangesReceived) {
                            buffer.append(myIsInExternal ? "Updated external to revision " + rev + ".\n" : "Updated to revision " + rev + ".\n");
                        } else {
                            buffer.append(myIsInExternal ? "External at revision " + rev + ".\n" : "At revision " + rev + ".\n");
                        }
                    }
                } else {
                    if (myIsExport) {
                        buffer.append(myIsInExternal ? "External export complete.\n" : "Export complete.\n");
                    } else if (myIsCheckout) {
                        buffer.append(myIsInExternal ? "External checkout complete.\n" : "Checkout complete.\n");
                    } else {
                        buffer.append(myIsInExternal ? "External update complete.\n" : "Update complete.\n");
                    }
                }
            }
            if (myIsInExternal) {
                buffer.append('\n');
                myIsInExternal = false;
            }
        } else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            buffer.append("Sending        " + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            if (SVNProperty.isBinaryMimeType(event.getMimeType())) {
                buffer.append("Adding  (bin)  " + path + "\n");
            } else {
                buffer.append("Adding         " + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            buffer.append("Deleting       " + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            buffer.append("Replacing      " + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            if (!myIsDeltaSent) {
                myIsDeltaSent = true;
                buffer.append("Transmitting file data ");
            }
            buffer.append('.');
        } else if (event.getAction() == SVNEventAction.ADD) {
            if (SVNProperty.isBinaryMimeType(event.getMimeType())) {
                buffer.append("A  (bin)  " + path + "\n");
            } else {
                buffer.append("A         " + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.DELETE) {
            buffer.append("D         " + path + "\n");
        } else if (event.getAction() == SVNEventAction.REVERT) {
            buffer.append("Reverted '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.FAILED_REVERT) {
            buffer.append("Failed to revert '" + path + "' -- try updating instead.\n");
        } else if (event.getAction() == SVNEventAction.LOCKED) {
            buffer.append("'" + path + "' locked by user '" + event.getLock().getOwner() + "'.\n");
        } else if (event.getAction() == SVNEventAction.UNLOCKED) {
            buffer.append("'" + path + "' unlocked.\n");
        } else if (event.getAction() == SVNEventAction.LOCK_FAILED || event.getAction() == SVNEventAction.UNLOCK_FAILED) {
            myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] {event.getErrorMessage().getErrorCode()},
                myEnvironment.isQuiet());
            return;
        } else if (event.getAction() == SVNEventAction.RESOLVED) {
            buffer.append("Resolved conflicted state of '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_SET) {
            buffer.append("Path '" + path + "' is now a member of changelist '" + event.getChangelistName() + "'.\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_CLEAR) {
            buffer.append("Path '" + path + "' is no longer a member of a changelist.\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_FAILED) {
            myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] {event.getErrorMessage().getErrorCode()},
                    myEnvironment.isQuiet());
            return;
        }
        if (buffer.length() > 0) {
            SVNDebugLog.getDefaultLog().info(buffer.toString());
            out.print(buffer);
        }
    }

    public void checkCancelled() throws SVNCancelException {
        myEnvironment.checkCancelled();
    }

}
