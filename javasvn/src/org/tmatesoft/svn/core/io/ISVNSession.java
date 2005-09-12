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
package org.tmatesoft.svn.core.io;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface ISVNSession {
    
    public boolean keepConnection();
    
    public void saveCommitMessage(SVNRepository repository, long revision, String message);

    public String getCommitMessage(SVNRepository repository, long revision);
    
    public boolean hasCommitMessage(SVNRepository repository, long revision);
    
    public ISVNSession DEFAULT = new ISVNSession() {
        public boolean keepConnection() {
            return false;
        }
        public void saveCommitMessage(SVNRepository repository, long revision, String message) {
        }
        public String getCommitMessage(SVNRepository repository, long revision) {
            return null;
        }
        public boolean hasCommitMessage(SVNRepository repository, long revision) {
            return false;
        }
    };

}
