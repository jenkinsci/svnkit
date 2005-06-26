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

import org.tmatesoft.svn.core.io.SVNCancelException;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/*
 * This class is an implementation of ISVNEventHandler intended to reflect the 
 * status of a commit operation. Think of an SVN*Client's operation (for example, 
 * committing a working copy with invoking SVNCommitClient.doCommit(..)) as of a 
 * number of actions it performs, - for example, for committing, a current action 
 * can be deleting a directory or adding a file. And there can be several of them. 
 * So, the SVN*Client* notifies an ISVNEventHandler provided by an implementor of 
 * each action it's running. Information on each action is incapsulated by SVNEvent
 * and is reported by ISVNEventHandler.svnEvent(SVNEvent event, double progress).    
 */
public class CommitEventListener implements ISVNEventHandler {
    /*
     * progress - will be the current percentage stage of an operation (how many of
     * operation has passed) 
     */
    public void handleEvent(SVNEvent event, double progress) {
        /*
         * Gets the current action. An action is represented by SVNEventAction.
         * In case of a commit a current action can be determined via 
         * SVNEvent.getAction() and  SVNEventAction.COMMIT_-like constants. 
         */
        SVNEventAction action = event.getAction();
        if (action == SVNEventAction.COMMIT_MODIFIED) {
            System.out.println("Sending   " + event.getPath());
        } else if (action == SVNEventAction.COMMIT_DELETED) {
            System.out.println("Deleting   " + event.getPath());
        } else if (action == SVNEventAction.COMMIT_REPLACED) {
            System.out.println("Replacing   " + event.getPath());
        } else if (action == SVNEventAction.COMMIT_DELTA_SENT) {
            System.out.println("Transmitting file data....");
        } else if (event.getAction() == SVNEventAction.COMMIT_ADDED) {
            /*
             * Gets the MIME-type of the item.
             */
            String mimeType = event.getMimeType();
            if (SVNWCUtil.isBinaryMimetype(mimeType)) {
                /*
                 * If the item is a file and binary
                 */
                System.out.println("Adding  (bin)  "
                        + event.getPath());
            } else {
                System.out.println("Adding         "
                        + event.getPath());
            }
        }
    }
    
    /*
     * Should be implemented to react on a user's cancel of the operation. 
     */
    public void checkCancelled() throws SVNCancelException {
    }

}