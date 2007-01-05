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
package org.tmatesoft.svn.core.wc.admin;

import java.io.File;


/**
 * @version 1.1
 * @author  TMate Software Ltd.
 */
public class SVNAdminEvent {
    private String myTxnName;
    private File myTxnDir;
    private long myRevision;
    private long myOriginalRevision;
    private SVNAdminEventAction myAction;
    
    /**
     * @param revision
     * @param originalRevision
     * @param action
     */
    public SVNAdminEvent(long revision, long originalRevision, SVNAdminEventAction action) {
        myRevision = revision;
        myOriginalRevision = originalRevision;
        myAction = action;
    }

    /**
     * @param revision
     * @param action
     */
    public SVNAdminEvent(long revision, SVNAdminEventAction action) {
        myRevision = revision;
        myOriginalRevision = -1;
        myAction = action;
    }

    /**
     * @param txnName
     * @param txnDir
     * @param action
     */
    public SVNAdminEvent(String txnName, File txnDir, SVNAdminEventAction action) {
        myTxnName = txnName;
        myTxnDir = txnDir;
        myRevision = -1;
        myOriginalRevision = -1;
        myAction = action;
    }

    public SVNAdminEventAction getAction() {
        return myAction;
    }

    public long getOriginalRevision() {
        return myOriginalRevision;
    }

    public long getRevision() {
        return myRevision;
    }

    public File getTxnDir() {
        return myTxnDir;
    }

    public String getTxnName() {
        return myTxnName;
    }
    
}
