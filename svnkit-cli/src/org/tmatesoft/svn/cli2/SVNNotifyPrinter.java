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
package org.tmatesoft.svn.cli2;

import java.io.File;
import java.io.PrintStream;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNNodeKind;
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

    public SVNNotifyPrinter(SVNCommandEnvironment env) {
        myEnvironment = env;
    }

    public void handleEvent(SVNEvent event, double progress) throws SVNException {
        File file = event.getFile();
        String path = null;
        if (file != null) {
            path = myEnvironment.getCurrentTargetRelativePath(file);
            path = SVNCommandUtil.getLocalPath(path);
        } else if (event.getPath() != null) {
            path = event.getPath();
        }
        PrintStream out = myEnvironment.getOut();
        StringBuffer buffer = new StringBuffer();
        if (event.getAction() == SVNEventAction.STATUS_EXTERNAL) {
            buffer.append("\nPerforming status on external item at '" + path + "'\n");
        } else if (event.getAction() == SVNEventAction.STATUS_COMPLETED) {
            String revStr = Long.toString(event.getRevision());
            buffer.append("Status against revision: " + SVNCommandUtil.formatString(revStr, 6, false) + "\n");
        } else if (event.getAction() == SVNEventAction.SKIP) {
            if (event.getContentsStatus() == SVNStatusType.MISSING) {
                buffer.append("Skipped missing target: '" + path + "'\n");
            } else {
                buffer.append("Skipped: '" + path + "'\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_DELETE) {
            buffer.append("D    " + path + "\n");
        } else if (event.getAction() == SVNEventAction.UPDATE_ADD) {
            if (event.getContentsStatus() == SVNStatusType.CONFLICTED) {
                buffer.append("C    " + path + "\n");
            } else {
                buffer.append("A    " + path + "\n");
            }
        } else if (event.getAction() == SVNEventAction.UPDATE_EXISTS) {
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
            long start = range.getStartRevision();
            long end = range.getEndRevision();
            if (start == end || start == end - 1) {
                buffer.append("--- Merging r" + end + ":\n");
            } else if (start - 1 == end) {
                buffer.append("--- Undoing r" + start + ":\n");
            } else if (start < end) {
                buffer.append("--- Merging r" + (start + 1) + " through r" + end + ":\n");
            } else {
                buffer.append("--- Undoing r" + start + " through r" + (end + 1) + ":\n");
            }
        }
        if (buffer.length() > 0) {
            SVNDebugLog.getDefaultLog().info(buffer.toString());
            out.print(buffer);
        }
    }

    public void checkCancelled() throws SVNCancelException {
    }

}
