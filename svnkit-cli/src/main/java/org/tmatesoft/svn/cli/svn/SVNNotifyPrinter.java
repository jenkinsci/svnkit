/*
 * ====================================================================
 * Copyright (c) 2004-2012 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli.svn;

import org.tmatesoft.svn.cli.SVNCommandUtil;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchHunk;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchHunkInfo;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

import java.io.File;
import java.io.PrintStream;


/**
 * @version 1.3
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

    private int myTextConflicts = 0;
    private int myPropConflicts = 0;
    private int myTreeConflicts = 0;
    private int mySkippedPaths = 0;

    private boolean myHasExternalErrors;
    private boolean myHasLockingError;

    private boolean myIsWcToReposCopy;

    public SVNNotifyPrinter(SVNCommandEnvironment env) {
        this(env, false, false, false);
    }

    public SVNNotifyPrinter(SVNCommandEnvironment env, boolean isCheckout, boolean isExport, boolean suppressLastLine) {
        myEnvironment = env;
        myIsCheckout = isCheckout;
        myIsExport = isExport;
        myIsSuppressLastLine = suppressLastLine;
    }
    
    public void setWcToReposCopy(boolean wcToReposCopy) {
        myIsWcToReposCopy = wcToReposCopy;
    }
    
    public boolean isWcToReposCopy() {
        return myIsWcToReposCopy;
    }
    
    public boolean hasExternalErrors() {
        return myHasExternalErrors;
    }

    public boolean hasLockingErrors() {
        return myHasLockingError;
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
            if (SVNRevision.isValidRevisionNumber(event.getRevision())) {
                String revStr = Long.toString(event.getRevision());
                buffer.append("Status against revision: " + SVNFormatUtil.formatString(revStr, 6, false) + "\n");
            }
        } else if (event.getAction() == SVNEventAction.SKIP) {
            if (event.getErrorMessage() != null && event.getExpectedAction() == SVNEventAction.UPDATE_EXTERNAL) {
                // hack to let external test #14 work.
                myEnvironment.getErr().println(event.getErrorMessage());
            }

            mySkippedPaths++;

            if (event.getContentsStatus() == SVNStatusType.MISSING) {
                buffer.append("Skipped missing target: '" + path + "'\n");
            } else if (path != null) {
                buffer.append("Skipped '" + path + "'\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_SKIP_OBSTRUCTION) {
            mySkippedPaths++;
            buffer.append("Skipped '" + path + "' -- An obstructing working copy was found\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_SKIP_WORKING_ONLY) {
            mySkippedPaths++;
            buffer.append("Skipped '" + path + "' -- Has no versioned parent\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_SKIP_ACCESS_DENINED) {
            mySkippedPaths++;
            buffer.append("Skipped '" + path + "' -- Access denied\n");
        } else if (event.getAction() == SVNEventAction.SKIP_CONFLICTED) {
            mySkippedPaths++;
            buffer.append("Skipped '" + path + "' -- Node remains in conflict\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
            myIsChangesReceived = true;
            buffer.append("D    " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_REPLACE) {
            myIsChangesReceived = true;
            buffer.append("R    " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
            myIsChangesReceived = true;
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                myTextConflicts++;

                buffer.append("C    " + path + "\n");
            } else {
                buffer.append("A    " + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_EXISTS) {
            myIsChangesReceived = true;
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                myTextConflicts++;
                buffer.append('C');
            } else {
                buffer.append('E');
            }
            if (event.getPropertiesStatus() == SVNStatusType.CONFLICTED) {
                myPropConflicts++;
                buffer.append('C');
            } else if (event.getPropertiesStatus() == SVNStatusType.MERGED) {
                buffer.append('G');
            } else {
                buffer.append(' ');
            }
            buffer.append("   " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_UPDATE || event.getAction() == SVNEventAction.MERGE_RECORD_INFO) {
            SVNStatusType propStatus = event.getPropertiesStatus();
            if (event.getNodeKind() == SVNNodeKind.DIR &&
                    (propStatus == SVNStatusType.INAPPLICABLE || propStatus == SVNStatusType.UNKNOWN || propStatus == SVNStatusType.UNCHANGED)) {
                return;
            }
            if (event.getNodeKind() == SVNNodeKind.FILE) {
                if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                    myTextConflicts++;
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
                myPropConflicts++;
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
        } else if (event.getAction() == SVNEventAction.MERGE_RECORD_INFO_BEGIN) {
            SVNMergeRange range = event.getMergeRange();
            if (range == null) {
                buffer.append("--- Recording mergeinfo for merge between repository URLs into '" + path + "':\n");
            } else {
                long start = range.getStartRevision();
                long end = range.getEndRevision();
                if (start == end || start == end - 1) {
                    buffer.append("--- Recording mergeinfo for merge of r" + end + " into '" + path + "':\n");
                } else if (start - 1 == end) {
                    buffer.append("--- Recording mergeinfo for reverse merge of r" + start + " into '" + path + "':\n");
                } else if (start < end) {
                    buffer.append("--- Recording mergeinfo for merge of r" + (start + 1) + " through r" + end + " into '" + path + "':\n");
                } else {
                    buffer.append("--- Recording mergeinfo for reverse merge of r" + start + " through r" + (end + 1) + " into '" + path + "':\n");
                }
            }
        }  else if (event.getAction() == SVNEventAction.FOREIGN_MERGE_BEGIN) {
            SVNMergeRange range = event.getMergeRange();
            if (range == null) {
                buffer.append("--- Merging differences between foreign repository URLs into '" + path + "':\n");
            } else {
                long start = range.getStartRevision();
                long end = range.getEndRevision();
                if (start == end || start == end - 1) {
                    buffer.append("--- Merging (from foreign repository) r" + end + " into '" + path + "':\n");
                } else if (start - 1 == end) {
                    buffer.append("--- Reverse-merging (from foreign repository) r" + start + " into '" + path + "':\n");
                } else if (start < end) {
                    buffer.append("--- Merging (from foreign repository) r" + (start + 1) + " through r" + end + " into '" + path + "':\n");
                } else {
                    buffer.append("--- Reverse-merging (from foreign repository) r" + start + " through r" + (end + 1) + " into '" + path + "':\n");
                }
            }
        } else if (event.getAction() == SVNEventAction.MERGE_ELIDE_INFO) {
            buffer.append("--- Eliding mergeinfo from '" + path + "':\n");
        } else if (event.getAction() == SVNEventAction.TREE_CONFLICT) {
            myTreeConflicts++;
            buffer.append("   C ");
            buffer.append(path);
            buffer.append("\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_SHADOWED_ADD) {
            myIsChangesReceived = true;
            buffer.append("   A ");
            buffer.append(path);
            buffer.append("\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_SHADOWED_UPDATE) {
            myIsChangesReceived = true;
            buffer.append("   U ");
            buffer.append(path);
            buffer.append("\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_SHADOWED_DELETE) {
            myIsChangesReceived = true;
            buffer.append("   D ");
            buffer.append(path);
            buffer.append("\n");
        } else if (event.getAction() == SVNEventAction.RESTORE) {
            buffer.append("Restored '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.RESTORE) {
            buffer.append("Restored '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL) {
            myIsInExternal = true;
            buffer.append("\nFetching external item into '" + path + "':\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_EXTERNAL_REMOVED) {
            buffer.append("Removed external '" + path + "'");
            if (event.getErrorMessage() != null) {
                buffer.append(": " + event.getErrorMessage().getMessage());
            }
            buffer.append("\n");
        } else if (event.getAction() == SVNEventAction.FAILED_EXTERNAL) {
            myHasExternalErrors = true;
            if (myIsInExternal) {
                myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] { event.getErrorMessage().getErrorCode() },
                        myEnvironment.isQuiet());
                myIsInExternal = false;
                return;
            }

            SVNErrorMessage warnMessage = SVNErrorMessage.create(SVNErrorCode.CL_ERROR_PROCESSING_EXTERNALS, "Error handling externals definition for ''{0}'':", path);
            myEnvironment.handleWarning(warnMessage, new SVNErrorCode[] { warnMessage.getErrorCode() },
                    myEnvironment.isQuiet());
            myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] { event.getErrorMessage().getErrorCode() },
                    myEnvironment.isQuiet());
            return;
        } else if(event.getAction() == SVNEventAction.UPDATE_STARTED) {
            if (! (myIsSuppressLastLine || myIsInExternal || myIsCheckout || myIsExport)) {
                buffer.append("Updating '"+path+"':\n");
            }
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
                    } 
                }
            }
            if (myIsInExternal) {
                buffer.append('\n');
                myIsInExternal = false;
            } 
        } else if (event.getAction() == SVNEventAction.COMMIT_MODIFIED) {
            buffer.append((!isWcToReposCopy() ? "Sending        " : "Sending copy of       ") + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            if (SVNProperty.isBinaryMimeType(event.getMimeType())) {
                buffer.append((!isWcToReposCopy() ? "Adding  (bin)  " : "Adding copy of (bin)  ") + path + "\n");
            } else {
                buffer.append((!isWcToReposCopy() ? "Adding         " : "Adding copy of        ") + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.COMMIT_DELETED) {
            buffer.append((!isWcToReposCopy() ? "Deleting       " : "Deleting copy of      ") + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_REPLACED) {
            buffer.append((!isWcToReposCopy() ? "Replacing      " : "Replacing copy of     ") + path + "\n");
        } else if (event.getAction() == SVNEventAction.COMMIT_DELTA_SENT) {
            if (!myIsDeltaSent) {
                myIsDeltaSent = true;
                buffer.append("Transmitting file data ");
            }
            buffer.append('.');
        } else if (event.getAction() == SVNEventAction.ADD || event.getAction() == SVNEventAction.COPY) {
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
        } else if (event.getAction() == SVNEventAction.LOCK_FAILED ||
                event.getAction() == SVNEventAction.UNLOCK_FAILED) {
            myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] {event.getErrorMessage().getErrorCode()},
                myEnvironment.isQuiet());
            myHasLockingError = true;
            return;
        } else if (event.getAction() == SVNEventAction.RESOLVED) {
            buffer.append("Resolved conflicted state of '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_SET) {
            buffer.append("A [" + event.getChangelistName() + "] " + path + "\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_CLEAR) {
            buffer.append("D [" + event.getChangelistName() + "] " + path + "\n");
        } else if (event.getAction() == SVNEventAction.CHANGELIST_MOVED) {
            myEnvironment.handleWarning(event.getErrorMessage(), new SVNErrorCode[] {
                event.getErrorMessage().getErrorCode() }, myEnvironment.isQuiet());
            return;
        } else if (event.getAction() == SVNEventAction.PATCH) {

            myIsChangesReceived = true;

            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                myTextConflicts++;
                buffer.append('C');
            } else if (event.getNodeKind() == SVNNodeKind.FILE) {
                if (event.getContentsStatus() == SVNStatusType.MERGED) {
                    buffer.append('G');
                } else if (event.getContentsStatus() == SVNStatusType.CHANGED) {
                    buffer.append('U');
                }
            }

            if (buffer.length() > 0) {
                buffer.append(' ');
                buffer.append(path);
            }

        } else if (event.getAction() == SVNEventAction.PATCH_APPLIED_HUNK) {

            myIsChangesReceived = true;

            final Object info = event.getInfo();

            if (info == null || !(info instanceof SVNPatchHunkInfo)) {
                return;
            }

            final SVNPatchHunkInfo hi = (SVNPatchHunkInfo) info;
            final SVNPatchHunk hunk = hi.getHunk();

            if (hunk.getOriginal().getStart() != hi.getMatchedLine()) {
                long off;
                String minus;

                if (hi.getMatchedLine() > hunk.getOriginal().getStart()) {
                    off = hi.getMatchedLine() - hunk.getOriginal().getStart();
                    minus = null;
                } else {
                    off = hunk.getOriginal().getStart() - hi.getMatchedLine();
                    minus = "-";
                }

                buffer.append(">         applied hunk @@ -");
                buffer.append(hunk.getOriginal().getStart());
                buffer.append(",");
                buffer.append(hunk.getOriginal().getLength());
                buffer.append(" +");
                buffer.append(hunk.getModified().getStart());
                buffer.append(",");
                buffer.append(hunk.getModified().getLength());
                buffer.append(" @@ with offset ");
                if (null != minus) {
                    buffer.append(minus);
                }
                buffer.append(off);
                if (hi.getFuzz() > 0) {
                    buffer.append(" and fuzz ");
                    buffer.append(hi.getFuzz());
                }
                buffer.append("\n");

            } else if (hi.getFuzz() > 0) {

                buffer.append(">         applied hunk @@ -");
                buffer.append(hunk.getOriginal().getStart());
                buffer.append(",");
                buffer.append(hunk.getOriginal().getLength());
                buffer.append(" +");
                buffer.append(hunk.getModified().getStart());
                buffer.append(",");
                buffer.append(hunk.getModified().getLength());
                buffer.append(" @@ with fuzz ");
                buffer.append(hi.getFuzz());
                buffer.append("\n");

            }

        } else if (event.getAction() == SVNEventAction.PATCH_REJECTED_HUNK) {

            myIsChangesReceived = true;

            final Object info = event.getInfo();

            if (info == null || !(info instanceof SVNPatchHunkInfo)) {
                return;
            }

            final SVNPatchHunkInfo hi = (SVNPatchHunkInfo) info;
            final SVNPatchHunk hunk = hi.getHunk();

            buffer.append(">         rejected hunk @@ -");
            buffer.append(hunk.getOriginal().getStart());
            buffer.append(",");
            buffer.append(hunk.getOriginal().getLength());
            buffer.append(" +");
            buffer.append(hunk.getModified().getStart());
            buffer.append(",");
            buffer.append(hunk.getModified().getLength());
            buffer.append(" @@\n");

        } else if (event.getAction() == SVNEventAction.UPGRADED_PATH) {
        	myIsChangesReceived = true;
            buffer.append("Upgraded '" + path + "'\n");
        }

        if (buffer.length() > 0) {
            SVNDebugLog.getDefaultLog().logFine(SVNLogType.CLIENT, buffer.toString());
            out.print(buffer);
        }
    }

    public void checkCancelled() throws SVNCancelException {
        myEnvironment.checkCancelled();
    }

    public void printConflictStatus(StringBuffer buffer) {
        int textConflicts = 0;
        int propConflicts = 0;
        int treeConflicts = 0;
        int skippedPaths = 0;
        String header = null;
        header = "Summary of conflicts:\n";
        textConflicts = myTextConflicts;
        propConflicts = myPropConflicts;
        treeConflicts = myTreeConflicts;
        skippedPaths = mySkippedPaths;

        if (textConflicts > 0 || propConflicts > 0 || treeConflicts > 0 || skippedPaths > 0) {
            buffer.append(header);
        }

        if (textConflicts > 0) {
            buffer.append("  Text conflicts: ");
            buffer.append(textConflicts);
            buffer.append("\n");
        }

        if (propConflicts > 0) {
            buffer.append("  Property conflicts: ");
            buffer.append(propConflicts);
            buffer.append("\n");
        }

        if (treeConflicts > 0) {
            buffer.append("  Tree conflicts: ");
            buffer.append(treeConflicts);
            buffer.append("\n");
        }

        if (skippedPaths > 0) {
            buffer.append("  Skipped paths: ");
            buffer.append(skippedPaths);
            buffer.append("\n");
        }
    }
}
