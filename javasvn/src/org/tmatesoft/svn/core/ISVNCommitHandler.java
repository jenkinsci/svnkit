/*
 * ISVNCommitHandler.java, created 25.10.2004
 * 
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

package org.tmatesoft.svn.core;

/**
 * @author TMate Software Ltd.
 */
public interface ISVNCommitHandler {
    
    /**
     * 
     * @param tobeCommited array elements could be set to null to suppress commit of certain items
     * @return null to cancel commit or commit message
     */
    public String handleCommit(SVNStatus[] tobeCommited);
    

}
