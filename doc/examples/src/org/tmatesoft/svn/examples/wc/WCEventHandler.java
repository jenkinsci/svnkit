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

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;

/*
 * This class is an implementation of ISVNEventHandler intended to reflect the 
 * status of Working Copy items for some certain operations initiated by the developer. 
 * So, the SVN*Client notifies an ISVNEventHandler provided by an implementor of each 
 * action it's running. Information on each action is incapsulated by SVNEvent and is
 * reported by ISVNEventHandler.svnEvent(SVNEvent event, double progress).    
 */
public class WCEventHandler implements ISVNEventHandler {
    /*
     * progress - will be the current percentage stage of an operation (how many of
     * operation has passed) 
     */
    public void handleEvent(SVNEvent event, double progress) {
        /*
         * Gets the current action. An action is represented by SVNEventAction.
         */
        SVNEventAction action = event.getAction();
        if (action == SVNEventAction.ADD){
            /*
             * The item is scheduled for addition.
             */
            System.out.println("A     " + event.getPath());
            return;
        }else if (action == SVNEventAction.COPY){
            /*
             * The item is scheduled for addition with history - copied
             * in other words.
             */
            System.out.println("A  +  " + event.getPath());
            return;
        }else if (action == SVNEventAction.DELETE){
            /*
             * The item is scheduled for deletion. 
             */
            System.out.println("D     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCKED){
            /*
             * The item is locked.
             */
            System.out.println("L     " + event.getPath());
            return;
        } else if (action == SVNEventAction.LOCK_FAILED){
            /*
             * Locking operation failed.
             */
            System.out.println("failed to lock    " + event.getPath());
            return;
        }
    }

    /*
     * Should be implemented to react on a user's cancel of the operation.
     * If the operation was cancelled this method should throw an 
     * SVNCancelException. 
     */
    public void checkCancelled() throws SVNCancelException {
    }

}
