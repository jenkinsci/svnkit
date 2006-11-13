/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;



/**
 * <b>DefaultSVNCommitParameters</b> is the default commit parameters 
 * implementation. 
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 */
public class DefaultSVNCommitParameters implements ISVNCommitParameters {

    /**
     * Says a committer to skip a missing file.
     * 
     * @param  file a missing file
     * @return      {@link ISVNCommitParameters#SKIP SKIP}
     */
    public Action onMissingFile(File file) {
        return SKIP;
    }

    /**
     * Says a committer to abort the operation.
     * 
     * @param  file a missing directory
     * @return      {@link ISVNCommitParameters#ERROR ERROR}
     */
    public Action onMissingDirectory(File file) {
        return ERROR;
    }

}
