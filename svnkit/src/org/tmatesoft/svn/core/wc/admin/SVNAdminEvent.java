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
 * The <b>SVNAdminEvent</b> is a type of an event used to notify callers' handlers 
 * in several methods of <b>SVNAdminClient</b>. 
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @since   1.1.1
 */
public class SVNAdminEvent {
    private String myTxnName;
    private File myTxnDir;
    private long myRevision;
    private long myOriginalRevision;
    private SVNAdminEventAction myAction;
    
    /**
     * Creates a new event.
     * 
     * @param revision               a new committed revision
     * @param originalRevision       the original revision
     * @param action                 an event action                 
     */
    public SVNAdminEvent(long revision, long originalRevision, SVNAdminEventAction action) {
        myRevision = revision;
        myOriginalRevision = originalRevision;
        myAction = action;
    }

    /**
     * Creates a new event.
     * 
     * @param revision    a revision number
     * @param action      an event action
     */
    public SVNAdminEvent(long revision, SVNAdminEventAction action) {
        myRevision = revision;
        myOriginalRevision = -1;
        myAction = action;
    }

    /**
     * Creates a new event.
     * 
     * @param txnName   a transaction name
     * @param txnDir    a transaction directory location
     * @param action    an event action
     */
    public SVNAdminEvent(String txnName, File txnDir, SVNAdminEventAction action) {
        myTxnName = txnName;
        myTxnDir = txnDir;
        myRevision = -1;
        myOriginalRevision = -1;
        myAction = action;
    }

    /**
     * Returns the type of an action this event is fired for.
     * 
     * @return event action
     */
    public SVNAdminEventAction getAction() {
        return myAction;
    }

    /**
     * Returns the original revision from which a {@link #getRevision() new one} 
     * is loaded. 
     *  
     * @return an original revision number met in a dumpfile
     */
    public long getOriginalRevision() {
        return myOriginalRevision;
    }

    /**
     * Returns a revision.
     * 
     * <p>
     * For {@link SVNAdminClient#doDump(File, java.io.OutputStream, org.tmatesoft.svn.core.wc.SVNRevision, org.tmatesoft.svn.core.wc.SVNRevision, boolean, boolean) dump} 
     * operations it means a next dumped revision. For {@link SVNAdminClient#doLoad(File, java.io.InputStream, boolean, boolean, SVNUUIDAction, String) load} 
     * operations it means a new committed revision. 
     *   
     * @return a revision number
     */
    public long getRevision() {
        return myRevision;
    }

    /**
     * Returns a transaction directory 
     * 
     * <p>
     * Relevant for both {@link SVNAdminClient#doListTransactions(File) SVNAdminClient.doListTransactions()} 
     * and {@link SVNAdminClient#doRemoveTransactions(File, String[]) SVNAdminClient.doRemoveTransactions()} 
     * operations.
     * 
     * @return txn directory
     */
    public File getTxnDir() {
        return myTxnDir;
    }

    /**
     * Returns a transaction name.
     *
     * <p>
     * Relevant for both {@link SVNAdminClient#doListTransactions(File) SVNAdminClient.doListTransactions()} 
     * and {@link SVNAdminClient#doRemoveTransactions(File, String[]) SVNAdminClient.doRemoveTransactions()} 
     * operations.
     * 
     * @return txn name
     */
    public String getTxnName() {
        return myTxnName;
    }
    
}
